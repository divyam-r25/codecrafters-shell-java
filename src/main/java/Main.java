import java.util.Scanner;
import java.io.File;

public class Main {

    static String currentDirectory = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String user = sc.nextLine();

            if (user.equals("exit")) {
                break;
            }

            else if (user.startsWith("echo ")) {
                System.out.println(user.substring(5));
            }

            else if (user.equals("pwd")) {
                System.out.println(currentDirectory);
            }

            else if (user.startsWith("cd ")) {
                String path = user.substring(3).trim();
                File dir;

                if (path.equals("~")) {
                    dir = new File(System.getenv("HOME"));
                }
                else if (path.startsWith("/")) {
                    dir = new File(path);
                }
                else {
                    dir = new File(currentDirectory, path);
                }

                if (dir.exists() && dir.isDirectory()) {
                    currentDirectory = dir.getCanonicalPath();
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            }

            else if (user.startsWith("type ")) {
                String cmd = user.substring(5);

                if (cmd.equals("echo") ||
                    cmd.equals("exit") ||
                    cmd.equals("type") ||
                    cmd.equals("pwd") ||
                    cmd.equals("cd")) {

                    System.out.println(cmd + " is a shell builtin");
                }
                else {
                    String executablePath = findExecutable(cmd);

                    if (executablePath != null) {
                        System.out.println(cmd + " is " + executablePath);
                    }
                    else {
                        System.out.println(cmd + ": not found");
                    }
                }
            }

            else {
                String[] parts = user.trim().split("\\s+");
                String command = parts[0];

                String executablePath = findExecutable(command);

                if (executablePath != null) {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.directory(new File(currentDirectory));
                    pb.inheritIO();

                    Process process = pb.start();
                    process.waitFor();
                }
                else {
                    System.out.println(command + ": command not found");
                }
            }
        }

        sc.close();
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