package com.eric_eldard;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.eric_eldard.ui.log.BaseLogPanel;
import com.eric_eldard.ui.log.HtmlLogPanel;
import com.eric_eldard.ui.log.LogEntry;
import com.eric_eldard.ui.log.LogLevel;
import com.eric_eldard.ui.log.TextLogPanel;
import com.eric_eldard.voice.OpenAIFilesService;
import com.eric_eldard.voice.OpenAIResponsesService;
import com.eric_eldard.voice.VoiceService;

/**
 * Panel component for the Voice Assistant tool window
 */
@Slf4j
public class VoiceAssistantPanel implements VoiceService.VoiceServiceListener
{
    // Message prefix constants
    public static final String USER_PREFIX = "👤 User: ";
    public static final String AGENT_PREFIX = "🤖 Agent: ";

    private JBPanel mainPanel;

    private JButton connectButton;

    private JButton disconnectButton;

    private JButton clearLogButton;

    private JButton copyLogButton;

    private JToggleButton micToggleButton;

    private JToggleButton speakerToggleButton;

    private JBLabel statusLabel;

    private JBPanel logContainer;

    private JBScrollPane logScrollPane;

    private JProgressBar volumeMeter;

    private JComboBox<LogLevel> logLevelComboBox;

    private JComboBox<String> modelComboBox;

    private JComboBox<String> voiceComboBox;

    private JBTextArea textInputArea;

    private JButton submitButton;

    private JButton uploadButton;

    private JButton pasteButton;

    private VoiceService voiceService;

    private OpenAIResponsesService responsesService;

    private OpenAIFilesService filesService;

    private boolean initialized = false;

    private ScheduledExecutorService volumeUpdateExecutor;

    // Speaker state tracking
    private boolean speakerMuted = false;

    // Track AI response state for microphone muting
    private volatile boolean aiResponseActive = false;

    // Track microphone state before AI response for proper restoration
    private volatile boolean micMutedBeforeAIResponse = false;

    // Track if user has used push-to-interrupt (disregards original mic state)
    private volatile boolean userInterruptedAI = false;

    // Log storage and filtering
    private final List<LogEntry> logEntries = new ArrayList<>();

    private final List<BaseLogPanel> logPanels = new ArrayList<>();

    private LogLevel currentLogLevel = LogLevel.INFO;

    // Streaming text state
    private final StringBuilder currentStreamingMessage = new StringBuilder();
    private boolean isStreamingActive = false;
    private int currentStreamingLogIndex = -1;

    // User transcript placeholder state
    private boolean waitingForUserTranscript = false;
    private int userPlaceholderLogIndex = -1;

    // File dialog folder memory
    private static java.io.File lastAccessedDirectory = null;

    public VoiceAssistantPanel()
    {
        initializeComponents();
        setupEventHandlers();
        checkForAutoConnect();
    }

