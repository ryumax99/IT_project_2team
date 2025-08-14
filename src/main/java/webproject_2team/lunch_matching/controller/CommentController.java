package webproject_2team.lunch_matching.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import webproject_2team.lunch_matching.domain.Comment;
import webproject_2team.lunch_matching.security.dto.CustomUserDetails;
import webproject_2team.lunch_matching.service.CommentService;
import org.springframework.security.access.AccessDeniedException;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/board") // ✅ 기존 경로 유지 => 프론트 수정 불필요
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/comment")
    public ResponseEntity<Map<String, Object>> addCommentApi(@RequestParam("boardId") Long boardId,
                                                             @RequestParam("content") String content,
                                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        if (userDetails == null) {
            response.put("success", false);
            response.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(401).body(response);
        }
        try {
            Comment comment = commentService.saveComment(boardId, content, userDetails.getNickname(), userDetails.getEmail());

            Map<String, Object> commentData = new HashMap<>();
            commentData.put("id", comment.getId());
            commentData.put("content", comment.getContent());
            commentData.put("writer", comment.getWriter());
            commentData.put("writerEmail", comment.getWriterEmail());
            commentData.put("createdAt", comment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            response.put("success", true);
            response.put("comment", commentData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PutMapping("/comment/{id}")
    public ResponseEntity<Map<String, Object>> updateComment(@PathVariable("id") Long commentId,
                                                             @RequestBody Map<String, String> payload,
                                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        if (userDetails == null) {
            response.put("success", false);
            response.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(401).body(response);
        }
        try {
            String content = payload.get("content");
            Comment updatedComment = commentService.updateComment(commentId, content, userDetails.getEmail(), userDetails.isAdmin());

            Map<String, Object> commentData = new HashMap<>();
            commentData.put("id", updatedComment.getId());
            commentData.put("content", updatedComment.getContent());
            response.put("success", true);
            response.put("comment", commentData);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            response.put("success", false);
            response.put("message", "수정 권한이 없습니다.");
            return ResponseEntity.status(403).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @DeleteMapping("/comment/{id}")
    public ResponseEntity<Map<String, Object>> deleteCommentApi(@PathVariable("id") Long commentId,
                                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        if (userDetails == null) {
            response.put("success", false);
            response.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(401).body(response);
        }
        try {
            commentService.deleteComment(commentId, userDetails.getEmail(), userDetails.isAdmin());
            response.put("success", true);
            response.put("message", "댓글이 삭제되었습니다.");
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            response.put("success", false);
            response.put("message", "삭제 권한이 없습니다.");
            return ResponseEntity.status(403).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
