package common;

public final class Protocol {
    public static final String CLIENT_PREFIX = "C:";
    public static final String SERVER_PREFIX = "S:";
    
    // Auth Commands
    public static final String LOGIN = "LOGIN"; 
    public static final String CHECK_LOGIN = "CHECK_LOGIN";
    public static final String REGISTER = "REGISTER"; 
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    
    // --- NEW: OTP Login Commands ---
    public static final String REQUEST_LOGIN_OTP = "REQ_LOGIN_OTP"; // C:REQ_LOGIN_OTP <email>
    public static final String VERIFY_LOGIN_OTP = "VERIFY_LOGIN_OTP"; // C:VERIFY_LOGIN_OTP <email> <code>
    
    // ... existing commands ...
    public static final String TYPING = "TYPING"; 
    
    private Protocol(){}
}