    private void initializeComponents()
    {
        mainPanel = new JBPanel(new BorderLayout());

        // Top panel with API key input
        JBPanel topPanel = new JBPanel(new BorderLayout());

        // Connection panel with proper layout for centering instruction text
        JBPanel connectionPanel = new JBPanel(new BorderLayout());
        connectionPanel.setBorder(BorderFactory.createTitledBorder("OpenAI Configuration"));

        // Top part with buttons and dropdown
        JBPanel connectionControlsPanel = new JBPanel(new BorderLayout());

        // Left side with Connect/Disconnect buttons
        JBPanel leftButtonPanel = new JBPanel(new FlowLayout(FlowLayout.LEFT));
        connectButton = new JButton("Connect");
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        leftButtonPanel.add(connectButton);
        leftButtonPanel.add(disconnectButton);

        // Right side with Model and Voice selection dropdowns
        JBPanel rightModelPanel = new JBPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel modelLabel = new JLabel("Model:");
        modelComboBox = new JComboBox<>(new String[]{"gpt-4o-mini-realtime-preview", "gpt-4o-realtime-preview"});
        modelComboBox.setSelectedItem("gpt-4o-mini-realtime-preview");
        modelComboBox.setPreferredSize(new Dimension(220, 25));

        JLabel voiceLabel = new JLabel("Voice:");
        voiceComboBox = new JComboBox<>(new String[]{
            "alloy", "ash", "ballad", "coral",
            "echo", "sage", "shimmer", "verse"
        });
        voiceComboBox.setSelectedItem("alloy"); // Set default to alloy
        voiceComboBox.setPreferredSize(new Dimension(100, 25));

        rightModelPanel.add(modelLabel);
        rightModelPanel.add(modelComboBox);
        rightModelPanel.add(voiceLabel);
        rightModelPanel.add(voiceComboBox);

        connectionControlsPanel.add(leftButtonPanel, BorderLayout.WEST);
        connectionControlsPanel.add(rightModelPanel, BorderLayout.EAST);

        // Bottom part with centered instruction text
        JBPanel instructionPanel = new JBPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel instructionLabel = new JLabel(
            "Click Connect to authenticate with OpenAI, then use the microphone or text input for conversations.");
        instructionLabel.setFont(instructionLabel.getFont().deriveFont(Font.ITALIC));
        instructionLabel.setForeground(Color.GRAY);
        instructionPanel.add(instructionLabel);

        connectionPanel.add(connectionControlsPanel, BorderLayout.NORTH);
        connectionPanel.add(instructionPanel, BorderLayout.SOUTH);

        topPanel.add(connectionPanel, BorderLayout.NORTH);

        // Status panel - centered above the control buttons
        JBPanel statusPanel = new JBPanel(new FlowLayout(FlowLayout.CENTER));
        statusLabel = new JBLabel("Click Connect to get started");
        updateStatus("Click Connect to get started", Color.GRAY);
        statusPanel.add(statusLabel);

        // Control panel with microphone toggle and other controls
        JBPanel controlPanel = new JBPanel(new BorderLayout());

        micToggleButton = new JToggleButton("🎤🚫");
        micToggleButton.setEnabled(false); // Muted by default

        speakerToggleButton = new JToggleButton("🔊");
        speakerToggleButton.setEnabled(true); // Not muted by default
        speakerToggleButton.setSelected(false);

        // Volume meter
        JLabel volumeLabel = new JLabel("Mic Volume:");
        volumeMeter = new JProgressBar(0, 100);
        volumeMeter.setStringPainted(true);
        volumeMeter.setString("0%");
        volumeMeter.setValue(0);
        volumeMeter.setForeground(Color.GREEN);
        volumeMeter.setPreferredSize(new Dimension(100, 20));

        // Log level dropdown
        JLabel logLevelLabel = new JLabel("Log Level:");
        logLevelComboBox = new JComboBox<>(LogLevel.values());
        logLevelComboBox.setSelectedItem(LogLevel.INFO);
        logLevelComboBox.setPreferredSize(new Dimension(80, 25));

        // Left panel - Mic Volume (left aligned)
        JBPanel leftControlPanel = new JBPanel(new FlowLayout(FlowLayout.LEFT));
        leftControlPanel.setPreferredSize(new Dimension(200, 30));
        leftControlPanel.add(volumeLabel);
        leftControlPanel.add(volumeMeter);

        // Center panel - Mic and Speaker buttons (center aligned)
        JBPanel centerControlPanel = new JBPanel(new FlowLayout(FlowLayout.CENTER));
        centerControlPanel.add(micToggleButton);
        centerControlPanel.add(speakerToggleButton);

        // Right panel - Log Level picker (right aligned)
        JBPanel rightControlPanel = new JBPanel(new FlowLayout(FlowLayout.RIGHT));
        rightControlPanel.setPreferredSize(new Dimension(200, 30));
        rightControlPanel.add(logLevelLabel);
        rightControlPanel.add(logLevelComboBox);

        controlPanel.add(leftControlPanel, BorderLayout.WEST);
        controlPanel.add(centerControlPanel, BorderLayout.CENTER);
        controlPanel.add(rightControlPanel, BorderLayout.EAST);

        // Add status panel and control panel directly to top panel
        topPanel.add(statusPanel, BorderLayout.CENTER);
        topPanel.add(controlPanel, BorderLayout.SOUTH);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel with response area
        JBPanel centerPanel = new JBPanel(new BorderLayout());

        // Log panel with titled border containing the actual log content
        JBPanel logPanel = new JBPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Log"));

        // Create container for individual log panels
        logContainer = new JBPanel();
        logContainer.setLayout(new BoxLayout(logContainer, BoxLayout.Y_AXIS));
        logContainer.setOpaque(false);

        // Add glue at the end to push content to the top
        logContainer.add(Box.createVerticalGlue());

        // Create scroll pane for the log container
        logScrollPane = new JBScrollPane(logContainer);
        logScrollPane.setPreferredSize(new Dimension(500, 300));
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        logScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Add the scroll pane to the center of the log panel
        logPanel.add(logScrollPane, BorderLayout.CENTER);

        // Text input components at the bottom of the log panel
        JBPanel textInputPanel = new JBPanel(new BorderLayout());

        // Multi-line text area with scroll pane
        textInputArea = new JBTextArea(3, 50);
        textInputArea.setWrapStyleWord(true);
        textInputArea.setLineWrap(true);
        textInputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        textInputArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // Add 5px padding all around
        JScrollPane textScrollPane = new JScrollPane(textInputArea);
        textScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        textScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Copy Log button
        copyLogButton = new JButton("Copy Log");
        copyLogButton.setPreferredSize(new Dimension(80, 32));

        // Clear Log button
        clearLogButton = new JButton("Clear Log");
        clearLogButton.setPreferredSize(new Dimension(80, 32));

        // Upload button
        uploadButton = new JButton("📎");
        uploadButton.setPreferredSize(new Dimension(40, 32));
        uploadButton.setEnabled(false); // Disabled until connected
        uploadButton.setToolTipText("Upload image files");

        // Paste button
        pasteButton = new JButton("📋");
        pasteButton.setPreferredSize(new Dimension(40, 32));
        pasteButton.setEnabled(false); // Disabled until connected
        pasteButton.setToolTipText("Paste image from clipboard");

        // Submit button
        submitButton = new JButton("Submit");
        submitButton.setPreferredSize(new Dimension(80, 32));
        submitButton.setEnabled(false); // Disabled until connected

        // Left buttons (Copy Log, Clear Log)
        JBPanel buttonPanel1 = new JBPanel();
        buttonPanel1.setLayout(new BoxLayout(buttonPanel1, BoxLayout.Y_AXIS));
        buttonPanel1.add(copyLogButton);
        buttonPanel1.add(clearLogButton);

        // Right side button panel with new layout
        JBPanel buttonPanel2 = new JBPanel(new BorderLayout());

        // Submit button on top, full width
        buttonPanel2.add(submitButton, BorderLayout.NORTH);

        // Bottom row with upload (left) and paste (right) buttons
        JBPanel bottomButtonPanel = new JBPanel(new BorderLayout());
        bottomButtonPanel.add(uploadButton, BorderLayout.WEST);
        bottomButtonPanel.add(pasteButton, BorderLayout.EAST);
        buttonPanel2.add(bottomButtonPanel, BorderLayout.SOUTH);

        textInputPanel.add(buttonPanel1, BorderLayout.WEST);
        textInputPanel.add(textScrollPane, BorderLayout.CENTER);
        textInputPanel.add(buttonPanel2, BorderLayout.EAST);

        // Add text input panel to the bottom of the log panel
        logPanel.add(textInputPanel, BorderLayout.SOUTH);

        centerPanel.add(logPanel, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
    }

    private void setupEventHandlers()
    {
        connectButton.addActionListener(e -> connectToOpenAI());

        disconnectButton.addActionListener(e -> disconnectFromOpenAI());

        micToggleButton.addActionListener(e -> toggleMicrophone());

        speakerToggleButton.addActionListener(e -> toggleSpeaker());

        // Submit button handler
        submitButton.addActionListener(e -> submitTextMessage());

        // Copy log button handler
        copyLogButton.addActionListener(e -> copyLog());

        // Clear log button handler
        clearLogButton.addActionListener(e -> clearLog());

        // Upload button handler
        uploadButton.addActionListener(e -> openFileDialog());

        // Paste button handler
        pasteButton.addActionListener(e -> handleClipboardPaste());

        // Text input area key handler for Enter/Shift+Enter
        textInputArea.addKeyListener(new java.awt.event.KeyAdapter()
        {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e)
            {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
                {
                    if (e.isShiftDown())
                    {
                        // Shift+Enter: explicitly add a newline
                        e.consume(); // Prevent default behavior
                        int caretPosition = textInputArea.getCaretPosition();
                        String currentText = textInputArea.getText();
                        String newText =
                            currentText.substring(0, caretPosition) + '\n' + currentText.substring(caretPosition);
                        textInputArea.setText(newText);
                        textInputArea.setCaretPosition(caretPosition + 1);
                    }
                    else
                    {
                        // Enter without shift: submit the message
                        e.consume(); // Prevent the newline from being added
                        submitTextMessage();
                    }
                }
            }
        });

        // Log level dropdown handler
        logLevelComboBox.addActionListener(e ->
        {
            LogLevel selectedLevel = (LogLevel) logLevelComboBox.getSelectedItem();
            if (selectedLevel != null)
            {
                currentLogLevel = selectedLevel;
                refreshLogDisplay();
            }
        });

        // Model selection dropdown handler
        modelComboBox.addActionListener(e ->
        {
            String selectedModel = (String) modelComboBox.getSelectedItem();
            if (selectedModel != null)
            {
                log.info("Model selection changed to: {}", selectedModel);

                // If already connected, reconnect with the new model
                if (initialized && voiceService != null && voiceService.isConnected())
                {
                    // Get the current API key from environment or show modal again
                    String envApiKey = System.getProperty("openai.api.key", System.getenv("OPENAI_API_KEY"));
                    if (envApiKey != null && !envApiKey.trim().isEmpty())
                    {
                        addLogEntry(LogLevel.INFO, "🔄 Switching model to: " + selectedModel + '\n');
                        connectWithApiKey(envApiKey);
                    }
                    else
                    {
                        addLogEntry(LogLevel.INFO,
                            "🔄 Model changed to: " + selectedModel + ". Reconnect to apply changes.\n");
                    }
                }
            }
        });

        // Voice selection dropdown handler
        voiceComboBox.addActionListener(e ->
        {
            String selectedVoice = (String) voiceComboBox.getSelectedItem();
            if (selectedVoice != null)
            {
                log.info("Voice selection changed to: {}", selectedVoice);

                // If already connected, reconnect with the new voice
                if (initialized && voiceService != null && voiceService.isConnected())
                {
                    // Get the current API key from environment or show modal again
                    String envApiKey = System.getProperty("openai.api.key", System.getenv("OPENAI_API_KEY"));
                    if (envApiKey != null && !envApiKey.trim().isEmpty())
                    {
                        addLogEntry(LogLevel.INFO, "🔄 Switching voice to: " + selectedVoice + '\n');
                        connectWithApiKey(envApiKey);
                    }
                    else
                    {
                        addLogEntry(LogLevel.INFO,
                            "🔄 Voice changed to: " + selectedVoice + ". Reconnect to apply changes.\n");
                    }
                }
            }
        });
    }

