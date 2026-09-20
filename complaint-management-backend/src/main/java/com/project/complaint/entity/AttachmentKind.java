package com.project.complaint.entity;

/**
 * What an attachment is for, so the complaint page can show "reported" vs
 * "completion" photos separately.
 * EVIDENCE   - the citizen's own photo/document (the only kind that existed before).
 * COMPLETION - the photo an official/admin uploads when marking a complaint resolved.
 * REOPEN     - an optional photo the citizen adds when reopening a resolved complaint.
 */
public enum AttachmentKind {
    EVIDENCE,
    COMPLETION,
    REOPEN
}
