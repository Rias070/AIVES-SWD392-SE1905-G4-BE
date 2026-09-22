package com.aives.service;

import com.aives.config.VnPayConfig;
import com.aives.dto.response.InitiatePaymentResponse;
import com.aives.dto.response.PaymentResponse;
import com.aives.entity.ExamSession;
import com.aives.entity.Payment;
import com.aives.entity.User;
import com.aives.enums.ErrorCode;
import com.aives.enums.PaymentStatus;
import com.aives.exception.AppException;
import com.aives.repository.ExamSessionRepository;
import com.aives.repository.PaymentRepository;
import com.aives.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnPayPaymentService {

    private final VnPayConfig vnPayConfig;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ExamSessionRepository examSessionRepository;

    @Transactional
    public InitiatePaymentResponse initiatePayment(UUID userId, UUID examSessionId, BigDecimal amount, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ExamSession examSession = null;
        if (examSessionId != null) {
            examSession = examSessionRepository.findById(examSessionId)
                    .orElseThrow(() -> new AppException(ErrorCode.EXAM_SESSION_NOT_FOUND));
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }

        long amountVal = amount.longValue();
        String txnRef = "AIVES_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String orderInfo = "Exam fee payment for user: " + user.getEmail();

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        params.put("vnp_Amount", String.valueOf(amountVal * 100)); // VNPay amount * 100
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        params.put("vnp_IpAddr", ipAddress != null ? ipAddress : "127.0.0.1");

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+07:00"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        params.put("vnp_CreateDate", formatter.format(cal.getTime()));

        cal.add(Calendar.MINUTE, 15);
        params.put("vnp_ExpireDate", formatter.format(cal.getTime()));

        StringBuilder query = new StringBuilder();
        StringBuilder hashData = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (hashData.length() > 0) {
                hashData.append("&");
                query.append("&");
            }
            hashData.append(entry.getKey()).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
            query.append(entry.getKey()).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }

        String secureHash = hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        String paymentUrl = vnPayConfig.getPayUrl() + "?" + query.toString();

        Payment payment = Payment.builder()
                .user(user)
                .examSession(examSession)
                .paymentMethod("VNPAY")
                .amount(amount)
                .vnpTxnRef(txnRef)
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        return InitiatePaymentResponse.builder()
                .payUrl(paymentUrl)
                .paymentId(payment.getUuid())
                .examSessionId(examSessionId)
                .build();
    }

    @Transactional
    public void processPaymentResult(Map<String, String> params) {
        String vnpSecureHash = params.get("vnp_SecureHash");
        if (vnpSecureHash == null) {
            throw new AppException(ErrorCode.INVALID_KEY, "Missing vnp_SecureHash in return URL");
        }

        Map<String, String> cleanParams = new TreeMap<>(params);
        cleanParams.remove("vnp_SecureHash");
        cleanParams.remove("vnp_SecureHashType");

        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : cleanParams.entrySet()) {
            if (hashData.length() > 0) {
                hashData.append("&");
            }
            hashData.append(entry.getKey()).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }

        String calculatedHash = hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
        if (!calculatedHash.equalsIgnoreCase(vnpSecureHash)) {
            log.error("VNPay checksum verification failed");
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "VNPay checksum verification failed");
        }

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");

        Payment payment = paymentRepository.findByVnpTxnRef(txnRef)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Payment for VNPAY txnRef {} is already completed.", txnRef);
            return;
        }

        payment.setTransactionId(transactionNo);
        payment.setVnpBankCode(bankCode);

        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
            log.info("VNPay payment succeeded for txnRef: {}", txnRef);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("VNPay payment failed for txnRef: {}. Response code: {}", txnRef, responseCode);
        }
    }

    public List<PaymentResponse> getUserPayments(UUID userId) {
        return paymentRepository.findByUserUuidOrderByCreatedAtDesc(userId).stream()
                .filter(p -> "VNPAY".equals(p.getPaymentMethod()))
                .map(payment -> {
                    String subjectName = (payment.getExamSession() != null && payment.getExamSession().getSubject() != null)
                            ? payment.getExamSession().getSubject().getName()
                            : "Exam Registration";
                    return PaymentResponse.builder()
                            .uuid(payment.getUuid())
                            .userId(payment.getUser().getUuid())
                            .examSessionId(payment.getExamSession() != null ? payment.getExamSession().getUuid() : null)
                            .subjectName(subjectName)
                            .paymentMethod(payment.getPaymentMethod())
                            .amount(payment.getAmount())
                            .transactionId(payment.getTransactionId())
                            .status(payment.getStatus() != null ? payment.getStatus().name() : "PENDING")
                            .createdAt(payment.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
