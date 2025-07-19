# Project
- This project is a plugin for IntelliJ which adds voice support to Junie
- This plugin will enable a developer to have a realtime voice conversation with an agent about a feature or product,
  then turn that conversation into robust instructions for Junie to implement

# Functionality
The plugin has four sections

## 1. OpenAI Configuration
1. **Connect**
    - The user can connect to their OpenAI account by entering their API key
    - Clicking Connect prompts the user with a modal to enter their key
    - The user can supply their API key with the `OPENAI_API_KEY` environment variable
        - When supplied, clicking Connect connects them without prompting
    - Once Connected:
        - The Connect button is disabled
        - The Disconnect button and all other controls are enabled
2. **Disconnect**
    - Clicking Disconnect stops the connection and audio recording
    - Once Connected:
        - The Connect button is enabled
        - The Disconnect button and all other controls are disabled
3. **Model**
    - Allows the user to choose a realtime voice model (for cost control)
4. **Voice**
    - Lets the user select a voice persona (for preference)

## 2. Main Controls
1. **Status**
    - Informational text for the user about the state of the connection and microphone
    - Possible states:
        - Disconnect
        - Connecting
        - Connected (ready, but mic off)
        - Recording (mic on)
        - AI speaking (mic muted to avoid echo feedback)
        - Recording (agent interrupted; the user has re-enabled the mic while the agent is talking)
2. **Mic Volume**
    - A bar indicating the microphone volume
    - Updated in realtime, as long as the microphone is on
    - This element is not interactive
3. **Mic button**
    - Indicates and controls whether the microphone is active/muted
    - **It is crucial this button stays in sync with the actual microphone state**
    - Agent auto-mutes mic while speaking; restores original state afterward (prevents echo)
    - Press-to-interrupt:
        - If clicked while agent is speaking, agent immediately stops speaking
        - Mic is enabled
        - The status reflects that the user is interrupting
        - The mic remains on after the user is done speaking, regardless of its original state
4. **Speaker button**
    - Indicates and controls whether agent audio is played
    - Clicking mutes/unmutes agent audio
    - Auto-mutes when user interrupts agent, to prevent feedback
5. **Log Level**
    - The user can pick the level of log messages they wish to see
        - INFO (transcript and error messages)
        - DEBUG (technical details about major events, API responses for when the user or agent has finished speaking)
        - TRACE (noisy technical details, like ongoing audio streaming)

## 3. Log
- Scrollable panel with all log messages
- Messages are filtered by the selected Log Level
    - Levels
        - INFO
        - DEBUG (includes INFO)
        - TRACE (includes INFO and TRACE)
    - Filtered messages are not dropped; they appear again when their level becomes visible
- Log message types
    1. Transcript
        - All voice messages are transcribed and logged
        - Identifies the speaker
        - Supports basic HTML and Markdown→HTML rendering
            - Inline elements appear on the same line as the speaker ID
            - Block-level elements begin on a new line
        - Level: INFO
    2. Technical
        - Developer debug info
        - Plain text only
        - Level: DEBUG or TRACE, based on noisiness
- Code handling
    - The voice agent does not speak code aloud
    - When the agent would respond with actual code, it instead explains the response in plain English
    - Every user message is also sent for text-based LLM processing
        - If the LLM responds "\[non-code-response]", this is logged to DEBUG and discarded
        - If the LLM responds with code—verified by detecting a Markdown code block (```code```)—it is displayed as an
          agent transcript message
- Scrolling
    - If the user is scrolled to bottom (defined as `[bottom] - [1 log item height]`), the log auto-scrolls
    - Otherwise, scroll position is preserved

## 4. Input
1. **Copy Log button**
    - Copies only visible log messages (based on filter) to the clipboard
    - Necessary because messages are appended in separate panels, which the user cannot select across 
2. **Clear Log button**
    - Permanently deletes all existing log messages, regardless of level and current filter
3. **Input box**
    - Multiline text input as an alternative to voice
    - Submit via Enter or the Submit button
    - Shift+Enter inserts a newline
    - Markdown/HTML is accepted but displayed as raw text
4. **Submit button** – Positioned at the top of the input section (full width)
    - Submits the user's current text input and clears the textbox
    - Button is only enabled when connected to OpenAI
5. **Upload button** (📎) – Positioned at the lower left of the input section
    - Allows users to upload image files for AI analysis
    - Only accepts image files (jpg, jpeg, png, gif, bmp, webp)
    - File dialog remembers the last accessed directory for convenience
    - Selected images are analyzed using OpenAI's gpt-4.1-mini model with vision capabilities
    - AI provides detailed descriptions of image content
    - Image analysis responses are displayed as agent messages (not spoken aloud)
    - Responses are injected into the voice session using `conversation.item.create` for proper context
    - Raw API responses are logged to DEBUG level
    - Button is only enabled when connected to OpenAI
6. **Paste button** (📋) – Positioned at the lower right of the input section
    - Allows users to paste images from clipboard for AI analysis
    - Only processes image data from clipboard (ignores text)
    - Images are converted to temporary PNG files and processed like uploaded files
    - Same image analysis functionality as the Upload button
    - Provides user feedback when no image is found in clipboard
    - Button is only enabled when connected to OpenAI
