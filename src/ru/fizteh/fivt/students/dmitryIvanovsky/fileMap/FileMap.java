package ru.fizteh.fivt.students.dmitryIvanovsky.fileMap;

import ru.fizteh.fivt.storage.structured.ColumnFormatException;
import ru.fizteh.fivt.storage.structured.Storeable;
import ru.fizteh.fivt.storage.structured.Table;
import ru.fizteh.fivt.students.dmitryIvanovsky.shell.CommandShell;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import static ru.fizteh.fivt.students.dmitryIvanovsky.fileMap.FileMapUtils.*;

public class FileMap implements Table, AutoCloseable {

    private final Path pathDb;
    private final CommandShell mySystem;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock write = readWriteLock.writeLock();
    private final Lock read  = readWriteLock.readLock();
    private int tableSize = 0;
    String nameTable;
    MyLazyHashMap tableData;
    ThreadLocal<MyHashMap> changeTable = new ThreadLocal<MyHashMap>() {
        @Override
        protected MyHashMap initialValue() {
            return new MyHashMap();
        }
    };
    volatile boolean tableDrop = false;
    volatile boolean isTableClose = false;
    volatile int sizeDataInFiles;
    FileMapProvider parent;
    List<Class<?>> columnType = new ArrayList<Class<?>>();

    public FileMap(Path pathDb, String nameTable, FileMapProvider parent) throws Exception {
        this.nameTable = nameTable;
        this.pathDb = pathDb;
        this.parent = parent;
        this.mySystem = new CommandShell(pathDb.toString(), false, false);
        this.tableData = new MyLazyHashMap(pathDb.resolve(nameTable), parent, this);

        File theDir = new File(String.valueOf(pathDb.resolve(nameTable)));
        boolean flag = false;
        if (!theDir.exists()) {
            try {
                mySystem.mkdir(new String[]{pathDb.resolve(nameTable).toString()});
            } catch (Exception e) {
                e.addSuppressed(new ErrorFileMap("I can't create a folder table " + nameTable));
                throw e;
            }
            writeSizeTsv(0);
            this.sizeDataInFiles = 0;
        } else {
            flag = true;
        }

        try {
            loadTable(nameTable);
            loadTypeFile(pathDb);
            if (flag) {
                loadSizeFile();
            }
        } catch (Exception e) {
            e.addSuppressed(new ErrorFileMap("Format error storage table " + nameTable));
            throw e;
        }
    }

