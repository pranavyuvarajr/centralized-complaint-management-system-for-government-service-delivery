package com.project.complaint.controller;

import com.project.complaint.dto.CommentDto;
import com.project.complaint.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/complaints/{complaintId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    private String roleOf(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    }

    @GetMapping
    public ResponseEntity<List<CommentDto.Response>> getComments(@PathVariable Long complaintId, Authentication auth) {
        return ResponseEntity.ok(commentService.getComments(complaintId, auth.getName(), roleOf(auth)));
    }

    @PostMapping
    public ResponseEntity<CommentDto.Response> addComment(@PathVariable Long complaintId, Authentication auth,
                                                            @Valid @RequestBody CommentDto.CreateRequest request) {
        return ResponseEntity.ok(commentService.addComment(complaintId, auth.getName(), roleOf(auth), request));
    }
}
