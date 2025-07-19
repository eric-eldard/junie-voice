package com.eric_eldard.ui.log;

import lombok.extern.slf4j.Slf4j;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Insets;
import java.util.List;

import com.eric_eldard.VoiceAssistantPanel;
import com.eric_eldard.ui.renderer.UnwrapParagraphRenderer;

/**
 * Panel for displaying transcript log messages using HTML rendering with hyperlink support
 */
@Slf4j
public class HtmlLogPanel extends BaseLogPanel
{
    private final JTextPane editorPane;

    public HtmlLogPanel(LogEntry logEntry, boolean visible)
    {
        super(logEntry, visible);

        // Use JTextPane with HTML for transcript messages
        editorPane = new JTextPane()
        {
            @Override
            public boolean getScrollableTracksViewportWidth()
            {
                return true;
            }
        };
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        editorPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        editorPane.setMargin(new Insets(0, 0, 0, 0));

        // Set alignment for the editor pane to ensure top alignment
        editorPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        editorPane.setAlignmentY(Component.TOP_ALIGNMENT);

        // Add hyperlink listener for transcript messages
        editorPane.addHyperlinkListener(e ->
        {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
            {
                try
                {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                }
                catch (Exception ex)
                {
                    log.error("Failed to open hyperlink: {}", e.getURL(), ex);
                }
            }
        });

        add(editorPane, BorderLayout.CENTER);
        updateContent();
    }

    public void updateContent()
    {
        updateContent(logEntry.message());
    }

    public void updateContent(String message)
    {
        SwingUtilities.invokeLater(() ->
        {
            String html = """
                <html>
                    <body style='font-family: monospace; font-size: 9px; margin: 0; padding: 0;
                    word-wrap: break-word; white-space: pre-wrap; overflow-wrap: break-word;
                    text-align: left; vertical-align: top; line-height: 1.2; width: 100%;
                    max-width: 100%; box-sizing: border-box; color: #ffffff;'>
                """
                + convertMarkdownToHtml(message) +
                """
                    </body>
                </html>
                """;
            editorPane.setText(html);
        });
    }

    public String convertMarkdownToHtml(String markdown)
    {
        if (markdown == null)
        {
            return "";
        }

        try
        {
            // Extract prefix and content from messages that already have emoji prefixes
            String prefix;
            String content;

            if (markdown.startsWith(VoiceAssistantPanel.USER_PREFIX))
            {
                prefix = VoiceAssistantPanel.USER_PREFIX;
                content = markdown.substring(VoiceAssistantPanel.USER_PREFIX.length());
            }
            else if (markdown.startsWith(VoiceAssistantPanel.AGENT_PREFIX))
            {
                prefix = VoiceAssistantPanel.AGENT_PREFIX;
                content = markdown.substring(VoiceAssistantPanel.AGENT_PREFIX.length());
            }
            else
            {
                throw new IllegalArgumentException("Well then who is this from? [" + markdown + ']');
            }

            // Create parser with extensions with tables
            Parser parser = Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .build();

            // Create HTML renderer with custom styling
            HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(List.of(TablesExtension.create()))
                .nodeRendererFactory(UnwrapParagraphRenderer::new)
                .escapeHtml(true)
                .build();

            // Parse and render markdown to HTML
            Node document = parser.parse(content);
            String html = renderer.render(document);

            // Special handling for transcribing placeholder - make it dimmer
            if (content.trim().equals("_transcribing_"))
            {
                html = html.replaceAll("<p>_transcribing_</p>",
                    "<p style='color: #888; font-style: italic;'>transcribing...</p>");
            }

            // Apply dark theme styling to code elements
            html = html.replaceAll("<code>([^<]*)</code>",
                """
                <code style='background-color: #1a1a1a; color: #e8e8e8; padding: 2px 4px; border-radius: 3px;'>$1</code>
                """.trim());

            // Make code blocks full width
            html = html.replace("<pre><code style='", "<pre style='width: 100%'><code style='width: 100%; ");

            // Add styling to headers for consistent spacing
            html = html.replaceAll("<h([1-6])>", "<h$1 style='margin: 8px 0 4px 0;'>");

            // Add styling to lists for consistent spacing and proper display
            html = html.replace("<ul>", "<ul style='padding-left: 20px; display: block;'>");
            html = html.replace("<ol>", "<ol style='padding-left: 20px; display: block;'>");
            html = html.replace("<li>", "<li style='display: list-item;'>");

            // Add styling to tables for better appearance
            html = html.replace("<table>",
                "<table style='border-collapse: collapse; margin: 8px 0;'>");
            html = html.replace("<th>",
                "<th style='border: 1px solid #666; padding: 4px 8px; background-color: #444; color: #fff;'>");
            html = html.replace("<td>",
                "<td style='border: 1px solid #666; padding: 4px 8px;'>");

            // Links will be handled by HyperlinkListener - no modification needed here

            // Add prefix AFTER markdown processing
            // Check if content starts with a block-level element
            // Special case: if it's just a simple paragraph (CommonMark wraps plain text in <p> tags),
            // treat it as inline content for chat messages
            if (startsWithBlockElement(html) && !isSimpleParagraph(html))
            {
                // Block-level elements should start on a new line
                return prefix + "<br>" + html;
            }
            else
            {
                // Inline content should continue on the same line as the prefix
                return prefix + html;
            }
        }
        catch (Exception e)
        {
            log.error("Failed to convert markdown to HTML", e);
            // Fallback to basic HTML escaping if markdown parsing fails
            return convertToBasicHtml(markdown);
        }
    }

