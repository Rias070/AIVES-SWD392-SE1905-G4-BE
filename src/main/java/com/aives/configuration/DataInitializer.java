package com.aives.configuration;

import com.aives.entity.Role;
import com.aives.entity.User;
import com.aives.entity.UserRole;
import com.aives.enums.RoleEnum;
import com.aives.enums.UserStatus;
import com.aives.repository.RoleRepository;
import com.aives.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking and initializing AIVES default roles and accounts...");

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
        if (!userRepository.existsByEmail("lecturer@aives.edu.vn")) {
            User lecturer = User.builder()
                    .email("lecturer@aives.edu.vn")
                    .password(passwordEncoder.encode("Lecturer@123"))
                    .fullName("Dr. Nguyen Van Giang")
                    .userCode("LEC-001")
                    .status(UserStatus.ACTIVE)
                    .build();

            UserRole userRole = UserRole.builder()
                    .user(lecturer)
                    .role(lecturerRole)
                    .build();
            lecturer.getUserRoles().add(userRole);

            userRepository.save(lecturer);
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

        log.info("AIVES data initialization completed.");
    }
}
