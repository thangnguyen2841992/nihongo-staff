package com.nihongo.staff.service.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.security.ContentHtml;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class BookImportService {
    private final BookImportSessionRepository sessions;
    private final BookImportPageRepository pages;
    private final BookImportStorage storage;
    private final ObjectMapper mapper;
    private final IBookRepository books;
    private final ILessonsRepository lessons;
    private final IGrammarRepository grammars;
    private final IExampleRepository examples;
    private final IExerciseKeywordRepository exercises;
    private final IExerciseTypeRepository exerciseTypes;
    private final ILevelsRepository levels;
    private final ITypeRepository types;

    public record Summary(String id,String fileName,String bookName,String state,int pageCount,int processedPages,String message,Long bookId,Long version,String autoMode,int aiProcessedPages,boolean aiCompleted) {}
    public record Detail(Summary summary,BookImportContent content,List<Page> pages,List<String> warnings) {}
    public record Page(int number,String method,String text) {}
    public record Save(Long version,boolean reviewed,BookImportContent content) {}
    public record Publish(Long version) {}

    @Transactional(readOnly=true) public List<Summary> list() { return sessions.findTop50ByOrderByCreatedAtDesc().stream().map(this::summary).toList(); }
    @Transactional(readOnly=true) public Detail get(String id) {
        var s=find(id);
        return new Detail(summary(s),content(s),pages.findBySessionIdOrderByPageNumberAsc(id).stream().map(p->new Page(p.getPageNumber(),p.getMethod(),p.getText())).toList(),warnings(s));
    }
    public BookImportSession find(String id) { storage.directory(id); return sessions.findById(id).orElseThrow(()->missing("Không tìm thấy lần nhập sách.")); }
    public BookImportContent content(BookImportSession s) {
        try { return mapper.readValue(s.getPayload(),BookImportContent.class); }
        catch(Exception e) { throw new IllegalStateException("Stored import draft is invalid",e); }
    }
    private Summary summary(BookImportSession s) { return new Summary(s.getId(),s.getFileName(),content(s).bookName(),s.getState(),s.getPageCount(),s.getProcessedPages(),s.getMessage(),s.getImportedBookId(),s.getVersion(),s.getAutoMode(),s.getAiProcessedPages(),s.isAiCompleted()); }
    private List<String> warnings(BookImportSession s) {try{return s.getAutoWarnings()==null?List.of():mapper.readValue(s.getAutoWarnings(),new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException("Invalid stored warnings");}}
    private BookImportContent link(BookImportContent c,BookImportSession s) {
        try {return BookImportAutomation.link(c,s.getAudioHints()==null?List.of():mapper.readValue(s.getAudioHints(),new com.fasterxml.jackson.core.type.TypeReference<List<BookImportAutomation.AudioHint>>(){}));}
        catch(Exception e){throw new IllegalStateException("Invalid stored audio hints");}
    }

    @Transactional public Detail create(MultipartFile pdf,String name,Long levelId,Long typeId) throws Exception {
        return create(pdf,name,levelId,typeId,false);
    }
    @Transactional public Detail create(MultipartFile pdf,String name,Long levelId,Long typeId,boolean automatic) throws Exception {
        if(pdf==null || pdf.isEmpty() || pdf.getSize()>100L*1024*1024) throw invalid("Chọn PDF tối đa 100 MB.");
        if(blank(name) || name.length()>180) throw invalid("Tên sách cần có từ 1 đến 180 ký tự.");
        catalogue(levelId,typeId);
        var temporary=Files.createTempFile(storage.root(),"upload-",".pdf");
        try {
            pdf.transferTo(temporary);
            byte[] header=new byte[5];
            try(var in=Files.newInputStream(temporary)) { if(in.read(header)!=5 || !Arrays.equals(header,"%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw invalid("File không phải PDF hợp lệ."); }
            var digest=MessageDigest.getInstance("SHA-256");
            try(var in=Files.newInputStream(temporary);var sink=new java.security.DigestInputStream(in,digest)) { sink.transferTo(java.io.OutputStream.nullOutputStream()); }
            String hash=HexFormat.of().formatHex(digest.digest());
            var existing=sessions.findBySourceHash(hash);
            if(existing.isPresent()) {
                var s=existing.get();
                if(automatic && s.getImportedBookId()==null && content(s).lessons().isEmpty() && !"AUTO".equals(s.getAutoMode()) && !Set.of("PROCESSING","ANALYZING").contains(s.getState())) {s.setAutoMode("AUTO");s.setState("QUEUED");sessions.saveAndFlush(s);}
                return get(s.getId());
            }
            int count;
            try(var document=Loader.loadPDF(temporary.toFile())) {
                if(document.isEncrypted()) throw invalid("Hãy dùng PDF không khóa mật khẩu.");
                count=document.getNumberOfPages();
                if(count<1 || count>500) throw invalid("PDF cần có từ 1 đến 500 trang.");
            } catch(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) { throw invalid("PDF có mật khẩu; hãy mở khóa trước khi tải."); }
            catch(java.io.IOException e) { throw invalid("Không đọc được PDF. Hãy kiểm tra file rồi thử lại."); }
            String id=UUID.randomUUID().toString();
            Files.createDirectories(storage.directory(id)); Files.move(temporary,storage.pdf(id));
            var s=new BookImportSession();s.setId(id);s.setSourceHash(hash);s.setFileName(fileName(pdf.getOriginalFilename()));
            s.setPageCount(count);s.setState("QUEUED");s.setMessage("Đang chờ nhận dạng chữ.");
            s.setAutoMode(automatic?"AUTO":"MANUAL");
            s.setPayload(mapper.writeValueAsString(new BookImportContent(name.trim(),levelId,typeId,"",List.of(),List.of())));
            sessions.saveAndFlush(s);return get(id);
        } finally { Files.deleteIfExists(temporary); }
    }

    @Transactional public Detail addAudio(String id,List<MultipartFile> files) throws Exception {
        var s=lock(id);editable(s,true);var c=content(s);
        if(files==null || files.isEmpty() || files.size()+c.audio().size()>128) throw invalid("Có thể thêm tối đa 128 file nghe.");
        var audio=new ArrayList<>(c.audio());long total=audio.stream().mapToLong(BookImportContent.Asset::bytes).sum();
        var written=new ArrayList<Path>();
        try {
            for(var file:files) {
                String name=fileName(file.getOriginalFilename());int dot=name.lastIndexOf('.');String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
                String mime=Map.of("mp3","audio/mpeg","m4a","audio/mp4","wav","audio/wav","ogg","audio/ogg").get(ext);
                if(mime==null || file.isEmpty() || file.getSize()>40L*1024*1024) throw invalid("File nghe cần là MP3, M4A, WAV hoặc OGG, tối đa 40 MB/file.");
                total+=file.getSize();if(total>300L*1024*1024) throw invalid("Tổng file nghe của một sách tối đa 300 MB.");
                byte[] header;try(var in=file.getInputStream()) { header=in.readNBytes(16); }
                if(!audioHeader(ext,header)) throw invalid("Nội dung file nghe không khớp định dạng: "+name);
                var asset=new BookImportContent.Asset(UUID.randomUUID().toString(),name,mime,ext,file.getSize());
                var path=storage.audio(id,asset);Files.createDirectories(path.getParent());written.add(path);file.transferTo(path);audio.add(asset);
            }
            s.setPayload(mapper.writeValueAsString(link(new BookImportContent(c.bookName(),c.levelId(),c.typeId(),c.description(),audio,c.lessons()),s)));
            if("READY".equals(s.getState())) s.setState("REVIEW");sessions.saveAndFlush(s);return get(id);
        } catch(Exception e) { for(var path:written) Files.deleteIfExists(path);throw e; }
    }
    static boolean audioHeader(String ext,byte[] b) {
        var text=new String(b,java.nio.charset.StandardCharsets.ISO_8859_1);
        return switch(ext) {
            case "mp3" -> text.startsWith("ID3") || b.length>1 && (b[0]&255)==255 && (b[1]&224)==224;
            case "m4a" -> b.length>=12 && text.substring(4,8).equals("ftyp");
            case "wav" -> b.length>=12 && text.startsWith("RIFF") && text.substring(8,12).equals("WAVE");
            case "ogg" -> text.startsWith("OggS");default -> false;
        };
    }

    @Transactional public Detail save(String id,Save request) throws Exception {
        var s=lock(id);editable(s,false);version(s,request.version());
        if(request.reviewed() && "AUTO".equals(s.getAutoMode()) && !s.isAiCompleted()) throw conflict("Bản nháp tự động chưa xử lý hết sách. Hãy tiếp tục AI hoặc chuyển sang biên tập thủ công trước khi duyệt.");
        var c=request.content();var stored=content(s);
        if(c==null) throw invalid("Thiếu nội dung sách.");
        // Uploaded assets are server-owned; the editor cannot inject paths or another session's IDs.
        c=new BookImportContent(c.bookName(),c.levelId(),c.typeId(),c.description(),stored.audio(),c.lessons());
        c=link(c,s);
        validate(c,s.getPageCount(),request.reviewed());
        String json=mapper.writeValueAsString(c);if(json.length()>4_000_000) throw invalid("Bản nháp quá lớn; hãy chia sách thành phần nhỏ hơn.");
        s.setPayload(json);s.setState(request.reviewed()?"READY":"REVIEW");
        s.setMessage(request.reviewed()?"Đã duyệt nội dung và đáp án. Sẵn sàng nhập.":"Bản nháp đã được lưu; chưa đưa vào bài học.");
        sessions.saveAndFlush(s);return get(id);
    }
    @Transactional @CacheEvict(value={"books","exerciseTypes"},allEntries=true)
    public Detail publish(String id,Publish request) {
        var s=lock(id);if(s.getImportedBookId()!=null) return get(id);
        version(s,request.version());if(!"READY".equals(s.getState())) throw conflict("Hãy duyệt nội dung và đáp án trước khi nhập sách.");
        var c=content(s);validate(c,s.getPageCount(),true);catalogue(c.levelId(),c.typeId());
        var book=new Books();book.setBookName(c.bookName().trim());book.setDescription(c.description());
        book.setLevel(levels.findById(c.levelId()).orElseThrow());book.setTypes(types.findById(c.typeId()).orElseThrow());books.save(book);
        for(var part:c.lessons()) {
            var lesson=new Lessons();lesson.setBook(book);lesson.setName(part.name().trim());lesson.setDescription(part.description());
            lesson.setReading(html(part.reading()));
            if(!blank(part.audioId())) { lesson.setAudioImportId(id);lesson.setAudioAssetId(part.audioId()); }
            lessons.save(lesson);
            for(var input:part.grammars()) {
                var grammar=new Grammar();grammar.setLessons(lesson);grammar.setTitle(input.title());grammar.setDescription(html(input.description()));grammars.save(grammar);
                for(var inputExample:input.examples()) {
                    var e=new Example();e.setGrammar(grammar);e.setNihongo(html(inputExample.nihongo()));e.setVietnamese(html(inputExample.vietnamese()));examples.save(e);
                }
            }
            for(var input:part.exercises()) {
                var group=exerciseTypes.findByName(input.groupName()).orElseGet(()->{var t=new ExerciseType();t.setName(input.groupName());return exerciseTypes.save(t);});
                var q=new ExersiceKeyword();q.setLessons(lesson);q.setExerciseType(group);q.setContentNihongo(html(input.contentNihongo()));
                q.setAnswerA(input.answerA());q.setAnswerB(input.answerB());q.setAnswerC(input.answerC());q.setAnswerD(input.answerD());q.setCorrectAnswer(input.correctAnswer());
                if(!blank(input.audioId())) { q.setAudioImportId(id);q.setAudioAssetId(input.audioId()); }exercises.save(q);
            }
        }
        s.setImportedBookId(book.getBookId());s.setState("IMPORTED");s.setMessage("Đã nhập sách. Các bài học có thể chỉnh sửa tại trang quản lý sách.");sessions.saveAndFlush(s);return get(id);
    }
    public void validate(BookImportContent c,int pageCount,boolean reviewed) {
        if(blank(c.bookName()) || c.bookName().length()>180 || c.lessons()==null || c.audio()==null || c.lessons().size()>300) throw invalid("Tên sách hoặc danh sách bài học không hợp lệ.");
        catalogue(c.levelId(),c.typeId());
        if(reviewed && c.lessons().isEmpty()) throw invalid("Cần có ít nhất một bài học trước khi duyệt.");
        if(reviewed && c.description()!=null && c.description().length()>255) throw invalid("Mô tả sách tối đa 255 ký tự.");
        var names=new HashSet<String>();var assets=c.audio().stream().map(BookImportContent.Asset::id).collect(java.util.stream.Collectors.toSet());
        int count=0;
        for(var l:c.lessons()) {
            if(l==null || l.grammars()==null || l.exercises()==null || l.firstPage()<1 || l.lastPage()<l.firstPage() || l.lastPage()>pageCount) throw invalid("Trang nguồn của bài học không hợp lệ.");
            if(reviewed && (blank(l.name()) || l.name().length()>180 || !names.add(l.name().trim()) || blank(l.reading()) && l.grammars().isEmpty() && l.exercises().isEmpty())) throw invalid("Bài học cần tên không trùng và nội dung học.");
            if(reviewed && l.description()!=null && l.description().length()>255) throw invalid("Mục tiêu bài học tối đa 255 ký tự.");
            audioId(l.audioId(),assets);
            count+=l.grammars().size()+l.exercises().size();if(count>5000) throw invalid("Một lần nhập tối đa 5.000 mục ngữ pháp và bài tập.");
            for(var g:l.grammars()) {
                if(g==null || g.examples()==null) throw invalid("Thiếu danh sách ví dụ ngữ pháp.");
                if(reviewed && (blank(g.title()) || g.title().length()>180 || blank(g.description()) || g.examples().isEmpty())) throw invalid("Ngữ pháp cần tiêu đề, giải thích và ví dụ.");
                for(var e:g.examples()) if(e==null || reviewed && (blank(e.nihongo()) || blank(e.vietnamese()))) throw invalid("Ví dụ cần tiếng Nhật và dịch tiếng Việt.");
            }
            for(var q:l.exercises()) {
                if(q==null) throw invalid("Câu hỏi không hợp lệ.");audioId(q.audioId(),assets);
                if(reviewed) {
                    if(blank(q.groupName()) || q.groupName().length()>180 || blank(q.contentNihongo()) || q.correctAnswer()==null || !q.correctAnswer().matches("[ABCD]")) throw invalid("Câu hỏi cần nhóm, nội dung và đáp án đã đối chiếu.");
                    var choices=Arrays.asList(q.answerA(),q.answerB(),q.answerC(),q.answerD());
                    if(choices.stream().filter(v->!blank(v)).count()<2 || blank(choices.get("ABCD".indexOf(q.correctAnswer()))) || choices.stream().anyMatch(v->v!=null && v.length()>255)) throw invalid("Đáp án đúng cần trỏ tới lựa chọn có nội dung (tối đa 255 ký tự/lựa chọn).");
                }
            }
        }
    }
    public BookImportContent.Asset asset(String id,String assetId) { return content(find(id)).audio().stream().filter(a->a.id().equals(assetId)).findFirst().orElseThrow(()->missing("Không tìm thấy file nghe.")); }
    @Transactional public Detail automate(String id,Publish request) {
        var s=lock(id);editable(s,false);version(s,request.version());
        if(!content(s).lessons().isEmpty() && !"AI_FAILED".equals(s.getState())) throw conflict("Bản nháp đã có nội dung. Tiếp tục biên tập để giữ các chỉnh sửa; không tự ghi đè.");
        if(content(s).lessons().isEmpty() && s.getAiProcessedPages()>=s.getPageCount()) {
            s.setAiProcessedPages(0);
        }
        s.setAiCompleted(false);
        s.setAutoMode("AUTO");s.setState("QUEUED");s.setMessage("Đang chờ tạo bản nháp tự động.");sessions.saveAndFlush(s);return get(id);
    }
    @Transactional public Detail manual(String id,Publish request) {
        var s=lock(id);editable(s,false);version(s,request.version());
        s.setAutoMode("MANUAL");s.setState("REVIEW");s.setMessage("Chuyển sang biên tập thủ công. Các phần AI đã tạo được giữ; hãy bổ sung phần còn thiếu trước khi nhập.");sessions.saveAndFlush(s);return get(id);
    }
    private void audioId(String id,Set<String> assets) { if(!blank(id) && !assets.contains(id)) throw invalid("File nghe chưa được tải lên cho sách này."); }
    private void catalogue(Long level,Long type) { if(level==null || type==null || !levels.existsById(level) || !types.existsById(type)) throw invalid("Hãy chọn trình độ và loại sách có trong danh mục."); }
    private BookImportSession lock(String id) { storage.directory(id);return sessions.lock(id).orElseThrow(()->missing("Không tìm thấy lần nhập sách.")); }
    private void editable(BookImportSession s,boolean audio) {
        if(s.getImportedBookId()!=null) throw conflict("Sách đã nhập; hãy chỉnh sửa ở trang quản lý sách.");
        if(!audio && Set.of("QUEUED","PROCESSING","ANALYZING").contains(s.getState())) throw conflict("Đang xử lý sách. Hãy chờ xử lý xong rồi lưu bản nháp.");
    }
    private void version(BookImportSession s,Long v) { if(!Objects.equals(s.getVersion(),v)) throw conflict("Bản nháp đã thay đổi. Hãy tải lại trước khi lưu."); }
    public static boolean blank(String s) { return s==null || org.jsoup.Jsoup.parse(s).text().isBlank(); }
    private static String html(String text) { if(text==null) return null;return ContentHtml.clean(text.contains("<")?text:Arrays.stream(text.split("\\R",-1)).map(t->"<p>"+org.jsoup.nodes.Entities.escape(t)+"</p>").collect(java.util.stream.Collectors.joining())); }
    private String fileName(String name) { if(name==null) return "book.pdf";var s=name.replace('\\','/');s=s.substring(s.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");return s.substring(0,Math.min(s.length(),180)); }
    static ResponseStatusException invalid(String s) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,s); }
    static ResponseStatusException conflict(String s) { return new ResponseStatusException(HttpStatus.CONFLICT,s); }
    static ResponseStatusException missing(String s) { return new ResponseStatusException(HttpStatus.NOT_FOUND,s); }
}
