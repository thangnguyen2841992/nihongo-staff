package com.nihongo.staff.service.imports;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Suggestions never modify a draft or approve content. */
@Service
public class BookImportQuestionAssistant {
    private final BookImportService service;
    private final BookImportGemini gemini;
    public BookImportQuestionAssistant(BookImportService service,BookImportGemini gemini) {this.service=service;this.gemini=gemini;}
    public record Request(Long version,BookImportContent.Question question,int sourcePage,Integer answerPage) {}
    public record Suggestion(String groupName,String contentNihongo,String answerA,String answerB,String answerC,String answerD,String correctAnswer,String basis,String explanation,int sourcePage,String evidence,List<String> warnings) {}
    public Suggestion suggest(String id,Request request) {
        var d=service.get(id);
        if(d.summary().bookId()!=null || Set.of("QUEUED","PROCESSING","ANALYZING").contains(d.summary().state())) throw BookImportService.conflict("Hãy chờ xử lý xong trước khi nhờ AI hỗ trợ câu hỏi.");
        if(!Objects.equals(request.version(),d.summary().version())) throw BookImportService.conflict("Bản nháp đã thay đổi. Hãy mở lại trước khi nhờ AI hỗ trợ.");
        var q=request.question();
        if(q==null || BookImportService.blank(q.contentNihongo())) throw BookImportService.invalid("Cần nội dung câu hỏi để AI đối chiếu.");
        var fields=Arrays.asList(q.groupName(),q.contentNihongo(),q.answerA(),q.answerB(),q.answerC(),q.answerD());
        if(fields.stream().anyMatch(Objects::isNull) || fields.stream().mapToInt(String::length).sum()>16000) throw BookImportService.invalid("Nội dung câu hỏi không hợp lệ hoặc quá dài.");
        int page=request.sourcePage(),answer=request.answerPage()==null?0:request.answerPage();
        if(page<1 || page>d.summary().pageCount() || answer<0 || answer>d.summary().pageCount()) throw BookImportService.invalid("Hãy chọn trang PDF hợp lệ.");
        var sources=new LinkedHashSet<Integer>();sources.add(page);
        if(page<d.summary().pageCount()) sources.add(page+1);
        if(answer>0) sources.add(answer);
        try {
            var result=gemini.assistQuestion(id,q,new ArrayList<>(sources),d.pages());
            if(result==null || result.warnings()==null || result.basis()==null || BookImportService.blank(result.explanation()) || !Set.of("SOURCE","REASONING","UNRESOLVED").contains(result.basis())) throw new BookImportGemini.Failure("AI chưa trả đủ giải thích. Hãy thử lại.");
            if(Arrays.asList(result.groupName(),result.contentNihongo(),result.answerA(),result.answerB(),result.answerC(),result.answerD(),result.correctAnswer(),result.evidence()).stream().anyMatch(Objects::isNull)) throw new BookImportGemini.Failure("Đề xuất AI thiếu nội dung.");
            if(result.groupName().length()>180 || Arrays.asList(result.answerA(),result.answerB(),result.answerC(),result.answerD()).stream().anyMatch(v->v.length()>255) || result.contentNihongo().length()>16000) throw new BookImportGemini.Failure("Đề xuất AI quá dài để áp dụng.");
            if(!result.correctAnswer().matches("[ABCD]?")) throw new BookImportGemini.Failure("Đáp án AI chưa hợp lệ.");
            if(result.basis().equals("SOURCE") && (!sources.contains(result.sourcePage()) || BookImportService.blank(result.evidence()))) throw new BookImportGemini.Failure("AI chưa chỉ ra trang và bằng chứng đáp án trong sách.");
            if(result.basis().equals("UNRESOLVED") && !result.correctAnswer().isEmpty()) throw new BookImportGemini.Failure("Câu chưa đủ dữ liệu phải giữ đáp án trống.");
            if(!result.correctAnswer().isEmpty()) {
                var choices=List.of(result.answerA(),result.answerB(),result.answerC(),result.answerD());
                if(choices.stream().filter(v->!v.isBlank()).count()<2 || choices.get("ABCD".indexOf(result.correctAnswer())).isBlank()) throw new BookImportGemini.Failure("Đáp án AI chưa khớp với các lựa chọn.");
            }
            return result;
        } catch(BookImportGemini.Failure e) {throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,e.getMessage());}
    }
}