    private String readFileTsv(String fileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new FileReader(new File(fileName).getAbsoluteFile()))) {
            String s;
            while ((s = in.readLine()) != null) {
                sb.append(s);
            }
        } catch (Exception e) {
            throw new IOException("not found " + fileName, e);
        }
        if (sb.length() == 0) {
            throw new IOException("tsv file is empty");
        }
        return sb.toString();
    }

    private void writeFileTsv() throws FileNotFoundException {
        Path pathTsv = pathDb.resolve(nameTable).resolve("signature.tsv");
        try (PrintWriter out = new PrintWriter(pathTsv.toFile().getAbsoluteFile())) {
            for (int i = 0; i < columnType.size(); ++i) {
                out.print(convertClassToString(columnType.get(i)));
                if (i != columnType.size() - 1) {
                    out.print(" ");
                }
            }
        }
    }

    private void writeSizeTsv(int newSize) throws FileNotFoundException {
        Path pathTsv = pathDb.resolve(nameTable).resolve("size.tsv");
        try (PrintWriter out = new PrintWriter(pathTsv.toFile().getAbsoluteFile())) {
            out.print(newSize);
        }
    }

    private void loadTypeFile(Path pathDb) throws IOException {
        String fileStr = readFileTsv(pathDb.resolve(nameTable).resolve("signature.tsv").toString());
        StringTokenizer token = new StringTokenizer(fileStr);
        while (token.hasMoreTokens()) {
            String tok = token.nextToken();
            Class<?> type = convertStringToClass(tok);
            if (type == null) {
                throw new IllegalArgumentException(String.format("wrong type %s", tok));
            }
            columnType.add(type);
        }
    }

    private void loadSizeFile() throws IOException, ErrorFileMap {
        String fileStr = readFileTsv(pathDb.resolve(nameTable).resolve("size.tsv").toString());
        try {
            this.sizeDataInFiles = Integer.valueOf(fileStr);
        } catch (Exception e) {
            throw new ErrorFileMap("not correct size in size.tsv");
        }
    }

    public FileMap(Path pathDb, String nameTable, FileMapProvider parent, List<Class<?>> columnType) throws Exception {
        this.nameTable = nameTable;
        this.pathDb = pathDb;
        this.parent = parent;
        this.columnType = columnType;
        this.mySystem = new CommandShell(pathDb.toString(), false, false);
        this.tableData = new MyLazyHashMap(pathDb.resolve(nameTable), parent, this);

        File theDir = new File(String.valueOf(pathDb.resolve(nameTable)));
        if (!theDir.exists()) {
            try {
                mySystem.mkdir(new String[]{pathDb.resolve(nameTable).toString()});
            } catch (Exception e) {
                e.addSuppressed(new ErrorFileMap("I can't create a folder table " + nameTable));
                throw e;
            }
            writeSizeTsv(0);
            this.sizeDataInFiles = 0;
        } else {
            loadSizeFile();
        }

        writeFileTsv();

        try {
            loadTable(nameTable);
        } catch (Exception e) {
            e.addSuppressed(new ErrorFileMap("Format error storage table " + nameTable));
            throw e;
        }
    }

    private void loadTable(String nameMap) throws Exception {
        File currentFileMap = pathDb.resolve(nameMap).toFile();
        if (!currentFileMap.isDirectory()) {
            throw new ErrorFileMap(currentFileMap.getAbsolutePath() + " isn't directory");
        }

        File[] listFileMap = currentFileMap.listFiles();

        if (listFileMap == null || listFileMap.length == 0) {
            throw new ErrorFileMap(pathDb + " empty table");
        }

        boolean haveSize = false;
        for (File nameDir : listFileMap) {
            if (nameDir.getName().equals("size.tsv")) {
                haveSize = true;
            }
            if (nameDir.getName().equals("signature.tsv") || nameDir.getName().equals("size.tsv")) {
                continue;
            }
            if (!nameDir.isDirectory()) {
                throw new ErrorFileMap(nameDir.getAbsolutePath() + " isn't directory");
            }
            String nameStringDir = nameDir.getName();
            Pattern p = Pattern.compile("(^[0-9].dir$)|(^1[0-5].dir$)");
            Matcher m = p.matcher(nameStringDir);

            if (!(m.matches() && m.start() == 0 && m.end() == nameStringDir.length())) {
                throw new ErrorFileMap(nameDir.getAbsolutePath() + " wrong folder name");
            }

            File[] listNameDir = nameDir.listFiles();
            if (listNameDir == null || listNameDir.length == 0) {
                throw new ErrorFileMap(nameDir.getAbsolutePath() + " empty dir");
            }
            for (File randomFile : listNameDir) {

                p = Pattern.compile("(^[0-9].dat$)|(^1[0-5].dat$)");
                m = p.matcher(randomFile.getName());
                int lenRandomFile = randomFile.getName().length();
                if (!(m.matches() && m.start() == 0 && m.end() == lenRandomFile)) {
                    throw new ErrorFileMap(randomFile.getAbsolutePath() + " invalid file name");
                }

                try {
                    checkTableFile(randomFile, nameDir.getName());
                } catch (Exception e) {
                    e.addSuppressed(new ErrorFileMap("Error in file " + randomFile.getAbsolutePath()));
                    throw e;
                }
            }
        }
        if (!haveSize) {
            writeSizeTsv(tableSize);
        }
    }

    public void checkTableFile(File randomFile, String nameDir) throws Exception {
        if (randomFile.isDirectory()) {
            throw new ErrorFileMap("data file can't be a directory");
        }
        int intDir = FileMapUtils.getCode(nameDir);
        int intFile = FileMapUtils.getCode(randomFile.getName());

        RandomAccessFile dbFile = null;
        Exception error = null;
        try {
            try {
                dbFile = new RandomAccessFile(randomFile, "rw");
            } catch (Exception e) {
                throw new ErrorFileMap("file doesn't open");
            }
            if (dbFile.length() == 0) {
                throw new ErrorFileMap("file is clear");
            }
            dbFile.seek(0);

            byte[] arrayByte;
            Vector<Byte> vectorByte = new Vector<Byte>();
            boolean isFirstKey = true;
            long shiftLast = dbFile.length();

            while (dbFile.getFilePointer() != shiftLast) {
                byte currentByte = dbFile.readByte();
                if (currentByte == '\0') {
                    int point1 = dbFile.readInt();
                    if (isFirstKey) {
                        shiftLast = point1;
                    }
                    isFirstKey = false;
                    arrayByte = new byte[vectorByte.size()];
                    for (int i = 0; i < vectorByte.size(); ++i) {
                        arrayByte[i] = vectorByte.elementAt(i).byteValue();
                    }
                    String key = new String(arrayByte, StandardCharsets.UTF_8);

                    if (tableData.getHashDir(key) != intDir || tableData.getHashFile(key) != intFile) {
                        throw new ErrorFileMap("wrong key in the file");
                    }
                    tableSize += 1;
                    vectorByte.clear();
                } else {
                    vectorByte.add(currentByte);
                }
            }
        } catch (Exception e) {
            error = e;
            throw error;
        } finally {
            try {
                dbFile.close();
            } catch (Exception e) {
                if (error != null) {
                    error.addSuppressed(e);
                }
            }
        }
    }

    private void refreshTableFiles(Set<String> changedKey) throws Exception {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        writeFileTsv();
        if (changeTable.get().isEmpty()) {
            return;
        }

        Set<Integer> changedFiles = new HashSet<>();
        for (String key : changedKey) {
            changedFiles.add(tableData.getHashDir(key) * tableData.numberFile + tableData.getHashFile(key));
        }

        for (Integer changedFile : changedFiles) {

            Integer numDir = changedFile / tableData.numberFile;
            Integer numFile = changedFile % tableData.numberFile;
            Path refreshDir = pathDb.resolve(nameTable).resolve(numDir.toString() + ".dir");
            File refreshFile = refreshDir.resolve(numFile.toString() + ".dat").toFile();

            if (!tableData.getMap(numDir, numFile).isEmpty()) {
                if (!refreshDir.toFile().exists()) {
                    boolean resMkdir = refreshDir.toFile().mkdir();
                    if (!resMkdir) {
                        throw new ErrorFileMap("I can't create a folder table " + refreshDir.toString());
                    }
                }
                closeTableFile(refreshFile, tableData.getMap(numDir, numFile));
            } else {
                if (refreshFile.exists()) {
                    boolean resRmFile = refreshFile.delete();
                    if (!resRmFile) {
                        throw new ErrorFileMap("I can't delete file " + refreshFile.toString());
                    }
                }
                String[] list = refreshDir.toFile().list();
                if (refreshDir.toFile().isDirectory() && (list == null || list.length == 0)) {
                    boolean resRmDir = refreshDir.toFile().delete();
                    if (!resRmDir) {
                        throw new ErrorFileMap("I can't delete dir " + refreshDir);
                    }
                }
            }
        }
    }

    public void closeTableFile(File randomFile, Map<String, Storeable> curMap) throws Exception {
        if (curMap == null || curMap.isEmpty()) {
            return;
        }
        RandomAccessFile dbFile = null;
        Exception error = null;
        try {
            dbFile = new RandomAccessFile(randomFile, "rw");
            dbFile.setLength(0);
            dbFile.seek(0);
            int len = 0;

            for (String key : curMap.keySet()) {
                len += key.getBytes(StandardCharsets.UTF_8).length + 1 + 4;
            }

            for (String key : curMap.keySet()) {
                dbFile.write(key.getBytes(StandardCharsets.UTF_8));
                dbFile.writeByte(0);
                dbFile.writeInt(len);

                long point = dbFile.getFilePointer();

                dbFile.seek(len);
                Storeable valueStoreable = curMap.get(key);
                String value = parent.serialize(this, valueStoreable);
                dbFile.write(value.getBytes(StandardCharsets.UTF_8));
                len += value.getBytes(StandardCharsets.UTF_8).length;

                dbFile.seek(point);
            }
        } catch (Exception e) {
            error = e;
            throw error;
        } finally {
            try {
                dbFile.close();
            } catch (Exception e) {
                if (error != null) {
                    error.addSuppressed(e);
                }
            }
        }
    }

    public String getName() {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        return nameTable;
    }

    public int changeKey() {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        return changeTable.get().size();
    }

    public Storeable put(String key, Storeable value) throws ColumnFormatException {
        checkArg(key);
        if (value == null) {
            throw new IllegalArgumentException("value can't be null");
        }
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        FileMapStoreable st = null;
        try {
            st = (FileMapStoreable) value;
        } catch (ClassCastException e) {

            boolean valueMoreSize = false;
            try {
                value.getColumnAt(columnType.size());
                valueMoreSize = true;
            } catch (Exception err) {
                valueMoreSize = false;
            }

            if (valueMoreSize) {
                throw new ColumnFormatException("this Storeable can't be use in this table");
            }

            int index = 0;

            while (true) {
                try {
                    if (columnType.get(index) == Integer.class) {
                        value.getIntAt(index);
                    } else if (columnType.get(index) == Long.class) {
                        value.getLongAt(index);
                    } else if (columnType.get(index) == Byte.class) {
                        value.getByteAt(index);
                    } else if (columnType.get(index) == Float.class) {
                        value.getFloatAt(index);
                    } else if (columnType.get(index) == Double.class) {
                        value.getDoubleAt(index);
                    } else if (columnType.get(index) == Boolean.class) {
                        value.getBooleanAt(index);
                    } else if (columnType.get(index) == String.class) {
                        value.getStringAt(index);
                    } else {
                        throw new ColumnFormatException("in ColumnType isn't provide type");
                    }

                    ++index;
                } catch (IndexOutOfBoundsException err) {
                    if (index != columnType.size()) {
                        throw new ColumnFormatException("this Storeable can't be use in this table");
                    }
                    break;
                }
            }

            st = null;
        }

        if (st != null && !st.messageEqualsType(columnType).isEmpty()) {
            throw new ColumnFormatException(st.messageEqualsType(columnType));
        }

        if (changeTable.get().containsKey(key)) {
            Storeable newValue = changeTable.get().get(key);
            if (newValue == null) {
                changeTable.get().put(key, value);
                return null;
            } else {
                Storeable oldValue = changeTable.get().get(key);
                changeTable.get().put(key, value);
                return oldValue;
            }
        } else {
            Storeable oldValue = null;
            read.lock();
            try {
                oldValue = tableData.get(key);
            } finally {
                read.unlock();
            }
            changeTable.get().put(key, value);
            return oldValue;
        }
    }

    public void setDrop() {
        tableDrop = true;
    }

    public Storeable get(String key) {
        checkArg(key);
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }

        Storeable value = null;
            if (changeTable.get().containsKey(key)) {
                Storeable newValue = changeTable.get().get(key);
                if (newValue == null) {
                    value = null;
                } else {
                    value = changeTable.get().get(key);
                    return value;
                }
            } else {
                read.lock();
                try {
                    value = tableData.get(key);
                } finally {
                    read.unlock();
                }
            }

        return value;
    }

    public Storeable remove(String key) {
        checkArg(key);
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }

        Storeable resValue = null;
        Storeable value = null;
        read.lock();
        try {
            value = tableData.get(key);
        } finally {
            read.unlock();
        }

        if (changeTable.get().containsKey(key)) {
            Storeable newValue = changeTable.get().get(key);
            if (value != null) {
                if (newValue == null) {
                    resValue =  null;
                } else {
                    Storeable valueChange = changeTable.get().get(key);
                    changeTable.get().put(key, null);
                    resValue =  valueChange;
                }
            } else {
                changeTable.get().remove(key);
                resValue =  newValue;
            }
        } else {
            if (value != null) {
                changeTable.get().put(key, null);
                resValue =  value;
            } else {
                resValue =  null;
            }
        }

        return resValue;
    }

    public int size() {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }

        int tmpSize = this.sizeDataInFiles;

        for (String key : changeTable.get().keySet()) {

            boolean containsKey;
            read.lock();
            try {
                containsKey = tableData.containsKey(key);
            } finally {
                read.unlock();
            }

            if (containsKey) {
                if (changeTable.get().get(key) == null) {
                    --tmpSize;
                }
            } else {
                if (changeTable.get().get(key) != null) {
                    ++tmpSize;
                }
            }

        }

        return tmpSize;
    }

    public int commit() {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        int count = 0;
        int currentSize = this.size();
        Set<String> changedKey = new HashSet<>();
        Exception err = null;
        write.lock();
        try {
            for (String key : changeTable.get().keySet()) {
                Storeable value = changeTable.get().get(key);
                Storeable oldValue = null;
                oldValue = tableData.get(key);
                if (value == null) {
                    if (oldValue != null) {
                        ++count;
                        changedKey.add(key);
                    }
                    tableData.remove(key);
                } else {
                    if (oldValue == null) {
                        ++count;
                        changedKey.add(key);
                    } else {
                        if (!parent.serialize(this, value).equals(parent.serialize(this, oldValue))) {
                            ++count;
                            changedKey.add(key);
                        }
                    }
                    tableData.put(key, changeTable.get().get(key));
                }
            }
        } catch (Exception e) {
            err = e;
        } finally {
            try {
                refreshTableFiles(changedKey);
                writeSizeTsv(currentSize);
                this.sizeDataInFiles = currentSize;
            } catch (Exception errRefresh) {
                if (err == null) {
                    err = errRefresh;
                } else {
                    err.addSuppressed(errRefresh);
                }
            } finally {
                write.unlock();
                changeTable.get().clear();
                if (err != null) {
                    throw new IllegalStateException(err);
                }
            }
        }
        return count;
    }

    public int rollback() {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        int count = 0;
        for (String key : changeTable.get().keySet()) {
            Storeable diffValue = changeTable.get().get(key);

            Storeable value = null;
            read.lock();
            try {
                value = tableData.get(key);
            } finally {
                read.unlock();
            }

            if (diffValue == null) {
                if (value != null) {
                    ++count;
                }
            } else {
                if (value == null) {
                    ++count;
                } else {
                    if (!parent.serialize(this, diffValue).equals(parent.serialize(this, value))) {
                        ++count;
                    }
                }
            }
        }
        changeTable.get().clear();
        return count;
    }

    @Override
    public int getColumnsCount() {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        return columnType.size();
    }

    @Override
    public Class<?> getColumnType(int columnIndex) throws IndexOutOfBoundsException {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        return columnType.get(columnIndex);
    }

    public String toString() {
        if (tableDrop || isTableClose) {
            throw new IllegalStateException("table was deleted");
        }
        return String.format("%s[%s]", getClass().getSimpleName(), pathDb.resolve(nameTable).toAbsolutePath());
    }

    @Override
    public void close() {
        write.lock();
        try {
            if (!isTableClose) {
                rollback();
                parent.closeTable(getName());
                isTableClose = true;
            }
        } finally {
            write.unlock();
        }
    }
}
