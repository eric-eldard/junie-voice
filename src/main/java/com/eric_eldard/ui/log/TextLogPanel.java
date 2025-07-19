package com.eric_eldard.ui.log;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

/**
 * Panel for displaying non-transcript log messages using plain text rendering
 */
public class TextLogPanel extends BaseLogPanel
{
    private final JLabel textLabel;

    public TextLogPanel(LogEntry logEntry,  boolean visible)
    {
        super(logEntry, visible);

        // Use JLabel with plain text for non-transcript messages
        textLabel = new JLabel();
        textLabel.setOpaque(false);
        textLabel.setVerticalAlignment(JLabel.TOP);
        textLabel.setHorizontalAlignment(JLabel.LEFT);
        // Increase font size to match HTML messages (12pt ≈ 9px at typical DPI)
        textLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textLabel.setForeground(Color.WHITE);
        textLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        add(textLabel, BorderLayout.CENTER);
        updateContent();
    }

    public void updateContent()
    {
        SwingUtilities.invokeLater(() ->
        {
            // For non-transcript messages, use plain text with wrapping
            String plainText = logEntry.message();
            // Convert to HTML with proper word wrapping and styling
            plainText = "<html><body style='width: 100%; word-wrap: break-word; white-space: pre-wrap;'>" + 
                       plainText.replace("\n", "<br>") + "</body></html>";
            textLabel.setText(plainText);
        });
    }

    public void updateContent(String newMessage)
    {
        SwingUtilities.invokeLater(() ->
        {
            // For non-transcript messages, use plain text with wrapping
            String plainText = newMessage;
            plainText = "<html><body style='width: 100%; word-wrap: break-word; white-space: pre-wrap;'>" + 
                       plainText.replace("\n", "<br>") + "</body></html>";
            textLabel.setText(plainText);
        });
    }
}