package ru.fizteh.fivt.students.ryabovaMaria.fileMap;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.fizteh.fivt.storage.structured.Storeable;
import ru.fizteh.fivt.storage.structured.Table;
import ru.fizteh.fivt.storage.structured.TableProvider;

public class TableCommands implements Table {
    private ArrayList<Class<?>> types;
    private TableProvider tableProvider;
    private File tableDir;
    private HashMap<String, String>[][] list;
    private HashMap<String, String>[][] lastList;
    private HashMap<Integer, String> update;
    private int hashCode;
    private int numberOfDir;
    private int numberOfFile;
    
    TableCommands(File directory, List<Class<?>> types, TableProvider tableProvider) throws IOException {
        this.tableProvider = tableProvider;
        this.types = new ArrayList(types);
        list = new HashMap[16][16];
        lastList = new HashMap[16][16];
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                list[i][j] = new HashMap<String, String>();
                lastList[i][j] = new HashMap<String, String>();
            }
        }
        tableDir = directory;
        update = new HashMap<Integer, String>();
        isCorrectTable();
    }
    
    private void isCorrectTable() throws IOException {
        String[] listOfDirs = tableDir.list();
        for (int i = 0; i < listOfDirs.length; ++i) {
            boolean ok = false;
            int value = -1;
            for (int j = 0; j < 16; ++j) {
                String validName = String.valueOf(j) + ".dir";
                if (listOfDirs[i].equals(validName)) {
                    ok = true;
                    value = j;
                    break;
                }
            }
            if (ok) {
                isCorrectDir(value, listOfDirs[i]);
            } else {
                if (!(listOfDirs[i].equals("signature.tsv") && new File(tableDir, listOfDirs[i]).isFile())) {
                    throw new IllegalArgumentException("Incorrect table");
                }
            }
        }
    }
    
    private void isCorrectDir(int numOfDir, String name) throws IOException {
        File curDir = tableDir.toPath().resolve(name).normalize().toFile();
        String[] listOfFiles = curDir.list();
        if (listOfFiles.length == 0) {
            throw new IllegalArgumentException("empty dir"); 
        }
        for (int i = 0; i < listOfFiles.length; ++i) {
            boolean ok = false;
            int value = -1;
            for (int j = 0; j < 16; ++j) {
                String validName = String.valueOf(j) + ".dat";
                if (listOfFiles[i].equals(validName)) {
                    ok = true;
                    value = j;
                    break;
                }
            }
            if (ok) {
                isCorrectFile(numOfDir, value, curDir, listOfFiles[i]);
            } else {
                throw new IllegalArgumentException("Incorrect table");
            }
        }
    }
    
    private void isCorrectFile(int numOfDir, int numOfFile, File current, String name) throws IOException {
        File dbFile = current.toPath().resolve(name).normalize().toFile();
        if (!dbFile.exists()) {
            throw new IllegalArgumentException("Incorrect table");
        }
        if (!dbFile.isFile()) {
            throw new IllegalArgumentException("Incorrect table");
        }
        RandomAccessFile db = null;
        try {
            db = new RandomAccessFile(dbFile, "rw");

            long curPointer = 0;
            long lastPointer = 0;
            long length = db.length();
            if (length == 0) {
                throw new IllegalArgumentException("empty file");
            }
            db.seek(0);
            String lastKey = "";
            int lastOffset = 0;
            while (curPointer < length) {
                byte curByte = db.readByte();
                if (curByte == '\0') {
                    byte[] byteKey = new byte[(int) curPointer - (int) lastPointer];
                    curPointer = db.getFilePointer();
                    db.seek(lastPointer);
                    db.readFully(byteKey);
                    db.seek(curPointer);
                    String currentKey = new String(byteKey, "UTF-8");
                    int currentHashCode = Math.abs(currentKey.hashCode());
                    int currentNumOfDir = currentHashCode % 16;
                    int currentNumOfFile = currentHashCode / 16 % 16;
                    if (currentNumOfDir != numOfDir || currentNumOfFile != numOfFile) {
                        throw new IllegalArgumentException("Incorrect file " + dbFile.toString());
                    }
                    int offset = db.readInt();
                    if (!lastKey.isEmpty()) {
                        byte[] byteValue = new byte[offset - lastOffset];
                        curPointer = db.getFilePointer();
                        db.seek(lastOffset);
                        db.readFully(byteValue);
                        String lastValue = new String(byteValue, "UTF-8");
                        db.seek(curPointer);
                        if (lastList[numOfDir][numOfFile].containsKey(lastKey)) {
                            System.err.println(lastKey + " meets twice in db.dat");
                            System.exit(1);
                        }
                        lastList[numOfDir][numOfFile].put(lastKey, lastValue);
                    }
                    lastOffset = offset;
                    lastKey = currentKey;
                    lastPointer = db.getFilePointer();
                }
                curPointer = db.getFilePointer();
            }
            if (lastOffset == 0 || lastKey.isEmpty()) {
                throw new IllegalArgumentException("Incorrect file " + dbFile.toString());
            }
            byte[] byteValue = new byte[(int) length - lastOffset];
            db.seek(lastOffset);
            db.readFully(byteValue);
            String lastValue = new String(byteValue, "UTF-8");
            if (lastList[numOfDir][numOfFile].containsKey(lastKey)) {
                throw new IllegalArgumentException("Incorrect file" + dbFile.toString());
            }
            lastList[numOfDir][numOfFile].put(lastKey, lastValue);
            db.close();
        } catch(Exception e) {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ex) {
                }
            }
            throw new IOException("Incorrect table");
        }
    }
    
    @Override
    public String getName() {
        return tableDir.getName();
    }

    private void getUsingDatFile(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Bad key");
        }
        hashCode = Math.abs(key.hashCode());
        numberOfDir = hashCode % 16;
        numberOfFile = hashCode / 16 % 16;
    }

    @Override
    public Storeable get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Bad key");
        }
        getUsingDatFile(key);
        String value = list[numberOfDir][numberOfFile].get(key);
        if (value == null) {
            if (!list[numberOfDir][numberOfFile].containsKey(key)) {
                value = lastList[numberOfDir][numberOfFile].get(key);
            }
        }
        try {
            return tableProvider.deserialize(this, value);
        } catch (ParseException e) {
            return null;
        }
    }

    @Override
    public Storeable put(String key, Storeable value) {
        if (value == null || key == null || key.isEmpty() || key.matches(".*\\s.*")) {
            throw new IllegalArgumentException("Bad args");
        }
        try {
            getUsingDatFile(key);
            update.put(numberOfDir * 16 + numberOfFile, " ");
            String stringValue = tableProvider.serialize(this, value);
            String lastValue = list[numberOfDir][numberOfFile].put(key, stringValue);
            if (lastValue == null) {
                if (!list[numberOfDir][numberOfFile].containsKey(key)) {
                    lastValue = lastList[numberOfDir][numberOfFile].get(key);
                }
            }
            Storeable answer = tableProvider.deserialize(this, lastValue);
            return answer;
        } catch (ParseException | NullPointerException e) {
            throw new IllegalArgumentException("incorrect args ");
        }
    }

    @Override
    public Storeable remove(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Bad args");
        }
        getUsingDatFile(key);
        update.put(numberOfDir * 16 + numberOfFile, null);
        String value = list[numberOfDir][numberOfFile].remove(key);
        if (value == null) {
            value = lastList[numberOfDir][numberOfFile].get(key);
            list[numberOfDir][numberOfFile].put(key, null);
        }
        try {
            return tableProvider.deserialize(this, value);
        } catch (ParseException e) {
            return null;
        }
    }

    private int getCountSize(int first, int second) {
        int result = lastList[first][second].size();
        //System.out.println(result);
        for (Map.Entry entry : list[first][second].entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if (value == null){
                if (lastList[first][second].containsKey(key)) {
                    --result;
                }
            } else {
                if (!lastList[first][second].containsKey(key)) {
                    ++result;
                }
            }
        }
        return result;
    }
    
    @Override
    public int size() {
        int countSize = 0;
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                countSize += getCountSize(i, j);
            }
        }
        return countSize; 
    }
    
    private void writeIntoFile(int numOfDir, int numOfFile) throws IOException {
        String dirString = String.valueOf(numOfDir) + ".dir";
        String fileString = String.valueOf(numOfFile) + ".dat";
        File dbDir = tableDir.toPath().resolve(dirString).normalize().toFile();
        if (!dbDir.isDirectory()) {
            if (!dbDir.mkdir()) {
                throw new IOException("incorrect file");
            }
        }
        File dbFile = dbDir.toPath().resolve(fileString).normalize().toFile();
        if (list[numOfDir][numOfFile].isEmpty()) {
            dbFile.delete();
            if (dbDir.list().length == 0) {
                if (!dbDir.delete()) {
                    throw new IOException("incorrect file");
                }
            }
            return;
        }
        RandomAccessFile db = null;
        try {
            db = new RandomAccessFile(dbFile, "rw");
            db.setLength(0);
            long[] pointers = new long[getCountSize(numOfDir, numOfFile)];
            int counter = 0;
            for (Map.Entry entry : lastList[numOfDir][numOfFile].entrySet()) {
                String key = (String) entry.getKey();
                String newValue = list[numOfDir][numOfFile].get(key);
                if (newValue == null && list[numOfDir][numOfFile].containsKey(key)) {
                    continue;
                }
                db.write(key.getBytes("UTF-8"));
                db.write("\0".getBytes("UTF-8"));
                pointers[counter] = db.getFilePointer();
                db.seek(pointers[counter] + 4);
                ++counter;
            }
            for (Map.Entry entry : list[numOfDir][numOfFile].entrySet()) {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                if (value != null) {
                    db.write(key.getBytes("UTF-8"));
                    db.write("\0".getBytes("UTF-8"));
                    pointers[counter] = db.getFilePointer();
                    db.seek(pointers[counter] + 4);
                    ++counter;
                }
            }
            counter = 0;
            for (Map.Entry entry : lastList[numOfDir][numOfFile].entrySet()) {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                String newValue = list[numOfDir][numOfFile].get(key);
                if (newValue == null && list[numOfDir][numOfFile].containsKey(key)) {
                    continue;
                }
                if (newValue != null) {
                    value = newValue;
                }
                int curPointer = (int) db.getFilePointer();
                db.seek(pointers[counter]);
                db.writeInt(curPointer);
                db.seek(curPointer);
                db.write(value.getBytes("UTF-8"));
                ++counter;
            }
            for (Map.Entry entry : list[numOfDir][numOfFile].entrySet()) {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                if (value != null) {
                    int curPointer = (int) db.getFilePointer();
                    db.seek(pointers[counter]);
                    db.writeInt(curPointer);
                    db.seek(curPointer);
                    db.write(value.getBytes("UTF-8"));
                    ++counter;
                }
            }
            /*Iterator<Map.Entry<String, String>> it;
            it = list[numOfDir][numOfFile].entrySet().iterator();
            long[] pointers = new long[list[numOfDir][numOfFile].size()];
            int counter = 0;
            while (it.hasNext()) {
                Map.Entry<String, String> m = (Map.Entry<String, String>) it.next();
                String key = m.getKey();
                db.write(key.getBytes("UTF-8"));
                db.write("\0".getBytes("UTF-8"));
                pointers[counter] = db.getFilePointer();
                db.seek(pointers[counter] + 4);
                ++counter;
            }
            it = list[numOfDir][numOfFile].entrySet().iterator();
            counter = 0;
            while (it.hasNext()) {
                Map.Entry<String, String> m = (Map.Entry<String, String>) it.next();
                String value = m.getValue();
                int curPointer = (int) db.getFilePointer();
                db.seek(pointers[counter]);
                db.writeInt(curPointer);
                db.seek(curPointer);
                db.write(value.getBytes("UTF-8"));
                ++counter;
            }*/
            db.close();
        } catch (Exception e) {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ex) {
                }
            }
            throw new IOException("incorrect file");
        }

    }
    
    public int countChanges(boolean isWrite) throws IOException {
        int result = 0;
        for (Integer file : update.keySet()) {
            numberOfFile = file % 16;
            numberOfDir = (file - numberOfFile) / 16;
            if (isWrite) {
                writeIntoFile(numberOfDir, numberOfFile);
            }
            for (Map.Entry entry : list[numberOfDir][numberOfFile].entrySet()) {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                if (value == null) {
                    if (lastList[numberOfDir][numberOfFile].containsKey(key)) {
                        ++result;
                    }
                } else {
                    if (!value.equals(lastList[numberOfDir][numberOfFile].get(key))) {
                        ++result;
                    }
                }
            }
        }
        return result;
    }
    
    private void assigment(HashMap<String, String>[][] first, HashMap<String, String>[][] second) {
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                first[i][j].clear();
                for (Map.Entry entry : second[i][j].entrySet()) {
                    first[i][j].put((String) entry.getKey(), (String) entry.getValue());
                }
            }
        }
    }
    
    @Override
    public int commit() throws IOException {
        int result = countChanges(true);
        //assigment(lastList, list);
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                list[i][j].clear();
            }
        }
        isCorrectTable();
        return result;
    }

    @Override
    public int rollback() {
        int result = 0;
        try {
            result = countChanges(false);
        } catch (IOException e) {
        }
        for (int i = 0; i < 16; ++i) {
            for(int j = 0; j < 16; ++j) {
                list[i][j].clear();
            }
        }
        return result;
    }

    @Override
    public int getColumnsCount() {
        return types.size();
    }

    @Override
    public Class<?> getColumnType(int columnIndex) throws IndexOutOfBoundsException {
        if (columnIndex < 0 || columnIndex >= types.size()) {
            throw new IndexOutOfBoundsException();
        }
        return types.get(columnIndex);
    }
}
