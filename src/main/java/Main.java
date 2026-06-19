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

            boolean hasPipe = false;
            for (String t : tokens) {
                if (t.equals("|")) {
                    hasPipe = true;
                    break;
                }
            }

            if (hasPipe) {
                List<List<String>> commands = new ArrayList<>();
                List<String> current = new ArrayList<>();
                for (String t : tokens) {
                    if (t.equals("|")) {
                        commands.add(current);
                        current = new ArrayList<>();
                    } else {
                        current.add(t);
                    }
                }
                commands.add(current);
                executePipeline(commands, runInBackground);
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

    static void executePipeline(List<List<String>> commands, boolean runInBackground)
            throws Exception {
        int n = commands.size();
        List<String> lastCmdTokens = commands.get(n - 1);

        // Parse redirection on the last command
        int redirectIndexLast = -1;
        boolean appendStdoutLast = false;
        boolean appendStderrLast = false;
        boolean redirectStderrLast = false;

        for (int i = 0; i < lastCmdTokens.size(); i++) {
            String t = lastCmdTokens.get(i);
            if (t.equals(">") || t.equals("1>")) {
                redirectIndexLast = i;
                break;
            } else if (t.equals(">>") || t.equals("1>>")) {
                redirectIndexLast = i;
                appendStdoutLast = true;
                break;
            } else if (t.equals("2>")) {
                redirectIndexLast = i;
                redirectStderrLast = true;
                break;
            } else if (t.equals("2>>")) {
                redirectIndexLast = i;
                appendStderrLast = true;
                break;
            }
        }

        List<String> actualLastTokens;
        File redirectFileLast = null;

        if (redirectIndexLast != -1) {
            actualLastTokens = new ArrayList<>(lastCmdTokens.subList(0, redirectIndexLast));
            redirectFileLast = new File(lastCmdTokens.get(redirectIndexLast + 1));
            File parent = redirectFileLast.getParentFile();
            if (parent != null)
                parent.mkdirs();
        } else {
            actualLastTokens = new ArrayList<>(lastCmdTokens);
        }

        // Replace last command tokens with the redirection-stripped version
        commands.set(n - 1, actualLastTokens);

        // Start all processes and connect pipes
        List<Process> processes = new ArrayList<>();
        List<Thread> pipeThreads = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            List<String> cmdTokens = commands.get(i);
            String cmd = cmdTokens.get(0);

            if (isBuiltin(cmd)) {
                // For builtins, we compute output and feed it into the next stage
                byte[] builtinOutput = executeBuiltinToBytes(cmdTokens);

                if (i == n - 1) {
                    // Last command is a builtin
                    if (redirectFileLast != null && !redirectStderrLast && !appendStderrLast) {
                        FileWriter fw = new FileWriter(redirectFileLast, appendStdoutLast);
                        fw.write(new String(builtinOutput));
                        fw.close();
                    } else {
                        System.out.write(builtinOutput);
                        System.out.flush();
                    }
                } else {
                    // Feed builtin output into the next process's stdin via a "source" process proxy
                    // We create a fake PipedInputStream to carry the data forward
                    // Start the next process and feed data
                    i++;
                    List<String> nextCmdTokens = commands.get(i);
                    String nextCmd = nextCmdTokens.get(0);

                    // We need to handle chains: builtin | ext | ext | ...
                    // Start all remaining processes
                    Process nextProcess = startExternalProcess(nextCmdTokens, i == n - 1, redirectFileLast,
                            appendStdoutLast, appendStderrLast, redirectStderrLast);
                    if (nextProcess == null) {
                        System.out.println(nextCmd + ": command not found");
                        return;
                    }
                    processes.add(nextProcess);

                    // Feed builtin output into next process
                    final byte[] data = builtinOutput;
                    final Process target = nextProcess;
                    Thread feedThread = new Thread(() -> {
                        try {
                            OutputStream os = target.getOutputStream();
                            os.write(data);
                            os.flush();
                            os.close();
                        } catch (IOException e) {
                        }
                    });
                    feedThread.start();
                    pipeThreads.add(feedThread);

                    // Now chain the remaining processes
                    for (int j = i + 1; j < n; j++) {
                        Process prev = processes.get(processes.size() - 1);
                        List<String> jCmdTokens = commands.get(j);

                        if (isBuiltin(jCmdTokens.get(0))) {
                            // Drain prev output, run builtin, output result
                            drainProcessOutput(prev);
                            prev.waitFor();
                            byte[] bOut = executeBuiltinToBytes(jCmdTokens);
                            if (j == n - 1) {
                                if (redirectFileLast != null && !redirectStderrLast && !appendStderrLast) {
                                    FileWriter fw = new FileWriter(redirectFileLast, appendStdoutLast);
                                    fw.write(new String(bOut));
                                    fw.close();
                                } else {
                                    System.out.write(bOut);
                                    System.out.flush();
                                }
                            }
                            // Mark that we've handled up to j
                            i = j;
                            continue;
                        }

                        Process jProcess = startExternalProcess(jCmdTokens, j == n - 1, redirectFileLast,
                                appendStdoutLast, appendStderrLast, redirectStderrLast);
                        if (jProcess == null) {
                            System.out.println(jCmdTokens.get(0) + ": command not found");
                            // Kill all started processes
                            for (Process p : processes) p.destroyForcibly();
                            return;
                        }
                        processes.add(jProcess);

                        Thread pt = createPipeThread(prev, jProcess);
                        pt.start();
                        pipeThreads.add(pt);
                        i = j;
                    }
                    break;
                }
            } else {
                // External command
                Process proc = startExternalProcess(cmdTokens, i == n - 1, redirectFileLast,
                        appendStdoutLast, appendStderrLast, redirectStderrLast);
                if (proc == null) {
                    System.out.println(cmd + ": command not found");
                    for (Process p : processes) p.destroyForcibly();
                    return;
                }
                processes.add(proc);

                // Connect pipe from previous process
                if (processes.size() >= 2) {
                    Process prev = processes.get(processes.size() - 2);
                    Thread pt = createPipeThread(prev, proc);
                    pt.start();
                    pipeThreads.add(pt);
                }
            }
        }

        if (processes.isEmpty()) return;

        Process lastProcess = processes.get(processes.size() - 1);

        // Read the last process's stdout if not redirected
        Thread outputThread = null;
        if (redirectFileLast == null || redirectStderrLast || appendStderrLast) {
            outputThread = new Thread(() -> {
                try {
                    InputStream input = lastProcess.getInputStream();
                    byte[] buf = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = input.read(buf)) != -1) {
                        System.out.write(buf, 0, bytesRead);
                        System.out.flush();
                    }
                } catch (IOException e) {
                }
            });
            outputThread.start();
        }

        if (runInBackground) {
            StringBuilder jobCmd = new StringBuilder();
            for (int i = 0; i < commands.size(); i++) {
                if (i > 0) jobCmd.append(" | ");
                jobCmd.append(String.join(" ", commands.get(i)));
            }
            jobCmd.append(" &");
            int jobId = getNextJobId();
            backgroundJobs.add(new Job(jobId, lastProcess, jobCmd.toString()));
            System.out.printf("[%d] %d%n", jobId, lastProcess.pid());
        } else {
            lastProcess.waitFor();
            for (Process p : processes) p.waitFor();
            for (Thread t : pipeThreads) t.join();
            if (outputThread != null) outputThread.join();
        }
    }

    static Process startExternalProcess(List<String> cmdTokens, boolean isLast, File redirectFile,
            boolean appendStdout, boolean appendStderr, boolean redirectStderr) throws Exception {
        String cmd = cmdTokens.get(0);
        String execPath;

        if (isDirectExecutable(cmd)) {
            execPath = cmd;
        } else {
            execPath = findExecutable(cmd);
        }

        if (execPath == null) return null;

        List<String> args = new ArrayList<>(cmdTokens);
        if (isDirectExecutable(cmd)) {
            args.set(0, execPath);
        }

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(new File(currentDirectory));
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        if (isLast && redirectFile != null) {
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
        }

        return pb.start();
    }

    static Thread createPipeThread(Process src, Process dst) {
        return new Thread(() -> {
            try {
                InputStream input = src.getInputStream();
                OutputStream output = dst.getOutputStream();
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
    }

    static void drainProcessOutput(Process p) {
        try {
            InputStream is = p.getInputStream();
            byte[] buf = new byte[1024];
            while (is.read(buf) != -1) {}
        } catch (IOException e) {
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