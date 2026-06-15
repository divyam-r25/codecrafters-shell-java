import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        //TODO: Uncomment the code below to pass the first stage

        Scanner sc = new Scanner(System.in);

        while(true){
            System.out.print("$ ");

            String user = sc.nextLine();
            
            if(user.equals("exit")) break;

            System.out.println(user+": command not found");

            }
    }
    
}
