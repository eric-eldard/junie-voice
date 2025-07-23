package com.eric_eldard.ui.log;

import com.intellij.ui.components.JBPanel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Base class for log panels containing shared functionality
 */
@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public abstract class BaseLogPanel extends JBPanel
{
    private final LogEntry logEntry;

    private final JTextPane textPane;

    public BaseLogPanel(LogEntry logEntry, boolean visible, boolean allowHtml)
    {
        this.logEntry = logEntry;

        setLayout(new BorderLayout());
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setAlignmentY(Component.TOP_ALIGNMENT);

        // Add margin between log messages
        setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        setVisible(visible);

        // Use JLabel with plain text for non-transcript messages
        textPane = new JTextPane()
        {
            @Override
            public boolean getScrollableTracksViewportWidth()
            {
                return true;
            }
        };

        if (allowHtml)
        {
            textPane.setContentType("text/html");
        }
        else
        {
            textPane.setContentType("text/plain");
        }

        textPane.setOpaque(false);
        textPane.setEditable(false); // for some reason, link listeners only work if this is explicitly set
        textPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        textPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPane.setAlignmentY(Component.TOP_ALIGNMENT);

        add(textPane(), BorderLayout.CENTER);
        updateContent();
    }

    public abstract void updateContent();

    public abstract void updateContent(String newMessage);

    public LogLevel getLogLevel()
    {
        return logEntry.level();
    }

    public void setLogVisible(boolean visible)
    {
        SwingUtilities.invokeLater(() ->
        {
            setVisible(visible);

            if (getParent() != null)
            {
                getParent().revalidate();
                getParent().repaint();
            }
        });
    }

    @Override
    public Dimension getMaximumSize()
    {
        Dimension preferredSize = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, preferredSize.height);
    }
}