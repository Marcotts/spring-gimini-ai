package info.bmdb.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves the project README.md over HTTP so the UI link "/README.md" works.
 *
 * Strategy:
 * - Try to read README.md from the current working directory (typical during dev).
 * - Fallback to classpath resource if available (e.g., if packaged into resources).
 *
 * Enhancement:
 * - By default renders Markdown to HTML for browser-friendly display (text/html).
 * - Support raw mode via query param `raw=true` or Accept: text/markdown to return the Markdown as-is.
 */
@RestController
public class DocsController {

    private static final MediaType TEXT_MARKDOWN = new MediaType("text", "markdown");
    private static final MediaType TEXT_HTML = new MediaType("text", "html");

    @GetMapping(value = "/README.md")
    public ResponseEntity<String> readme(
            @RequestParam(name = "raw", required = false, defaultValue = "false") boolean raw,
            @RequestHeader(name = "Accept", required = false, defaultValue = "") String accept
    ) {
        String md = loadReadmeMarkdown();
        if (md == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "README.md introuvable côté serveur");
        }

        boolean wantsMarkdown = raw || (accept != null && accept.toLowerCase().contains("text/markdown"));
        if (wantsMarkdown) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType(TEXT_MARKDOWN, StandardCharsets.UTF_8));
            return new ResponseEntity<>(md, headers, HttpStatus.OK);
        }

        // Default: render to HTML
        String htmlBody = renderMarkdownToHtml(md);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(TEXT_HTML, StandardCharsets.UTF_8));
        return new ResponseEntity<>(htmlBody, headers, HttpStatus.OK);
    }

    private String loadReadmeMarkdown() {
        // 1) Try filesystem in working directory
        Path fsPath = Path.of("README.md");
        try {
            if (Files.exists(fsPath)) {
                return Files.readString(fsPath, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}

        // 2) Fallback: classpath (if someone added it under resources)
        try {
            ClassPathResource cpr = new ClassPathResource("README.md");
            if (cpr.exists()) {
                byte[] bytes = cpr.getContentAsByteArray();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}
        return null;
    }

    private String renderMarkdownToHtml(String markdown) {
        // Minimal Markdown -> HTML conversion (headings, code blocks, inline code, bold/italic, links, lists, paragraphs)
        if (markdown == null) markdown = "";
        String md = markdown.replace("\r\n", "\n");

        // Protect fenced code blocks first
        StringBuilder html = new StringBuilder();
        String[] lines = md.split("\n", -1);
        boolean inCode = false;
        StringBuilder codeBuf = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```")) {
                if (!inCode) {
                    inCode = true; codeBuf.setLength(0);
                } else {
                    // close code block
                    html.append("<pre><code>").append(escapeHtml(codeBuf.toString())).append("</code></pre>\n");
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                codeBuf.append(line).append('\n');
                continue;
            }
            // Headings
            if (line.matches("^###### \\S.*")) { html.append("<h6>").append(escapeInline(line.substring(7))).append("</h6>\n"); continue; }
            if (line.matches("^##### \\S.*")) { html.append("<h5>").append(escapeInline(line.substring(6))).append("</h5>\n"); continue; }
            if (line.matches("^#### \\S.*"))  { html.append("<h4>").append(escapeInline(line.substring(5))).append("</h4>\n"); continue; }
            if (line.matches("^### \\S.*"))   { html.append("<h3>").append(escapeInline(line.substring(4))).append("</h3>\n"); continue; }
            if (line.matches("^## \\S.*"))    { html.append("<h2>").append(escapeInline(line.substring(3))).append("</h2>\n"); continue; }
            if (line.matches("^# \\S.*"))     { html.append("<h1>").append(escapeInline(line.substring(2))).append("</h1>\n"); continue; }
            
            // Horizontal rule
            if (line.trim().matches("^(---+|===+)$")) { html.append("<hr/>\n"); continue; }

            // Unordered list items
            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                // gather consecutive list lines
                StringBuilder list = new StringBuilder();
                list.append("<ul>\n");
                int j = i;
                for (; j < lines.length; j++) {
                    String l2 = lines[j];
                    String t = l2.trim();
                    if (t.startsWith("- ") || t.startsWith("* ")) {
                        list.append("  <li>").append(escapeInline(t.substring(2))).append("</li>\n");
                    } else if (t.isEmpty()) {
                        list.append("\n");
                    } else {
                        break;
                    }
                }
                list.append("</ul>\n");
                html.append(list);
                i = j - 1; // adjust outer loop index
                continue;
            }

            if (line.trim().isEmpty()) {
                html.append("\n");
            } else {
                html.append("<p>").append(escapeInline(line)).append("</p>\n");
            }
        }
        if (inCode) {
            // Unclosed code fence: render what we have
            html.append("<pre><code>").append(escapeHtml(codeBuf.toString())).append("</code></pre>\n");
        }

        String body = html.toString();
        return "<!DOCTYPE html>\n" +
                "<html lang=\"fr\">\n<head>\n<meta charset=\"UTF-8\"/>\n<title>README.md</title>\n" +
                "<style>body{font-family:ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,Helvetica Neue,Arial;line-height:1.6;padding:24px;max-width:900px;margin:auto;background:#0f172a;color:#e5e7eb;}" +
                "a{color:#93c5fd;} code,pre{font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, \\\"Liberation Mono\\\", monospace;} pre{background:#0a0f1a;border:1px solid #0f1a2b;padding:12px;border-radius:8px;overflow:auto;} h1,h2,h3{color:#bfdbfe;} hr{border:0;border-top:1px solid #1f2937;margin:24px 0;} table{border-collapse: collapse;} th,td{border:1px solid #1f2937;padding:6px 10px;} p{margin:10px 0;} </style>\n" +
                "</head><body>\n" + body + "\n</body></html>";
    }

    private String escapeInline(String s) {
        // Inline markdown: bold, italic, code, links
        String x = s;
        // inline code `code`
        x = x.replaceAll("`([^`]+)`", "<code>$1</code>");
        // bold **text**
        x = x.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        // italic *text*
        x = x.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<em>$1</em>");
        // links [text](url)
        x = x.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\" target=\"_blank\">$1</a>");
        return escapeHtml(x, false);
    }

    private String escapeHtml(String s) { return escapeHtml(s, true); }
    private String escapeHtml(String s, boolean replaceSpaces) {
        if (s == null) return "";
        String out = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (replaceSpaces) {
            out = out.replace("\t", "    ");
        }
        return out;
    }
}
