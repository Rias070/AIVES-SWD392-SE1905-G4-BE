package com.aives.dto.momo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MomoQueryResponse {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private String extraData;
    private long amount;
    private long transId;
    private String payType;
    private int resultCode;
    private String message;
    private long responseTime;
}
