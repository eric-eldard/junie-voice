# Agent Instructions
- **You are not allowed to modify this document**
- Ask clarifying questions until you are confident you understand the user's request
- Do not generate code that has not been requested. For example, when asked to create a service, do not add methods to
  the service that are not specifically asked for.

## Response format
When delivering any response after changing project code, use the following format:

**Simple Requests:**
```
**What Changed:** [Key improvements]

**Optimized Prompt:**
[An example of how you could've been prompted better to produce this output with a single prompt]
```

**Complex Requests:**
```
**Key Improvements:**
[Primary changes and benefits]

**Techniques Applied:**
[Brief mention]

**Pro Tip:**
[Usage guidance]

**Optimized Prompt:**
[An example of how you could've been prompted better to produce this output with a single prompt]
```

## Memory
- Read all of the following memory files
- Keep these memory files current with the state of the project and compact them as needed

### About the Project
- **[project-functionality.md](project-functionality.md)**
- Describes the application's current functionality in non-technical terms
- Helps avoid regressive defects

### Project Stack
- **[project-stack.md](project-stack.md)**
- You will adhere to using these technologies unless they cannot accomplish your goal

### Project Memory
- **[project-memory.md](project-memory.md)**

#### Primary Functions
1. **Long-term Memory Storage** – Acts as a persistent memory system where AI agents can store important project
   insights, patterns, and learnings that would otherwise be lost between sessions—this is your space

2. **Project Context Documentation** – Provides a space to document:
  - Feature explanations which don't belong in project-functionality.md (overly nuanced or not user-focused
    explanations)
  - Planned features and functionality
  - Technical notes that don't belong in code comments

3. **Knowledge Transfer** – Helps future AI agent interactions by preserving:
  - "Lessons learned" from previous development work
  - Project-specific patterns and conventions
  - Important architectural decisions and rationale

#### Key Characteristics
- **Agent-Managed**: Primarily maintained by AI agents, though developers can edit it to correct inaccuracies
- **Markdown Format**: Uses standard Markdown for easy readability and formatting
- **Living Document**: Content is subject to change as the project evolves
- **Supplementary to Code**: Contains information that's valuable but not appropriate for code comments
- **Instructions**: The agent cannot alter the About section of this document

### Current Prompt
- Optional file: **[current-prompt.md](current-prompt.md)**
  - If this file doesn't exist, skip it
- If this file does exist, read it to discover **what the user wants you to build**
- These instructions are **central to this request** and must be heeded
- Whenever you read the contents of this file, **truncate the entire file** to prepare it for the next request

### Developer Preferences
- Optional file: **[developer-preferences.md](developer-preferences.md)**
  - If this file doesn't exist, skip it

#### Primary Function
- **Long-term Memory Storage**: Acts as a persistent memory system where AI agents can store developer-specific
  preferences for agent interaction and code development that would otherwise be lost between sessions

#### Key Characteristics
- **Agent-Managed**: Primarily maintained by AI agents, though the developer can edit it to correct inaccuracies
- **Markdown Format**: Uses standard markdown for easy readability and formatting
- **Living Document**: Content is subject to change as the developer-agent relationship evolves
- **Not Versioned**: Because this file contains developer preferences, and not team standards, it is never added to
  version control


## Cleanup Mode
When asked to perform code cleanup, do the following:

1. Remove unused methods
2. Remove unused classes, including inner classes
3. Remove unused variables
4. Remove unused fields
5. Remove tests for any methods or classes that were removed
6. Remove temporary tests you created during development
7. Remove temporary Markdown files you created
8. Remove unused dependencies
9. Remove TODOs for complete items
10. Compact your project-memory, where appropriate

Adhere to the order of these removals; the order is designed for efficiency.

## Acknowledgement
- Everytime you read this file, send an acknowledgement message that you have done so
- Do not summarize this file, or any of the memory files, in the acknowledgement