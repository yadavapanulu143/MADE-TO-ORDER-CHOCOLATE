package com.choco.notification.application.template;

import java.util.Map;

/**
 * The finished text of a message: a subject and a body, both already substituted.
 *
 * <p>Rendering produces this once, before the notification is stored, so a retry re-sends exactly what
 * the customer would have received and a later template change cannot rewrite history. The stored body
 * is what actually went out, not a recipe for reproducing it.
 */
public record MessageContent(String subject, String textBody, String htmlBody) {

    public MessageContent {
        if (subject == null) {
            throw new IllegalArgumentException("subject is required");
        }
        if (textBody == null || textBody.isBlank()) {
            throw new IllegalArgumentException("textBody is required");
        }
    }

    /**
     * Renders a template, substituting {@code {{key}}} placeholders.
     *
     * <p>A missing key is a programming error, not a customer-visible one: it would ship a message with
     * a literal {@code {{customerName}}} in it. Rendering therefore fails loudly here rather than
     * producing a broken message, and the failure is caught by the caller as a permanent one — no
     * number of retries will invent the missing value.
     *
     * <p>Both bodies are produced from the same variable map, so the two never disagree.
     */
    public static MessageContent render(String subjectTemplate, String textTemplate, String htmlTemplate,
                                        Map<String, String> variables) {
        return new MessageContent(
                substitute(subjectTemplate, variables, false),
                substitute(textTemplate, variables, false),
                substitute(htmlTemplate, variables, true));
    }

    /**
     * Substitutes {@code {{key}}} placeholders.
     *
     * <p>When {@code escapeHtml} is set the value is HTML-escaped before insertion. That is what makes
     * the HTML body safe: a customer may put markup in their own display name, and a delivery note can
     * contain anything, so a value interpolated into the HTML part must not be able to introduce a tag
     * or a script. The subject and the text body are not HTML, so they take the value unescaped —
     * escaping there would show a customer {@code &amp;#39;} in their own email.
     */
    static String substitute(String template, Map<String, String> variables, boolean escapeHtml) {
        if (template == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(template.length() + 64);
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf("{{", i);
            if (open < 0) {
                out.append(template, i, template.length());
                break;
            }
            int close = template.indexOf("}}", open + 2);
            if (close < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, open);
            String key = template.substring(open + 2, close).trim();
            String value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException("No value supplied for template variable '" + key + "'");
            }
            out.append(escapeHtml ? escape(value) : value);
            i = close + 2;
        }
        return out.toString();
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
