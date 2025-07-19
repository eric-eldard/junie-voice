package com.eric_eldard.ui.log;

import com.intellij.ui.components.JBPanel;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Base class for log panels containing shared functionality
 */
public abstract class BaseLogPanel extends JBPanel
{
    protected final LogEntry logEntry;
    protected final LogPanelDependencies dependencies;

    public BaseLogPanel(LogEntry logEntry, LogPanelDependencies dependencies)
    {
        this.logEntry = logEntry;
        this.dependencies = dependencies;
        
        setLayout(new BorderLayout());
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setAlignmentY(Component.TOP_ALIGNMENT);

        // Add margin between log messages
        setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        // Set initial visibility based on current log level
        setVisible(dependencies.shouldShowLogLevel(logEntry.level()));
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
            boolean shouldShow = visible && dependencies.shouldShowLogLevel(logEntry.level());
            setVisible(shouldShow);
            
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

    // TODO This interface represents unnecessary tight coupling
    //  Functionality that should be a part of this class is in VoiceAssistantPanel
    public interface LogPanelDependencies
    {
        boolean shouldShowLogLevel(LogLevel level);
        boolean isChatStyleMessage(String message);
    }
}