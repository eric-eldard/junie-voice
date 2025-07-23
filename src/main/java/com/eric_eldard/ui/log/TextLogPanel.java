package com.eric_eldard.ui.log;

import javax.swing.SwingUtilities;
import java.awt.Font;

/**
 * Panel for displaying non-transcript log messages using plain text rendering
 */
public class TextLogPanel extends BaseLogPanel
{
    public TextLogPanel(LogEntry logEntry,  boolean visible)
    {
        super(logEntry, visible, false);
        textPane().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
    }

    public void updateContent()
    {
       updateContent(logEntry().message());
    }

    public void updateContent(String newText)
    {
        SwingUtilities.invokeLater(() -> textPane().setText(newText));
    }
}