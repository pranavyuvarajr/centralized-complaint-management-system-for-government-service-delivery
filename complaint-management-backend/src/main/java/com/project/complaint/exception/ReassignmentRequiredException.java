package com.project.complaint.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Thrown when deleting an entity (department, category, official) is blocked
 * because other records (complaints, users) still reference it. Carries a
 * list of valid reassignment targets so the client can prompt the admin to
 * pick one and retry the delete with a target id.
 */
@Getter
public class ReassignmentRequiredException extends RuntimeException {

    private final long count;
    private final List<Map<String, Object>> options;

    public ReassignmentRequiredException(String message, long count, List<Map<String, Object>> options) {
        super(message);
        this.count = count;
        this.options = options;
    }
}