    public String convertToBasicHtml(String text)
    {
        if (text == null)
        {
            return "";
        }

        String html = text;

        // Basic HTML escaping
        html = html.replace("&", "&amp;");
        html = html.replace("<", "&lt;");
        html = html.replace(">", "&gt;");
        html = html.replace("\"", "&quot;");

        // Convert line breaks to HTML
        html = html.replace("\n", "<br>");

        return html;
    }

    private boolean startsWithBlockElement(String html)
    {
        if (html == null || html.trim().isEmpty())
        {
            return false;
        }

        String trimmedHtml = html.trim();

        // Check for block-level elements that should start on a new line
        return trimmedHtml.startsWith("<h1") ||
            trimmedHtml.startsWith("<h2") ||
            trimmedHtml.startsWith("<h3") ||
            trimmedHtml.startsWith("<h4") ||
            trimmedHtml.startsWith("<h5") ||
            trimmedHtml.startsWith("<h6") ||
            trimmedHtml.startsWith("<p") ||
            trimmedHtml.startsWith("<ul") ||
            trimmedHtml.startsWith("<ol") ||
            trimmedHtml.startsWith("<pre") ||
            trimmedHtml.startsWith("<table") ||
            trimmedHtml.startsWith("<blockquote") ||
            trimmedHtml.startsWith("<div");
    }

    private boolean isSimpleParagraph(String html)
    {
        if (html == null || html.trim().isEmpty())
        {
            return false;
        }

        String trimmedHtml = html.trim();

        // Check if it's a simple paragraph: starts with <p> and ends with </p>
        // and doesn't contain other block elements inside
        if (trimmedHtml.startsWith("<p>") && trimmedHtml.endsWith("</p>"))
        {
            // Extract content between <p> and </p>
            String content = trimmedHtml.substring(3, trimmedHtml.length() - 4);

            // Check if the content contains any block-level elements
            // If it contains other block elements, it's not a simple paragraph
            return !content.contains("<h1") && !content.contains("<h2") && !content.contains("<h3") &&
                !content.contains("<h4") && !content.contains("<h5") && !content.contains("<h6") &&
                !content.contains("<ul") && !content.contains("<ol") && !content.contains("<li") &&
                !content.contains("<pre") && !content.contains("<table") && !content.contains("<blockquote") &&
                !content.contains("<div") && !content.contains("<p>");
        }

        return false;
    }
}