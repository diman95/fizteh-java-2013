package ru.fizteh.fivt.students.vyatkina.shell.commands;

import ru.fizteh.fivt.students.vyatkina.shell.Command;
import ru.fizteh.fivt.students.vyatkina.shell.FileManager;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MvCommand implements Command {

    private final FileManager fileManager;

    public MvCommand (FileManager fileManager) {
        this.fileManager = fileManager;
    }

    @Override
    public void execute (String[] args) throws RuntimeException {
    Path fromPath = Paths.get (args[0]);
    Path toPath = Paths.get (args[1]);
    fileManager.moveFile (fromPath,toPath);
    }

    @Override
    public String getName () {
        return "mv";
    }

    @Override
    public int getArgumentCount () {
        return 2;
    }
}
