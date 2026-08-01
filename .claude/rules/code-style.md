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

## Types, not bare Ints

A domain quantity gets its own type. `Int` says nothing about what a value
means, and the codebase has many small integer spaces that are mutually
meaningless: palette indices, sub-palette indices, tile indices, item icon
ids, screen coordinates, scale steps, trigger flag words.

```kotlin
// no
data class Trigger(val location: Location, val flags: Int, ...)
fun colorOrTransparent(index: Int): RGB

// yes
data class Trigger(val location: Location, val flags: TriggerFlags, ...)
fun colorOrTransparent(index: PaletteIndex): RGB
```

Use `@JvmInline value class` for a single value (remember `import
kotlin.jvm.JvmInline` — it resolves implicitly only on the JVM target, so
without it the Wasm and JS builds break while `jvmTest` stays green), and a
sealed type when the value has cases. Put the operations on the type: a flag
word should answer `reactsTo(event)` rather than expose bit arithmetic to its
callers.

This is about readability, not memory. Bit-packing a flag word into an Int was
the original engine's constraint, not ours.
