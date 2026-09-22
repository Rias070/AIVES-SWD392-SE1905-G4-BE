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

Extension vector sẽ được tự động kích hoạt thông qua file `docker/init.sql`.

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
Khi ứng dụng khởi động lần đầu, hệ thống tự động sinh 3 role (`ADMIN`, `LECTURER`, `STUDENT`) và 3 tài khoản mẫu:
- **Admin**: `admin@aives.edu.vn` / `Admin@123`
- **Lecturer**: `lecturer@aives.edu.vn` / `Lecturer@123`
- **Student**: `student@aives.edu.vn` / `Student@123`
