import java.util.*;
import java.io.*;

public class Main {

    static String currentDirectory = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String user = sc.nextLine();
            List<String> tokens = parseCommand(user);

            if (tokens.isEmpty()) continue;

            String command = tokens.get(0);

            if (command.equals("exit")) {
                break;
            }

            else if (command.equals("echo")) {
                int stdoutIndex = findStdoutRedirect(tokens);
                int appendStdoutIndex = findAppendStdoutRedirect(tokens);
                int stderrIndex = findStderrRedirect(tokens);
                int appendStderrIndex = findAppendStderrRedirect(tokens);

                int cutIndex = tokens.size();
                for (int idx : new int[]{stdoutIndex, appendStdoutIndex, stderrIndex, appendStderrIndex}) {
                    if (idx != -1) cutIndex = Math.min(cutIndex, idx);
                }

                String output = cutIndex > 1
                        ? String.join(" ", tokens.subList(1, cutIndex))
                        : "";

                if (stdoutIndex != -1 || appendStdoutIndex != -1) {
                    int idx = stdoutIndex != -1 ? stdoutIndex : appendStdoutIndex;
                    boolean append = appendStdoutIndex != -1;

                    File outFile = new File(tokens.get(idx + 1));
                    File parent = outFile.getParentFile();
                    if (parent != null) parent.mkdirs();

                    try (FileWriter writer = new FileWriter(outFile, append)) {
                        writer.write(output + System.lineSeparator());
                    }
                } else {
                    System.out.println(output);
                }

                // create stderr file if redirected/appended (echo doesn't write stderr)
                if (stderrIndex != -1 || appendStderrIndex != -1) {
                    int idx = stderrIndex != -1 ? stderrIndex : appendStderrIndex;
                    File errFile = new File(tokens.get(idx + 1));
                    File parent = errFile.getParentFile();
                    if (parent != null) parent.mkdirs();
                    if (!errFile.exists()) errFile.createNewFile();
                }
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
                        || cmd.equals("pwd") || cmd.equals("cd")) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String executablePath = findExecutable(cmd);
                    if (executablePath != null) System.out.println(cmd + " is " + executablePath);
                    else System.out.println(cmd + ": not found");
                }
            }

            else {
                int stdoutIndex = findStdoutRedirect(tokens);
                int appendStdoutIndex = findAppendStdoutRedirect(tokens);
                int stderrIndex = findStderrRedirect(tokens);
                int appendStderrIndex = findAppendStderrRedirect(tokens);

                File stdoutFile = null;
                File stderrFile = null;
                boolean appendStdout = false;
                boolean appendStderr = false;

                int cutIndex = tokens.size();
                for (int idx : new int[]{stdoutIndex, appendStdoutIndex, stderrIndex, appendStderrIndex}) {
                    if (idx != -1) cutIndex = Math.min(cutIndex, idx);
                }

                List<String> cmdTokens = new ArrayList<>(tokens.subList(0, cutIndex));

                if (stdoutIndex != -1 || appendStdoutIndex != -1) {
                    int idx = stdoutIndex != -1 ? stdoutIndex : appendStdoutIndex;
                    appendStdout = appendStdoutIndex != -1;

                    stdoutFile = new File(tokens.get(idx + 1));
                    File parent = stdoutFile.getParentFile();
                    if (parent != null) parent.mkdirs();
                }

                if (stderrIndex != -1 || appendStderrIndex != -1) {
                    int idx = stderrIndex != -1 ? stderrIndex : appendStderrIndex;
                    appendStderr = appendStderrIndex != -1;

                    stderrFile = new File(tokens.get(idx + 1));
                    File parent = stderrFile.getParentFile();
                    if (parent != null) parent.mkdirs();
                }

                String executablePath = null;
                boolean directExecutable = false;

                command = cmdTokens.get(0);

                if (isDirectExecutable(command)) {
                    executablePath = command;
                    directExecutable = true;
                } else {
                    executablePath = findExecutable(command);
                }

                if (executablePath != null) {
                    List<String> processArgs = new ArrayList<>(cmdTokens);
                    if (directExecutable) processArgs.set(0, executablePath);

                    ProcessBuilder pb = new ProcessBuilder(processArgs);
                    pb.directory(new File(currentDirectory));

                    if (stdoutFile != null) {
                        if (appendStdout) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutFile));
                        } else {
                            pb.redirectOutput(stdoutFile);
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (stderrFile != null) {
                        if (appendStderr) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(stderrFile));
                        } else {
                            pb.redirectError(stderrFile);
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process process = pb.start();
                    process.waitFor();
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }

        sc.close();
    }

    public static int findStdoutRedirect(List<String> tokens) {
        int i = tokens.indexOf(">");
        if (i == -1) i = tokens.indexOf("1>");
        return i;
    }

    public static int findAppendStdoutRedirect(List<String> tokens) {
        int i = tokens.indexOf(">>");
        if (i == -1) i = tokens.indexOf("1>>");
        return i;
    }

    public static int findStderrRedirect(List<String> tokens) {
        return tokens.indexOf("2>");
    }

    public static int findAppendStderrRedirect(List<String> tokens) {
        return tokens.indexOf("2>>");
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