# Build
```
./gradlew buildPlugin
```

# TODO
1. Allow interruption _w/o push-to-interrupt_ (echo cancellation)
2. Fix "code blocks not block-level elements"
3. Clean up log messages

# Vibe Coding
1. Claude 4 increases viability significantly
2. Strongest at solving technical issues; less so at forging new ground and business logic 
3. Doesn't understand the right things to test, especially for sensory output (UI, audio, etc)
4. Growing codebase... 
    - becomes gross quickly (confused designs & patterns)
    - brings back the same regressive bugs over and over
    - becomes harder for the agent to manage
    - rapidly slows development pace
5. Great for prototyping, not yet production-ready
6. Burns through credits fast
7. Code needs cleanup
    - Agent appends to same files, even for unrelated functionality, and even for new classes
    - Design patterns become confused; much tight coupling, but also indirection
    - Doesn't use constants
    - Doesn't reuse code
    - Agent forgets to clean up unused code (can be reminded via prompt/spec)
8. Agent needs memory to track work and lessons-learned
9. Overall, feels A LOT like working with a junior developer, especially one who is copy-pasting code from Stack
   Overflow that they don't understand; requires very similar instruction and correction


