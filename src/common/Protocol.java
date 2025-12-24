package common;

public final class Protocol {
    public static final String CLIENT_PREFIX = "C:";
    public static final String SERVER_PREFIX = "S:";
    
    // Auth Commands
    public static final String LOGIN = "LOGIN";       // C:LOGIN user pass
    public static final String REGISTER = "REGISTER"; // C:REGISTER user pass
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    
    private Protocol(){}
}