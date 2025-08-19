package org.stefanapetri.licenta.view;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class MarkdownConverter {

    private static final Parser parser = Parser.builder().build();
    private static final HtmlRenderer renderer = HtmlRenderer.builder().build();

    /**
     * Converts a Markdown string into an HTML string, suitable for display in a WebView.
     * Includes basic CSS for readability.
     * @param markdown The input Markdown text.
     * @return An HTML string.
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "<html><body><p>No content to display.</p></body></html>";
        }

        Node document = parser.parse(markdown);
        String htmlContent = renderer.render(document);

        // Wrap the HTML content with basic HTML structure and CSS for better readability
        return "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 14px; line-height: 1.5; margin: 10px; }" +
                "h1, h2, h3, h4, h5, h6 { margin-top: 1em; margin-bottom: 0.5em; font-weight: bold; }" +
                "ul, ol { margin-left: 1.5em; padding-left: 0; }" +
                "li { margin-bottom: 0.25em; }" +
                "p { margin-bottom: 0.75em; }" +
                "strong { font-weight: bold; }" +
                "em { font-style: italic; }" +
                "blockquote { border-left: 4px solid #ccc; padding-left: 10px; color: #555; margin-left: 0; }" +
                "pre, code { font-family: 'Consolas', 'Courier New', monospace; background-color: #f0f0f0; padding: 2px 4px; border-radius: 3px; }" +
                "pre { display: block; padding: 10px; overflow-x: auto; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                htmlContent +
                "</body>" +
                "</html>";
    }
}