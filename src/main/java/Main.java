import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        //TODO: Uncomment the code below to pass the first stage

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
            else if (user.startsWith("type ")) {
                String cmd = user.substring(5);

                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    System.out.println(cmd + ": not found");
                }
            }
            else {
                System.out.println(user + ": command not found");
            }
        }
    }
    
}
