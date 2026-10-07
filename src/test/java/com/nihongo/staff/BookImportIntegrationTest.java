package com.nihongo.staff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.model.*;
import com.nihongo.staff.repository.*;
import com.nihongo.staff.service.imports.*;
import com.nihongo.staff.security.SubscriptionClient;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=", "spring.datasource.url=jdbc:h2:mem:generic_import;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
 "spring.jpa.hibernate.ddl-auto=create-drop","eureka.client.enabled=false","spring.cloud.discovery.enabled=false",
 "monitoring.collection.enabled=false","monitoring.prometheus-targets-file=target/generic-test/node.json",
 "monitoring.prometheus-windows-targets-file=target/generic-test/windows.json","book-import.processing-enabled=false",
 "book-import.storage-directory=target/generic-test/files","jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA="})
@AutoConfigureMockMvc
class BookImportIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired BookImportService service; @Autowired BookImportProcessor processor;
 @Autowired BookImportSessionRepository sessions; @Autowired BookImportPageRepository pages;
 @Autowired IBookRepository books; @Autowired ILessonsRepository lessons; @Autowired IGrammarRepository grammars;
 @Autowired IExampleRepository examples; @Autowired IExerciseKeywordRepository questions; @Autowired ILevelsRepository levels;
 @Autowired ITypeRepository types; @Autowired PlatformTransactionManager transactions;
 @MockitoBean SubscriptionClient subscriptions;
 @MockitoBean BookImportGemini gemini;
 @Autowired BookImportAutomation automation;
 @Autowired TryN3BookData knownBook;
 @Autowired BookImportQuestionAssistant questionAssistant;
 Long levelId,typeId;
 @BeforeEach void prepare() {
  questions.deleteAll();examples.deleteAll();grammars.deleteAll();lessons.deleteAll();books.deleteAll();pages.deleteAll();sessions.deleteAll();
  var level=levels.findAll().stream().findFirst().orElseGet(()->{var l=new Levels();l.setLevelName("N3");return levels.save(l);});levelId=level.getLevelId();
  var type=types.findAll().stream().findFirst().orElseGet(()->{var t=new Types();t.setTypeName("Generic test");return types.save(t);});typeId=type.getTypeId();
 }
 String auth(String role) throws Exception {
  var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),new com.nimbusds.jwt.JWTClaimsSet.Builder()
   .subject("test").claim("roles",List.of(role)).expirationTime(Date.from(java.time.Instant.now().plusSeconds(300))).build());
  jwt.sign(new com.nimbusds.jose.crypto.MACSigner(Base64.getDecoder().decode("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=")));return "Bearer "+jwt.serialize();
 }
 byte[] pdf(boolean scan) throws Exception {
  try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
   var page=new PDPage();doc.addPage(page);
   try(var content=new PDPageContentStream(doc,page)) {
    if(scan) {
     try(var in=getClass().getResourceAsStream("/imports/try-n3/pages/15.png")) {
      assertNotNull(in);content.drawImage(LosslessFactory.createFromImage(doc,javax.imageio.ImageIO.read(in)),0,0,600,790);
     }
    } else { content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),16);content.newLineAtOffset(50,700);content.showText("Synthetic import test reading");content.endText(); }
   }
   doc.save(out);return out.toByteArray();
  }
 }
 BookImportService.Detail create() throws Exception { return service.create(new MockMultipartFile("pdf","test.pdf","application/pdf",pdf(false)),"TEST ONLY",levelId,typeId); }
 BookImportContent draftContent(BookImportService.Detail d,String translation,String key,String asset) {
  var grammar=new BookImportContent.Grammar("～うちに","Trong lúc",List.of(new BookImportContent.Example("明るいうちに帰る。",translation)));
  var question=new BookImportContent.Question("Ôn tập","正しいものを選ぶ。","一","二","","",key,asset);
  var lesson=new BookImportContent.Lesson("Bài kiểm thử","",1,1,"<p>本文</p><script>alert(1)</script>",asset,List.of(grammar),List.of(question));
  return new BookImportContent("TEST ONLY",levelId,typeId,"",List.of(),List.of(lesson));
 }
 BookImportService.Detail ready(boolean audio) throws Exception {
  var d=create();processor.process(d.summary().id());d=service.get(d.summary().id());
  String asset="";
  if(audio) {
   // Minimal local WAV fixture. No fixture data is written to the real database.
   byte[] wav=new byte[44];System.arraycopy("RIFF".getBytes(),0,wav,0,4);System.arraycopy("WAVEfmt ".getBytes(),0,wav,8,8);
   d=service.addAudio(d.summary().id(),List.of(new MockMultipartFile("files","test.wav","audio/wav",wav)));asset=d.content().audio().get(0).id();
  }
  return service.save(d.summary().id(),new BookImportService.Save(d.summary().version(),true,draftContent(d,"Về khi trời còn sáng.","B",asset)));
 }
 @Test void extractsTextAndSourceAndDeduplicatesPdfWithoutPublishing() throws Exception {
  byte[] pdf=pdf(false);
  var upload=new MockMultipartFile("pdf","test.pdf","application/pdf",pdf);
  var response=mvc.perform(multipart("/api/staff/book-imports").file(upload).param("bookName","TEST ONLY").param("levelId",levelId.toString()).param("typeId",typeId.toString())
   .header("Authorization",auth("STAFF"))).andExpect(status().isCreated()).andReturn();
  var d=mapper.readValue(response.getResponse().getContentAsByteArray(),BookImportService.Detail.class);processor.process(d.summary().id());
  assertEquals("REVIEW",service.get(d.summary().id()).summary().state());assertEquals("PDF_TEXT",service.get(d.summary().id()).pages().get(0).method());
  assertTrue(service.get(d.summary().id()).pages().get(0).text().contains("Synthetic import test"));
  assertEquals(d.summary().id(),service.create(upload,"Renamed",levelId,typeId).summary().id());assertEquals(1,sessions.count());assertEquals(0,books.count());
  mvc.perform(get("/api/staff/book-imports/"+d.summary().id()+"/pages/1").header("Authorization",auth("STAFF"))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  mvc.perform(get("/api/staff/book-imports/"+d.summary().id()+"/pages/2").header("Authorization",auth("STAFF"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/staff/book-imports").header("Authorization",auth("USER"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/staff/book-imports")).andExpect(status().isUnauthorized());
  mvc.perform(multipart("/api/staff/book-imports").file(new MockMultipartFile("pdf","bad.pdf","application/pdf","bad".getBytes())).param("bookName","Bad").param("levelId",levelId.toString()).param("typeId",typeId.toString()).header("Authorization",auth("STAFF"))).andExpect(status().isBadRequest());
 }
 @Test void rejectsIncompleteReviewStaleSaveAndForeignAudio() throws Exception {
  var d=create();processor.process(d.summary().id());d=service.get(d.summary().id());String path="/api/staff/book-imports/"+d.summary().id();
  mvc.perform(post(path+"/publish").header("Authorization",auth("STAFF")).contentType("application/json").content(mapper.writeValueAsString(new BookImportService.Publish(d.summary().version())))).andExpect(status().isConflict());
  for(var invalid:List.of(draftContent(d,"","B",""),draftContent(d,"Dịch","",""),draftContent(d,"Dịch","B",UUID.randomUUID().toString()))) {
   mvc.perform(put(path).header("Authorization",auth("STAFF")).contentType("application/json").content(mapper.writeValueAsString(new BookImportService.Save(d.summary().version(),true,invalid)))).andExpect(status().isBadRequest());
  }
  service.save(d.summary().id(),new BookImportService.Save(d.summary().version(),false,draftContent(d,"","","")));
  mvc.perform(put(path).header("Authorization",auth("STAFF")).contentType("application/json").content(mapper.writeValueAsString(new BookImportService.Save(d.summary().version(),false,draftContent(d,"","",""))))).andExpect(status().isConflict());
  assertEquals(0,books.count());
 }
 @Test void publishesAtomicallyOnceAndProtectsSeekableAudio() throws Exception {
  var d=ready(true);String id=d.summary().id();Long version=d.summary().version();
  assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(s->{service.publish(id,new BookImportService.Publish(version));throw new IllegalStateException("Rollback test");}));
  assertEquals(0,books.count());assertEquals("READY",service.get(id).summary().state());
  var result=service.publish(id,new BookImportService.Publish(version));assertEquals("IMPORTED",result.summary().state());
  assertEquals(result.summary().bookId(),service.publish(id,new BookImportService.Publish(-1L)).summary().bookId());assertEquals(1,books.count());
  assertEquals(1,lessons.count());assertEquals(1,grammars.count());assertEquals(1,examples.count());assertEquals(1,questions.count());
  var l=lessons.findAll().get(0);var q=questions.findAll().get(0);assertFalse(l.getReading().contains("<script>"));assertEquals("B",q.getCorrectAnswer());
  String audio="/api/staff/imported-audio/lessons/"+l.getLessonId();
  mvc.perform(get("/api/staff/lessons/"+l.getLessonId()).header("Authorization",auth("STAFF"))).andExpect(status().isOk()).andExpect(jsonPath("$.audioUrl").value(audio));
  mvc.perform(get("/api/staff/getAllExcercisesKeywordOfLesson/"+l.getLessonId()).header("Authorization",auth("STAFF"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].audioUrl").value("/api/staff/imported-audio/exercises/"+q.getExerciseKeywordId()));
  mvc.perform(get(audio).header("Authorization",auth("STAFF")).header("Range","bytes=0-11")).andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 0-11/44"));
  when(subscriptions.hasAccess(levelId)).thenReturn(false);
  mvc.perform(get(audio).header("Authorization",auth("USER"))).andExpect(status().isForbidden());
  when(subscriptions.hasAccess(levelId)).thenReturn(true);
  mvc.perform(get(audio).header("Authorization",auth("USER"))).andExpect(status().isForbidden());
  String publicationPath="/api/staff/books/"+result.summary().bookId()+"/publication";
  mvc.perform(get(publicationPath).header("Authorization",auth("USER"))).andExpect(status().isForbidden());
  mvc.perform(post(publicationPath+"/submit").header("Authorization",auth("STAFF"))).andExpect(status().isOk());
  mvc.perform(post(publicationPath+"/publish").header("Authorization",auth("STAFF"))).andExpect(status().isForbidden());
  mvc.perform(post(publicationPath+"/publish").header("Authorization",auth("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get(audio).header("Authorization",auth("USER"))).andExpect(status().isOk()).andExpect(content().contentType("audio/wav"));
  mvc.perform(get("/api/staff/imported-audio/exercises/"+q.getExerciseKeywordId()).header("Authorization",auth("USER"))).andExpect(status().isOk());
  mvc.perform(get("/api/staff/book-imports/"+id+"/audio/"+d.content().audio().get(0).id()).header("Authorization",auth("USER"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/staff/lessons/"+l.getLessonId()+"/grade").header("Authorization",auth("STAFF")).contentType("application/json").content(mapper.writeValueAsString(Map.of(q.getExerciseKeywordId(),"B")))).andExpect(status().isOk()).andExpect(jsonPath("$.correctCount").value(1));
 }
 @Test void retriesOnlyFailedOrIncompleteRecognitionWithoutDiscardingDraft() throws Exception {
  var d=create();String id=d.summary().id();processor.process(id);d=service.get(id);
  service.save(id,new BookImportService.Save(d.summary().version(),false,draftContent(d,"Dịch","B","")));
  var s=sessions.findById(id).orElseThrow();s.setProcessedPages(0);sessions.saveAndFlush(s);processor.retry(id);
  assertEquals("QUEUED",service.get(id).summary().state());assertEquals(1,service.get(id).content().lessons().size());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->processor.retry(id));
 }
 @Test void automaticDraftResumesAfterProviderFailureMergesContinuationAndReadsAppendixAnswers() throws Exception {
  var d=create();processor.process(d.summary().id());String id=d.summary().id();var s=sessions.findById(id).orElseThrow();s.setAutoMode("AUTO");s.setPageCount(7);sessions.saveAndFlush(s);
  var g=new BookImportContent.Grammar("～うちに","Trong lúc",List.of(new BookImportContent.Example("明るいうちに帰る。","Về khi trời còn sáng.")));
  var q=new BookImportGemini.Question("Ôn tập","1. 明るい（　）帰る。","だけ","うちに","","","B","04",0,"");
  var first=new BookImportGemini.Batch(List.of(new BookImportGemini.Part("Bài 1","Mục tiêu",1,6,"本文","02",List.of(g),List.of(q))),List.of(),List.of("Cần xem phụ lục đáp án."));
  when(gemini.configured()).thenReturn(true);
  when(gemini.extract(eq(id),eq(1),eq(6),any(),any())).thenReturn(first);
  when(gemini.extract(eq(id),eq(7),eq(7),any(),any())).thenThrow(new BookImportGemini.Failure("Gemini giới hạn lượt (429)."));
  automation.run(id);d=service.get(id);assertEquals("AI_FAILED",d.summary().state());assertEquals(6,d.summary().aiProcessedPages());assertEquals(1,d.content().lessons().size());assertEquals("",d.content().lessons().get(0).exercises().get(0).correctAnswer());
  var partial=d;assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.save(id,new BookImportService.Save(partial.summary().version(),true,partial.content())));
  var last=new BookImportGemini.Batch(List.of(new BookImportGemini.Part("Bài 1","",7,7,"","02",List.of(g),List.of())),List.of(new BookImportGemini.Answer("L0Q0","B",7,"1: 2")),List.of());
  when(gemini.extract(eq(id),eq(7),eq(7),any(),any())).thenReturn(last);
  automation.run(id);d=service.get(id);assertEquals("REVIEW",d.summary().state());assertTrue(d.summary().aiCompleted());assertEquals(7,d.summary().aiProcessedPages());assertEquals(1,d.content().lessons().size());assertEquals(1,d.content().lessons().get(0).grammars().size());assertEquals("B",d.content().lessons().get(0).exercises().get(0).correctAnswer());
  verify(gemini,times(1)).extract(eq(id),eq(1),eq(6),any(),any());
  byte[] wav=new byte[44];System.arraycopy("RIFF".getBytes(),0,wav,0,4);System.arraycopy("WAVE".getBytes(),0,wav,8,4);
  d=service.addAudio(id,List.of(new MockMultipartFile("files","02 Track 02.wav","audio/wav",wav),new MockMultipartFile("files","04 Track 04.wav","audio/wav",wav)));
  assertEquals(d.content().audio().get(0).id(),d.content().lessons().get(0).audioId());assertEquals(d.content().audio().get(1).id(),d.content().lessons().get(0).exercises().get(0).audioId());
  service.save(id,new BookImportService.Save(d.summary().version(),true,d.content()));assertEquals("READY",service.get(id).summary().state());
 }
 @Test void sourceFingerprintReusesPreparedBookAndMatchesAudioWithoutGemini() throws Exception {
  var d=create();String id=d.summary().id();var s=sessions.findById(id).orElseThrow();s.setSourceHash(knownBook.book().sourceSha256());s.setPageCount(209);s.setAutoMode("AUTO");sessions.saveAndFlush(s);
  automation.run(id);d=service.get(id);assertTrue(d.summary().aiCompleted());assertEquals(21,d.content().lessons().size());
  assertEquals(113,d.content().lessons().stream().mapToInt(l->l.grammars().size()).sum());assertEquals(198,d.content().lessons().stream().mapToInt(l->l.exercises().size()).sum());
  service.validate(d.content(),209,true);assertFalse(d.warnings().isEmpty());verifyNoInteractions(gemini);
 }
 @Test void emptyCompletedAiDraftFailsAndCanRestartWithoutLosingSources() throws Exception {
  var d=create();String id=d.summary().id();processor.process(id);
  when(gemini.configured()).thenReturn(true);
  when(gemini.extract(anyString(),anyInt(),anyInt(),any(),anyList())).thenReturn(new BookImportGemini.Batch(List.of(),List.of(),List.of("Trang trắng")));
  d=service.get(id);service.automate(id,new BookImportService.Publish(d.summary().version()));processor.process(id);
  d=service.get(id);assertEquals("AI_FAILED",d.summary().state());assertFalse(d.summary().aiCompleted());assertEquals(1,d.summary().aiProcessedPages());assertTrue(d.content().lessons().isEmpty());
  var restarted=service.automate(id,new BookImportService.Publish(d.summary().version()));
  assertEquals(0,restarted.summary().aiProcessedPages());assertFalse(restarted.summary().aiCompleted());assertEquals(d.content().audio(),restarted.content().audio());
  assertEquals(d.summary().processedPages(),restarted.summary().processedPages());assertEquals(d.pages(),restarted.pages());
 }
 @Test void rendersJpeg2000ScanInsteadOfSilentlyReturningWhitePage() throws Exception {
  var source=new java.awt.image.BufferedImage(40,40,java.awt.image.BufferedImage.TYPE_INT_RGB);
  var graphics=source.createGraphics();graphics.setColor(java.awt.Color.BLACK);graphics.fillRect(0,0,40,40);graphics.dispose();
  try(var bytes=new ByteArrayOutputStream();var doc=new PDDocument()) {
   assertTrue(javax.imageio.ImageIO.write(source,"JPEG2000",bytes));
   var image=new org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject(doc,new ByteArrayInputStream(bytes.toByteArray()),org.apache.pdfbox.cos.COSName.JPX_DECODE,40,40,8,org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE);
   var page=new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(40,40));doc.addPage(page);
   try(var stream=new PDPageContentStream(doc,page)){stream.drawImage(image,0,0,40,40);}
   var rendered=new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0);
   assertEquals(0,rendered.getRGB(20,20)&0xffffff);
  }
 }
 @Test void questionAssistanceReturnsProposalWithoutSavingAndRejectsUnsupportedEvidence() throws Exception {
  var d=create();processor.process(d.summary().id());d=service.get(d.summary().id());
  var q=new BookImportContent.Question("Ôn tập","明るい（　）帰る。","うちに","だけ","","","","");
  var request=new BookImportQuestionAssistant.Request(d.summary().version(),q,1,null);
  final String id=d.summary().id();
  var proposal=new BookImportQuestionAssistant.Suggestion(q.groupName(),q.contentNihongo(),q.answerA(),q.answerB(),"","","A","REASONING","うちに diễn tả làm việc trong khi trạng thái còn tiếp diễn.",0,"",List.of());
  when(gemini.assistQuestion(anyString(),any(),anyList(),anyList())).thenReturn(proposal);
  assertEquals(proposal,questionAssistant.suggest(d.summary().id(),request));
  assertEquals(d,service.get(d.summary().id()));
  mvc.perform(post("/api/staff/book-imports/"+d.summary().id()+"/assist-question").header("Authorization",auth("USER")).contentType("application/json").content(mapper.writeValueAsBytes(request))).andExpect(status().isForbidden());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->questionAssistant.suggest(id,new BookImportQuestionAssistant.Request(-1L,q,1,null)));
  when(gemini.assistQuestion(anyString(),any(),anyList(),anyList())).thenReturn(new BookImportQuestionAssistant.Suggestion(q.groupName(),q.contentNihongo(),q.answerA(),q.answerB(),"","","A","SOURCE","Đáp án từ nguồn",2,"1 A",List.of()));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->questionAssistant.suggest(id,request));
 }
 @Test void missingProviderKeyKeepsSourcesAndAllowsExplicitManualFallback() throws Exception {
  var d=create();String id=d.summary().id();processor.process(id);d=service.get(id);service.automate(id,new BookImportService.Publish(d.summary().version()));processor.process(id);d=service.get(id);
  assertEquals("AI_FAILED",d.summary().state());assertTrue(d.summary().message().contains("key Gemini"));assertEquals(1,d.summary().processedPages());
  d=service.manual(id,new BookImportService.Publish(d.summary().version()));assertEquals("MANUAL",d.summary().autoMode());assertEquals("REVIEW",d.summary().state());
 }
 @Test void recognizesJapaneseScanLocallyOnWindows() throws Exception {
  Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("windows"));
  var d=service.create(new MockMultipartFile("pdf","scan-test.pdf","application/pdf",pdf(true)),"OCR TEST ONLY",levelId,typeId);processor.process(d.summary().id());
  var result=service.get(d.summary().id());assertEquals("REVIEW",result.summary().state());assertEquals("WINDOWS_OCR",result.pages().get(0).method());assertFalse(result.pages().get(0).text().isBlank());assertEquals(1,result.summary().processedPages());
 }
}
