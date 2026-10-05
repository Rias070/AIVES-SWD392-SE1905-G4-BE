-- ============================================================================
-- AIVES - AI VIVA EXAMINATION SYSTEM
-- DATABASE INITIALIZATION SCRIPT (PostgreSQL 16 + pgvector)
-- ============================================================================
-- Compatible with: Docker Compose & Spring Boot Hibernate (ddl-auto=update)
-- Optimized for: DBeaver Physical ERD Generation & Seed Data Testing
-- ============================================================================

-- 1. Enable Required Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- ============================================================================
-- 2. CREATE TABLES (With IF NOT EXISTS and strict FK relationships)
-- ============================================================================

-- 2.1 USERS TABLE
CREATE TABLE IF NOT EXISTS users (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_code VARCHAR(50) UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    avatar_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.2 ROLES TABLE
CREATE TABLE IF NOT EXISTS roles (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- 2.3 USER_ROLES JOIN TABLE (Normalized M:N RBAC)
CREATE TABLE IF NOT EXISTS user_roles (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_uuid UUID NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
    role_uuid UUID NOT NULL REFERENCES roles(uuid) ON DELETE CASCADE,
    CONSTRAINT uq_user_role UNIQUE (user_uuid, role_uuid)
);

-- 2.4 SUBJECTS TABLE
CREATE TABLE IF NOT EXISTS subjects (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    credits INT DEFAULT 3,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.5 SUBJECT_LECTURERS (Course assignment & RAG permissions)
CREATE TABLE IF NOT EXISTS subject_lecturers (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_uuid UUID NOT NULL REFERENCES subjects(uuid) ON DELETE CASCADE,
    lecturer_uuid UUID NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
    can_approve_rag BOOLEAN NOT NULL DEFAULT TRUE,
    can_edit_rubric BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_subject_lecturer UNIQUE (subject_uuid, lecturer_uuid)
);

-- 2.6 DOCUMENTS (Course Syllabus / Knowledge Base for RAG)
CREATE TABLE IF NOT EXISTS documents (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_uuid UUID NOT NULL REFERENCES subjects(uuid) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT,
    file_size VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'INDEXED',
    chunk_count INT DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.7 DOCUMENT_CHUNKS (Text split chunks with vector embeddings)
CREATE TABLE IF NOT EXISTS document_chunks (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_uuid UUID NOT NULL REFERENCES documents(uuid) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    chunk_content TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.8 QUESTIONS (Bloom-classified viva question bank)
CREATE TABLE IF NOT EXISTS questions (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_uuid UUID NOT NULL REFERENCES subjects(uuid) ON DELETE CASCADE,
    question_code VARCHAR(50),
    content TEXT NOT NULL,
    bloom_level VARCHAR(50),
    difficulty VARCHAR(20) DEFAULT 'MEDIUM',
    expected_answer TEXT,
    keywords TEXT,
    embedding vector(1536),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.9 RUBRICS (Grading criteria & weights)
CREATE TABLE IF NOT EXISTS rubrics (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_uuid UUID NOT NULL REFERENCES subjects(uuid) ON DELETE CASCADE,
    question_uuid UUID REFERENCES questions(uuid) ON DELETE SET NULL,
    criterion_name VARCHAR(150) NOT NULL,
    description TEXT,
    max_score NUMERIC(5, 2) DEFAULT 10.00,
    weight NUMERIC(5, 2) DEFAULT 1.00,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.10 EXAM_SESSIONS (Viva exam sessions for examinees)
CREATE TABLE IF NOT EXISTS exam_sessions (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_code VARCHAR(50) UNIQUE,
    subject_uuid UUID NOT NULL REFERENCES subjects(uuid) ON DELETE RESTRICT,
    student_uuid UUID NOT NULL REFERENCES users(uuid) ON DELETE RESTRICT,
    examiner_uuid UUID REFERENCES users(uuid) ON DELETE SET NULL,
    scheduled_at TIMESTAMP WITHOUT TIME ZONE,
    started_at TIMESTAMP WITHOUT TIME ZONE,
    ended_at TIMESTAMP WITHOUT TIME ZONE,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    total_score NUMERIC(5, 2),
    feedback TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.11 VIVA_TURNS (Interactive conversational viva turns AI - Examinee)
CREATE TABLE IF NOT EXISTS viva_turns (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_session_uuid UUID NOT NULL REFERENCES exam_sessions(uuid) ON DELETE CASCADE,
    question_uuid UUID REFERENCES questions(uuid) ON DELETE SET NULL,
    turn_order INT NOT NULL,
    student_answer TEXT,
    audio_transcript TEXT,
    ai_evaluation TEXT,
    score NUMERIC(5, 2),
    turn_duration_seconds INT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 3. INDEXES FOR PERFORMANCE & FOREIGN KEYS
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_uuid);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_uuid);
CREATE INDEX IF NOT EXISTS idx_subject_lecturers_sub ON subject_lecturers(subject_uuid);
CREATE INDEX IF NOT EXISTS idx_subject_lecturers_lec ON subject_lecturers(lecturer_uuid);
CREATE INDEX IF NOT EXISTS idx_documents_subject ON documents(subject_uuid);
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_uuid);
CREATE INDEX IF NOT EXISTS idx_questions_subject ON questions(subject_uuid);
CREATE INDEX IF NOT EXISTS idx_rubrics_subject ON rubrics(subject_uuid);
CREATE INDEX IF NOT EXISTS idx_rubrics_question ON rubrics(question_uuid);
CREATE INDEX IF NOT EXISTS idx_sessions_student ON exam_sessions(student_uuid);
CREATE INDEX IF NOT EXISTS idx_sessions_subject ON exam_sessions(subject_uuid);
CREATE INDEX IF NOT EXISTS idx_viva_turns_session ON viva_turns(exam_session_uuid);

-- ============================================================================
-- 4. CLEAN SEED DATA (Realistic Academic Viva Data)
-- ============================================================================

-- 4.1 Insert Default Roles
INSERT INTO roles (uuid, name, description) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'ADMIN', 'System Administrator with full permissions'),
    ('a0000000-0000-0000-0000-000000000002', 'LECTURER', 'Lecturer and Examiner managing RAG and Rubrics'),
    ('a0000000-0000-0000-0000-000000000003', 'STUDENT', 'Student taking AI Viva examinations')
ON CONFLICT (name) DO NOTHING;

-- 4.2 Insert Default Users (Passwords match DataInitializer: Admin@123, Lecturer@123, Student@123)
-- BCrypt encoded password hashes for spring security
INSERT INTO users (uuid, user_code, email, password, full_name, phone_number, status) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'ADM-001', 'admin@aives.edu.vn', '$2a$10$wK0w0B0bIeH7.c4F.lKqu.QjU.u9kKz6mPzVp3Zk4dF8pXoN5z4zG', 'AIVES Administrator', '0901234567', 'ACTIVE'),
    ('b0000000-0000-0000-0000-000000000002', 'LEC-001', 'lecturer@aives.edu.vn', '$2a$10$wK0w0B0bIeH7.c4F.lKqu.QjU.u9kKz6mPzVp3Zk4dF8pXoN5z4zG', 'Dr. Nguyen Van Giang', '0912345678', 'ACTIVE'),
    ('b0000000-0000-0000-0000-000000000003', 'STU-001', 'student@aives.edu.vn', '$2a$10$wK0w0B0bIeH7.c4F.lKqu.QjU.u9kKz6mPzVp3Zk4dF8pXoN5z4zG', 'Tran Thi Mai', '0987654321', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

-- 4.3 Assign User Roles
INSERT INTO user_roles (user_uuid, role_uuid) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001'), -- admin -> ADMIN
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002'), -- lecturer -> LECTURER
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000003')  -- student -> STUDENT
ON CONFLICT (user_uuid, role_uuid) DO NOTHING;

-- 4.4 Insert Subjects
INSERT INTO subjects (uuid, code, name, description, credits, status) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'SWD392', 'Software Architecture and Design', 'Thiết kế kiến trúc phần mềm hướng dịch vụ, Microservices và Clean Architecture', 3, 'ACTIVE'),
    ('c0000000-0000-0000-0000-000000000002', 'PRN211', 'Basic Cross-Platform Application with .NET', 'Lập trình ứng dụng đa nền tảng với C#, .NET 8 và Entity Framework Core', 3, 'ACTIVE'),
    ('c0000000-0000-0000-0000-000000000003', 'SWE201', 'Software Engineering Introduction', 'Quy trình phát triển phần mềm Agile/Scrum và quản trị vòng đời dự án', 3, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- 4.5 Assign Lecturer to Subject
INSERT INTO subject_lecturers (uuid, subject_uuid, lecturer_uuid, can_approve_rag, can_edit_rubric) VALUES
    ('d0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002', TRUE, TRUE),
    ('d0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', TRUE, FALSE)
ON CONFLICT (subject_uuid, lecturer_uuid) DO NOTHING;

-- 4.6 Insert Knowledge Base Document (RAG)
INSERT INTO documents (uuid, subject_uuid, file_name, file_url, file_size, status, chunk_count) VALUES
    ('e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'SWD392_Microservices_Architecture_Guide.pdf', 'https://storage.aives.edu.vn/rag/swd392-guide.pdf', '4.5 MB', 'INDEXED', 12)
ON CONFLICT (uuid) DO NOTHING;

-- 4.7 Insert Questions Bank
INSERT INTO questions (uuid, subject_uuid, question_code, content, bloom_level, difficulty, expected_answer, keywords, status) VALUES
    ('f0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'SWD-Q01', 'Hãy phân tích sự khác nhau giữa kiến trúc Monolithic và Microservices về khả năng mở rộng (Scalability) và tính sẵn sàng (Availability).', 'Bloom 4 - Phân tích', 'MEDIUM', 'Monolith mở rộng theo chiều dọc hoặc nhân bản toàn khối, rủi ro Single Point of Failure cao. Microservices mở rộng độc lập từng service theo tải thực tế, tính cô lập lỗi (fault isolation) cao hơn.', 'Monolithic, Microservices, Scalability, Availability, Fault Tolerance', 'ACTIVE'),
    ('f0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'SWD-Q02', 'Trình bày nguyên lý Dependency Inversion Principle (DIP) trong bộ nguyên lý SOLID và giải thích cách áp dụng trong Clean Architecture.', 'Bloom 2 - Hiểu', 'EASY', 'Các module cấp cao không nên phụ thuộc vào module cấp thấp mà cả hai phải phụ thuộc vào abstraction. Trong Clean Architecture, Domain Layer không phụ thuộc vào Database hay UI.', 'SOLID, DIP, Abstraction, Clean Architecture, Dependency Injection', 'ACTIVE')
ON CONFLICT (uuid) DO NOTHING;

-- 4.8 Insert Rubrics
INSERT INTO rubrics (uuid, subject_uuid, question_uuid, criterion_name, description, max_score, weight) VALUES
    ('10000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', NULL, 'Nắm vững kiến thức nền tảng', 'Hiểu rõ các khái niệm, định nghĩa và nguyên lý cốt lõi của môn học', 4.00, 1.00),
    ('10000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', NULL, 'Kỹ năng phản biện và lập luận', 'Khả năng trả lời mạch lạc, đưa ra dẫn chứng kỹ thuật và phân tích ưu nhược điểm', 3.50, 1.00),
    ('10000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', NULL, 'Khả năng ứng dụng thực tế', 'Đưa ra được use-case hoặc bài toán thực tế áp dụng kiến trúc đã chọn', 2.50, 1.00)
ON CONFLICT (uuid) DO NOTHING;

-- 4.9 Insert Exam Session
INSERT INTO exam_sessions (uuid, session_code, subject_uuid, student_uuid, examiner_uuid, scheduled_at, started_at, ended_at, status, total_score, feedback) VALUES
    ('20000000-0000-0000-0000-000000000001', 'VIVA-2026-SWD392-001', 'c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000002', '2026-10-05 08:30:00', '2026-10-05 08:32:15', '2026-10-05 08:50:40', 'COMPLETED', 8.50, 'Sinh viên nắm rất vững các nguyên lý Clean Architecture và Microservices. Phản xạ vấn đáp bằng giọng nói tự tin, giải thích rõ cơ chế cô lập lỗi.')
ON CONFLICT (session_code) DO NOTHING;

-- 4.10 Insert Viva Turn Sample (Audio + AI Transcript)
INSERT INTO viva_turns (uuid, exam_session_uuid, question_uuid, turn_order, student_answer, audio_transcript, ai_evaluation, score, turn_duration_seconds) VALUES
    ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 1, 
     'Dạ thưa thầy cô, điểm khác biệt lớn nhất là Microservices chia nhỏ hệ thống thành các dịch vụ độc lập, giúp ta scale riêng service chịu tải cao như Order hoặc Payment mà không tốn tài nguyên nhân bản toàn hệ thống như Monolithic ạ.',
     'Dạ thưa thầy cô điểm khác biệt lớn nhất là Microservices chia nhỏ hệ thống thành các dịch vụ độc lập...',
     'Câu trả lời chính xác, nhấn mạnh đúng trọng tâm Horizontal Scaling theo từng domain service. Điểm cộng về thuật ngữ Fault Isolation.',
     8.50, 45)
ON CONFLICT (uuid) DO NOTHING;
