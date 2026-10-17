package com.aives.configuration;

import com.aives.entity.*;
import com.aives.enums.RoleEnum;
import com.aives.enums.UserStatus;
import com.aives.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectLecturerRepository subjectLecturerRepository;
    private final QuestionRepository questionRepository;
    private final RubricRepository rubricRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           RoleRepository roleRepository,
                           SubjectRepository subjectRepository,
                           SubjectLecturerRepository subjectLecturerRepository,
                           QuestionRepository questionRepository,
                           RubricRepository rubricRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.subjectRepository = subjectRepository;
        this.subjectLecturerRepository = subjectLecturerRepository;
        this.questionRepository = questionRepository;
        this.rubricRepository = rubricRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking and initializing AIVES default roles, accounts, and academic data...");

        // 1. Initialize Roles
        Role adminRole = roleRepository.findByName(RoleEnum.ADMIN.name())
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .name(RoleEnum.ADMIN.name())
                                .description("System Administrator")
                                .build()));

        Role lecturerRole = roleRepository.findByName(RoleEnum.LECTURER.name())
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .name(RoleEnum.LECTURER.name())
                                .description("Lecturer / Examiner")
                                .build()));

        Role studentRole = roleRepository.findByName(RoleEnum.STUDENT.name())
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .name(RoleEnum.STUDENT.name())
                                .description("Student / Examinee")
                                .build()));

        // 2. Initialize Default Admin
        if (!userRepository.existsByEmail("admin@aives.edu.vn")) {
            User admin = User.builder()
                    .email("admin@aives.edu.vn")
                    .password(passwordEncoder.encode("Admin@123"))
                    .fullName("AIVES Administrator")
                    .userCode("ADM-001")
                    .status(UserStatus.ACTIVE)
                    .build();

            UserRole userRole = UserRole.builder()
                    .user(admin)
                    .role(adminRole)
                    .build();
            admin.getUserRoles().add(userRole);

            userRepository.save(admin);
            log.info("Default Admin created: admin@aives.edu.vn / Admin@123");
        }

        // 3. Initialize Default Lecturer
        User defaultLecturer = userRepository.findByEmail("lecturer@aives.edu.vn").orElse(null);
        if (defaultLecturer == null) {
            defaultLecturer = User.builder()
                    .email("lecturer@aives.edu.vn")
                    .password(passwordEncoder.encode("Lecturer@123"))
                    .fullName("Dr. Nguyen Van Giang")
                    .userCode("LEC-001")
                    .status(UserStatus.ACTIVE)
                    .build();

            UserRole userRole = UserRole.builder()
                    .user(defaultLecturer)
                    .role(lecturerRole)
                    .build();
            defaultLecturer.getUserRoles().add(userRole);

            defaultLecturer = userRepository.save(defaultLecturer);
            log.info("Default Lecturer created: lecturer@aives.edu.vn / Lecturer@123");
        }

        // 4. Initialize Default Student
        if (!userRepository.existsByEmail("student@aives.edu.vn")) {
            User student = User.builder()
                    .email("student@aives.edu.vn")
                    .password(passwordEncoder.encode("Student@123"))
                    .fullName("Tran Thi Mai")
                    .userCode("STU-001")
                    .status(UserStatus.ACTIVE)
                    .build();

            UserRole userRole = UserRole.builder()
                    .user(student)
                    .role(studentRole)
                    .build();
            student.getUserRoles().add(userRole);

            userRepository.save(student);
            log.info("Default Student created: student@aives.edu.vn / Student@123");
        }

        // 5. Initialize Foundation Subjects
        Subject cs301 = subjectRepository.findByCode("CS301").orElseGet(() -> subjectRepository.save(
                Subject.builder()
                        .code("CS301")
                        .name("Cấu trúc Dữ liệu & Giải thuật nâng cao")
                        .description("Nghiên cứu cấu trúc cây nhị phân, giải thuật đồ thị, quy hoạch động và tối ưu hóa thời gian/không gian.")
                        .credits(3)
                        .language("vi-VN")
                        .technicalTerms("Binary Heap, Fibonacci Heap, Dijkstra, AVL Tree, Red-Black Tree, Dynamic Programming, Time Complexity")
                        .status("ACTIVE")
                        .build()
        ));

        Subject prj301 = subjectRepository.findByCode("PRJ301").orElseGet(() -> subjectRepository.save(
                Subject.builder()
                        .code("PRJ301")
                        .name("Lập trình ứng dụng Java Web")
                        .description("Phát triển ứng dụng Web chuẩn MVC với Java Servlet, JSP, JDBC, Session Management và Spring Framework.")
                        .credits(3)
                        .language("vi-VN")
                        .technicalTerms("Servlet, JSP, HttpSession, JDBC, Connection Pool, MVC Pattern, Filter, Cookies")
                        .status("ACTIVE")
                        .build()
        ));

        Subject swp391 = subjectRepository.findByCode("SWP391").orElseGet(() -> subjectRepository.save(
                Subject.builder()
                        .code("SWP391")
                        .name("Dự án phát triển phần mềm")
                        .description("Quy trình Scrum/Agile, thiết kế kiến trúc hệ thống phân tán, CI/CD và triển khai phần mềm.")
                        .credits(3)
                        .language("vi-VN")
                        .status("ACTIVE")
                        .build()
        ));

        Subject ai204 = subjectRepository.findByCode("AI204").orElseGet(() -> subjectRepository.save(
                Subject.builder()
                        .code("AI204")
                        .name("Học máy & Thị giác máy tính")
                        .description("Mô hình hồi quy, mạng nơ-ron tích chập (CNN), tối ưu hóa hàm mất mát và phân tích hình ảnh.")
                        .credits(3)
                        .language("vi-VN")
                        .status("ACTIVE")
                        .build()
        ));

        // 6. Assign Default Lecturer to Foundation Subjects
        if (defaultLecturer != null) {
            for (Subject subj : List.of(cs301, prj301, swp391, ai204)) {
                if (!subjectLecturerRepository.existsBySubjectUuidAndLecturerUuid(subj.getUuid(), defaultLecturer.getUuid())) {
                    subjectLecturerRepository.save(SubjectLecturer.builder()
                            .subject(subj)
                            .lecturer(defaultLecturer)
                            .canApproveRag(true)
                            .canEditRubric(true)
                            .build());
                }
            }
        }

        // 7. Initialize Foundation Questions with Real Rubrics & Bloom Taxonomy
        seedFoundationQuestion(
                cs301,
                "CS301-Q101",
                "Giải thích nguyên lý cân bằng của cây AVL và sự khác biệt giữa phép xoay đơn và xoay kép.",
                "MEDIUM",
                "Bloom 4 - Phân tích",
                "Cây AVL là BST tự cân bằng với điều kiện chênh lệch chiều cao hai cây con không vượt quá 1. Phép xoay đơn giải quyết lệch LL/RR, xoay kép giải quyết lệch LR/RL.",
                "AVL Tree, Balance Factor, Single Rotation, Double Rotation",
                List.of(
                        new RubricItem("Bản chất cân bằng AVL & Balance Factor", "Hiểu đúng định nghĩa hệ số cân bằng và điều kiện bất biến của cây AVL", new BigDecimal("3.00"), new BigDecimal("30.00")),
                        new RubricItem("Phân tích cơ chế xoay đơn và xoay kép", "Mô tả chính xác thuật toán xoay đơn (LL, RR) và xoay kép (LR, RL) khi mất cân bằng", new BigDecimal("4.00"), new BigDecimal("40.00")),
                        new RubricItem("Đánh giá chi phí và thực tiễn", "So sánh độ phức tạp O(log N) và so sánh ưu thế tra cứu với Red-Black Tree", new BigDecimal("3.00"), new BigDecimal("30.00"))
                )
        );

        seedFoundationQuestion(
                cs301,
                "CS301-Q102",
                "Phân tích độ phức tạp thời gian Binary Heap vs Fibonacci Heap trong thuật toán Dijkstra.",
                "HARD",
                "Bloom 5 - Đánh giá",
                "Dijkstra dùng Binary Heap đạt O((V + E) log V), trong khi Fibonacci Heap giảm thao tác Decrease-Key xuống O(1) amortized, tổng thời gian đạt O(E + V log V).",
                "Dijkstra, Binary Heap, Fibonacci Heap, Decrease-Key, Time Complexity",
                List.of(
                        new RubricItem("Hiểu cấu trúc Heap & Decrease-Key", "Phân tích thao tác Decrease-Key và vai trò trong duyệt đỉnh Dijkstra", new BigDecimal("4.00"), new BigDecimal("40.00")),
                        new RubricItem("So sánh đồ thị thưa vs đồ thị dày", "Chỉ rõ khi nào Fibonacci Heap vượt trội hơn Binary Heap về mặt lý thuyết", new BigDecimal("3.00"), new BigDecimal("30.00")),
                        new RubricItem("Đánh giá tính khả thi triển khai", "Nêu lý do thực tế ít cài Fibonacci Heap do hằng số ẩn lớn và overhead con trỏ", new BigDecimal("3.00"), new BigDecimal("30.00"))
                )
        );

        seedFoundationQuestion(
                prj301,
                "PRJ301-Q201",
                "Trình bày vòng đời của một HTTP Session trong Java Web và cơ chế Session Replication trong môi trường phân tán.",
                "MEDIUM",
                "Bloom 3 - Vận dụng",
                "HttpSession được tạo khi gọi request.getSession(true), gắn liền với JSESSIONID cookie. Trong cluster, session được replicate qua multicast hoặc lưu trữ tập trung tại Redis.",
                "HttpSession, JSESSIONID, Session Replication, Redis Cluster, Distributed Cache",
                List.of(
                        new RubricItem("Vòng đời Session & JSESSIONID", "Trình bày đúng lifecycle: tạo, lưu trữ context, timeout và invalidate", new BigDecimal("4.00"), new BigDecimal("40.00")),
                        new RubricItem("Cơ chế phân tán Session", "Nêu giải pháp Session Replication và Centralized Cache (Redis) trong distributed app", new BigDecimal("3.50"), new BigDecimal("35.00")),
                        new RubricItem("An toàn bảo mật Session", "Phân tích rủi ro Session Hijacking, Session Fixation và cấu hình HttpOnly/Secure", new BigDecimal("2.50"), new BigDecimal("25.00"))
                )
        );

        log.info("AIVES data initialization completed successfully.");
    }

    private void seedFoundationQuestion(
            Subject subject,
            String code,
            String content,
            String difficulty,
            String bloomLevel,
            String expectedAnswer,
            String keywords,
            List<RubricItem> rubrics
    ) {
        if (questionRepository.findBySubjectCode(subject.getCode()).stream().noneMatch(q -> code.equalsIgnoreCase(q.getQuestionCode()))) {
            Question q = questionRepository.save(Question.builder()
                    .subject(subject)
                    .questionCode(code)
                    .content(content)
                    .difficulty(difficulty)
                    .bloomLevel(bloomLevel)
                    .expectedAnswer(expectedAnswer)
                    .keywords(keywords)
                    .status("ACTIVE")
                    .build());

            for (RubricItem item : rubrics) {
                rubricRepository.save(Rubric.builder()
                        .subject(subject)
                        .question(q)
                        .criterionName(item.name)
                        .description(item.desc)
                        .maxScore(item.maxScore)
                        .weight(item.weight)
                        .build());
            }
            log.info("Seeded foundation question {} with {} rubrics for {}", code, rubrics.size(), subject.getCode());
        }
    }

    private record RubricItem(String name, String desc, BigDecimal maxScore, BigDecimal weight) {}
}
