package ru.fizteh.fivt.students.vyatkina.shell;

import java.io.PrintStream;

public interface Command  {
    void execute (String [] args);
    String getName ();
    int getArgumentCount ();

}
