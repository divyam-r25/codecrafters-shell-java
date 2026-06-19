import java.util.*;
import java.io.*;

public class Main {

    static String currentDirectory = System.getProperty("user.dir");

    static class Job {
        int id;
        Process process;
        String command;

        Job(int id, Process process, String command) {
            this.id = id;
            this.process = process;
            this.command = command;
        }
    }

    static List<Job> backgroundJobs = new ArrayList<>();
    static int nextJobId = 1;
    static TreeSet<Integer> availableJobIds = new TreeSet<>();

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            if (!backgroundJobs.isEmpty()) {
                Thread.sleep(50);
            }
            reapFinishedJobs();

            System.out.print("$ ");
            System.out.flush();

            String originalUser = sc.nextLine();
            List<String> tokens = parseCommand(originalUser);

            if (tokens.isEmpty())
                continue;

            boolean runInBackground = false;
            if (tokens.get(tokens.size() - 1).equals("&")) {
                runInBackground = true;
                tokens.remove(tokens.size() - 1);
            }

            int pipeIndex = -1;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).equals("|")) {
                    pipeIndex = i;
                    break;
                }
            }

            if (pipeIndex != -1) {
                List<String> command1Tokens = new ArrayList<>(tokens.subList(0, pipeIndex));
                List<String> command2Tokens = new ArrayList<>(tokens.subList(pipeIndex + 1, tokens.size()));
                executePipeline(command1Tokens, command2Tokens, runInBackground);
                continue;
            }

            String command = tokens.get(0);
            if (command.equals("exit")) {
                break;
            }

            else if (command.equals("echo")) {
                int cutIndex = tokens.size();
                String redirectOp = null;
                String redirectFile = null;

                for (int i = 0; i < tokens.size(); i++) {
                    String t = tokens.get(i);

                    if (t.equals(">") || t.equals("1>") ||
                            t.equals(">>") || t.equals("1>>") ||
                            t.equals("2>") || t.equals("2>>")) {

                        cutIndex = i;
                        redirectOp = t;

                        if (i + 1 < tokens.size()) {
                            redirectFile = tokens.get(i + 1);
                        }
                        break;
                    }
                }

                String output = "";
                if (cutIndex > 1) {
                    output = String.join(" ", tokens.subList(1, cutIndex));
                }

                if (redirectOp == null) {
                    System.out.println(output);
                } else if (redirectOp.equals(">") || redirectOp.equals("1>")
                        || redirectOp.equals(">>") || redirectOp.equals("1>>")) {

                    File file = new File(redirectFile);
                    File parent = file.getParentFile();
                    if (parent != null)
                        parent.mkdirs();

                    boolean append = redirectOp.equals(">>") || redirectOp.equals("1>>");

                    FileWriter fw = new FileWriter(file, append);
                    fw.write(output + System.lineSeparator());
                    fw.close();
                } else if (redirectOp.equals("2>") || redirectOp.equals("2>>")) {
                    System.out.println(output);

                    File file = new File(redirectFile);
                    File parent = file.getParentFile();
                    if (parent != null)
                        parent.mkdirs();

                    boolean append = redirectOp.equals("2>>");
                    FileWriter fw = new FileWriter(file, append);
                    fw.close();
                }
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDirectory);
            }

            else if (command.equals("cd")) {
                String path = tokens.size() > 1 ? tokens.get(1) : "";
                File dir;

                if (path.equals("~"))
                    dir = new File(homeDirectory());
                else if (path.startsWith("/"))
                    dir = new File(path);
                else
                    dir = new File(currentDirectory, path);

                if (dir.exists() && dir.isDirectory()) {
                    currentDirectory = dir.getCanonicalPath();
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            }

            else if (command.equals("type")) {
                String cmd = tokens.size() > 1 ? tokens.get(1) : "";

                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")
                        || cmd.equals("pwd") || cmd.equals("cd") || cmd.equals("jobs")) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String executablePath = findExecutable(cmd);
                    if (executablePath != null) {
                        System.out.println(cmd + " is " + executablePath);
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                }
            }

            else if (command.equals("jobs")) {
                for (int i = 0; i < backgroundJobs.size();) {
                    Job job = backgroundJobs.get(i);

                    String marker = " ";
                    if (i == backgroundJobs.size() - 1) {
                        marker = "+";
                    } else if (i == backgroundJobs.size() - 2) {
                        marker = "-";
                    }

                    if (job.process.isAlive()) {
                        System.out.printf("[%d]%s  Running                 %s%n",
                                job.id,
                                marker,
                                job.command);
                        i++;
                    } else {
                        String cmd = job.command;
                        if (cmd.endsWith(" &")) {
                            cmd = cmd.substring(0, cmd.length() - 2);
                        }

                        System.out.printf("[%d]%s  Done                 %s%n",
                                job.id,
                                marker,
                                cmd);
                        recycleJobId(job.id);
                        backgroundJobs.remove(i);
                    }
                }
            }

            else {
                int redirectIndex = -1;
                boolean appendStdout = false;
                boolean appendStderr = false;
                boolean redirectStderr = false;

                for (int i = 0; i < tokens.size(); i++) {
                    String t = tokens.get(i);

                    if (t.equals(">") || t.equals("1>")) {
                        redirectIndex = i;
                        break;
                    } else if (t.equals(">>") || t.equals("1>>")) {
                        redirectIndex = i;
                        appendStdout = true;
                        break;
                    } else if (t.equals("2>")) {
                        redirectIndex = i;
                        redirectStderr = true;
                        break;
                    } else if (t.equals("2>>")) {
                        redirectIndex = i;
                        appendStderr = true;
                        break;
                    }
                }

                List<String> commandTokens;
                File redirectFile = null;

                if (redirectIndex != -1) {
                    commandTokens = new ArrayList<>(tokens.subList(0, redirectIndex));
                    redirectFile = new File(tokens.get(redirectIndex + 1));

                    File parent = redirectFile.getParentFile();
                    if (parent != null)
                        parent.mkdirs();
                } else {
                    commandTokens = new ArrayList<>(tokens);
                }

                command = commandTokens.get(0);

                String executablePath;
                boolean directExecutable = false;

                if (isDirectExecutable(command)) {
                    executablePath = command;
                    directExecutable = true;
                } else {
                    executablePath = findExecutable(command);
                }

                if (executablePath != null) {
                    List<String> processArgs = new ArrayList<>(commandTokens);
                    if (directExecutable)
                        processArgs.set(0, executablePath);

                    ProcessBuilder pb = new ProcessBuilder(processArgs);
                    pb.directory(new File(currentDirectory));

                    if (redirectFile != null) {
                        if (appendStdout) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(redirectFile));
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        } else if (appendStderr) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(redirectFile));
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        } else if (redirectStderr) {
                            pb.redirectError(redirectFile);
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        } else {
                            pb.redirectOutput(redirectFile);
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }
                    } else {
                        pb.inheritIO();
                    }

                    Process process = pb.start();

                    if (runInBackground) {
                        String jobCmd = String.join(" ", commandTokens) + " &";
                        int jobId = getNextJobId();
                        backgroundJobs.add(new Job(jobId, process, jobCmd));
                        System.out.printf("[%d] %d%n",
                                jobId,
                                process.pid());
                    } else {
                        process.waitFor();
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }

        }

        sc.close();
    }

    static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }

            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }

            if (c == '\\') {
                if (inSingle) {
                    current.append(c);
                    continue;
                }

                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (!inDouble) {
                        current.append(next);
                        i++;
                        continue;
                    } else {
                        if (next == '\\' || next == '"' || next == '$') {
                            current.append(next);
                            i++;
                            continue;
                        } else {
                            current.append('\\');
                            continue;
                        }
                    }
                }
            }

            if (!inSingle && !inDouble) {
                if (c == '|') {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    tokens.add("|");
                    continue;
                }
                if (current.length() == 0) {
                    if (c == '>') {
                        if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                            tokens.add(">>");
                            i++;
                        } else {
                            tokens.add(">");
                        }
                        continue;
                    }

                    if ((c == '1' || c == '2')
                            && i + 1 < input.length()
                            && input.charAt(i + 1) == '>') {

                        String token = "" + c + ">";
                        i++;

                        if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                            token += ">";
                            i++;
                        }

                        tokens.add(token);
                        continue;
                    }
                }

                if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    continue;
                }
            }

            current.append(c);
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    static boolean isDirectExecutable(String cmd) {
        File file = new File(cmd);
        if (file.exists() && file.canExecute()) {
            return true;
        }

        if (isWindows() && !cmd.contains(".")) {
            for (String extension : new String[] { ".exe", ".cmd", ".bat" }) {
                File withExtension = new File(cmd + extension);
                if (withExtension.exists() && withExtension.canExecute()) {
                    return true;
                }
            }
        }

        return false;
    }

    static String findExecutable(String cmd) {
        String path = System.getenv("PATH");
        if (path == null || path.isEmpty()) {
            return null;
        }

        String[] directories = path.split(java.io.File.pathSeparator);
        String[] extensions = isWindows() ? new String[] { "", ".exe", ".cmd", ".bat" } : new String[] { "" };

        for (String dir : directories) {
            for (String extension : extensions) {
                File file = new File(dir, cmd + extension);
                if (file.exists() && file.canExecute()) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    static String homeDirectory() {
        String home = System.getenv("HOME");
        if (home != null && !home.isEmpty()) {
            return home;
        }

        home = System.getenv("USERPROFILE");
        if (home != null && !home.isEmpty()) {
            return home;
        }

        return System.getProperty("user.home");
    }

    static int getNextJobId() {
        if (!availableJobIds.isEmpty()) {
            return availableJobIds.pollFirst();
        }
        return nextJobId++;
    }

    static void recycleJobId(int jobId) {
        availableJobIds.add(jobId);
    }

    static void reapFinishedJobs() {
        for (int i = 0; i < backgroundJobs.size();) {
            Job job = backgroundJobs.get(i);

            if (job != null && !job.process.isAlive()) {
                String marker = " ";
                if (i == backgroundJobs.size() - 1) {
                    marker = "+";
                } else if (i == backgroundJobs.size() - 2) {
                    marker = "-";
                }

                String cmd = job.command;
                if (cmd.endsWith(" &")) {
                    cmd = cmd.substring(0, cmd.length() - 2);
                }

                System.out.printf("[%d]%s  Done                 %s%n",
                        job.id,
                        marker,
                        cmd);
                recycleJobId(job.id);
                backgroundJobs.remove(i);
            } else {
                i++;
            }
        }
    }

    static void executePipeline(List<String> command1Tokens, List<String> command2Tokens, boolean runInBackground)
            throws Exception {
        String cmd1 = command1Tokens.get(0);
        String cmd2 = command2Tokens.get(0);

       
        int redirectIndex2 = -1;
        boolean appendStdout2 = false;
        boolean appendStderr2 = false;
        boolean redirectStderr2 = false;

        for (int i = 0; i < command2Tokens.size(); i++) {
            String t = command2Tokens.get(i);
            if (t.equals(">") || t.equals("1>")) {
                redirectIndex2 = i;
                break;
            } else if (t.equals(">>") || t.equals("1>>")) {
                redirectIndex2 = i;
                appendStdout2 = true;
                break;
            } else if (t.equals("2>")) {
                redirectIndex2 = i;
                redirectStderr2 = true;
                break;
            } else if (t.equals("2>>")) {
                redirectIndex2 = i;
                appendStderr2 = true;
                break;
            }
        }

        List<String> actualCmd2Tokens;
        File redirectFile2 = null;

        if (redirectIndex2 != -1) {
            actualCmd2Tokens = new ArrayList<>(command2Tokens.subList(0, redirectIndex2));
            redirectFile2 = new File(command2Tokens.get(redirectIndex2 + 1));
            File parent = redirectFile2.getParentFile();
            if (parent != null)
                parent.mkdirs();
        } else {
            actualCmd2Tokens = new ArrayList<>(command2Tokens);
        }

        cmd2 = actualCmd2Tokens.get(0);

       
        boolean cmd1Builtin = isBuiltin(cmd1);

        String exec1 = null;
        boolean direct1 = false;

        if (!cmd1Builtin) {
            if (isDirectExecutable(cmd1)) {
                exec1 = cmd1;
                direct1 = true;
            } else {
                exec1 = findExecutable(cmd1);
            }
            if (exec1 == null) {
                System.out.println(cmd1 + ": command not found");
                return;
            }
        }

        boolean cmd2Builtin = isBuiltin(cmd2);

        String exec2 = null;
        boolean direct2 = false;

        if (!cmd2Builtin) {
            if (isDirectExecutable(cmd2)) {
                exec2 = cmd2;
                direct2 = true;
            } else {
                exec2 = findExecutable(cmd2);
            }
            if (exec2 == null) {
                System.out.println(cmd2 + ": command not found");
                return;
            }
        }

        List<String> args2 = null;
        ProcessBuilder pb2 = null;
        Process process2 = null;

        if (!cmd2Builtin) {
            args2 = new ArrayList<>(actualCmd2Tokens);
            if (direct2)
                args2.set(0, exec2);

            pb2 = new ProcessBuilder(args2);
            pb2.directory(new File(currentDirectory));

            if (redirectFile2 != null) {
                if (appendStdout2) {
                    pb2.redirectOutput(ProcessBuilder.Redirect.appendTo(redirectFile2));
                    pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                } else if (appendStderr2) {
                    pb2.redirectError(ProcessBuilder.Redirect.appendTo(redirectFile2));
                    pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                } else if (redirectStderr2) {
                    pb2.redirectError(redirectFile2);
                    pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                } else {
                    pb2.redirectOutput(redirectFile2);
                    pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                }
            } else {
                pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            process2 = pb2.start();
        }

        if (cmd1Builtin) {
            byte[] cmd1Output = executeBuiltinToBytes(command1Tokens);

            if (cmd2Builtin) {
                String builtinResult = executeBuiltinToString(actualCmd2Tokens);
                if (redirectFile2 != null && !redirectStderr2 && !appendStderr2) {
                    boolean append = appendStdout2;
                    FileWriter fw = new FileWriter(redirectFile2, append);
                    fw.write(builtinResult);
                    fw.close();
                } else {
                    System.out.print(builtinResult);
                    System.out.flush();
                }
            } else {
                final Process p2 = process2;
                Thread pipeInputThread = new Thread(() -> {
                    try {
                        OutputStream output = p2.getOutputStream();
                        output.write(cmd1Output);
                        output.flush();
                        output.close();
                    } catch (IOException e) {
                    }
                });
                pipeInputThread.start();

                Thread pipeOutputThread = null;
                if (redirectFile2 == null || redirectStderr2 || appendStderr2) {
                    pipeOutputThread = new Thread(() -> {
                        try {
                            InputStream input = p2.getInputStream();
                            byte[] buf = new byte[1024];
                            int n;
                            while ((n = input.read(buf)) != -1) {
                                System.out.write(buf, 0, n);
                                System.out.flush();
                            }
                        } catch (IOException e) {
                        }
                    });
                    pipeOutputThread.start();
                }

                if (runInBackground) {
                    String jobCmd = String.join(" ", command1Tokens) + " | " + String.join(" ", command2Tokens) + " &";
                    int jobId = getNextJobId();
                    backgroundJobs.add(new Job(jobId, p2, jobCmd));
                    System.out.printf("[%d] %d%n", jobId, p2.pid());
                } else {
                    p2.waitFor();
                    pipeInputThread.join();
                    if (pipeOutputThread != null)
                        pipeOutputThread.join();
                }
            }
        } else {
            List<String> args1 = new ArrayList<>(command1Tokens);
            if (direct1)
                args1.set(0, exec1);

            ProcessBuilder pb1 = new ProcessBuilder(args1);
            pb1.directory(new File(currentDirectory));
            pb1.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process process1 = pb1.start();

            if (cmd2Builtin) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream is1 = process1.getInputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is1.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                process1.waitFor();

                String builtinResult = executeBuiltinToString(actualCmd2Tokens);
                if (redirectFile2 != null && !redirectStderr2 && !appendStderr2) {
                    boolean append = appendStdout2;
                    FileWriter fw = new FileWriter(redirectFile2, append);
                    fw.write(builtinResult);
                    fw.close();
                } else {
                    System.out.print(builtinResult);
                    System.out.flush();
                }
            } else {
                final Process p1 = process1;
                final Process p2 = process2;

                Thread pipeInputThread = new Thread(() -> {
                    try {
                        InputStream input = p1.getInputStream();
                        OutputStream output = p2.getOutputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = input.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                            output.flush();
                        }
                        output.close();
                    } catch (IOException e) {
                    }
                });
                pipeInputThread.start();

                Thread pipeOutputThread = null;
                if (redirectFile2 == null || redirectStderr2 || appendStderr2) {
                    pipeOutputThread = new Thread(() -> {
                        try {
                            InputStream input = p2.getInputStream();
                            byte[] buf = new byte[1024];
                            int n;
                            while ((n = input.read(buf)) != -1) {
                                System.out.write(buf, 0, n);
                                System.out.flush();
                            }
                        } catch (IOException e) {
                        }
                    });
                    pipeOutputThread.start();
                }

                if (runInBackground) {
                    String jobCmd = String.join(" ", command1Tokens) + " | " + String.join(" ", command2Tokens) + " &";
                    int jobId = getNextJobId();
                    backgroundJobs.add(new Job(jobId, p2, jobCmd));
                    System.out.printf("[%d] %d%n", jobId, p2.pid());
                } else {
                    p2.waitFor();
                    p1.waitFor();
                    pipeInputThread.join();
                    if (pipeOutputThread != null)
                        pipeOutputThread.join();
                }
            }
        }
    }

    static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("pwd") || cmd.equals("cd")
                || cmd.equals("type") || cmd.equals("exit") || cmd.equals("jobs");
    }

    static byte[] executeBuiltinToBytes(List<String> tokens) {
        String result = executeBuiltinToString(tokens);
        return result.getBytes();
    }

    static String executeBuiltinToString(List<String> tokens) {
        String cmd = tokens.get(0);
        if (cmd.equals("echo")) {
            if (tokens.size() > 1) {
                return String.join(" ", tokens.subList(1, tokens.size())) + System.lineSeparator();
            }
            return System.lineSeparator();
        } else if (cmd.equals("pwd")) {
            return currentDirectory + System.lineSeparator();
        } else if (cmd.equals("type")) {
            if (tokens.size() > 1) {
                String target = tokens.get(1);
                if (isBuiltin(target)) {
                    return target + " is a shell builtin" + System.lineSeparator();
                } else {
                    String execPath = findExecutable(target);
                    if (execPath != null) {
                        return target + " is " + execPath + System.lineSeparator();
                    } else {
                        return target + ": not found" + System.lineSeparator();
                    }
                }
            }
            return "";
        }
        return "";
    }
}