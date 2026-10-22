package com.project.complaint.dto;

import lombok.*;

public class UrgencyDto {

    /**
     * decision is CONFIRM_URGENT or MARK_NORMAL. For MARK_NORMAL the note is the required reason
     * (min 10 characters); for CONFIRM_URGENT it is optional. The admin override always needs a note.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DecisionRequest {
        private String decision;
        private String note;
    }
}
