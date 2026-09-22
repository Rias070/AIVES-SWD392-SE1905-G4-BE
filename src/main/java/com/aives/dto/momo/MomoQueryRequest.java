package com.aives.dto.momo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MomoQueryRequest {
    private String partnerCode;
    private String requestId;
    private String orderId;
    private String lang;
    private String signature;
}
