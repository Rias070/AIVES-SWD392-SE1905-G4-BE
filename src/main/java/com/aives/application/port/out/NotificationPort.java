package com.aives.application.port.out;

/**
 * Outbound Port: Gửi thông báo / email ra ngoài hệ thống (Clean / Onion Architecture).
 */
public interface NotificationPort {

    void sendRegistrationWelcomeEmail(String toEmail, String fullName, String role, String userCode);

    void sendAccountCreationEmail(String toEmail, String fullName, String role, String userCode, String tempPassword);
}
