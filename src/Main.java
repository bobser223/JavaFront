import web.Client;

import java.util.Scanner;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {


    public int handleRegistration(){
        Scanner scanner = new Scanner(System.in);
        String[] auth = new String[2];

        System.out.println("Registration block");

        System.out.println("Do you exist? <yes/no> || <y/n>"); String existance = scanner.nextLine();

        System.out.println("enter name: "); auth[0] = scanner.nextLine();
        System.out.println("enter password: "); auth[1] = scanner.nextLine();

        return Client.sendAuth(auth, existance);


    }

    public static void main(String[] args) {

        label:
        while (true){
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();

            switch (input) {
                case "exit":
                    break label;
                case "help":
                    System.out.println("exit - to exit");
                    System.out.println("help - to get help");
                    break;
                case "add localy":
                    break;
            }



        }
    }
}