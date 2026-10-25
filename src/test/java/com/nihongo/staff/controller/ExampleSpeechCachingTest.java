package com.nihongo.staff.controller;

import com.nihongo.staff.model.Example;
import com.nihongo.staff.model.Grammar;
import com.nihongo.staff.repository.IExampleRepository;
import com.nihongo.staff.service.VoicevoxExampleSpeechService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExampleSpeechCachingTest {
    @Test void unchangedSentenceIsRevalidatedWithoutSendingTheAudioAgain() {
        var examples = mock(IExampleRepository.class);
        var speech = mock(VoicevoxExampleSpeechService.class);
        var grammar = mock(Grammar.class);
        when(grammar.getGrammarId()).thenReturn(1L);
        var example = mock(Example.class);
        when(example.getGrammar()).thenReturn(grammar);
        when(example.getNihongo()).thenReturn("日本語を学ぶ。");
        when(examples.findById(5L)).thenReturn(Optional.of(example));
        when(speech.etag("日本語を学ぶ。")).thenReturn("\"abc\"");
        when(speech.speech("日本語を学ぶ。")).thenReturn(new byte[]{1, 2, 3});
        var controller = new ExampleSpeechController(examples, speech);

        var first = controller.speech(1L, 5L, null);
        assertEquals(200, first.getStatusCode().value());
        assertEquals("\"abc\"", first.getHeaders().getETag());
        assertTrue(first.getHeaders().getCacheControl().contains("private"));
        assertTrue(first.getHeaders().getCacheControl().contains("no-cache"));

        var again = controller.speech(1L, 5L, "\"abc\"");
        assertEquals(304, again.getStatusCode().value());
        assertNull(again.getBody());
        verify(speech, times(1)).speech(anyString());

        assertEquals(200, controller.speech(1L, 5L, "\"old\"").getStatusCode().value());
    }
}
