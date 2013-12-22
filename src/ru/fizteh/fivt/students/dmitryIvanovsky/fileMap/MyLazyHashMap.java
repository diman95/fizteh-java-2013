package ru.fizteh.fivt.students.dmitryIvanovsky.fileMap;

import ru.fizteh.fivt.storage.structured.Storeable;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class MyLazyHashMap {
    Path pathTable;
    int size;
    final int numberDir = 16;
    final int numberFile = 16;
    Map<String, Storeable>[][] arrayMap = new WeakHashMap[numberDir][numberFile];
    FileMapProvider provider;
    FileMap fileMap;

    MyLazyHashMap(Path pathTable, FileMapProvider provider, FileMap fileMap) {
        for (int i = 0; i < numberDir; ++i) {
            for (int j = 0; j < numberFile; ++j) {
                arrayMap[i][j] = new WeakHashMap<>();
            }
        }
        size = 0;
        this.pathTable = pathTable;
        this.provider = provider;
        this.fileMap = fileMap;
    }

    public Storeable loadTableFile(String refKey) throws Exception {
        int intDir = getHashDir(refKey);
        int intFile = getHashFile(refKey);
        String strDir = String.valueOf(intDir) + ".dir";
        File randomFile = pathTable.resolve(strDir).resolve(String.valueOf(intFile) + ".dat").toFile();

        if (!randomFile.exists()) {
            throw new FileIsNotExist();
        }

        if (randomFile.isDirectory()) {
            throw new ErrorFileMap("data file can't be a directory");
        }

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
            long separator = -1;

            while (dbFile.getFilePointer() != dbFile.length()) {
                byte currentByte = dbFile.readByte();
                if (currentByte == '\0') {
                    int point1 = dbFile.readInt();
                    if (separator == -1) {
                        separator = point1;
                    }
                    long currentPoint = dbFile.getFilePointer();

                    while (dbFile.getFilePointer() != separator) {
                        if (dbFile.readByte() == '\0') {
                            break;
                        }
                    }

                    int point2;
                    if (dbFile.getFilePointer() == separator) {
                        point2 = (int) dbFile.length();
                    } else {
                        point2 = dbFile.readInt();
                    }

                    dbFile.seek(point1);

                    arrayByte = new byte[point2 - point1];
                    dbFile.readFully(arrayByte);
                    String value = new String(arrayByte, StandardCharsets.UTF_8);

                    arrayByte = new byte[vectorByte.size()];
                    for (int i = 0; i < vectorByte.size(); ++i) {
                        arrayByte[i] = vectorByte.elementAt(i).byteValue();
                    }
                    String key = new String(arrayByte, StandardCharsets.UTF_8);

                    if (getHashDir(key) != intDir || getHashFile(key) != intFile) {
                        throw new ErrorFileMap("wrong key in the file");
                    }

                    arrayMap[intDir][intFile].put(key, provider.deserialize(fileMap, value));
                    if (key.equals(refKey)) {
                        arrayMap[intDir][intFile].put(key, provider.deserialize(fileMap, value));
                        return provider.deserialize(fileMap, value);
                    }

                    vectorByte.clear();
                    dbFile.seek(currentPoint);
                } else {
                    vectorByte.add(currentByte);
                }
            }
            return null;
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

    private Map<String, Storeable> initMap(String key) {
        return arrayMap[getHashDir(key)][getHashFile(key)];
    }

    public Storeable get(String key) {
        try {
            Storeable res = initMap(key).get(key);
            if (res == null) {
                return loadTableFile(key);
            } else {
                return res;
            }
        } catch (FileIsNotExist e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Error with open file with key = " + key, e);
        }
    }

    public void put(String key, Storeable value) {
        try {
            initMap(key).put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Error with put " + key, e);
        }

    }

    public void remove(String key) {
        try {
            initMap(key).remove(key);
        } catch (Exception e) {
            throw new IllegalStateException("Error with open file with key = " + key, e);
        }
    }

    public int getHashDir(String key) {
        int hashcode = key.hashCode();
        int ndirectory = hashcode % numberDir;
        if (ndirectory < 0) {
            ndirectory *= -1;
        }
        return ndirectory;
    }

    public int getHashFile(String key) {
        int hashcode = key.hashCode();
        int nfile = hashcode / numberDir % numberFile;
        if (nfile < 0) {
            nfile *= -1;
        }
        return nfile;
    }

    public boolean containsKey(String key) {
        try {
            Storeable st = initMap(key).get(key);
            if (st == null) {
                return loadTableFile(key) != null;
            } else {
                return true;
            }
        } catch (FileIsNotExist e) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Error with open file with key = " + key, e);
        }
    }

    public Map<String, Storeable> getMap(int i, int j) {
        return arrayMap[i][j];
    }

}

class FileIsNotExist extends Exception {
    public FileIsNotExist() {
        super();
    }
}
