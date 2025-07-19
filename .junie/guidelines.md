# Agent Instructions
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

## Permanent memory
- You will read [project-functionality.md](project-functionality.md) to understand the existing features and avoid regressive defects
- You will read the instructions in [project-memory.md](project-memory.md) and make use of this space as needed
- If the file [developer-preferences.md](developer-preferences.md) exists, you will use this space to store preferences
  of the developer.
- Keep these memory files current with the state of the project and compact them as needed.

## Cleanup Mode
When asked to perform code cleanup, do the following:

1. Remove unused methods
2. Remove unused classes, including inner classes
3. Remove unused variables
4. Remove unused fields
5. Remove tests for any methods or classes that were removed
6. Remove temporary tests you created during development
7. Remove temporary Markdown files you created
8. Remove TODOs for complete items
9. Compact your project-memory, where appropriate

Respect the order of these removals; the order is designed for efficiency.
Examples
- Removing methods before classes may reveal newly unused classes
- Removing classes before fields may preclude the need to remove some unused fields

## Acknowledgement
- Everytime you read this file, send an acknowledgement message that you have done so.
- Do not summarize this file in the acknowledgement.