    private void checkForAutoConnect()
    {
        // Try to load API key from system properties or environment variables
        String apiKey = System.getProperty("openai.api.key", System.getenv("OPENAI_API_KEY"));
        if (apiKey != null && !apiKey.trim().isEmpty())
        {
            // Auto-connect if API key is available from environment
            SwingUtilities.invokeLater(() -> connectWithApiKey(apiKey));
        }
    }

    public void addLogEntry(LogLevel level, String message)
    {
        synchronized (logEntries)
        {
            LogEntry entry = new LogEntry(level, message);
            logEntries.add(entry);
            boolean visible = shouldShowLogLevel(level);

            // Create appropriate panel type based on message content
            BaseLogPanel logPanel;
            if (isChatStyleMessage(message))
            {
                logPanel = new HtmlLogPanel(entry, visible);
            }
            else
            {
                logPanel = new TextLogPanel(entry, visible);
            }
            logPanels.add(logPanel);

            // Add to container on EDT (insert before the glue component)
            SwingUtilities.invokeLater(() ->
            {
                // Insert before the last component (which is the glue)
                int insertIndex = logContainer.getComponentCount() - 1;
                logContainer.add(logPanel, insertIndex);
                logContainer.revalidate();
                logContainer.repaint();

                // Only scroll to bottom if already at bottom
                scrollToBottomIfNeeded();
            });
        }
        refreshLogDisplay();
    }

    private void refreshLogDisplay()
    {
        SwingUtilities.invokeLater(() ->
        {
            synchronized (logEntries)
            {
                // Update visibility of existing log panels based on current log level
                for (BaseLogPanel logPanel : logPanels)
                {
                    boolean shouldShow = shouldShowLogLevel(logPanel.getLogLevel());
                    logPanel.setLogVisible(shouldShow);
                }

                // Revalidate and repaint the container to reflect visibility changes
                logContainer.revalidate();
                logContainer.repaint();

                // Always scroll to bottom when changing log views
                scrollToBottom();
            }
        });
    }

    public boolean shouldShowLogLevel(LogLevel level)
    {
        switch (currentLogLevel)
        {
            case TRACE:
                // TRACE shows all messages
                return true;
            case DEBUG:
                // DEBUG shows DEBUG and INFO messages
                return level == LogLevel.DEBUG || level == LogLevel.INFO;
            case INFO:
                // INFO shows only INFO messages
                return level == LogLevel.INFO;
            default:
                return false;
        }
    }

    private boolean isScrolledToBottom()
    {
        JScrollBar verticalScrollBar = logScrollPane.getVerticalScrollBar();
        int currentValue = verticalScrollBar.getValue();
        int maximum = verticalScrollBar.getMaximum();
        int extent = verticalScrollBar.getVisibleAmount();

        // Consider "at bottom" if within a larger threshold to handle fast incoming messages
        // Use the height of approximately one message to allow for scroll lag
        int messageHeight = 50; // Approximate height of one message
        return currentValue + extent >= maximum - messageHeight;
    }

