package com.nihongo.staff.security;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/** HTML used by the Japanese rich-text editors; executable content is never returned to v-html. */
public final class ContentHtml {
    private static final Safelist ALLOWED = Safelist.relaxed()
            .addTags("ruby", "rt", "rp", "span", "u")
            .addAttributes("span", "class")
            .addAttributes("ruby", "class");

    private ContentHtml() {}

    public static String clean(String html) {
        if (html == null) return null;
        return Jsoup.clean(html, "", ALLOWED, new Document.OutputSettings().prettyPrint(false));
    }
}
