package common;

public final class Protocol {
    public static final String CLIENT_PREFIX = "C:";
    public static final String SERVER_PREFIX = "S:";
    
    // Auth Commands
    public static final String LOGIN = "LOGIN";       // Actual Login (Joins chat)
    public static final String CHECK_LOGIN = "CHECK_LOGIN"; // New: Just checks password (Silent)
    public static final String REGISTER = "REGISTER"; 
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    
    private Protocol(){}
}