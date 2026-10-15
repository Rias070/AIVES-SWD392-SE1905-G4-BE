# Email Activation Flow - Test Guide

## Tổng quan
Flow email activation cho AIVES — admin tạo user thì ACTIVE ngay, user tự đăng ký phải activate qua email.

## Prerequisites
- Backend Spring Boot đang chạy ở `http://localhost:8080`
- PostgreSQL + pgvector đang chạy
- Gmail SMTP cấu hình trong `application.properties` đã đúng (đang dùng `contact.basaucycle2026@gmail.com`)

## Khởi động Backend
```cmd
cd d:\Work\SWD392\AIVES\AIVES-SWD392-SE1905-G4-BE
mvnw.cmd spring-boot:run
```

---

## Test Cases

### Test 1: Self-register STUDENT → phải PENDING_EMAIL
**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test.student@aives.edu.vn\",\"password\":\"SecurePass123\",\"fullName\":\"Test Student\",\"phoneNumber\":\"0912345678\",\"roleName\":\"STUDENT\"}"
```
**Expected Response (HTTP 200):**
```json
{
  "code": 1000,
  "message": "Register account successfully",
  "result": {
    "token": null,
    "authenticated": false,
    "user": {
      "uuid": "...",
      "email": "test.student@aives.edu.vn",
      "status": "PENDING_EMAIL",
      ...
    }
  }
}
```
**Verify trong DB:** `SELECT email, status, activated_at FROM users WHERE email = 'test.student@aives.edu.vn';` → status phải là `PENDING_EMAIL`, `activated_at` phải `NULL`.

**Verify email:** Kiểm tra hộp thư `test.student@aives.edu.vn` (bao gồm Spam) — phải có email "AIVES - Kích hoạt tài khoản" với nút "Kích hoạt tài khoản AIVES".

---

### Test 2: Login với tài khoản PENDING → phải báo lỗi
**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test.student@aives.edu.vn\",\"password\":\"SecurePass123\"}"
```
**Expected Response (HTTP 403):**
```json
{
  "code": 1300,
  "message": "Hãy kích hoạt tài khoản trong email đã đăng ký trước khi đăng nhập."
}
```

---

### Test 3: Click activation link trong email
Copy link activation từ email (có dạng):
```
http://localhost:5173/activate?token=eyJhbGciOiJIUzUxMiJ9...
```

**Request (qua browser hoặc curl):**
```bash
curl "http://localhost:8080/api/v1/auth/activate?token=eyJhbGciOiJIUzUxMiJ9..."
```
**Expected Response (HTTP 200):**
```json
{
  "code": 1000,
  "message": "Kích hoạt tài khoản thành công! Bạn có thể đăng nhập ngay.",
  "result": {
    "email": "test.student@aives.edu.vn",
    "fullName": "Test Student",
    "message": "Kích hoạt tài khoản thành công! Bạn có thể đăng nhập ngay."
  }
}
```
**Verify trong DB:** `status` phải đổi thành `ACTIVE`, `activated_at` được set.

**Verify email:** Phải nhận email "AIVES - Tài khoản đã được kích hoạt thành công!".

---

### Test 4: Login sau khi activate → thành công
**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test.student@aives.edu.vn\",\"password\":\"SecurePass123\"}"
```
**Expected Response (HTTP 200):**
```json
{
  "code": 1000,
  "message": "Login successfully",
  "result": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "authenticated": true,
    "user": { ..., "status": "ACTIVE" }
  }
}
```

---

### Test 5: Admin tạo user (qua admin endpoint) → ACTIVE ngay
**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/admin/users ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer <admin-jwt-token>" ^
  -d "{\"email\":\"new.user@aives.edu.vn\",\"password\":\"SecurePass123\",\"fullName\":\"New User\",\"roleName\":\"STUDENT\",\"userCode\":\"SE20001\"}"
```
**Expected:** User được tạo với `status=ACTIVE` ngay, login được luôn (không cần email activation).

---

### Test 6: Register với roleName=ADMIN → ACTIVE ngay
**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test.admin@aives.edu.vn\",\"password\":\"SecurePass123\",\"fullName\":\"Test Admin\",\"roleName\":\"ADMIN\"}"
```
**Expected:** Trả về `token` và `authenticated: true` ngay (admin không cần activate).

---

### Test 7: Resend activation email
**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/resend-activation ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test.student@aives.edu.vn\"}"
```
**Expected Response (HTTP 200):**
```json
{
  "code": 1000,
  "message": "Email kích hoạt mới đã được gửi. Vui lòng kiểm tra hộp thư (bao gồm thư rác)."
}
```
**Verify:** Email mới được gửi với link activation mới (token mới).

---

### Test 8: Activate với token hết hạn
Đợi 24h hoặc tạo token expired thủ công bằng cách set `jwt.activation-expiration=1` rồi test.

**Expected Response (HTTP 400):**
```json
{
  "code": 1303,
  "message": "Liên kết kích hoạt đã hết hạn (sau 24 giờ). Vui lòng yêu cầu gửi lại email."
}
```

---

### Test 9: Activate với token không hợp lệ
**Request:**
```bash
curl "http://localhost:8080/api/v1/auth/activate?token=invalid-token-123"
```
**Expected Response (HTTP 400):**
```json
{
  "code": 1302,
  "message": "Liên kết kích hoạt không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu gửi lại email."
}
```

---

## Tổng kết các endpoint mới

| Endpoint | Method | Auth | Mô tả |
|----------|--------|------|-------|
| `/api/v1/auth/register` | POST | Public | Tạo tài khoản mới (PENDING_EMAIL hoặc ACTIVE nếu ADMIN) |
| `/api/v1/auth/activate?token=...` | GET | Public | Kích hoạt tài khoản qua token từ email |
| `/api/v1/auth/resend-activation` | POST | Public | Gửi lại email kích hoạt |
| `/api/v1/auth/login` | POST | Public | Đăng nhập (đã cập nhật để check PENDING_EMAIL) |

## Error codes mới
- `1300` ACCOUNT_NOT_ACTIVATED — Hãy kích hoạt tài khoản trong email
- `1301` ACCOUNT_ALREADY_ACTIVATED — Tài khoản đã được kích hoạt
- `1302` ACTIVATION_TOKEN_INVALID — Token không hợp lệ
- `1303` ACTIVATION_TOKEN_EXPIRED — Token hết hạn
