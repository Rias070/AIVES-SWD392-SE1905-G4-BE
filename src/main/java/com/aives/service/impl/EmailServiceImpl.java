package com.aives.service.impl;

import com.aives.service.EmailService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service thực thi gửi email thực tế qua SMTP Gmail với giao diện HTML chuẩn AIVES.
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${spring.mail.username:contact.basaucycle2026@gmail.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:AIVES Examination System}")
    private String fromName;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    /**
     * Tạo MimeMessageHelper với FROM address chuẩn chống spam.
     */
    private MimeMessageHelper createHelper(MimeMessage message) throws Exception {
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(fromEmail, fromName, "UTF-8"));
        return helper;
    }

    @Override
    @Async
    public void sendRegistrationWelcomeEmail(String toEmail, String fullName, String role, String userCode) {
        log.info("Preparing to send registration welcome email to: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = createHelper(message);

            helper.setTo(toEmail);
            helper.setSubject("AIVES - Xác nhận đăng ký tài khoản thành công (" + role + ")");

            String loginLink = frontendUrl + "/login";
            String currentTime = LocalDateTime.now().format(DATE_FORMATTER);

            String roleLabel;
            String roleNote;
            if ("LECTURER".equalsIgnoreCase(role)) {
                roleLabel = "Giảng viên / Hội đồng Khảo thí (LECTURER)";
                roleNote = "Với vai trò Giảng viên, bạn có quyền truy cập kho học liệu RAG, ngân hàng câu hỏi theo thang Bloom và cấu hình barem điểm Rubric cho các môn học được phân công.";
            } else if ("ADMIN".equalsIgnoreCase(role)) {
                roleLabel = "Quản trị viên Hệ thống (ADMIN)";
                roleNote = "Tài khoản Quản trị viên có toàn quyền cấu hình mô hình AI khảo thí, phân công giảng viên và quản lý tài khoản người dùng.";
            } else {
                roleLabel = "Sinh viên Khảo thí (STUDENT)";
                roleNote = "Trước khi tham gia phòng thi vấn đáp viva trực tuyến với AI Examiner qua WebSocket, bạn hãy hoàn thành bước Kiểm tra thiết bị Mic & Camera (Pre-exam Check) để đảm bảo chất lượng khảo thí tốt nhất.";
            }

            String displayUserCode = (userCode != null && !userCode.isBlank()) ? userCode : "N/A";

            String htmlBody = """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 0; }
                            .container { max-width: 620px; margin: 24px auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1); border: 1px solid #e2e8f0; }
                            .header { background: linear-gradient(135deg, #0284c7 0%%, #0369a1 50%%, #0f172a 100%%); padding: 36px 24px; text-align: center; color: #ffffff; }
                            .header-badge { display: inline-block; padding: 4px 12px; background-color: rgba(255,255,255,0.15); border-radius: 9999px; font-size: 11px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase; margin-bottom: 12px; backdrop-filter: blur(4px); }
                            .header h1 { margin: 0; font-size: 28px; font-weight: 900; letter-spacing: 2px; }
                            .header p { margin: 8px 0 0 0; font-size: 14px; color: #e0f2fe; }
                            .content { padding: 32px 28px; background-color: #ffffff; color: #1e293b; }
                            .greeting { font-size: 16px; line-height: 1.6; color: #334155; margin-bottom: 20px; }
                            .card { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin: 24px 0; }
                            .card-title { font-size: 13px; font-weight: 800; color: #0369a1; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 14px; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }
                            .info-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; border-bottom: 1px dashed #e2e8f0; }
                            .info-row:last-child { border-bottom: none; }
                            .info-label { color: #64748b; font-weight: 500; }
                            .info-value { color: #0f172a; font-weight: 700; text-align: right; }
                            .highlight-box { background-color: #eff6ff; border-left: 4px solid #0284c7; padding: 14px 16px; margin: 20px 0; border-radius: 0 8px 8px 0; font-size: 13px; color: #1e40af; line-height: 1.5; }
                            .btn-container { text-align: center; margin: 32px 0 20px 0; }
                            .btn { display: inline-block; padding: 14px 36px; background: linear-gradient(135deg, #0284c7 0%%, #0369a1 100%%); color: #ffffff !important; text-decoration: none; font-size: 15px; font-weight: bold; border-radius: 10px; box-shadow: 0 4px 14px rgba(2, 132, 199, 0.35); }
                            .footer { text-align: center; padding: 24px; font-size: 12px; color: #94a3b8; background-color: #f8fafc; border-top: 1px solid #e2e8f0; line-height: 1.6; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <div class="header-badge">Hệ Thống Khảo Thí Vấn Đáp AI</div>
                                <h1>A I V E S</h1>
                                <p>AI-Powered Online Viva Examination System</p>
                            </div>
                            <div class="content">
                                <p class="greeting">Xin chào <strong>%s</strong>,</p>
                                <p class="greeting">Chúc mừng bạn đã hoàn tất đăng ký tài khoản thành công trên nền tảng <strong>AIVES</strong>. Dưới đây là thông tin chi tiết về tài khoản vừa được khởi tạo:</p>

                                <div class="card">
                                    <div class="card-title">THÔNG TIN TÀI KHOẢN ĐỊNH DANH</div>
                                    <table style="width: 100%%; border-collapse: collapse;">
                                        <tr style="border-bottom: 1px dashed #e2e8f0;">
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Họ và tên:</td>
                                            <td style="padding: 8px 0; color: #0f172a; font-weight: 700; text-align: right; font-size: 14px;">%s</td>
                                        </tr>
                                        <tr style="border-bottom: 1px dashed #e2e8f0;">
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Email đăng ký:</td>
                                            <td style="padding: 8px 0; color: #0284c7; font-weight: 700; text-align: right; font-size: 14px;">%s</td>
                                        </tr>
                                        <tr style="border-bottom: 1px dashed #e2e8f0;">
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Mã số định danh:</td>
                                            <td style="padding: 8px 0; color: #0f172a; font-weight: 700; text-align: right; font-size: 14px;">%s</td>
                                        </tr>
                                        <tr style="border-bottom: 1px dashed #e2e8f0;">
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Vai trò hệ thống:</td>
                                            <td style="padding: 8px 0; color: #0369a1; font-weight: 700; text-align: right; font-size: 14px;">%s</td>
                                        </tr>
                                        <tr>
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Thời gian đăng ký:</td>
                                            <td style="padding: 8px 0; color: #64748b; text-align: right; font-size: 14px;">%s</td>
                                        </tr>
                                    </table>
                                </div>

                                <div class="highlight-box">
                                    <strong>Hướng dẫn tiếp theo:</strong><br/>
                                    %s
                                </div>

                                <div class="btn-container">
                                    <a href="%s" class="btn">Đăng nhập vào Hệ thống AIVES</a>
                                </div>
                            </div>
                            <div class="footer">
                                <p>&copy; 2026 AIVES Examination System. Nền tảng khảo thí vấn đáp trực tuyến tích hợp Trí tuệ nhân tạo.</p>
                                <p>Email này được gửi tự động đến <strong>%s</strong> khi hoàn tất đăng ký tài khoản.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(
                    fullName,
                    fullName,
                    toEmail,
                    displayUserCode,
                    roleLabel,
                    currentTime,
                    roleNote,
                    loginLink,
                    toEmail
            );

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Registration welcome HTML email successfully sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send registration welcome email to {}: {}", toEmail, e.getMessage(), e);
            // Không throw exception để không làm gián đoạn luồng đăng ký tài khoản chính của người dùng
            log.warn("User account registered successfully, but email notification failed for: {}", toEmail);
        }
    }

    @Override
    @Async
    public void sendAccountCreationEmail(String toEmail, String fullName, String role, String userCode, String tempPassword) {
        log.info("Preparing to send account creation email to: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = createHelper(message);

            helper.setTo(toEmail);
            helper.setSubject("AIVES - Thông báo cấp tài khoản hệ thống (" + role + ")");

            String loginLink = frontendUrl + "/login";
            String currentTime = LocalDateTime.now().format(DATE_FORMATTER);

            String roleLabel;
            if ("LECTURER".equalsIgnoreCase(role)) {
                roleLabel = "Giảng viên / Hội đồng Khảo thí";
            } else if ("ADMIN".equalsIgnoreCase(role)) {
                roleLabel = "Quản trị viên Hệ thống";
            } else {
                roleLabel = "Sinh viên Khảo thí";
            }

            String displayUserCode = (userCode != null && !userCode.isBlank()) ? userCode : "N/A";

            String htmlBody = """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 0; }
                            .container { max-width: 620px; margin: 24px auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.1); border: 1px solid #e2e8f0; }
                            .header { background: linear-gradient(135deg, #0284c7 0%%, #0369a1 50%%, #0f172a 100%%); padding: 36px 24px; text-align: center; color: #ffffff; }
                            .header h1 { margin: 0; font-size: 28px; font-weight: 900; letter-spacing: 2px; }
                            .header p { margin: 8px 0 0 0; font-size: 14px; color: #e0f2fe; }
                            .content { padding: 32px 28px; background-color: #ffffff; color: #1e293b; }
                            .card { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin: 24px 0; }
                            .card-title { font-size: 13px; font-weight: 800; color: #0369a1; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 14px; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }
                            .btn-container { text-align: center; margin: 32px 0 20px 0; }
                            .btn { display: inline-block; padding: 14px 36px; background: linear-gradient(135deg, #0284c7 0%%, #0369a1 100%%); color: #ffffff !important; text-decoration: none; font-size: 15px; font-weight: bold; border-radius: 10px; }
                            .warning-box { background-color: #fffbeb; border-left: 4px solid #f59e0b; padding: 14px 16px; margin: 20px 0; border-radius: 0 8px 8px 0; font-size: 13px; color: #92400e; }
                            .footer { text-align: center; padding: 24px; font-size: 12px; color: #94a3b8; background-color: #f8fafc; border-top: 1px solid #e2e8f0; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>A I V E S</h1>
                                <p>AI-Powered Online Viva Examination System</p>
                            </div>
                            <div class="content">
                                <p>Xin chào <strong>%s</strong>,</p>
                                <p>Tài khoản của bạn đã được Quản trị viên cấp thành công trên hệ thống <strong>AIVES</strong>. Thông tin đăng nhập của bạn như sau:</p>

                                <div class="card">
                                    <div class="card-title">THÔNG TIN ĐĂNG NHẬP</div>
                                    <table style="width: 100%%; border-collapse: collapse;">
                                        <tr style="border-bottom: 1px dashed #e2e8f0;">
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Tên đăng nhập (Email):</td>
                                            <td style="padding: 8px 0; color: #0284c7; font-weight: 700; text-align: right; font-size: 14px;">%s</td>
                                        </tr>
                                        <tr style="border-bottom: 1px dashed #e2e8f0;">
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Mật khẩu tạm thời:</td>
                                            <td style="padding: 8px 0; color: #e11d48; font-weight: 700; text-align: right; font-size: 14px; font-family: monospace;">%s</td>
                                        </tr>
                                        <tr style="border-bottom: 1px dashed #e2e8f0;">
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Mã định danh:</td>
                                            <td style="padding: 8px 0; color: #0f172a; font-weight: 700; text-align: right; font-size: 14px;">%s</td>
                                        </tr>
                                        <tr>
                                            <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Vai trò:</td>
                                            <td style="padding: 8px 0; color: #0369a1; font-weight: 700; text-align: right; font-size: 14px;">%s</td>
                                        </tr>
                                    </table>
                                </div>

                                <div class="warning-box">
                                    <strong>Lưu ý bảo mật:</strong> Vui lòng đổi mật khẩu mới ngay trong lần đăng nhập đầu tiên để bảo vệ an toàn thông tin cá nhân và tài liệu khảo thí.
                                </div>

                                <div class="btn-container">
                                    <a href="%s" class="btn">Đăng nhập ngay</a>
                                </div>
                            </div>
                            <div class="footer">
                                <p>&copy; 2026 AIVES. Email tự động từ hệ thống khảo thí vấn đáp trực tuyến AIVES.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(
                    fullName,
                    toEmail,
                    tempPassword,
                    displayUserCode,
                    roleLabel,
                    loginLink
            );

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Account creation email successfully sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account creation email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void sendAccountActivationEmail(String toEmail, String fullName, String role, String activationLink) {
        log.info("Preparing to send account activation email to: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = createHelper(message);

            helper.setTo(toEmail);
            helper.setSubject("AIVES - Kích hoạt tài khoản để bắt đầu sử dụng");

            String roleLabel;
            if ("LECTURER".equalsIgnoreCase(role)) {
                roleLabel = "Giảng viên / Hội đồng Khảo thí";
            } else {
                roleLabel = "Sinh viên Khảo thí";
            }

            String htmlBody = """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 0; }
                            .container { max-width: 620px; margin: 24px auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.1); border: 1px solid #e2e8f0; }
                            .header { background: linear-gradient(135deg, #0284c7 0%%, #0369a1 50%%, #0f172a 100%%); padding: 36px 24px; text-align: center; color: #ffffff; }
                            .header-badge { display: inline-block; padding: 4px 12px; background-color: rgba(255,255,255,0.15); border-radius: 9999px; font-size: 11px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase; margin-bottom: 12px; }
                            .header h1 { margin: 0; font-size: 28px; font-weight: 900; letter-spacing: 2px; }
                            .header p { margin: 8px 0 0 0; font-size: 14px; color: #e0f2fe; }
                            .content { padding: 32px 28px; background-color: #ffffff; color: #1e293b; }
                            .greeting { font-size: 16px; line-height: 1.6; color: #334155; margin-bottom: 20px; }
                            .card { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin: 24px 0; }
                            .card-title { font-size: 13px; font-weight: 800; color: #0369a1; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 14px; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }
                            .warning-box { background-color: #fffbeb; border-left: 4px solid #f59e0b; padding: 14px 16px; margin: 20px 0; border-radius: 0 8px 8px 0; font-size: 13px; color: #92400e; }
                            .btn-container { text-align: center; margin: 32px 0 20px 0; }
                            .btn { display: inline-block; padding: 14px 36px; background: linear-gradient(135deg, #0284c7 0%%, #0369a1 100%%); color: #ffffff !important; text-decoration: none; font-size: 15px; font-weight: bold; border-radius: 10px; box-shadow: 0 4px 14px rgba(2, 132, 199, 0.35); }
                            .footer { text-align: center; padding: 24px; font-size: 12px; color: #94a3b8; background-color: #f8fafc; border-top: 1px solid #e2e8f0; line-height: 1.6; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <div class="header-badge">Kích hoạt tài khoản</div>
                                <h1>A I V E S</h1>
                                <p>AI-Powered Online Viva Examination System</p>
                            </div>
                            <div class="content">
                                <p class="greeting">Xin chào <strong>%s</strong>,</p>
                                <p class="greeting">Cảm ơn bạn đã đăng ký tài khoản <strong>AIVES</strong> với vai trò <strong>%s</strong>.</p>
                                <p class="greeting">Để bắt đầu sử dụng hệ thống, vui lòng nhấp vào nút bên dưới để <strong>kích hoạt tài khoản</strong> của bạn. Liên kết kích hoạt sẽ hết hạn sau <strong>24 giờ</strong>.</p>

                                <div class="warning-box">
                                    <strong>Lưu ý quan trọng:</strong> Tài khoản của bạn sẽ chưa thể đăng nhập cho đến khi được kích hoạt qua email này.
                                </div>

                                <div class="btn-container">
                                    <a href="%s" class="btn">Kích hoạt tài khoản AIVES</a>
                                </div>

                                <p style="font-size: 12px; color: #64748b; text-align: center; margin-top: 16px;">
                                    Nếu nút không hoạt động, hãy sao chép và dán liên kết sau vào trình duyệt của bạn:<br/>
                                    <code style="font-size: 11px; word-break: break-all; color: #0284c7;">%s</code>
                                </p>
                            </div>
                            <div class="footer">
                                <p>&copy; 2026 AIVES Examination System.</p>
                                <p>Email này được gửi tự động đến <strong>%s</strong>. Nếu bạn không đăng ký tài khoản này, vui lòng bỏ qua email này.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(
                    fullName,
                    roleLabel,
                    activationLink,
                    activationLink,
                    toEmail
            );

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Account activation email successfully sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account activation email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void sendActivationSuccessEmail(String toEmail, String fullName, String role) {
        log.info("Preparing to send activation success email to: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = createHelper(message);

            helper.setTo(toEmail);
            helper.setSubject("AIVES - Tài khoản đã được kích hoạt thành công!");

            String loginLink = frontendUrl + "/login";

            String htmlBody = """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 0; }
                            .container { max-width: 620px; margin: 24px auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.1); border: 1px solid #e2e8f0; }
                            .header { background: linear-gradient(135deg, #059669 0%%, #047857 100%%); padding: 36px 24px; text-align: center; color: #ffffff; }
                            .header-badge { display: inline-block; padding: 4px 12px; background-color: rgba(255,255,255,0.2); border-radius: 9999px; font-size: 11px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase; margin-bottom: 12px; }
                            .header h1 { margin: 0; font-size: 28px; font-weight: 900; letter-spacing: 2px; }
                            .header p { margin: 8px 0 0 0; font-size: 14px; color: #a7f3d0; }
                            .content { padding: 32px 28px; background-color: #ffffff; color: #1e293b; }
                            .greeting { font-size: 16px; line-height: 1.6; color: #334155; margin-bottom: 20px; }
                            .success-box { background-color: #ecfdf5; border-left: 4px solid #059669; padding: 14px 16px; margin: 20px 0; border-radius: 0 8px 8px 0; font-size: 13px; color: #065f46; }
                            .btn-container { text-align: center; margin: 32px 0 20px 0; }
                            .btn { display: inline-block; padding: 14px 36px; background: linear-gradient(135deg, #059669 0%%, #047857 100%%); color: #ffffff !important; text-decoration: none; font-size: 15px; font-weight: bold; border-radius: 10px; box-shadow: 0 4px 14px rgba(5, 150, 105, 0.35); }
                            .footer { text-align: center; padding: 24px; font-size: 12px; color: #94a3b8; background-color: #f8fafc; border-top: 1px solid #e2e8f0; line-height: 1.6; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <div class="header-badge">Kích hoạt thành công</div>
                                <h1>A I V E S</h1>
                                <p>AI-Powered Online Viva Examination System</p>
                            </div>
                            <div class="content">
                                <p class="greeting">Xin chào <strong>%s</strong>,</p>
                                <p class="greeting">Tài khoản của bạn trên hệ thống <strong>AIVES</strong> đã được kích hoạt thành công!</p>

                                <div class="success-box">
                                    <strong>Chúc mừng!</strong> Tài khoản của bạn đã sẵn sàng sử dụng. Bây giờ bạn có thể đăng nhập và bắt đầu tham gia các ca thi vấn đáp AI trên nền tảng AIVES.
                                </div>

                                <div class="btn-container">
                                    <a href="%s" class="btn">Đăng nhập ngay</a>
                                </div>
                            </div>
                            <div class="footer">
                                <p>&copy; 2026 AIVES Examination System.</p>
                                <p>Email này được gửi tự động đến <strong>%s</strong>.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(
                    fullName,
                    loginLink,
                    toEmail
            );

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Activation success email successfully sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send activation success email to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
