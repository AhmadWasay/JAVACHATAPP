package server;

public class ServerMain {
    public static void main(String[] args) {
        int port = 5555;
        
        // Optional: specific database test (Updated for String return type)
        // String user = DatabaseManager.checkLogin("ahmad", "secret123");
        // if (user != null) System.out.println("DB Check: Login OK for " + user);
        
        System.out.println("-----------------------------------");
        System.out.println("   JAVA CHAT SERVER STARTING...    ");
        System.out.println("-----------------------------------");
        
        // This starts the loop that listens for clients
        new ChatServer(port).start(); 
    }
}