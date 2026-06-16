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

                for (String op : Arrays.asList(">", "1>", ">>", "1>>", "2>", "2>>")) {
                    int idx = tokens.indexOf(op);
                    if (idx != -1)
                        cutIndex = Math.min(cutIndex, idx);
                }

                String output = "";
                if (cutIndex > 1) {
                    output = String.join(" ", tokens.subList(1, cutIndex));
                }

                System.out.println(output);
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDirectory);
            }

            else if (command.equals("cd")) {
                String path = tokens.size() > 1 ? tokens.get(1) : "";
                File dir;

                if (path.equals("~"))
                    dir = new File(System.getenv("HOME"));
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
            } else if (command.equals("jobs")) {
                for (int i = 0; i < backgroundJobs.size(); i++) {
                    Job job = backgroundJobs.get(i);

                    if (job.process.isAlive()) {
                        System.out.printf("[%d]+  Running                 %s%n",
                                i + 1,
                                job.command);
                    }
                }
            }

            else {
                String executablePath;
                boolean directExecutable = false;

                if (isDirectExecutable(command)) {
                    executablePath = command;
                    directExecutable = true;
                } else {
                    executablePath = findExecutable(command);
                }

                if (executablePath != null) {
                    List<String> processArgs = new ArrayList<>(tokens);
                    if (directExecutable)
                        processArgs.set(0, executablePath);

                    ProcessBuilder pb = new ProcessBuilder(processArgs);
                    pb.directory(new File(currentDirectory));
                    pb.inheritIO();

                    Process process = pb.start();

                    if (runInBackground) {
                        String jobCmd = String.join(" ", tokens) + " &";
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
        return file.exists() && file.canExecute();
    }

    static String findExecutable(String cmd) {
        String path = System.getenv("PATH");
        String[] directories = path.split(":");

        for (String dir : directories) {
            File file = new File(dir, cmd);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}