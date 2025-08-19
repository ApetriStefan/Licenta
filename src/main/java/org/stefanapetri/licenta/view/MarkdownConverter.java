// src\main\java\org\stefanapetri\licenta\view\MarkdownConverter.java
package org.stefanapetri.licenta.view;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class MarkdownConverter {

    private static final Parser parser = Parser.builder().build();
    private static final HtmlRenderer renderer = HtmlRenderer.builder().build();

    /**
     * Converts a Markdown string into an HTML string, suitable for display in a WebView.
     * Includes basic CSS for readability in dark mode.
     * @param markdown The input Markdown text.
     * @return An HTML string.
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            // This case now specifically handles an empty reminder, which is different
            // from the "no selection" placeholder.
            String emptyContent = "### Empty Reminder\n\nThis reminder has no content.";
            Node document = parser.parse(emptyContent);
            String htmlContent = renderer.render(document);
            return buildHtmlWrapper(htmlContent);
        }

        Node document = parser.parse(markdown);
        String htmlContent = renderer.render(document);

        return buildHtmlWrapper(htmlContent);
    }

    private static String buildHtmlWrapper(String htmlContent) {
        // Wrap the HTML content with dark theme CSS
        return "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 14px; line-height: 1.5; margin: 10px; background-color: #45494A; color: #DDDDDD; }" +
                "h1, h2, h3, h4, h5, h6 { margin-top: 1em; margin-bottom: 0.5em; font-weight: bold; color: #EEEEEE; }" +
                "ul, ol { margin-left: 1.5em; padding-left: 0; }" +
                "li { margin-bottom: 0.25em; }" +
                "p { margin-bottom: 0.75em; }" +
                "a { color: #58a6ff; }" + // A pleasant blue for links in dark mode
                "strong { font-weight: bold; }" +
                "em { font-style: italic; }" +
                "blockquote { border-left: 4px solid #666; padding-left: 10px; color: #AAAAAA; margin-left: 0; }" +
                "pre, code { font-family: 'Consolas', 'Courier New', monospace; background-color: #3C3F41; color: #DDDDDD; padding: 2px 4px; border-radius: 4px; border: 1px solid #555; }" +
                "pre { display: block; padding: 10px; overflow-x: auto; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                htmlContent +
                "</body>" +
                "</html>";
    }
}