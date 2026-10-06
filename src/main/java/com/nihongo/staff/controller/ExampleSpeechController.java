package com.nihongo.staff.controller;

import com.nihongo.staff.repository.IExampleRepository;
import com.nihongo.staff.service.VoicevoxExampleSpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff/grammars/{grammarId}/examples")
@RequiredArgsConstructor
public class ExampleSpeechController {
    private final IExampleRepository examples;
    private final VoicevoxExampleSpeechService speech;

    @GetMapping(value = "/{exampleId}/speech", produces = "audio/wav")
    @PreAuthorize("@contentAccess.grammar(#grammarId,authentication)")
    public ResponseEntity<byte[]> speech(@PathVariable Long grammarId, @PathVariable Long exampleId) {
        var example = examples.findById(exampleId)
                .filter(row -> row.getGrammar().getGrammarId().equals(grammarId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy câu ví dụ."));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav"))
                .cacheControl(CacheControl.noStore()).body(speech.speech(example.getNihongo()));
    }
}
