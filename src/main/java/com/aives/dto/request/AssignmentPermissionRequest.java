package com.aives.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentPermissionRequest {
    private Boolean canApproveRag;
    private Boolean canEditRubric;
}
