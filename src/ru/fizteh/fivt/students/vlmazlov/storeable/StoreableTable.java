package ru.fizteh.fivt.students.vlmazlov.storeable;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.io.File;
import ru.fizteh.fivt.students.vlmazlov.shell.FileUtils;
import ru.fizteh.fivt.students.vlmazlov.filemap.GenericTable;
import ru.fizteh.fivt.students.vlmazlov.multifilemap.ValidityChecker;
import ru.fizteh.fivt.students.vlmazlov.multifilemap.ValidityCheckFailedException;
import ru.fizteh.fivt.storage.structured.Table;
import ru.fizteh.fivt.storage.structured.Storeable;
import ru.fizteh.fivt.storage.structured.ColumnFormatException;

public class StoreableTable extends GenericTable<Storeable> implements Table, Cloneable {

	private final List<Class<?>> valueTypes;

	public StoreableTable(String name, List<Class<?>> valueTypes) {
		super(name);
		if (valueTypes == null) {
			throw new IllegalArgumentException("Value types not specified");
		}
		///questionable
		this.valueTypes = Collections.unmodifiableList(new ArrayList<Class<?>>(valueTypes));
	}

	public StoreableTable(String name, boolean autoCommit, List<Class<?>> valueTypes) {
		super(name, autoCommit);
		this.valueTypes = Collections.unmodifiableList(new ArrayList<Class<?>>(valueTypes));
	}

	@Override
	public Storeable put(String key, Storeable value) throws ColumnFormatException {
		try {
			ValidityChecker.checkValueFormat(this, value);
		} catch (ValidityCheckFailedException ex) {
			throw new ColumnFormatException(ex.getMessage());
		}

		return super.put(key, value);
	}

	@Override
    public int getColumnsCount() {
    	return valueTypes.size();
    }

    @Override
	public Class<?> getColumnType(int columnIndex) throws IndexOutOfBoundsException {
    	return valueTypes.get(columnIndex);
    }

    @Override
	public StoreableTable clone() {
        return new StoreableTable(getName(), autoCommit, valueTypes);
    }

    @Override
    protected boolean isValueEqual(Storeable first, Storeable second) {
    	File tempDir = FileUtils.createTempDir("writeprovider", null);

		if (tempDir == null) {
			throw new RuntimeException("Unable to create a temporary directory");
		}

		StoreableTableProvider tmpProvider = null;

		try {
			tmpProvider = new StoreableTableProvider(tempDir.getPath(), true);
		} catch (ValidityCheckFailedException ex) {
			throw new RuntimeException("Unable to check storeable equality");
		}

	    return tmpProvider.serialize(this, first).equals(tmpProvider.serialize(this, second));
    }
}