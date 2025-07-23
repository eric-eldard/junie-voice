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

## Current Architecture

### Core Components
1. **OpenAI Realtime Service** (`com.eric_eldard.voice.OpenAIRealtimeService`) - WebSocket connection to OpenAI's Realtime voice API
2. **Audio Service** (`com.eric_eldard.voice.AudioService`) - Manages microphone/speaker with 24kHz sample rate
3. **Voice Service** (`com.eric_eldard.voice.VoiceService`) - Unified interface combining OpenAI and Audio services
4. **OpenAI Responses Service** (`com.eric_eldard.voice.OpenAIResponsesService`) - Chat Completions API for code/prompt detection
5. **IntelliJ Plugin Integration** (`VoiceAssistantPanel.java`) - Main UI panel with proper cleanup

### Package Structure
- `com.eric_eldard.ui.log` - Log components (LogLevel, LogEntry, LogPanel)
- `com.eric_eldard.ui.renderer` - Custom renderers (UnwrapParagraphRenderer)
- `com.eric_eldard.voice` - Voice services
- `com.eric_eldard.util` - Utilities (JunieConfigReader)

## Key Features

### Voice Processing
- ✅ Real-time audio capture and OpenAI Realtime API integration
- ✅ Rate limiting (500ms general, 1000ms transcription) with exponential backoff
- ✅ Audio buffer management with batch transmission
- ✅ Microphone auto-muting during AI responses to prevent feedback loops
- ✅ Push-to-interrupt capability during AI responses

### Request Processing
- ✅ **Three Request Types Detected**:
  - Code requests: Direct code generation
  - Prompt requests: Creates implementation prompts in `.junie/current-prompt.md`
  - Non-generative requests: Regular conversation
- ✅ **Dual Processing**: Both voice and text inputs processed through voice API and responses API
- ✅ **LLM Label Constants**: Centralized constants for `[code-request]`, `[prompt-request]`, `[non-generative-request]`

### UI/UX
- ✅ Polymorphic log panel structure with HTML rendering for transcript messages
- ✅ Message prefixes: `👤 User:` and `🤖 Agent:` with emoji constants
- ✅ Auto-scroll to bottom on log level changes
- ✅ Visual microphone states: 🎤 (recording), 🎤🚫 (not recording), 🎤🔇 (muted during AI response)

## Configuration
- OpenAI API key via `openai.api.key` system property or `OPENAI_API_KEY` environment variable
- Audio permissions may be required on some systems
- Dependencies: OkHttp, Jackson, SLF4J, Java Sound API

## Recent Technical Fixes
- ✅ **Threading Compliance**: VFS operations use `ApplicationManager.invokeLater()` for proper EDT execution
- ✅ **File Writing**: Prompt files use try-with-resources with explicit flush and VFS refresh for immediate IntelliJ visibility
- ✅ **Voice Agent Instructions**: Brief acknowledgments for prompts/code instead of speaking full content
- ✅ **Empty Input Validation**: Prevents processing of empty/whitespace-only voice transcripts

## Removed Features
- ❌ **Web Search**: Completely removed due to OpenAI API incompatibility
- ❌ **Echo Cancellation**: Removed NormalizedLeastMeansSquareFilter - replaced with microphone muting approach

## Build System
- Gradle build system with IntelliJ Platform Plugin development
- Runs successfully with `./gradlew runIde`
- All tests pass (3/3 VoiceAssistantPanelTest)