package com.nihongo.staff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihongo.staff.service.imports.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in single-page smoke check using the user's configured provider. Not part of normal CI. */
@EnabledIfSystemProperty(named="book-import.live-test",matches="true")
class BookImportGeminiLiveTest {
 @Test void extractsARealPageIntoATranslatedDraft() throws Exception {
  var properties=new Properties();try(var in=Files.newBufferedReader(Path.of("../.local/gemini.properties"))) {properties.load(in);}
  var mapper=new ObjectMapper();var storage=new BookImportStorage("target/generic-live-test");String id=UUID.randomUUID().toString();
  Files.createDirectories(storage.image(id,1).getParent());
  try(var in=getClass().getResourceAsStream("/imports/try-n3/pages/15.png")) {assertNotNull(in);Files.copy(in,storage.image(id,1));}
  var gemini=new BookImportGemini(mapper,storage,properties.getProperty("gemini.local-api-key",""),"gemini-3.1-flash-lite","https://generativelanguage.googleapis.com/v1beta");
  var content=new BookImportContent("TRY N3 - one page smoke test",1L,1L,"",List.of(),List.of());
  var batch=gemini.extract(id,1,1,content,List.of(new BookImportService.Page(1,"IMAGE","")));
  assertFalse(batch.lessons().isEmpty());assertTrue(batch.lessons().stream().anyMatch(l->l.reading().contains("富士山")));
  assertTrue(batch.lessons().stream().flatMap(l->l.grammars().stream()).flatMap(g->g.examples().stream()).anyMatch(e->!e.nihongo().isBlank() && !e.vietnamese().isBlank()));
  assertTrue(batch.lessons().stream().allMatch(l->l.firstPage()==1 && l.lastPage()==1));
  assertTrue(batch.lessons().stream().flatMap(l->l.exercises().stream()).allMatch(q->q.correctAnswer().isBlank()));
  Path result=Path.of("../.local/startup-check/book-import-ai-live.json");mapper.writerWithDefaultPrettyPrinter().writeValue(result.toFile(),batch);
 }
}
