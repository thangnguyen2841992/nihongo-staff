package com.nihongo.staff;

import com.nihongo.staff.security.ContentHtml;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContentHtmlTest {
    @Test void removesStoredScriptsHandlersAndExecutableUrls() {
        String clean = ContentHtml.clean("<script>alert(1)</script><img src=x onerror=alert(2)>"
                + "<a href='javascript:alert(3)'>link</a><span style='background:url(javascript:alert(4))'>text</span>");
        assertFalse(clean.contains("script"));
        assertFalse(clean.contains("onerror"));
        assertFalse(clean.contains("javascript"));
        assertFalse(clean.contains("style="));
        assertTrue(clean.contains("text"));
    }

    @Test void preservesFuriganaUnderlinedKeywordsAndFormatting() {
        String html = "<p><ruby>日本<rt>にほん</rt></ruby><u>学校</u><strong>bold</strong><br></p>";
        assertEquals(html, ContentHtml.clean(html));
        assertNull(ContentHtml.clean(null));
    }
}
