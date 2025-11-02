# About
I've frequently used ChatGPT voice mode as a sounding board—but it doesn't have access to my code base, so the best I
can do is copy-paste snippets in and out of it. I use JetBrain's Claude 4-backed Junie as my coding agent of choice—
Claude 4 is, IMHO, the best model for coding tasks, and Junie lives where I live: in IntelliJ. But without voice
capability, I can't riff with Junie the way I do with ChatGPT.

Junie Voice is a little vibe-coding project (that is, 10 hours vibing...30 hours coding) which attempts to give Junie
that voice capability. It's multi-modal: I can talk, type, and share images with it. When I'm ready for code, Junie
Voice seemlessly passes requirements off to Junie. And it adds a feature I've always wished existed in ChatGPT:
simulatanous, multi-modal output. That is, it can say one thing and print another, which finally fixes the problem of
the voice agent speaking code solutions aloud.

Junie Voice is a hobby project and proof of concept, while the IntelliJ userbase waits on JetBrains to get their act
together to give Junie first-class voice capability. Fork if you like; I'd love a push back if you do anything cool
with it. And don't judge the code quality...there are a couple of vibed files I've never even opened 😅

# Build
```
./gradlew buildPlugin
```

# TODO
1. Stop sending audio when it's below human speech amplitude
2. Fix "code blocks not block-level elements"
3. Fix "mic button sometimes becomes permanently disabled"
4. Clean up log messages
5. Allow interruption _w/o push-to-interrupt_ (echo cancellation)
