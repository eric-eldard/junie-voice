package com.eric_eldard.ui.log;

import lombok.extern.slf4j.Slf4j;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Insets;

/**
 * Panel for displaying transcript log messages using HTML rendering with hyperlink support
 */
@Slf4j
public class HtmlLogPanel extends BaseLogPanel
{
    private final JTextPane editorPane;

    public HtmlLogPanel(LogEntry logEntry, LogPanelDependencies dependencies)
    {
        super(logEntry, dependencies);

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
        SwingUtilities.invokeLater(() ->
        {
            // For transcript messages, use HTML rendering
            String html = """
                <html>
                    <body style='font-family: monospace; font-size: 9px; margin: 0; padding: 0;
                    word-wrap: break-word; white-space: pre-wrap; overflow-wrap: break-word;
                    text-align: left; vertical-align: top; line-height: 1.2; width: 100%;
                    max-width: 100%; box-sizing: border-box; color: #ffffff;'>
                """
                + dependencies.convertMarkdownToHtml(logEntry.message()) +
                """
                    </body>
                </html>
                """;
            editorPane.setText(html);
        });
    }

    public void updateContent(String newMessage)
    {
        SwingUtilities.invokeLater(() ->
        {
            // For transcript messages, use HTML rendering
            String html = dependencies.convertMarkdownToHtml(newMessage);
            String fullHtml = """
                <html>
                    <body style='font-family: monospace; font-size: 9px; margin: 0; padding: 0;
                    word-wrap: break-word; white-space: pre-wrap; overflow-wrap: break-word;
                    text-align: left; vertical-align: top; line-height: 1.2; width: 100%;
                    max-width: 100%; box-sizing: border-box; color: #ffffff;'>
                """
                + html +
                """
                    </body>
                </html>
                """;
            editorPane.setText(fullHtml);
        });
    }

    public String getTextContent()
    {
        return extractTextFromTranscript(logEntry.message());
    }

    private String extractTextFromTranscript(String message)
    {
        // Remove markdown formatting for plain text extraction
        return message.replaceAll("\\*\\*(.*?)\\*\\*", "$1")  // Bold
                     .replaceAll("\\*(.*?)\\*", "$1")        // Italic
                     .replaceAll("`(.*?)`", "$1")            // Code
                     .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1"); // Links
    }
}