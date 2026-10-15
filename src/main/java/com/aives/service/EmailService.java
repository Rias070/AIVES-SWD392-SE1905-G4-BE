package com.aives.service;

import com.aives.application.port.out.NotificationPort;

/**
 * Service gửi email thông báo cho hệ thống khảo thí AIVES.
 */
public interface EmailService extends NotificationPort {

    /**
     * Gửi email chào mừng và xác nhận đăng ký tài khoản thành công cho Sinh viên hoặc Giảng viên.
     *
     * @param toEmail  email người nhận
     * @param fullName họ và tên đầy đủ
     * @param role     vai trò (STUDENT / LECTURER)
     * @param userCode mã số định danh (MSSV hoặc mã GV)
     */
    void sendRegistrationWelcomeEmail(String toEmail, String fullName, String role, String userCode);

    /**
     * Gửi email thông báo tạo tài khoản bởi quản trị viên kèm thông tin đăng nhập.
     *
     * @param toEmail      email người nhận
     * @param fullName     họ và tên đầy đủ
     * @param role         vai trò (STUDENT / LECTURER / ADMIN)
     * @param userCode     mã số định danh
     * @param tempPassword mật khẩu tài khoản
     */
    void sendAccountCreationEmail(String toEmail, String fullName, String role, String userCode, String tempPassword);
}
