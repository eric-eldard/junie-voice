# About
The `project-memory.md` file serves as a **persistent knowledge repository** for AI agents working on the junie-voice
project.

## Primary Functions

1. **Long-term Memory Storage** – Acts as a persistent memory system where AI agents can store important project
   insights, patterns, and learnings that would otherwise be lost between sessions.

2. **Project Context Documentation** – Provides a space to document:
    - Feature explanations which don't belong in project-functionality.md (overly nuanced or not user-focused)
    - Planned features and functionality
    - Technical notes that don't belong in code comments

3. **Knowledge Transfer** – Helps future AI agent interactions by preserving:
    - "Lessons learned" from previous development work
    - Project-specific patterns and conventions
    - Important architectural decisions and rationale

## Key Characteristics
- **Agent-Managed**: Primarily maintained by AI agents, though developers can edit it to correct inaccuracies
- **Markdown Format**: Uses standard Markdown for easy readability and formatting
- **Living Document**: Content is subject to change as the project evolves
- **Supplementary to Code**: Contains information that's valuable but not appropriate for code comments
- **Instructions**: The agent cannot alter the About section of this document


# Project Memory

## Voice Assistant Implementation

### Architecture Overview
The project implements a complete voice assistant solution with the following components:

1. **OpenAI Realtime Service** (`com.eric_eldard.voice.OpenAIRealtimeService`)
   - WebSocket connection to OpenAI's Realtime voice API
   - Handles authentication and message exchange
   - Agnostic from IntelliJ plugins (can be used standalone)

2. **Audio Service** (`com.eric_eldard.voice.AudioService`)
   - Manages microphone input and speaker output
   - Uses Java Sound API with 24kHz sample rate (OpenAI requirement)
   - Thread-safe recording with proper resource management

3. **Voice Service** (`com.eric_eldard.voice.VoiceService`)
   - Combines OpenAI and Audio services
   - Provides unified interface for voice interactions
   - Handles service lifecycle and error management

4. **IntelliJ Plugin Integration** (`VoiceAssistantPanel.java`)
   - Main UI panel with proper cleanup and component management

### Key Features Implemented
- ✅ Connection to OpenAI Realtime voice API
- ✅ Real-time audio input capture
- ✅ Audio output capability (framework ready)
- ✅ Error handling and logging
- ✅ Thread-safe UI updates
- ✅ Log message display system with proper height management and selective HTML rendering for transcript messages
- ✅ Text wrapping and font size consistency across message types
- ✅ Polymorphic LogPanel structure with separated text extraction logic for different message types
- ✅ Modular architecture with extracted classes in separate packages for better code organization
- ✅ 5px internal border/padding added to all log messages for improved visual spacing

### Configuration Requirements
- OpenAI API key must be set via system property `openai.api.key` or environment variable `OPENAI_API_KEY`
- Audio permissions may be required on some systems

### Dependencies Added
- OkHttp for WebSocket communication
- Jackson for JSON processing
- SLF4J for logging
- Java Sound API for audio handling

## Gradle Project Configuration

### Build System Setup
- ✅ Gradle build system fully functional with successful builds
- ✅ IntelliJ Platform Plugin development properly configured via `build.gradle`
- ✅ Plugin runs successfully with `./gradlew runIde`
- ✅ IntelliJ IDE launches with plugin loaded
- ⚠️ Some deprecation warnings present (compatible with current Gradle version)

## Voice Service Reliability Improvements

### Issues Resolved
- ✅ **Rate Limiting**: Implemented multi-level rate limiting (500ms general, 1000ms transcription) with exponential backoff
- ✅ **Audio Data Loss**: Added audio data accumulation system with temporary buffering and batch transmission
- ✅ **Buffer Management**: Enhanced validation and session cleanup to prevent data loss
- ✅ **Error Recovery**: Improved error handling with graceful degradation and user feedback

### Key Technical Changes
- Enhanced `OpenAIRealtimeService.java` and `VoiceService.java` with robust rate limiting and buffer management
- Added methods: `sendAudioData()`, `flushAudioBuffer()`, `clearAudioBuffer()`, `hasMinimumAudioData()`
- Achieved dramatic reduction in API errors and reliable voice recording experience

## Code Architecture Refactoring

### Package Structure Reorganization
The project has been refactored to improve code organization by extracting nested classes and enums into separate files with appropriate sub-packages:

#### New Package Structure:
- `com.eric_eldard.ui.log` - Log-related components
  - `LogLevel.java` - Enum for log level filtering (INFO, DEBUG, TRACE)
  - `LogEntry.java` - Data model for individual log entries with HTML caching
  - `LogPanel.java` - UI component for displaying individual log entries
