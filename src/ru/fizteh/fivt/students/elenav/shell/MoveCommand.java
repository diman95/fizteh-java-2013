package ru.fizteh.fivt.students.elenav.shell;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class MoveCommand extends AbstractCommand {
	MoveCommand(ShellState s) { 
		super(s, "mv", 2);
	}
	public void execute(String[] args, PrintStream s) throws IOException {
		File sourse = new File(absolutePath(args[1]));
		File destination = new File(absolutePath(args[2]));
		if (!sourse.getAbsolutePath().equals(destination.getAbsolutePath())) {
			if (!sourse.exists()) {
				throw new IOException("mv: cannot move '" + args[1] + "' to '" + args[2] +
								"': No such file or directory");
			} else {
				if (!destination.isDirectory()) {
					sourse.renameTo(destination);
				} else {
					sourse.renameTo(new File(destination.getAbsolutePath() + File.separator + sourse.getName()));
				}
			}
		} else {
			throw new IOException("mv: cannot copy: Files are same");
		}
	}
}
