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

### Microphone Interruption Fixes
- ✅ **Audio Flow During Interruption**: Fixed VoiceService to allow microphone recording during AI responses when user explicitly starts voice session
- ✅ **Interruption State Management**: Modified startVoiceSession to set wasRecordingBeforeResponse=true during AI responses to maintain recording state
- ✅ **Icon State Synchronization**: Updated updateMicrophoneButton to check actual recording state from VoiceService instead of just aiResponseActive flag
- ✅ **Visual Feedback Enhancement**: Added distinct status messages for different states:
  - "Recording during AI response - Interrupting" (orange) when actively interrupting
  - "AI speaking - Microphone muted (click to interrupt)" (orange) when muted during AI response
  - "Recording - Speak now" (blue) for normal recording
- ✅ **Real-time Icon Updates**: Microphone button icon now always reflects true microphone state, updating immediately on state changes

### Audio Flow Interruption Fix
- ✅ **Root Cause Identified**: Discovered that VoiceService.onAudioData was blocking audio data from being sent to OpenAI during AI responses because microphone was automatically stopped
- ✅ **Automatic Microphone Stopping Removed**: Eliminated the automatic microphone stopping logic in VoiceService.onAudioResponse that was preventing interruption
- ✅ **Continuous Audio Flow**: Audio data now flows continuously to OpenAI when microphone is recording, even during AI responses
- ✅ **State Management Cleanup**: Removed wasRecordingBeforeResponse field and all related automatic resuming logic
- ✅ **Debug Log Level**: Changed "User interrupted AI response" message from INFO to DEBUG level as requested
- ✅ **Testing Verified**: All existing tests continue to pass, confirming no regressions introduced

### Microphone Button Synchronization Fix
- ✅ **State Tracking Enhancement**: Added `micStateBeforeAIResponse` field to VoiceAssistantPanel to track microphone state before AI responses start
- ✅ **AI Response Start Handling**: Modified `onAudioResponseStarted()` to capture current microphone recording state before setting aiResponseActive flag
- ✅ **AI Response End Handling**: Enhanced `onAudioResponseCompleted()` to restore microphone to its previous state unless user intervened during AI response
- ✅ **User Intervention Support**: System properly handles cases where user clicks microphone button during AI response - keeps user's chosen state
- ✅ **Automatic State Restoration**: When AI finishes talking, microphone automatically returns to whatever state it was in before AI started (recording or idle)
- ✅ **UI Synchronization**: Microphone button icon and status always reflect the actual microphone state with proper visual feedback
- ✅ **Testing Coverage**: All existing tests continue to pass, ensuring no regressions in functionality

### Microphone Muting Fix
- ✅ **Root Cause Identified**: Discovered that VoiceService was not automatically stopping microphone recording when AI responses started
- ✅ **Automatic Muting Restored**: Added microphone stopping logic to VoiceService.onAudioResponse() method when AI starts speaking
- ✅ **Feedback Prevention**: Microphone is now properly muted when agent starts talking to prevent audio feedback loops
- ✅ **Logging Enhancement**: Added informative log message when microphone is stopped during AI responses
- ✅ **Testing Verified**: All existing tests continue to pass, confirming no regressions introduced

### Audio Flow Interruption Fix
- ✅ **Root Cause Identified**: Discovered that VoiceService.onAudioData was blocking audio data from being sent to OpenAI during AI responses with isRecording() check
- ✅ **Blocking Logic Removed**: Eliminated the recording state check in onAudioData method that was preventing interruption
- ✅ **Continuous Audio Flow**: Audio data now flows continuously to OpenAI when microphone is recording, even during AI responses
- ✅ **Proper Flow Control**: AudioService handles recording state management, allowing VoiceService to focus on data transmission
- ✅ **Interruption Enabled**: Users can now successfully interrupt AI responses by unmuting microphone during AI speech
- ✅ **Testing Verified**: All existing tests continue to pass, confirming no regressions introduced

### Enhanced Microphone State Tracking and Output Muting
- ✅ **Push-to-Interrupt State Tracking**: Added `userInterruptedAI` field to track when user has used push-to-interrupt functionality
- ✅ **Proper State Restoration**: Enhanced `onAudioResponseCompleted()` to handle two scenarios:
  - Normal AI completion: Restores microphone to original state before AI started talking
  - Push-to-interrupt used: Disregards original state and keeps microphone unmuted as requested
- ✅ **Output Muting During User Speech**: Added `shouldMuteOutputDuringUserSpeech` field and logic to:
  - Mute output immediately when user starts talking after push-to-interrupt
  - Unmute output when user finishes talking (in `onVoiceSessionStopped`)
- ✅ **Interrupt Detection**: Modified `toggleMicrophone()` to set interrupt flags and mute output when user interrupts AI
- ✅ **Comprehensive Logging**: Added debug messages for output muting/unmuting during interruption scenarios
- ✅ **Testing Coverage**: All existing tests continue to pass, ensuring no regressions in functionality
- ✅ **User Experience Enhancement**: Provides faster interruption capability and prevents agent voice pickup during user interruption

### Microphone and Speaker State Bug Fixes
- ✅ **Speaker Unmuting Fix**: Fixed bug where speaker wasn't becoming unmuted after push-to-interrupt
  - Removed redundant `voiceService.isAudioMuted()` check in `onVoiceSessionStopped()`
  - Speaker now always unmutes when `shouldMuteOutputDuringUserSpeech` flag is true
  - Ensures reliable speaker unmuting when user finishes speaking after interruption
- ✅ **Microphone State Restoration Fix**: Fixed bug where mic wasn't returning to proper state when agent finished talking
  - Enhanced `onAudioResponseCompleted()` to explicitly start voice session if mic should be unmuted after interruption
  - Ensures mic stays unmuted after push-to-interrupt regardless of original state
  - Properly handles all three scenarios: mic starts muted, mic starts unmuted, user interrupts
- ✅ **Microphone State Logic Bug Fix**: Fixed critical bug where microphone was always being turned on after agent finished talking
  - **Root Cause**: Inverted logic in `onAudioResponseCompleted()` method - restoration conditions were backwards
  - **Fix Applied**: Corrected the boolean logic in microphone state restoration:
    - When `micMutedBeforeAIResponse = false` (mic was recording), restore to recording if not currently recording
    - When `micMutedBeforeAIResponse = true` (mic was muted), stop recording if currently recording
  - **Result**: Microphone now properly returns to whatever state it was in before agent started talking (unless user interrupted)
- ✅ **Microphone State Restoration Logic Fix**: Fixed bug where microphone was always muted after agent finished talking
  - **Root Cause**: VoiceService always stops microphone during AI response, making restoration logic dependent on current state ineffective
  - **Issue**: Previous logic checked current microphone state, but VoiceService.onAudioResponse() always stops recording during AI speech
  - **Fix Applied**: Simplified restoration logic to only depend on original state before AI response:
    - If `micMutedBeforeAIResponse = false` (was recording): Always start voice session after AI completes
    - If `micMutedBeforeAIResponse = true` (was muted): Do nothing (already stopped by VoiceService)
  - **Result**: Microphone now correctly returns to whatever state it was in before agent started talking
- ✅ **Robust State Management**: All fixes ensure consistent behavior across all user interaction scenarios
- ✅ **Testing Verified**: All existing tests continue to pass, confirming no regressions introduced
