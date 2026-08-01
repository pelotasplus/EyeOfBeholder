# Code Style

**Do not write comments that restate the code.** A comment that says what the
next line already says out loud is noise — delete it. Examples of comments that
must NOT be written:

```kotlin
// file picker keeps a fixed column on the left
LazyColumn(modifier = Modifier.width(FILE_LIST_WIDTH)) { ... }

// same filled style as the other buttons
Button(onClick = { ... })
```

Write a comment only when it carries information the code cannot: why a
non-obvious constant or workaround exists, a reference to an original-game
table or ScummVM symbol, or a subtle invariant a reader would otherwise get
wrong. Examples worth keeping:

```kotlin
// the web build's default font has no glyph for "▾"
Text("Debug")

// integer scale only, so the pixels stay square
val scaleFactor = minOf(...).toInt().coerceAtLeast(1)
```

KDoc on public types and rendering tables (citing `kEoB2Dsc*` names and the
like) stays — that is reference material, not narration.
