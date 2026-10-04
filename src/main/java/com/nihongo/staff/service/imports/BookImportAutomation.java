package com.nihongo.staff.service.imports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.repository.BookImportSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class BookImportAutomation {
    private final BookImportSessionRepository sessions;
    private final BookImportService service;
    private final BookImportGemini gemini;
    private final TryN3BookData knownBook;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    public record AudioHint(String lessonName,String questionText,String track) {}
    public BookImportAutomation(BookImportSessionRepository sessions,BookImportService service,BookImportGemini gemini,TryN3BookData knownBook,ObjectMapper mapper,PlatformTransactionManager manager) {
        this.sessions=sessions;this.service=service;this.gemini=gemini;this.knownBook=knownBook;this.mapper=mapper;tx=new TransactionTemplate(manager);
    }
    public boolean configured() { return gemini.configured(); }
    public void run(String id) {
        try {
            if(preparedBook(id)) return;
            if(!gemini.configured()) throw new BookImportGemini.Failure("Chưa có key Gemini cho nhập sách. Cấu hình key đang dùng trong project rồi bấm Tiếp tục tự động.");
            state(id,"ANALYZING","Gemini đang tách bài, ngữ pháp, dịch ví dụ và đọc bài tập.",false);
            while(true) {
                var d=service.get(id);var s=service.find(id);int first=s.getAiProcessedPages()+1;
                if(first>s.getPageCount()) break;
                int last=Math.min(first+5,s.getPageCount());
                var batch=gemini.extract(id,first,last,d.content(),d.pages());
                checkpoint(id,first,last,batch);
                if(Thread.currentThread().isInterrupted()) return;
            }
            if(service.get(id).content().lessons().isEmpty()) throw new BookImportGemini.Failure("Đã đọc hết trang nhưng chưa tạo được bài học nào. Hãy tạo lại bản nháp tự động hoặc chuyển sang chỉnh thủ công; PDF và file nghe vẫn được giữ.");
            state(id,"REVIEW","Đã tạo bản nháp tự động. Kiểm tra các mục cần bổ sung và đối chiếu đáp án trước khi nhập sách.",true);
        } catch(BookImportGemini.Failure e) {state(id,"AI_FAILED",e.getMessage(),false);}
        catch(Exception e) {state(id,"AI_FAILED","Bản nháp AI chưa hợp lệ. Các phần đã lưu được giữ; hãy thử tiếp tục hoặc biên tập thủ công.",false);}
    }
    private void state(String id,String state,String message,boolean complete) {
        tx.executeWithoutResult(t->{var s=sessions.lock(id).orElseThrow();s.setState(state);s.setMessage(message);s.setAiCompleted(complete);sessions.save(s);});
    }
    /** A verified source fingerprint can reuse previously edited data without another paid AI call. */
    private boolean preparedBook(String id) throws Exception {
        var s=service.find(id);
        if(!Objects.equals(s.getSourceHash(),knownBook.book().sourceSha256()) || s.getPageCount()!=knownBook.book().pageCount() || !service.content(s).lessons().isEmpty()
            || knownBook.book().chapters().stream().anyMatch(c->!c.reviewed())) return false;
        var parts=new ArrayList<BookImportContent.Lesson>();var hints=new ArrayList<AudioHint>();
        for(var chapter:knownBook.book().chapters()) for(var l:knownBook.reviewedChapter(chapter.number()).lessons()) {
            var grammar=l.grammars().stream().map(g->new BookImportContent.Grammar(g.title(),g.description(),g.examples().stream().map(e->new BookImportContent.Example(e.nihongo(),e.vietnamese())).toList())).toList();
            var questions=l.exercises().stream().map(q->{var track=Pattern.compile("(?i)CD\\s*(\\d{1,3})").matcher(q.contentNihongo());if(track.find()) hints.add(new AudioHint(l.name(),q.contentNihongo(),track.group(1)));
                return new BookImportContent.Question(q.exerciseTypeName(),q.contentNihongo(),q.answerA(),q.answerB(),q.answerC(),q.answerD(),q.correctAnswer(),"");}).toList();
            hints.add(new AudioHint(l.name(),"",l.audioTrack()));
            int first=Math.max(1,Collections.min(l.sourcePrintedPages())-1),last=Math.min(s.getPageCount(),Collections.max(l.sourcePrintedPages())-1);
            String description=l.description()==null?"":l.description();
            parts.add(new BookImportContent.Lesson(l.name(),description.substring(0,Math.min(255,description.length())),first,last,l.reading(),"",grammar,questions));
        }
        tx.executeWithoutResult(t->{var current=sessions.lock(id).orElseThrow();var c=service.content(current);
            var content=link(new BookImportContent(c.bookName(),c.levelId(),c.typeId(),c.description(),c.audio(),parts),hints);
            service.validate(content,current.getPageCount(),false);current.setPayload(json(content));current.setAudioHints(json(hints));
            current.setAutoWarnings(json(List.of("Dùng nội dung TRY! N3 đã biên tập theo đúng mã PDF. Các bài Check/やってみよう trong từng mục chưa được chuyển thành câu hỏi tương tác; xem trang nguồn.")));
            current.setAiProcessedPages(current.getPageCount());current.setAiCompleted(true);current.setState("REVIEW");current.setMessage("Đã tự điền nội dung TRY! N3 đã chuẩn bị. Kiểm tra bản nháp và ghép audio trước khi nhập.");sessions.save(current);
        });return true;
    }
    public void checkpoint(String id,int first,int last,BookImportGemini.Batch batch) {
        tx.executeWithoutResult(t->{
            var s=sessions.lock(id).orElseThrow();
            if(s.getAiProcessedPages()>=last) return;
            if(s.getAiProcessedPages()+1!=first || last>s.getPageCount()) throw BookImportService.conflict("Tiến độ xử lý đã thay đổi.");
            var c=service.content(s);var parts=new ArrayList<>(c.lessons());var hints=new ArrayList<>(hints(s.getAudioHints()));
            var warnings=new ArrayList<>(warnings(s.getAutoWarnings()));
            for(var input:batch.lessons()) {
                if(input==null || input.firstPage()<first || input.lastPage()>last || input.lastPage()<input.firstPage() || BookImportService.blank(input.name()) || input.description()==null || input.reading()==null || input.grammars()==null || input.exercises()==null) throw new IllegalArgumentException("Invalid source range");
                String name=input.name().trim();int index=-1;for(int n=0;n<parts.size();n++) if(parts.get(n).name().trim().equals(name)) {index=n;break;}
                var previous=index<0?new BookImportContent.Lesson(name,"",input.firstPage(),input.lastPage(),"","",List.of(),List.of()):parts.get(index);
                var grammar=new ArrayList<>(previous.grammars());
                for(var g:input.grammars()) {
                    if(g==null || g.title()==null || g.description()==null || g.examples()==null || g.examples().stream().anyMatch(e->e==null || e.nihongo()==null || e.vietnamese()==null)) throw new IllegalArgumentException("Incomplete grammar");
                    int found=-1;for(int n=0;n<grammar.size();n++) if(Objects.equals(grammar.get(n).title(),g.title())) {found=n;break;}
                    if(found<0) grammar.add(g);else {
                        var old=grammar.get(found);var examples=new ArrayList<>(old.examples());
                        for(var example:g.examples()) if(examples.stream().noneMatch(e->Objects.equals(e.nihongo(),example.nihongo()))) examples.add(example);
                        grammar.set(found,new BookImportContent.Grammar(old.title(),join(old.description(),g.description()),examples));
                    }
                }
                var questions=new ArrayList<>(previous.exercises());
                for(var q:input.exercises()) {
                    if(q==null || q.groupName()==null || q.contentNihongo()==null || q.answerA()==null || q.answerB()==null || q.answerC()==null || q.answerD()==null) throw new IllegalArgumentException("Incomplete question");
                    boolean sourced=validKey(q.correctAnswer()) && q.answerPage()>=first && q.answerPage()<=last && !BookImportService.blank(q.answerEvidence());
                    var question=new BookImportContent.Question(q.groupName(),q.contentNihongo(),q.answerA(),q.answerB(),q.answerC(),q.answerD(),sourced?q.correctAnswer():"","");
                    if(questions.stream().noneMatch(e->sameQuestion(e,question))) questions.add(question);
                    if(track(q.audioTrack())!=null) hints.add(new AudioHint(name,q.contentNihongo(),q.audioTrack()));
                }
                if(track(input.audioTrack())!=null) hints.add(new AudioHint(name,"",input.audioTrack()));
                var part=new BookImportContent.Lesson(name,BookImportService.blank(previous.description())?input.description():previous.description(),Math.min(previous.firstPage(),input.firstPage()),Math.max(previous.lastPage(),input.lastPage()),join(previous.reading(),input.reading()),previous.audioId(),grammar,questions);
                if(index<0) parts.add(part);else parts.set(index,part);
            }
            for(var answer:batch.answers()) {
                var m=Pattern.compile("L(\\d+)Q(\\d+)").matcher(answer.questionId()==null?"":answer.questionId());
                if(!m.matches() || !validKey(answer.correctAnswer()) || answer.sourcePage()<first || answer.sourcePage()>last || BookImportService.blank(answer.evidence())) continue;
                int l=Integer.parseInt(m.group(1)),q=Integer.parseInt(m.group(2));if(l>=c.lessons().size() || q>=c.lessons().get(l).exercises().size()) continue;
                var part=parts.get(l);var questions=new ArrayList<>(part.exercises());var old=questions.get(q);
                if(validKey(old.correctAnswer()) && !old.correctAnswer().equals(answer.correctAnswer())) {warnings.add(part.name()+": đáp án phụ lục khác bản nháp; cần đối chiếu.");continue;}
                questions.set(q,new BookImportContent.Question(old.groupName(),old.contentNihongo(),old.answerA(),old.answerB(),old.answerC(),old.answerD(),answer.correctAnswer(),old.audioId()));
                parts.set(l,new BookImportContent.Lesson(part.name(),part.description(),part.firstPage(),part.lastPage(),part.reading(),part.audioId(),part.grammars(),questions));
            }
            warnings.addAll(batch.warnings());var updated=link(new BookImportContent(c.bookName(),c.levelId(),c.typeId(),c.description(),c.audio(),parts),hints);
            service.validate(updated,s.getPageCount(),false);String payload=json(updated);if(payload.length()>4_000_000) throw new IllegalArgumentException("Draft too large");
            s.setPayload(payload);s.setAudioHints(json(hints.stream().distinct().toList()));s.setAutoWarnings(json(warnings.stream().filter(Objects::nonNull).map(w->w.substring(0,Math.min(500,w.length()))).distinct().limit(100).toList()));
            s.setAiProcessedPages(last);s.setMessage("Đã tạo bản nháp từ "+last+"/"+s.getPageCount()+" trang. Các phần đã xong được lưu tự động.");sessions.save(s);
        });
    }
    private boolean sameQuestion(BookImportContent.Question a,BookImportContent.Question b) {return Objects.equals(a.groupName(),b.groupName()) && Objects.equals(a.contentNihongo(),b.contentNihongo()) && Objects.equals(a.answerA(),b.answerA()) && Objects.equals(a.answerB(),b.answerB()) && Objects.equals(a.answerC(),b.answerC()) && Objects.equals(a.answerD(),b.answerD());}
    private static boolean validKey(String key) {return key!=null && key.matches("[ABCD]");}
    private static String join(String a,String b) {if(BookImportService.blank(a))return b;if(BookImportService.blank(b)||a.contains(b))return a;return a+"\n\n"+b;}
    private String json(Object value) {try {return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Draft serialization failed");}}
    public List<AudioHint> hints(String json) {try{return json==null?List.of():mapper.readValue(json,new TypeReference<List<AudioHint>>(){});}catch(Exception e){throw new IllegalStateException("Invalid stored audio hints");}}
    public List<String> warnings(String json) {try{return json==null?List.of():mapper.readValue(json,new TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException("Invalid stored import warnings");}}
    private static Integer track(String value) {try {return value!=null && value.matches("\\d{1,3}")?Integer.parseInt(value):null;}catch(Exception e){return null;}}
    public static BookImportContent link(BookImportContent c,List<AudioHint> hints) {
        var assets=new HashMap<Integer,List<String>>();var pattern=Pattern.compile("(?i)^(?:cd[ _-]*|track[ _-]*)?0*(\\d{1,3})(?=\\D|$)");
        for(var a:c.audio()) {var m=pattern.matcher(a.name());if(m.find()) assets.computeIfAbsent(Integer.parseInt(m.group(1)),k->new ArrayList<>()).add(a.id());}
        var parts=new ArrayList<BookImportContent.Lesson>();
        for(var l:c.lessons()) {
            String reading=l.audioId();if(BookImportService.blank(reading)) reading=match(hints,assets,l.name(),"");
            var questions=new ArrayList<BookImportContent.Question>();for(var q:l.exercises()) {String audio=q.audioId();if(BookImportService.blank(audio))audio=match(hints,assets,l.name(),q.contentNihongo());questions.add(new BookImportContent.Question(q.groupName(),q.contentNihongo(),q.answerA(),q.answerB(),q.answerC(),q.answerD(),q.correctAnswer(),audio));}
            parts.add(new BookImportContent.Lesson(l.name(),l.description(),l.firstPage(),l.lastPage(),l.reading(),reading,l.grammars(),questions));
        }
        return new BookImportContent(c.bookName(),c.levelId(),c.typeId(),c.description(),c.audio(),parts);
    }
    private static String match(List<AudioHint> hints,Map<Integer,List<String>> assets,String name,String text) {
        var tracks=hints.stream().filter(h->Objects.equals(h.lessonName(),name)&&Objects.equals(h.questionText(),text)).map(h->track(h.track())).filter(Objects::nonNull).distinct().toList();
        if(tracks.size()!=1)return "";var files=assets.getOrDefault(tracks.get(0),List.of());return files.size()==1?files.get(0):"";
    }
}
