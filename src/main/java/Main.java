import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        //TODO: Uncomment the code below to pass the first stage
        Scanner sc = new Scanner(System.in);
        String user = sc.next();

        System.out.print("$ ");
        System.out.println(user+": command not found");
        System.out.flush();
    }
    
}