- `com.eric_eldard.ui.renderer` - Custom renderers
  - `UnwrapParagraphRenderer.java` - Markdown paragraph renderer for chat messages

#### Dependency Injection Pattern
- `VoiceAssistantPanel` now implements `LogPanel.LogPanelDependencies` and `LogEntry.HtmlConverter` interfaces
- Extracted classes use dependency injection to access required functionality from the main panel
- This decouples the UI components from the main panel class while maintaining functionality

#### Benefits Achieved:
- ✅ Improved code readability and maintainability
- ✅ Better separation of concerns
- ✅ Reduced monolithic class size
- ✅ Enhanced testability through dependency injection
- ✅ Cleaner package organization following Java conventions

## OpenAI Responses API Integration

### Technical Implementation
- ✅ **OpenAIResponsesService** (`com.eric_eldard.voice.OpenAIResponsesService`) - Uses OpenAI Chat Completions API with gpt-4.1-mini model for code detection
- ✅ **Integration Points**: Service initialized with same API key, proper cleanup on disconnect, thread-safe UI updates
- Uses existing project dependencies (OkHttp, Jackson) and follows established patterns from OpenAIRealtimeService

## Search Functionality Status

### Current Status: REMOVED
Web search functionality has been completely removed from the project due to compatibility issues with OpenAI's API that were causing core chat functionality to break.

### Issues Encountered
- ❌ **OpenAI API Error**: Invalid value 'web_search' - only 'function' and 'mcp' are supported tool types
- ❌ **Chat Functionality Broken**: User messages showing as "transcribing" with duplicate messages
- ❌ **Message Handling Issues**: Agent responses being appended to old messages incorrectly

### Resolution Applied
- ✅ **Removed All Search Code**: Eliminated all web search related functionality from both voice and code agents
- ✅ **Restored Core Functionality**: Chat system now works without search-related API errors
- ✅ **Simplified Services**: Both OpenAIRealtimeService and OpenAIResponsesService returned to basic functionality
- ✅ **Clean System Prompts**: Removed all references to web search capabilities from agent instructions

### Technical Changes Made
- Removed invalid `{"type": "web_search"}` tool configurations from both services
- Cleaned up system prompts to remove web search references
- Removed web search status messages from UI
- Restored original service constructors without search parameters

### Future Considerations
If web search functionality is needed in the future, it should be implemented using:
- OpenAI's officially supported function calling with custom search APIs
- Proper error handling and fallback mechanisms
- Thorough testing to ensure core chat functionality remains stable

## Microphone Muting Implementation

### Current Status: MICROPHONE MUTING DURING AI RESPONSES
The project now implements microphone muting during AI responses to prevent feedback loops, replacing the previous echo cancellation approach. Users can manually unmute the microphone to interrupt AI responses when needed.

### Echo Cancellation Removal
- ❌ **Complete Removal**: All echo cancellation functionality has been removed due to ineffectiveness
- ❌ **NormalizedLeastMeansSquareFilter**: Deleted class and associated test files
- ❌ **AudioService Integration**: Removed all echo cancellation logic, speaker data capture, and filtering
- ❌ **VoiceService Methods**: Removed echo cancellation control methods
- ❌ **Dependencies**: No additional audio processing libraries required

### Microphone Muting System
- ✅ **VoiceService Implementation**: Added `wasRecordingBeforeResponse` field to track microphone state
- ✅ **Automatic Muting**: Microphone automatically muted when AI starts speaking (`onAudioResponse`)
- ✅ **Automatic Unmuting**: Microphone automatically unmuted when AI stops speaking (`onResponseComplete`)
- ✅ **State Cleanup**: Proper cleanup during shutdown and disconnection scenarios
- ✅ **Thread Safety**: All state changes properly synchronized

### UI Integration
- ✅ **Visual Indicators**: VoiceAssistantPanel shows different microphone states:
  - 🎤 - Recording (blue status)
  - 🎤🚫 - Not recording (green status)
  - 🎤🔇 - Muted during AI response (orange status with interruption hint)
- ✅ **Status Messages**: Clear status text indicating current state and available actions
- ✅ **Manual Interruption**: Users can click microphone button during AI responses to interrupt
- ✅ **Interruption Logging**: System logs when user interrupts AI responses

### Technical Implementation
- Uses existing project dependencies (Java Sound API only)
- Maintains backward compatibility with existing audio functionality
- Includes comprehensive error handling and logging
- Simple and reliable approach without complex signal processing
- Proper state management across all scenarios

