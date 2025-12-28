package server;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class EmailService {
    // REPLACE THESE WITH YOUR DETAILS
    private static final String MY_EMAIL = "kazutokirito7009@gmail.com";
    private static final String MY_APP_PASSWORD = "eijg agox yqdq vlae"; // The 16-char App Password

    public static void sendOTP(String recipient, String otpCode) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(MY_EMAIL, MY_APP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(MY_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject("JavaChat Verification Code");
            message.setText("Your OTP code is garbage, just like you... Just kidding it's: " + otpCode + "\n\nWelcome to JavaChat!😏");

            Transport.send(message);
            System.out.println("OTP sent to " + recipient);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}