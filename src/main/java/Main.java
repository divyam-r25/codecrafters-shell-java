import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        //TODO: Uncomment the code below to pass the first stage
        System.out.print("$ ");
        
        Scanner sc = new Scanner(System.in);
        String user = sc.nextLine();

        System.out.println(user+": command not found");
    }
    
}
