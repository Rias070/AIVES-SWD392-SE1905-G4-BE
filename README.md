# AIVES Backend - AI-powered Viva Exam System

Dịch vụ Backend cho hệ thống thi vấn đáp tự động ứng dụng trí tuệ nhân tạo (AIVES), xây dựng trên nền tảng **Spring Boot 3**, **PostgreSQL** kết hợp extension **pgvector**, **Spring Security JWT**, và **OpenAPI Swagger**.

## 1. Yêu cầu môi trường
- Java 17+
- Docker & Docker Compose (cho PostgreSQL 16 tích hợp `pgvector`)

## 2. Khởi động CSDL Docker
Khởi chạy container PostgreSQL pgvector trên cổng 5432:
```bash
docker compose up -d
```

Extension `vector` sẽ được tự động kích hoạt thông qua file `docker/init.sql`.

> **Lưu ý quan trọng:** file `docker/init.sql` **chỉ được chạy 1 lần**, tại thời điểm Docker tạo mới volume `aives_pgdata`.
> Vì vậy, khi clone repo lần đầu **hoặc** sau khi có thay đổi trong `docker/init.sql` (ví dụ seed dữ liệu, hash mật khẩu),
> bắt buộc phải xoá volume cũ để script được chạy lại:
> ```bash
> # Windows
> docker compose down -v
> docker compose up -d
> ```
> Nếu bỏ qua bước `down -v`, dữ liệu cũ trong volume vẫn được giữ nguyên và script mới sẽ không có hiệu lực.

## 3. Khởi chạy ứng dụng Backend
Sử dụng Maven Wrapper:
```bash
# Windows
./mvnw.cmd spring-boot:run

# Linux / MacOS
./mvnw spring-boot:run
```

## 4. Tài liệu API (Swagger UI)
Sau khi ứng dụng khởi động thành công, truy cập Swagger UI:
- URL: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## 5. Tài khoản mặc định khởi tạo sẵn (DataInitializer)
Khi ứng dụng khởi động, hệ thống tự động sinh 3 role (`ADMIN`, `LECTURER`, `STUDENT`) và 3 tài khoản mẫu:
- **Admin**: `admin@aives.edu.vn` / `Admin@123`
- **Lecturer**: `lecturer@aives.edu.vn` / `Lecturer@123`
- **Student**: `student@aives.edu.vn` / `Student@123`

Mật khẩu được lưu dưới dạng **BCrypt hash**:
- Trong `docker/init.sql`, hash được sinh trực tiếp bởi extension `pgcrypto` với `crypt('<mật khẩu>', gen_salt('bf', 10))`
  → tương thích với `BCryptPasswordEncoder` của Spring Security.
- `DataInitializer` đồng thời kiểm tra lại mật khẩu mỗi khi app khởi động. Nếu hash trong DB không khớp
  (ví dụ do dữ liệu cũ hoặc seed bị sai), ứng dụng sẽ tự reset về mật khẩu mặc định ở trên
  và ghi log: `Reset default password for account: ...`.

### Gỡ lỗi đăng nhập thất bại (lỗi `1006 - Email or password is invalid`)
Kiểm tra hash trong database:
```sql
SELECT email, status, left(password, 7) AS hash_prefix, length(password) AS hash_len FROM users;
```
Kết quả đúng phải là:
- `hash_prefix` = `$2a$10$`
- `hash_len` = `60`

Nếu không khớp, hãy xoá volume và khởi động lại database theo mục 2, sau đó khởi động lại ứng dụng
(ứng dụng sẽ tự reset lại mật khẩu mặc định).

