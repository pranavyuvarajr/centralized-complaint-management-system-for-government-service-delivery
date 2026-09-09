package com.project.complaint.controller;

import com.project.complaint.dto.AttachmentDto;
import com.project.complaint.entity.ComplaintAttachment;
import com.project.complaint.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    private String roleOf(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    }

    @PostMapping("/api/complaints/{complaintId}/attachments")
    public ResponseEntity<AttachmentDto.Response> upload(@PathVariable Long complaintId,
                                                           @RequestParam("file") MultipartFile file,
                                                           Authentication auth) {
        return ResponseEntity.ok(attachmentService.upload(complaintId, auth.getName(), roleOf(auth), file));
    }

    @GetMapping("/api/complaints/{complaintId}/attachments")
    public ResponseEntity<List<AttachmentDto.Response>> list(@PathVariable Long complaintId, Authentication auth) {
        return ResponseEntity.ok(attachmentService.list(complaintId, auth.getName(), roleOf(auth)));
    }

    @GetMapping("/api/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId, Authentication auth) {
        ComplaintAttachment attachment = attachmentService.loadForDownload(attachmentId, auth.getName(), roleOf(auth));
        Resource resource = attachmentService.loadAsResource(attachment);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getOriginalFileName() + "\"")
                .body(resource);
    }
}
