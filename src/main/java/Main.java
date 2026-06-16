import java.util.*;
import java.io.*;

public class Main {

    static String currentDirectory = System.getProperty("user.dir");
    static List<Process> backgroundJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String user = sc.nextLine();
            List<String> tokens = parseCommand(user);

            if (tokens.isEmpty()) continue;

            boolean runInBackground = false;
            if (tokens.get(tokens.size() - 1).equals("&")) {
                runInBackground = true;
                tokens.remove(tokens.size() - 1);
            }

            String command = tokens.get(0);

            if (command.equals("exit")) {
                break;
            }

            else if (command.equals("jobs")) {
                for (int i = 0; i < backgroundJobs.size(); i++) {
                    Process p = backgroundJobs.get(i);
                    if (p.isAlive()) {
                        System.out.println("[" + (i + 1) + "] " + p.pid());
                    }
                }
            }

            else if (command.equals("echo")) {
                System.out.println(String.join(" ", tokens.subList(1, tokens.size())));
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDirectory);
            }

            else if (command.equals("cd")) {
                String path = tokens.size() > 1 ? tokens.get(1) : "";
                File dir;

                if (path.equals("~")) dir = new File(System.getenv("HOME"));
                else if (path.startsWith("/")) dir = new File(path);
                else dir = new File(currentDirectory, path);

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

            else {
                String executablePath = null;
                boolean directExecutable = false;

                if (isDirectExecutable(command)) {
                    executablePath = command;
                    directExecutable = true;
                } else {
                    executablePath = findExecutable(command);
                }

                if (executablePath != null) {
                    List<String> processArgs = new ArrayList<>(tokens);
                    if (directExecutable) {
                        processArgs.set(0, executablePath);
                    }

                    ProcessBuilder pb = new ProcessBuilder(processArgs);
                    pb.directory(new File(currentDirectory));
                    pb.inheritIO();

                    Process process = pb.start();

                    if (runInBackground) {
                        backgroundJobs.add(process);
                        System.out.println("[" + backgroundJobs.size() + "] " + process.pid());
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

    public static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\\' && !inSingleQuote) {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                }
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == ' ' && !inSingleQuote && !inDoubleQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    public static boolean isDirectExecutable(String cmd) {
        File file = new File(cmd);
        return file.exists() && file.canExecute();
    }

    public static String findExecutable(String cmd) {
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