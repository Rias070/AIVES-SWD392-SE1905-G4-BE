package com.aives.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectLanguageConfigDto {

    @Pattern(regexp = "^(vi-VN|en-US|vi|en)$", message = "Ngôn ngữ khảo thí chỉ hỗ trợ 'vi-VN' hoặc 'en-US'")
    private String language;

    private String technicalTerms;
}