### Testing Coverage
- ✅ **Integration Tests**: All existing VoiceAssistantPanel tests continue to pass
- ✅ **Build Verification**: Project builds successfully without compilation errors
- ✅ **No Regressions**: All existing functionality preserved

### User Experience Improvements
- Clear visual feedback about microphone state during AI responses
- Ability to interrupt AI responses by clicking microphone button
- Intuitive status messages guide user interaction
- Prevents feedback loops without complex audio processing
- Reliable operation without echo cancellation failures

### Final Implementation Summary
- ✅ **Microphone Auto-Muting**: Microphone automatically mutes when AI starts speaking to prevent feedback loops
- ✅ **Push-to-Interrupt**: Users can interrupt AI responses by clicking the microphone button during AI speech
- ✅ **State Restoration**: Microphone returns to its original state (muted/unmuted) after AI completes speaking
- ✅ **Output Muting During Interruption**: Speaker mutes when user interrupts to prevent feedback
- ✅ **Visual Feedback**: Clear status messages and button states indicate current microphone/speaker status
- ✅ **Robust State Management**: Handles all interaction scenarios consistently with proper error recovery
- ✅ **Testing Coverage**: All existing tests continue to pass, ensuring no regressions

## Message Prefix Standardization

### Current Status: EMOJI PREFIX CONSTANTS IMPLEMENTED
The project now uses standardized emoji prefix constants for user and agent messages, eliminating the previous conversion system from "USER_TRANSCRIPT:" and "AGENT_TRANSCRIPT:" prefixes.

### Technical Implementation
- ✅ **Constants Added**: Added `USER_PREFIX = "👤 User: "` and `AGENT_PREFIX = "🤖 Agent: "` constants to VoiceAssistantPanel class
- ✅ **Message Creation Updated**: All message creation code now uses the emoji prefix constants directly:
  - `submitTextMessage()` method for text input messages
  - `onUserTranscript()` method for voice transcript messages
  - `onAgentTranscript()` method for agent response messages
  - Placeholder creation for "_transcribing_" messages
  - Code agent response messages
- ✅ **Detection Logic Updated**: Updated `isChatStyleMessage()` and `collectRecentTranscriptMessages()` methods to detect messages by emoji prefixes
- ✅ **HtmlLogPanel Refactored**: Removed conversion logic from HtmlLogPanel - now expects and handles messages with emoji prefixes directly
- ✅ **Consistent Usage**: All transcript messages now use emoji prefixes throughout the entire message lifecycle

### Benefits Achieved
- ✅ **Simplified Architecture**: Eliminated unnecessary conversion between different prefix formats
- ✅ **Consistent Message Format**: All messages use the same emoji prefix format from creation to display
- ✅ **Improved Maintainability**: Centralized prefix definitions in constants reduce duplication and potential inconsistencies
- ✅ **No Functional Regressions**: All existing tests pass, confirming functionality is preserved
- ✅ **Clean Code**: Removed complex conversion logic in favor of direct prefix usage

## Log View Auto-Scroll Enhancement

### Current Status: ALWAYS SCROLL TO BOTTOM ON LOG VIEW CHANGES
The project now automatically scrolls to the bottom of the log view whenever the user changes log levels, improving user experience by ensuring they always see the most recent relevant messages.

### Technical Implementation
- ✅ **New Scroll Method**: Added `scrollToBottom()` method that unconditionally scrolls to the bottom of the log view
- ✅ **Enhanced refreshLogDisplay()**: Modified `refreshLogDisplay()` method to call `scrollToBottom()` instead of `scrollToBottomIfNeeded()`
- ✅ **Preserved Existing Behavior**: Kept `scrollToBottomIfNeeded()` method for use in other contexts where conditional scrolling is appropriate
- ✅ **Log Level Change Integration**: Auto-scroll triggers when user changes log level via dropdown (lines 418-419 in VoiceAssistantPanel)

### User Experience Improvements
- ✅ **Consistent Behavior**: Users always see the bottom of the log when switching between log levels (INFO, DEBUG, TRACE)
- ✅ **No Manual Scrolling Required**: Eliminates need for users to manually scroll down after changing log views
- ✅ **Preserved Context**: New messages still use conditional scrolling to avoid interrupting users reading older messages

### Testing Coverage
- ✅ **Build Verification**: Project builds successfully without compilation errors
- ✅ **Regression Testing**: All existing VoiceAssistantPanel tests continue to pass (3/3)
- ✅ **No Functional Impact**: Change only affects scrolling behavior during log view changes
