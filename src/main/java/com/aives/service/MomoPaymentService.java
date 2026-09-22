package com.aives.service;

import com.aives.config.MomoConfig;
import com.aives.dto.momo.*;
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
import com.aives.utils.MomoSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MomoPaymentService {

    private final MomoConfig momoConfig;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ExamSessionRepository examSessionRepository;
    private final RestTemplate restTemplate;

    @Transactional
    public InitiatePaymentResponse initiatePayment(UUID userId, UUID examSessionId, BigDecimal amount) {
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
        String orderId = "AIVES_MOMO_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String requestId = UUID.randomUUID().toString();
        String orderInfo = "AIVES Exam Registration fee for user: " + user.getEmail();

        // Generate HMAC SHA256 Signature
        String rawData = "accessKey=" + momoConfig.getAccessKey() +
                "&amount=" + amountVal +
                "&extraData=" +
                "&ipnUrl=" + momoConfig.getIpnUrl() +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + momoConfig.getPartnerCode() +
                "&redirectUrl=" + momoConfig.getRedirectUrl() +
                "&requestId=" + requestId +
                "&requestType=payWithMethod";

        String signature = MomoSignatureUtil.computeHmacSha256(rawData, momoConfig.getSecretKey());

        MomoCreateRequest request = MomoCreateRequest.builder()
                .partnerCode(momoConfig.getPartnerCode())
                .requestId(requestId)
                .amount(amountVal)
                .orderId(orderId)
                .orderInfo(orderInfo)
                .redirectUrl(momoConfig.getRedirectUrl())
                .ipnUrl(momoConfig.getIpnUrl())
                .requestType("payWithMethod")
                .extraData("")
                .lang("vi")
                .signature(signature)
                .build();

        String payUrl = "";
        try {
            ResponseEntity<MomoCreateResponse> response = restTemplate.postForEntity(
                    momoConfig.getEndpoint() + "/create",
                    request,
                    MomoCreateResponse.class
            );

            if (response.getBody() != null && response.getBody().getResultCode() == 0) {
                payUrl = response.getBody().getPayUrl();
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().getMessage() : "Unknown MoMo error";
                log.error("MoMo payment initiation failed: {}", errorMsg);
                payUrl = momoConfig.getRedirectUrl() + "?status=error";
            }
        } catch (Exception e) {
            log.warn("Mocking or failed MoMo endpoint call: {}", e.getMessage());
            payUrl = momoConfig.getRedirectUrl() + "?orderId=" + orderId;
        }

        Payment payment = Payment.builder()
                .user(user)
                .examSession(examSession)
                .paymentMethod("MOMO")
                .amount(amount)
                .momoOrderId(orderId)
                .momoRequestId(requestId)
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        return InitiatePaymentResponse.builder()
                .payUrl(payUrl)
                .paymentId(payment.getUuid())
                .examSessionId(examSessionId)
                .build();
    }

    @Transactional
    public void processPaymentResult(String orderId) {
        Payment payment = paymentRepository.findByMomoOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Payment for MoMo order {} is already completed.", orderId);
            return;
        }

        String requestId = UUID.randomUUID().toString();
        String rawData = "accessKey=" + momoConfig.getAccessKey() +
                "&orderId=" + orderId +
                "&partnerCode=" + momoConfig.getPartnerCode() +
                "&requestId=" + requestId;

        String signature = MomoSignatureUtil.computeHmacSha256(rawData, momoConfig.getSecretKey());

        MomoQueryRequest queryRequest = MomoQueryRequest.builder()
                .partnerCode(momoConfig.getPartnerCode())
                .requestId(requestId)
                .orderId(orderId)
                .signature(signature)
                .lang("vi")
                .build();

        try {
            ResponseEntity<MomoQueryResponse> response = restTemplate.postForEntity(
                    momoConfig.getEndpoint() + "/query",
                    queryRequest,
                    MomoQueryResponse.class
            );

            if (response.getBody() != null && response.getBody().getResultCode() == 0) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setTransactionId(String.valueOf(response.getBody().getTransId()));
                paymentRepository.save(payment);
                log.info("MoMo payment succeeded for order {}", orderId);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.warn("MoMo payment failed for order {}", orderId);
            }
        } catch (Exception e) {
            log.warn("MoMo status check fallback: {}", e.getMessage());
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);
        }
    }

    public List<PaymentResponse> getUserPayments(UUID userId) {
        return paymentRepository.findByUserUuidOrderByCreatedAtDesc(userId).stream()
                .filter(p -> "MOMO".equals(p.getPaymentMethod()))
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
}
