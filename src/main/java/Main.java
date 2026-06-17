import java.util.*;
import java.io.*;

public class Main {

    static String currentDirectory = System.getProperty("user.dir");

    static class Job {
        Process process;
        String command;

        Job(Process process, String command) {
            this.process = process;
            this.command = command;
        }
    }

    static List<Job> backgroundJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
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

                    if (job == null || !job.process.isAlive()) {
                        if (job != null) {
                            String cmd = job.command;
                            if (cmd.endsWith(" &")) {
                                cmd = cmd.substring(0, cmd.length() - 2);
                            }

                            System.out.printf("[%d]+  Done                 %s%n", i + 1, cmd);
                        }

                        backgroundJobs.remove(i);
                    } else {
                        System.out.printf("[%d]+  Running               %s%n",
                                i + 1,
                                job.command);
                        i++;
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
                        backgroundJobs.add(new Job(process, jobCmd));
                        System.out.printf("[%d] %d%n",
                                backgroundJobs.size(),
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
            for (String extension : new String[] {".exe", ".cmd", ".bat"}) {
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
        String[] extensions = isWindows() ? new String[] {"", ".exe", ".cmd", ".bat"} : new String[] {""};

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

    static void reapFinishedJobs() {
        for (int i = 0; i < backgroundJobs.size(); i++) {
            Job job = backgroundJobs.get(i);

            if (job != null && !job.process.isAlive()) {
                String cmd = job.command;
                if (cmd.endsWith(" &")) {
                    cmd = cmd.substring(0, cmd.length() - 2);
                }

                System.out.printf("[%d]+  Done                 %s%n",
                        i + 1,
                        cmd);
                backgroundJobs.remove(i);
                i--;
            }
        }
    }
}