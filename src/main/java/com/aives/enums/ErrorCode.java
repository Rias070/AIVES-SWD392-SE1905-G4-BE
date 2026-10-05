package com.aives.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Tập trung quản lý tất cả mã lỗi của hệ thống AIVES.
 */
@Getter
public enum ErrorCode {
    // === Lỗi hệ thống ===
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized system error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Invalid request key", HttpStatus.BAD_REQUEST),

    // === Lỗi User / Auth ===
    USER_EXISTED(1002, "User already exists", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003, "Username must be at least 3 characters", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005, "User does not exist", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "Email or password is invalid. Please try again!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    INVALID_TOKEN(1008, "Token is invalid", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1009, "Token has expired", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(1010, "Account has been locked!", HttpStatus.FORBIDDEN),
    ACCOUNT_INACTIVE(1035, "Account is inactive!", HttpStatus.FORBIDDEN),
    ROLE_NOT_FOUND(1040, "Role not found", HttpStatus.NOT_FOUND),

    // === Lỗi Validation Input ===
    INVALID_EMAIL(1011, "Invalid email address", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Password must be at least 8 characters", HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH(1014, "Password and Confirm Password do not match", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED(1032, "Email is required", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(1033, "Password is required", HttpStatus.BAD_REQUEST),
    FULLNAME_REQUIRED(1024, "Full name is required", HttpStatus.BAD_REQUEST),

    // === Lỗi Subject & Question (RAG) ===
    SUBJECT_NOT_FOUND(2001, "Subject not found", HttpStatus.NOT_FOUND),
    SUBJECT_ALREADY_EXISTS(2002, "Subject code already exists", HttpStatus.BAD_REQUEST),
    QUESTION_NOT_FOUND(2101, "Question not found", HttpStatus.NOT_FOUND),
    RUBRIC_NOT_FOUND(2201, "Rubric criteria not found", HttpStatus.NOT_FOUND),

    // === Lỗi Exam Session & Viva ===
    EXAM_SESSION_NOT_FOUND(3001, "Exam session not found", HttpStatus.NOT_FOUND),
    EXAM_SESSION_ALREADY_STARTED(3002, "Exam session has already started", HttpStatus.BAD_REQUEST),
    EXAM_SESSION_FINISHED(3003, "Exam session has already finished", HttpStatus.BAD_REQUEST),
    VIVA_TURN_NOT_FOUND(3101, "Viva turn not found", HttpStatus.NOT_FOUND),

    // === Lỗi Document (RAG) ===
    DOCUMENT_NOT_FOUND(2301, "Document not found", HttpStatus.NOT_FOUND),
    DOCUMENT_UPLOAD_FAILED(2302, "Document upload failed", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatus statusCode;
}
