package com.nihongo.staff.controller;
import com.nihongo.staff.service.*;
import com.nihongo.staff.model.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.Page;
import java.util.List;
@RestController @RequestMapping("/api/staff/content") @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class ContentOperationsController {
 private final ContentOperationsService operations;private final ContentRevisionService revisions;
 public record PageView<T>(List<T> content,long totalElements,int number,int totalPages){static <T> PageView<T> of(Page<T> p){return new PageView<>(p.getContent(),p.getTotalElements(),p.getNumber(),p.getTotalPages());}}
 @PostMapping("/reports") @PreAuthorize("hasAnyRole('ADMIN','STAFF','USER')") public ContentIssue report(@AuthenticationPrincipal Jwt jwt,Authentication auth,@Valid @RequestBody ContentOperationsService.Report input){return operations.report(jwt.getSubject(),auth,input);}
 @GetMapping("/reports/mine") @PreAuthorize("hasAnyRole('ADMIN','STAFF','USER')") public PageView<ContentIssue> mine(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page){return PageView.of(operations.mine(jwt.getSubject(),page));}
 @GetMapping("/issues") public PageView<ContentIssue> issues(@RequestParam(required=false) String status,@RequestParam(required=false) String assignee,@RequestParam(defaultValue="0") int page){return PageView.of(operations.issues(status,assignee,page));}
 @PutMapping("/issues/{id}") public ContentIssue update(@PathVariable Long id,@Valid @RequestBody ContentOperationsService.IssueUpdate input){return operations.updateIssue(id,input);}
 @GetMapping("/reviews") public PageView<ContentOperationsService.ReviewBook> reviews(@RequestParam(defaultValue="IN_REVIEW") String status,@RequestParam(defaultValue="0") int page){return PageView.of(operations.reviewQueue(status,page));}
 @PostMapping("/reviews/{id}") @PreAuthorize("hasRole('ADMIN') or (hasRole('STAFF') and #input.action() == 'SUBMIT')") public BookPublicationService.Review review(@AuthenticationPrincipal Jwt jwt,Authentication auth,@PathVariable Long id,@Valid @RequestBody ContentOperationsService.ReviewAction input){return operations.review(jwt.getSubject(),auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")),id,input);}
 @GetMapping("/reviews/{id}/history") public PageView<BookReviewEntry> history(@PathVariable Long id,@RequestParam(defaultValue="0") int page){return PageView.of(operations.reviewHistory(id,page));}
 @GetMapping("/questions") public PageView<ContentOperationsService.Question> bank(@RequestParam(required=false) Long level,@RequestParam(required=false) Long type,@RequestParam(required=false) String difficulty,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return PageView.of(operations.bank(level,type,difficulty,search,page));}
 public record Difficulty(String difficulty) {}
 @PutMapping("/questions/{id}/difficulty") public void difficulty(@PathVariable Long id,@RequestBody Difficulty input){operations.difficulty(id,input.difficulty());}
 @PostMapping("/exams") public ContentOperationsService.Exam createExam(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ContentOperationsService.NewExam input){return operations.createExam(jwt.getSubject(),input);}
 public record ExamSummary(Long id,String title,String owner,java.time.LocalDateTime createdAt,int questions) {}
 @GetMapping("/exams") public PageView<ExamSummary> exams(@RequestParam(defaultValue="0") int page){return PageView.of(operations.exams(page).map(e->new ExamSummary(e.id(),e.title(),e.owner(),e.createdAt(),e.questions().size())));}
 @GetMapping("/exams/{id}") public ContentOperationsService.Exam exam(@PathVariable Long id){return operations.exam(id);}
 public record HistoryView(String current,String token,PageView<ContentRevision> revisions) {}
 @GetMapping("/revisions/{kind}/{id}") public HistoryView revisions(@PathVariable String kind,@PathVariable Long id,@RequestParam(defaultValue="0") int page){var h=revisions.history(kind,id,page);return new HistoryView(h.current(),h.token(),PageView.of(h.revisions()));}
 public record Restore(Long revisionId,String token) {}
 @PostMapping("/revisions/{kind}/{id}/restore") public void restore(@PathVariable String kind,@PathVariable Long id,@RequestBody Restore input){revisions.restore(kind,id,input.revisionId(),input.token());}
 @GetMapping("/audit/revisions") @PreAuthorize("hasRole('ADMIN')") public PageView<ContentRevision> revisionAudit(@RequestParam(defaultValue="0") int page){return PageView.of(revisions.audit(page));}
 @GetMapping("/audit/reviews") @PreAuthorize("hasRole('ADMIN')") public PageView<BookReviewEntry> reviewAudit(@RequestParam(defaultValue="0") int page){return PageView.of(operations.reviewAudit(page));}
 @GetMapping("/audit/operations") @PreAuthorize("hasRole('ADMIN')") public PageView<ContentOperationAudit> operationsAudit(@RequestParam(defaultValue="0") int page){return PageView.of(operations.audit(page));}
}