    private void scrollToBottomIfNeeded()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (isScrolledToBottom())
            {
                JScrollBar verticalScrollBar = logScrollPane.getVerticalScrollBar();
                verticalScrollBar.setValue(verticalScrollBar.getMaximum());
            }
        });
    }

    private void scrollToBottom()
    {
        SwingUtilities.invokeLater(() ->
        {
            JScrollBar verticalScrollBar = logScrollPane.getVerticalScrollBar();
            verticalScrollBar.setValue(verticalScrollBar.getMaximum());
        });
    }

    public boolean isChatStyleMessage(String message)
    {
        if (message == null)
        {
            return false;
        }

        // Chat-style messages are user and agent transcripts (now identified by emoji prefixes)
        return message.startsWith(USER_PREFIX) || message.startsWith(AGENT_PREFIX);
    }

    private void connectToOpenAI()
    {
        // First check if API key is available from environment variables
        String envApiKey = System.getProperty("openai.api.key", System.getenv("OPENAI_API_KEY"));
        if (envApiKey != null && !envApiKey.trim().isEmpty())
        {
            connectWithApiKey(envApiKey);
            return;
        }

        // Show modal dialog to get API key from user
        showApiKeyModal();
    }

    private void showApiKeyModal()
    {
        JDialog modal = new JDialog((Frame) SwingUtilities.getWindowAncestor(mainPanel), "Enter API Key", true);
        modal.setLayout(new BorderLayout());
        modal.setSize(400, 150);
        modal.setLocationRelativeTo(mainPanel);

        JBPanel contentPanel = new JBPanel(new FlowLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("OpenAI API Key:");
        JPasswordField apiKeyField = new JPasswordField(25);

        contentPanel.add(label);
        contentPanel.add(apiKeyField);

        JBPanel buttonPanel = new JBPanel(new FlowLayout());
        JButton cancelButton = new JButton("Cancel");
        JButton okButton = new JButton("OK");

        cancelButton.addActionListener(e -> modal.dispose());

        okButton.addActionListener(e ->
        {
            String apiKey = new String(apiKeyField.getPassword()).trim();
            if (!apiKey.isEmpty())
            {
                modal.dispose();
                connectWithApiKey(apiKey);
            }
            else
            {
                JOptionPane.showMessageDialog(modal, "Please enter a valid API key", "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });

        // Allow Enter key to trigger OK
        apiKeyField.addActionListener(e -> okButton.doClick());

        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        modal.add(contentPanel, BorderLayout.CENTER);
        modal.add(buttonPanel, BorderLayout.SOUTH);
        modal.setVisible(true);
    }

    private void connectWithApiKey(String apiKey)
    {
        updateStatus("Connecting...", new Color(100, 149, 237));
        connectButton.setEnabled(false);

        // Initialize voice service in background thread
        SwingUtilities.invokeLater(() ->
        {
            if (voiceService != null)
            {
                voiceService.shutdown();
            }

            String selectedModel = (String) modelComboBox.getSelectedItem();
            String selectedVoice = (String) voiceComboBox.getSelectedItem();
            voiceService = new VoiceService(apiKey, selectedModel, selectedVoice);
            voiceService.setServiceListener(this);

            // Initialize OpenAI Responses Service for code detection
            responsesService = new OpenAIResponsesService(apiKey);

            // Initialize OpenAI Files Service for file uploads
            filesService = new OpenAIFilesService(apiKey);

            voiceService.initialize().thenAccept(success ->
            {
                SwingUtilities.invokeLater(() ->
                {
                    initialized = success;
                    connectButton.setEnabled(true);

                    if (success)
                    {
                        updateStatus("Connected - Ready to use voice", Color.GREEN);
                        micToggleButton.setEnabled(true);
                        submitButton.setEnabled(true);
                        uploadButton.setEnabled(true);
                        pasteButton.setEnabled(true);
                        connectButton.setEnabled(false);
                        disconnectButton.setEnabled(true);
                        addLogEntry(LogLevel.INFO, "✅ Successfully connected to OpenAI Realtime API\n");
                    }
                    else
                    {
                        addLogEntry(LogLevel.INFO,
                            "❌ Failed to connect to OpenAI Realtime API. Please check your API key.\n");
                    }
                });
            });
        });
    }

    private void disconnectFromOpenAI()
    {
        if (voiceService != null)
        {
            voiceService.shutdown();
            voiceService = null;
        }

        responsesService = null;
        filesService = null;

        initialized = false;
        updateStatus("Disconnected", Color.GRAY);
        micToggleButton.setEnabled(false);
        submitButton.setEnabled(false);
        uploadButton.setEnabled(false);
        pasteButton.setEnabled(false);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);

        addLogEntry(LogLevel.INFO, "🔌 Disconnected from OpenAI Realtime API\n");
    }

    private void toggleMicrophone()
    {
        if (!initialized || voiceService == null)
        {
            return;
        }

        if (voiceService.isRecording())
        {
            voiceService.stopVoiceSession();
        }
        else
        {
            // Allow starting voice session even during AI response (for interruption)
            voiceService.startVoiceSession();

            // If we're interrupting an AI response, log it and mark as interrupted
            if (aiResponseActive)
            {
                userInterruptedAI = true;
                speakerMuted = true;
                updateSpeakerButton();

                // Mute the output immediately when user starts talking after push-to-interrupt
                if (voiceService != null && voiceService.getAudioService() != null && !voiceService.getAudioService().isAudioMuted())
                {
                    voiceService.getAudioService().setAudioMuted(true);
                    addLogEntry(LogLevel.DEBUG, "🔇 Output muted during user interruption\n");
                }

                addLogEntry(LogLevel.DEBUG, "🎤 User interrupted AI response\n");
            }
        }
    }

    private void toggleSpeaker()
    {
        if (voiceService != null && voiceService.getAudioService() != null)
        {
            voiceService.getAudioService().toggleAudioMute();
            speakerMuted = voiceService.getAudioService().isAudioMuted();
        }
        else
        {
            speakerMuted = true;
        }

        updateSpeakerButton();

        String status = speakerMuted ? "muted" : "unmuted";
        addLogEntry(LogLevel.DEBUG, "🔊 Speaker " + status + '\n');
    }

    private void submitTextMessage()
    {
        if (!initialized || voiceService == null)
        {
            return;
        }

        String text = textInputArea.getText().trim();
        if (text.isEmpty())
        {
            return;
        }

        // Add the user's message to the log
        addLogEntry(LogLevel.INFO, USER_PREFIX + text + '\n');

        // Send the text message to the voice service
        voiceService.sendTextMessage(text);

        // Clear the text input area
        textInputArea.setText("");
    }

    private void copyLog()
    {
        StringBuilder logText = new StringBuilder();

        synchronized (logEntries)
        {
            // Use the updated LogEntry message directly to get current content
            for (LogEntry entry : logEntries)
            {
                // Only include entries that should be visible based on current log level
                if (shouldShowLogLevel(entry.level()))
                {
                    // Extract plain text from the message (remove HTML/markdown for transcript messages)
                    String plainText;
                    if (isChatStyleMessage(entry.message()))
                    {
                        // For transcript messages, remove HTML/markdown formatting
                        plainText = entry.message().replaceAll("<[^>]+>", "")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'");
                    }
                    else
                    {
                        // For regular log messages, use as-is
                        plainText = entry.message();
                    }

                    logText.append(plainText);
                    if (!plainText.endsWith("\n"))
                    {
                        logText.append("\n");
                    }
                }
            }
        }

        // Copy to clipboard
        if (logText.length() > 0)
        {
            try
            {
                java.awt.datatransfer.StringSelection stringSelection =
                    new java.awt.datatransfer.StringSelection(logText.toString());
                java.awt.datatransfer.Clipboard clipboard =
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
            }
            catch (Exception e)
            {
                log.error("Failed to copy log to clipboard", e);
            }
        }
    }

    private int countVisibleEntries()
    {
        int count = 0;
        synchronized (logEntries)
        {
            for (LogEntry entry : logEntries)
            {
                if (shouldShowLogLevel(entry.level()))
                {
                    count++;
                }
            }
        }
        return count;
    }

    private void clearLog()
    {
        synchronized (logEntries)
        {
            logEntries.clear();
            logPanels.clear();

            SwingUtilities.invokeLater(() ->
            {
                // Remove all components except the last one (which is the glue)
                int componentCount = logContainer.getComponentCount();
                if (componentCount > 1)
                {
                    // Remove all components except the glue (last component)
                    for (int i = componentCount - 2; i >= 0; i--)
                    {
                        logContainer.remove(i);
                    }
                }
                logContainer.revalidate();
                logContainer.repaint();
            });
        }
        refreshLogDisplay();
    }

    private void openFileDialog()
    {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Select image files to upload");

        // Set up image file filter
        FileNameExtensionFilter imageFilter = new FileNameExtensionFilter(
            "Image files (*.jpg, *.jpeg, *.png, *.gif, *.bmp, *.webp)",
            "jpg", "jpeg", "png", "gif", "bmp", "webp");
        fileChooser.setFileFilter(imageFilter);
        fileChooser.setAcceptAllFileFilterUsed(false);

        // Remember last accessed directory
        if (lastAccessedDirectory != null && lastAccessedDirectory.exists())
        {
            fileChooser.setCurrentDirectory(lastAccessedDirectory);
        }

        int result = fileChooser.showOpenDialog(mainPanel);
        if (result == JFileChooser.APPROVE_OPTION)
        {
            java.io.File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length > 0)
            {
                // Save the directory for next time
                lastAccessedDirectory = selectedFiles[0].getParentFile();
                uploadFiles(selectedFiles);
            }
        }
    }

    private void uploadFiles(java.io.File[] files)
    {
        if (filesService == null)
        {
            addLogEntry(LogLevel.DEBUG, "Files service not initialized");
            return;
        }

        addLogEntry(LogLevel.INFO, "Uploading " + files.length + " file(s)...");

        filesService.uploadFiles(files).thenAccept(response ->
        {
            SwingUtilities.invokeLater(() ->
            {
                // Add the raw API response to DEBUG log as specified
                addLogEntry(LogLevel.DEBUG, "OpenAI Files API Response:\n" + response);

                // Treat the response as user input and send to both voice and code agents
                processFileUploadResponse(response);
            });
        }).exceptionally(throwable ->
        {
            SwingUtilities.invokeLater(() ->
            {
                addLogEntry(LogLevel.DEBUG, "File upload failed: " + throwable.getMessage());
            });
            return null;
        });
    }

    private void processFileUploadResponse(String response)
    {
        // Display the image analysis response as an agent message (not spoken aloud)
        addLogEntry(LogLevel.INFO, AGENT_PREFIX + response + '\n');

        // Inject the image analysis response into the voice session using conversation.item.create
        if (voiceService != null && voiceService.isConnected())
        {
            voiceService.injectAssistantMessage(response);
        }

        // Send the response to the code agent (OpenAI Responses Service)
        if (responsesService != null)
        {
            // Collect recent transcript messages for context
            List<OpenAIResponsesService.TranscriptMessage> transcriptMessages = collectRecentTranscriptMessages(10);

            // Add the file upload response as a user message
            transcriptMessages.add(new OpenAIResponsesService.TranscriptMessage("user", response));

            responsesService.analyzeForCodeRequest(transcriptMessages).thenAccept(codeResponse ->
            {
                SwingUtilities.invokeLater(() ->
                {
                    if (!"[non-code-request]".equals(codeResponse))
                    {
                        // Display code response as agent transcript
                        addLogEntry(LogLevel.INFO, "Assistant: " + codeResponse);
                    }
                    else
                    {
                        addLogEntry(LogLevel.DEBUG, "Code agent response: [non-code-request]");
                    }
                });
            });
        }
    }

    private void handleClipboardPaste()
    {
        addLogEntry(LogLevel.DEBUG, "Paste button clicked - checking clipboard for images");
        try
        {
            // Get system clipboard
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable transferable = clipboard.getContents(null);

            if (transferable == null)
            {
                addLogEntry(LogLevel.INFO, "No content found in clipboard");
                return;
            }

            // Check if clipboard contains image data
            if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor))
            {
                // Only process if we're connected and files service is available
                if (filesService == null || !initialized)
                {
                    addLogEntry(LogLevel.INFO, "Cannot paste image - not connected to OpenAI");
                    return;
                }

                // Extract image from clipboard
                Image image = (Image) transferable.getTransferData(DataFlavor.imageFlavor);

                // Convert to BufferedImage
                BufferedImage bufferedImage;
                if (image instanceof BufferedImage)
                {
                    bufferedImage = (BufferedImage) image;
                }
                else
                {
                    // Convert Image to BufferedImage
                    bufferedImage = new BufferedImage(
                        image.getWidth(null),
                        image.getHeight(null),
                        BufferedImage.TYPE_INT_RGB
                    );
                    bufferedImage.getGraphics().drawImage(image, 0, 0, null);
                }

                // Create temporary file
                File tempFile = File.createTempFile("clipboard_image_", ".png");
                tempFile.deleteOnExit(); // Clean up on JVM exit

                // Write image to temporary file
                ImageIO.write(bufferedImage, "png", tempFile);

                addLogEntry(LogLevel.INFO, "Pasted image from clipboard");

                // Process through existing upload pipeline
                uploadFiles(new File[]{tempFile});
            }
            else
            {
                addLogEntry(LogLevel.INFO, "No image found in clipboard - only images can be pasted");
            }
        }
        catch (Exception e)
        {
            log.error("Error handling clipboard paste", e);
            addLogEntry(LogLevel.INFO, "Error pasting from clipboard: " + e.getMessage());
        }
    }

    private void updateMicrophoneButton()
    {
        boolean isRecording = voiceService != null && voiceService.isRecording();

        if (isRecording)
        {
            // Microphone is actively recording
            micToggleButton.setText("🎤");
            if (aiResponseActive)
            {
                updateStatus("Recording during AI response - Interrupting", new Color(255, 165, 0)); // Orange for interruption
            }
            else
            {
                updateStatus("Recording - Speak now", new Color(160, 232, 255)); // Blue for normal recording
            }
        }
        else if (aiResponseActive)
        {
            // AI is speaking and microphone is muted
            micToggleButton.setText("🎤🔇");
            updateStatus("AI speaking - Microphone muted (click to interrupt)", Color.ORANGE);
        }
        else
        {
            // Normal idle state
            micToggleButton.setText("🎤🚫");
            if (initialized)
            {
                updateStatus("Connected - Ready to use voice", Color.GREEN);
            }
        }
    }

    private void updateSpeakerButton()
    {
        speakerToggleButton.setText(speakerMuted ? "🔇" : "🔊");
    }

    private void updateStatus(String status, Color color)
    {
        statusLabel.setText(status);
        statusLabel.setForeground(color);
    }

    public JComponent getContent()
    {
        return mainPanel;
    }

    public void dispose()
    {
        if (voiceService != null)
        {
            voiceService.shutdown();
            voiceService = null;
        }
    }

    @Override
    public void onConnected()
    {
        SwingUtilities.invokeLater(() ->
        {
            updateStatus("Connected - Ready to use voice", Color.GREEN);
            micToggleButton.setEnabled(true);
        });
    }

    @Override
    public void onDisconnected()
    {
        SwingUtilities.invokeLater(() ->
        {
            updateStatus("Disconnected from voice service", Color.RED);
            micToggleButton.setEnabled(false);
            updateMicrophoneButton();
        });
    }

    @Override
    public void onVoiceSessionStarted()
    {
        SwingUtilities.invokeLater(this::updateMicrophoneButton);
        startVolumeUpdates();
    }

    @Override
    public void onVoiceSessionStopped()
    {
        SwingUtilities.invokeLater(this::updateMicrophoneButton);
        stopVolumeUpdates();
    }

    @Override
    public void onResponseReceived(String response)
    {
        String logMessage = String.format("[%s] %s\n", getTimestamp(), response);
        addLogEntry(LogLevel.DEBUG, logMessage);
    }

    @Override
    public void onTraceReceived(String trace)
    {
        String logMessage = String.format("[%s] %s\n", getTimestamp(), trace);
        addLogEntry(LogLevel.TRACE, logMessage);
    }

    @Override
    public void onAudioResponseStarted()
    {
        SwingUtilities.invokeLater(() ->
        {
            // Capture current microphone state before AI response starts
            micMutedBeforeAIResponse = !(voiceService != null && voiceService.isRecording());
            aiResponseActive = true;
            userInterruptedAI = false; // Reset interrupt flag for new AI response
            updateMicrophoneButton();
        });
    }

    @Override
    public void onAudioResponseCompleted()
    {
        SwingUtilities.invokeLater(() ->
        {
            aiResponseActive = false;

            if (!userInterruptedAI && voiceService != null) // else, whatever state the mic is in is desired
            {
                // Normal AI response completion - restore original mic state
                // Note: VoiceService always stops microphone during AI response, so we need to restore based on original state
                if (!micMutedBeforeAIResponse)
                {
                    // Mic was recording before AI started - restore it to recording state
                    voiceService.startVoiceSession();
                }
                // If micMutedBeforeAIResponse is true, mic was muted before AI started
                // Since VoiceService stops recording during AI response, mic is already in correct muted state
                // No action needed for muted state restoration
            }

            // Ensure microphone button is enabled when connected and initialized
            if (initialized && voiceService != null && voiceService.isConnected())
            {
                micToggleButton.setEnabled(true);
            }

            updateMicrophoneButton();

            // Reset streaming state when response is completed
            synchronized (logEntries)
            {
                isStreamingActive = false;
                currentStreamingMessage.setLength(0);
                currentStreamingLogIndex = -1;

                // Note: We don't reset user transcript placeholder state here
                // because the user transcript can arrive after the audio response is completed.
                // The placeholder will be handled when onUserTranscript() is called.
            }
        });
    }

    @Override
    public void onUserTranscript(String transcript)
    {
        SwingUtilities.invokeLater(() ->
        {
            synchronized (logEntries)
            {
                log.debug("onUserTranscript: waitingForUserTranscript={}, userPlaceholderLogIndex={}, logEntries.size()={}",
                    waitingForUserTranscript, userPlaceholderLogIndex, logEntries.size());

                String userMessage = USER_PREFIX + transcript + '\n';
                if (waitingForUserTranscript && userPlaceholderLogIndex >= 0 &&
                    userPlaceholderLogIndex < logEntries.size() && userPlaceholderLogIndex < logPanels.size())
                {
                    // Replace placeholder with actual transcript
                    LogEntry updatedEntry = new LogEntry(LogLevel.INFO, userMessage);
                    logEntries.set(userPlaceholderLogIndex, updatedEntry);

                    // Update the corresponding LogPanel
                    BaseLogPanel logPanel = logPanels.get(userPlaceholderLogIndex);
                    logPanel.updateContent(userMessage);

                    // Reset placeholder state
                    waitingForUserTranscript = false;
                    userPlaceholderLogIndex = -1;
                    log.debug("Replaced placeholder with transcript, reset state");

                    // No need to call refreshLogDisplay() since we updated the specific panel
                }
                else
                {
                    // No placeholder, add transcript normally
                    log.debug("No placeholder found, adding transcript normally");
                    waitingForUserTranscript = false;
                    userPlaceholderLogIndex = -1;
                    addLogEntry(LogLevel.INFO, userMessage);
                }
            }

            // Call OpenAI Responses API to determine if code should be produced
            // This happens when user finishes talking for faster response time
            if (responsesService != null)
            {
                List<OpenAIResponsesService.TranscriptMessage> transcriptMessages = collectRecentTranscriptMessages(10);
                if (!transcriptMessages.isEmpty())
                {
                    responsesService.analyzeForCodeRequest(transcriptMessages)
                        .thenAccept(result -> SwingUtilities.invokeLater(() ->
                        {
                            if ("[non-code-request]".equals(result))
                            {
                                // Log non-code requests to DEBUG
                                addLogEntry(LogLevel.DEBUG, "Code agent: <i>No code found in this request</i>\n");
                            }
                            else if (!result.trim().startsWith("```"))
                            {
                                // Disregard responses that don't begin with markdown blocks
                                addLogEntry(LogLevel.DEBUG, "Code agent: <i>Response doesn't begin with markdown block, disregarding</i>\n");
                            }
                            else
                            {
                                // Append code response to the log as a new message from the agent
                                String codeMessage = AGENT_PREFIX + result + '\n';
                                addLogEntry(LogLevel.INFO, codeMessage);
                            }
                        }))
                        .exceptionally(throwable ->
                        {
                            log.error("Error calling OpenAI Responses API", throwable);
                            SwingUtilities.invokeLater(() ->
                                addLogEntry(LogLevel.DEBUG, "❌ Error calling OpenAI Responses API\n"));
                            return null;
                        });
                }
            }
        });
    }

    @Override
    public void onAgentTranscript(String transcript)
    {
        SwingUtilities.invokeLater(() ->
        {
            synchronized (logEntries)
            {
                String agentMessage = AGENT_PREFIX + transcript + '\n';
                if (isStreamingActive && currentStreamingLogIndex >= 0 &&
                    currentStreamingLogIndex < logEntries.size() && currentStreamingLogIndex < logPanels.size())
                {
                    // Compare the final transcript with our streaming message
                    String streamingContent = currentStreamingMessage.toString().trim();
                    String finalContent = transcript.trim();

                    if (streamingContent.equals(finalContent))
                    {
                        // Contents match - just mark streaming as complete, don't add duplicate
                        log.debug("Final transcript matches streaming content, discarding duplicate");
                    }
                    else
                    {
                        // Contents differ - replace the streaming message with the final transcript
                        log.debug("Final transcript differs from streaming content, replacing with final version");
                        LogEntry existingEntry = logEntries.get(currentStreamingLogIndex);
                        LogEntry updatedEntry = new LogEntry(existingEntry.level(), agentMessage);
                        logEntries.set(currentStreamingLogIndex, updatedEntry);

                        // Update the corresponding LogPanel
                        BaseLogPanel logPanel = logPanels.get(currentStreamingLogIndex);
                        logPanel.updateContent(agentMessage);
                    }

                    // Reset streaming state
                    isStreamingActive = false;
                    currentStreamingMessage.setLength(0);
                    currentStreamingLogIndex = -1;
                }
                else
                {
                    // No active streaming, add as new log entry (fallback case)
                    addLogEntry(LogLevel.INFO, agentMessage);
                }
            }
        });
    }

    @Override
    public void onUserSpeechEnded()
    {
        // Unmute output if it was muted during user speech after push-to-interrupt
        if (speakerMuted && userInterruptedAI && voiceService != null && voiceService.getAudioService() != null)
        {
            voiceService.getAudioService().setAudioMuted(false);
            speakerMuted = false;
            updateSpeakerButton();
            addLogEntry(LogLevel.DEBUG, "🔊 Output unmuted after user finished speaking\n");
        }
        userInterruptedAI = false;
    }

    @Override
    public void onTextDelta(String delta)
    {
        SwingUtilities.invokeLater(() ->
        {
            synchronized (logEntries)
            {
                if (!isStreamingActive)
                {
                    // Start a new streaming message
                    isStreamingActive = true;
                    currentStreamingMessage.setLength(0);
                    currentStreamingMessage.append(delta);

                    // Add initial log entry for streaming message
                    LogEntry streamingEntry = new LogEntry(LogLevel.INFO, AGENT_PREFIX + delta);
                    logEntries.add(streamingEntry);
                    currentStreamingLogIndex = logEntries.size() - 1;
                    boolean visible = shouldShowLogLevel(streamingEntry.level());

                    // Create HtmlLogPanel for this transcript entry
                    BaseLogPanel logPanel = new HtmlLogPanel(streamingEntry, visible);
                    logPanels.add(logPanel);

                    // Add to container on EDT (insert before the glue component)
                    SwingUtilities.invokeLater(() ->
                    {
                        // Insert before the last component (which is the glue)
                        int insertIndex = logContainer.getComponentCount() - 1;
                        logContainer.add(logPanel, insertIndex);
                        logContainer.revalidate();
                        logContainer.repaint();

                        // Only scroll to bottom if already at bottom
                        scrollToBottomIfNeeded();
                    });
                }
                else
                {
                    // Append to existing streaming message
                    currentStreamingMessage.append(delta);

                    // Update the existing log entry and LogPanel
                    if (currentStreamingLogIndex >= 0 && currentStreamingLogIndex < logEntries.size() &&
                        currentStreamingLogIndex < logPanels.size())
                    {
                        LogEntry existingEntry = logEntries.get(currentStreamingLogIndex);
                        LogEntry updatedEntry = new LogEntry(existingEntry.level(),
                            AGENT_PREFIX + currentStreamingMessage);
                        logEntries.set(currentStreamingLogIndex, updatedEntry);

                        // Update the corresponding LogPanel
                        BaseLogPanel logPanel = logPanels.get(currentStreamingLogIndex);
                        logPanel.updateContent(AGENT_PREFIX + currentStreamingMessage);
                    }
                }
            }
        });
    }

    @Override
    public void onError(Throwable error)
    {
        SwingUtilities.invokeLater(() -> log.error("Voice service error", error));

        // Format the error log with timestamp
        String timestamp = getTimestamp();
        String logMessage = String.format("[%s] ❌ Error: %s\n", timestamp, error.getMessage());
        addLogEntry(LogLevel.INFO, logMessage);
    }

    private static @NotNull String getTimestamp()
    {
        return LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @Override
    public void onRequestLog(String requestType, String details, String responseCode)
    {
        // Format the request log with timestamp and color coding based on response code
        String timestamp = getTimestamp();
        String emoji = getEmojiForResponseCode(responseCode);
        String logMessage = String.format("[%s] %s %s: %s [%s]\n",
            timestamp, emoji, requestType, details, responseCode);

        addLogEntry(LogLevel.DEBUG, logMessage);

        // Create placeholder for user transcript when audio buffer is committed for processing
        if ("API Response".equals(requestType) && "Audio buffer committed for processing".equals(details))
        {
            SwingUtilities.invokeLater(() ->
            {
                synchronized (logEntries)
                {
                    log.debug("onRequestLog: Audio buffer committed - creating placeholder. waitingForUserTranscript={}, userPlaceholderLogIndex={}",
                        waitingForUserTranscript, userPlaceholderLogIndex);

                    // Always create a new placeholder when audio buffer is committed
                    // Reset any previous state first
                    waitingForUserTranscript = false;
                    userPlaceholderLogIndex = -1;

                    // Add placeholder log entry for user transcript
                    LogEntry placeholderEntry = new LogEntry(LogLevel.INFO, USER_PREFIX + "_transcribing_\n");
                    logEntries.add(placeholderEntry);
                    userPlaceholderLogIndex = logEntries.size() - 1;
                    waitingForUserTranscript = true;
                    boolean visible = shouldShowLogLevel(placeholderEntry.level());

                    // Create HtmlLogPanel for this placeholder entry (will become transcript)
                    BaseLogPanel logPanel = new HtmlLogPanel(placeholderEntry, visible);
                    logPanels.add(logPanel);

                    // Add to container on EDT (insert before the glue component)
                    SwingUtilities.invokeLater(() ->
                    {
                        // Insert before the last component (which is the glue)
                        int insertIndex = logContainer.getComponentCount() - 1;
                        logContainer.add(logPanel, insertIndex);
                        logContainer.revalidate();
                        logContainer.repaint();

                        // Only scroll to bottom if already at bottom
                        scrollToBottomIfNeeded();
                    });

                    log.debug("Created placeholder at index: {}", userPlaceholderLogIndex);
                }
            });
        }
    }

    private String getEmojiForResponseCode(String responseCode)
    {
        switch (responseCode)
        {
            case "101":
            case "200":
            case "SENT":
                return "✅";
            case "PENDING":
                return "⏳";
            case "RATE_LIMITED":
                return "⏸️";
            case "ERROR":
            default:
                return "❌";
        }
    }

    private void startVolumeUpdates()
    {
        if (volumeUpdateExecutor != null && !volumeUpdateExecutor.isShutdown())
        {
            volumeUpdateExecutor.shutdown();
        }

        volumeUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
        volumeUpdateExecutor.scheduleAtFixedRate(() ->
        {
            if (voiceService != null && voiceService.isRecording() && voiceService.getAudioService() != null)
            {
                double volume = voiceService.getAudioService().getCurrentVolume();
                SwingUtilities.invokeLater(() ->
                {
                    int volumePercent = (int) Math.round(volume);
                    volumeMeter.setValue(volumePercent);
                    volumeMeter.setString(volumePercent + "%");
                });
            }
        }, 0, 100, TimeUnit.MILLISECONDS); // Update every 100ms
    }

    private void stopVolumeUpdates()
    {
        if (volumeUpdateExecutor != null && !volumeUpdateExecutor.isShutdown())
        {
            volumeUpdateExecutor.shutdown();
        }

        SwingUtilities.invokeLater(() ->
        {
            volumeMeter.setValue(0);
            volumeMeter.setString("0%");
        });
    }

    /**
     * Collects recent transcript messages for OpenAI Responses API analysis
     *
     * @param maxMessages Maximum number of messages to collect (default 10)
     * @return List of transcript messages in chronological order
     */
    private List<OpenAIResponsesService.TranscriptMessage> collectRecentTranscriptMessages(int maxMessages)
    {
        List<OpenAIResponsesService.TranscriptMessage> transcriptMessages = new ArrayList<>();

        synchronized (logEntries)
        {
            // Iterate through log entries in reverse order to get most recent first
            for (int i = logEntries.size() - 1; i >= 0 && transcriptMessages.size() < maxMessages; i--)
            {
                LogEntry entry = logEntries.get(i);
                String message = entry.message();

                if (message.startsWith(USER_PREFIX))
                {
                    String content = message.substring(USER_PREFIX.length()).trim();
                    // Skip placeholder messages
                    if (!content.equals("_transcribing_"))
                    {
                        transcriptMessages.add(0, new OpenAIResponsesService.TranscriptMessage("user", content));
                    }
                }
                else if (message.startsWith(AGENT_PREFIX))
                {
                    String content = message.substring(AGENT_PREFIX.length()).trim();
                    transcriptMessages.add(0, new OpenAIResponsesService.TranscriptMessage("assistant", content));
                }
            }
        }

        return transcriptMessages;
    }

}