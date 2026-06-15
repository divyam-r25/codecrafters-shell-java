import java.util.Scanner;
import java.io.File;
public class Main {

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String user = sc.nextLine();

            if (user.equals("exit")) {
                break;
            }

            else if (user.startsWith("echo ")) {
                System.out.println(user.substring(5));
            }

            else if (user.startsWith("type ")) {
                String cmd = user.substring(5);

                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")) {
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
                String[] parts = user.split(" ");
                String command = parts[0];

                String executablePath = findExecutable(command);

                if (executablePath != null) {
                    parts[0] = executablePath;

                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.inheritIO();

                    Process process = pb.start();
                    process.waitFor();
                } else {
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