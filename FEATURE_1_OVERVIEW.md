# AIVES Backend - Feature 1: Lecturer Question Bank, RAG & Rubric Management

## 1. Overview
Phân hệ Chức năng 1 (Dành cho Giảng viên & Khảo thí) cung cấp toàn bộ nghiệp vụ nền tảng:
- Quản lý học liệu giáo trình (Syllabus RAG Ingestion Pipeline với text chunking & vector embeddings 1536 chiều).
- Ngân hàng câu hỏi vấn đáp phân loại theo độ khó (EASY, MEDIUM, HARD) và thang đo nhận thức Bloom (Bloom 2, 3, 4, 5).
- Barem chấm điểm (Rubric) chi tiết với các tiêu chí thành phần, trọng số (weight) và điểm tối đa 10.0.

## 2. Technical Enhancements
- **Clean Architecture & Dependency Injection:** Thay thế toàn bộ Lombok `@RequiredArgsConstructor` bằng constructor tường minh, giúp mã nguồn tương thích hoàn toàn với tất cả IDE và loại bỏ 100% cảnh báo đỏ.
- **PostgreSQL 16 + pgvector Integration:** Cấu hình `@ColumnTransformer(read = "embedding::text", write = "?::vector")` trên các entity `Question` và `DocumentChunk`, loại bỏ lỗi ép kiểu vector khi lưu trữ dữ liệu.
- **Automated Seeding:** `DataInitializer.java` tự động nạp các môn học thực tế (`CS301`, `PRJ301`, `SWP391`, `AI204`), tự động gán quyền `canApproveRag=true` và `canEditRubric=true` cho giảng viên `lecturer@aives.edu.vn`.

## 3. Verified Endpoints
- `POST /api/v1/auth/login`: Xác thực tài khoản Giảng viên và cấp quyền JWT Token.
- `GET /api/v1/lecturer/questions`: Truy xuất ngân hàng câu hỏi theo môn học và Bloom taxonomy.
- `POST /api/v1/lecturer/questions`: Thêm câu hỏi mới kèm 3 tiêu chí Rubric trực tiếp vào PostgreSQL.
- `GET /api/v1/lecturer/rubrics/by-subject/{subjectId}`: Lấy barem chấm điểm chi tiết theo môn học.
- `POST /api/v1/lecturer/documents/upload`: Tải lên và xử lý vector hóa học liệu RAG phục vụ thi vấn đáp.
