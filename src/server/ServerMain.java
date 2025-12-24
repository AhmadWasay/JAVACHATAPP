package server;

public class ServerMain {
    public static void main(String[] args) {
    // 1. Try to register a user
    if (server.DatabaseManager.registerUser("ahmad", "secret123")) {
        System.out.println("User 'ahmad' registered successfully!");
    } else {
        System.out.println("Registration failed (User might already exist).");
    }

    // 2. Try to login
    if (server.DatabaseManager.checkLogin("ahmad", "secret123")) {
        System.out.println("Login Successful!");
    } else {
        System.out.println("Login Failed.");
    }
    
    // 3. Save a message
    server.DatabaseManager.saveMessage("ahmad", "bob", "Hello Bob, this is saved in SQL!");
    System.out.println("Message saved.");
    }
}