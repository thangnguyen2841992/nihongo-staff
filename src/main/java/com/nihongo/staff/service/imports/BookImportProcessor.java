package com.nihongo.staff.service.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.BookImportPage;
import com.nihongo.staff.repository.*;
import jakarta.annotation.PreDestroy;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded page processing; explicit AUTO sessions use the configured Gemini draft extractor. */
@Component
public class BookImportProcessor {
    private final BookImportSessionRepository sessions;
    private final BookImportPageRepository pages;
    private final BookImportStorage storage;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final BookImportAutomation automation;
    private final Set<String> running=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"book-import-ocr");t.setDaemon(true);return t;});
    public BookImportProcessor(BookImportSessionRepository s,BookImportPageRepository p,BookImportStorage f,ObjectMapper m,PlatformTransactionManager manager,BookImportAutomation automation,@Value("${book-import.processing-enabled:true}") boolean enabled) {
        sessions=s;pages=p;storage=f;mapper=m;tx=new TransactionTemplate(manager);this.enabled=enabled;this.automation=automation;
    }
    @EventListener(ApplicationReadyEvent.class) public void recover() { if(enabled) sessions.findByStateIn(List.of("QUEUED","PROCESSING","ANALYZING")).forEach(s->start(s.getId())); }
    public void start(String id) {
        if(!enabled || !running.add(id)) return;
        try { executor.execute(()->{try { process(id); } finally { running.remove(id); }}); }
        catch(RejectedExecutionException e) { running.remove(id);state(id,"FAILED","Hàng chờ đang đầy. Hãy thử nhận dạng lại.",null); }
    }
    public void process(String id) {
        try {
            var session=sessions.findById(id).orElseThrow();
            if(session.getImportedBookId()!=null || Set.of("READY","IMPORTED").contains(session.getState())) return;
            boolean automatic="AUTO".equals(session.getAutoMode());
            boolean rebuildImages=automatic && session.getAiProcessedPages()==0 && session.getProcessedPages()>=session.getPageCount();
            state(id,"PROCESSING","Đang đọc PDF và nhận dạng chữ tại máy chủ.",null);
            var pending=new ArrayList<Integer>();Files.createDirectories(storage.directory(id).resolve("pages"));
            try(var document=Loader.loadPDF(storage.pdf(id).toFile())) {
                var renderer=new PDFRenderer(document);renderer.setSubsamplingAllowed(true);var stripper=new PDFTextStripper();stripper.setSortByPosition(true);
                for(int n=1;n<=document.getNumberOfPages();n++) {
                    if(Thread.currentThread().isInterrupted()) return;
                    var key=id+":"+n;var saved=pages.findById(key);
                    if(!rebuildImages && saved.isPresent() && Files.exists(storage.image(id,n))) { if("OCR_PENDING".equals(saved.get().getMethod())) {if(automatic){saved.get().setMethod("IMAGE");pages.save(saved.get());}else pending.add(n);}continue; }
                    var box=document.getPage(n-1).getCropBox();float w=box.getWidth(),h=box.getHeight();
                    if(w<=0 || h<=0 || w>20000 || h>20000) throw new IllegalArgumentException("Page dimensions");
                    var bitmap=renderer.renderImage(n-1,Math.min(1646f/w,2400f/h));ImageIO.write(bitmap,"png",storage.image(id,n).toFile());bitmap.flush();
                    stripper.setStartPage(n);stripper.setEndPage(n);String text=stripper.getText(document).trim();boolean direct=text.replaceAll("\\s","").length()>=5;
                    var page=new BookImportPage();page.setId(key);page.setSessionId(id);page.setPageNumber(n);page.setMethod(direct?"PDF_TEXT":automatic?"IMAGE":"OCR_PENDING");page.setText(direct?text:"");pages.save(page);
                    if(!direct && !automatic) pending.add(n);progress(id);
                }
            }
            if(automatic) {progress(id);automation.run(id);return;}
            String message="Nhận dạng xong. Hãy tạo bài học và đối chiếu nội dung, bản dịch, đáp án với PDF.";
            if(!pending.isEmpty()) {
                if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
                    if(!windowsOcr(id,pending)) message="Chưa nhận dạng được một số trang scan. Bạn vẫn có thể xem PDF và biên tập chữ thủ công.";
                } else message="PDF có trang scan. Máy chủ này chưa hỗ trợ Windows OCR; hãy biên tập chữ từ trang nguồn hoặc triển khai bộ OCR tại máy chủ.";
            }
            state(id,"REVIEW",message,null);progress(id);
        } catch(Exception e) { state(id,"FAILED","Chưa đọc xong PDF. Bản nháp và các trang đã xử lý được giữ lại; bạn có thể thử lại.",null); }
    }
    private boolean windowsOcr(String id,List<Integer> pending) throws Exception {
        var dir=storage.directory(id);var out=dir.resolve("ocr");Files.createDirectories(out);var request=dir.resolve("ocr-pages.json");mapper.writeValue(request.toFile(),pending);
        String script;try(var in=new ClassPathResource("imports/book-import/windows-ocr.ps1").getInputStream()) {script=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        String command="& {\n"+script+"\n} -Source '"+quote(dir.resolve("pages"))+"' -Output '"+quote(out)+"' -Request '"+quote(request)+"'";
        String encoded=Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
        var child=new ProcessBuilder("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",encoded).redirectErrorStream(true).redirectOutput(dir.resolve("ocr.log").toFile()).start();
        long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(20);var remaining=new HashSet<>(pending);
        try {
            do {
                collectOcr(id,out,remaining);progress(id);
                if(child.waitFor(1,TimeUnit.SECONDS)) break;
                if(System.nanoTime()>deadline || Thread.currentThread().isInterrupted()) {child.destroyForcibly();return false;}
            } while(true);
            collectOcr(id,out,remaining);return child.exitValue()==0 && remaining.isEmpty();
        } finally { if(child.isAlive()) child.destroyForcibly(); }
    }
    private void collectOcr(String id,Path out,Set<Integer> remaining) {
        for(var n:new ArrayList<>(remaining)) {
            var file=out.resolve(String.format("%03d.json",n));if(!Files.exists(file)) continue;
            try {
                String text=mapper.readTree(Files.readString(file,StandardCharsets.UTF_8).replace("\ufeff","")).path("text").asText();
                var p=pages.findById(id+":"+n).orElseThrow();p.setText(text);p.setMethod(text.isBlank()?"OCR_EMPTY":"WINDOWS_OCR");pages.save(p);remaining.remove(n);
            } catch(java.io.IOException incompleteWrite) { /* Retry after the child finishes this page. */ }
        }
    }
    private String quote(Path path) { return path.toAbsolutePath().toString().replace("'","''"); }
    private void progress(String id) { int completed=Math.toIntExact(pages.countBySessionIdAndMethodNot(id,"OCR_PENDING"));state(id,null,null,completed); }
    private void state(String id,String state,String message,Integer processed) { tx.executeWithoutResult(t->sessions.lock(id).ifPresent(s->{if(state!=null)s.setState(state);if(message!=null)s.setMessage(message);if(processed!=null)s.setProcessedPages(processed);sessions.save(s);})); }
    public void retry(String id) {
        tx.executeWithoutResult(t->{var s=sessions.lock(id).orElseThrow();
            boolean incomplete="REVIEW".equals(s.getState()) && s.getProcessedPages()<s.getPageCount();
            if(!Set.of("FAILED","AI_FAILED").contains(s.getState()) && !incomplete) throw BookImportService.conflict("Chỉ nhận dạng lại khi lần xử lý trước thất bại hoặc còn trang chưa xử lý.");
            if("AUTO".equals(s.getAutoMode()) && s.getAiProcessedPages()>=s.getPageCount()) {
                try {if(mapper.readTree(s.getPayload()).path("lessons").isEmpty()) {s.setAiProcessedPages(0);s.setAiCompleted(false);}}
                catch(java.io.IOException e) {throw new IllegalStateException("Invalid stored draft",e);}
            }
            s.setState("QUEUED");s.setMessage("Đang chờ nhận dạng lại.");sessions.save(s);});start(id);
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
