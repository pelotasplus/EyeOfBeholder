# Code Style

## Edit files with the editing tools, never with a script

Source files are changed with the Edit and Write tools. Never with `python3`,
a shell heredoc, `sed`, or `awk` — not even for a bulk rename, where many Edit
calls are still the right answer.

A script puts an extra escaping layer between the intent and the file, and
Kotlin is unusually good at hiding the damage. A heredoc wrote this:

```kotlin
cpsRepository.loadCps("${'$'}{instruction.pictureName.uppercase()}.CPS")
```

`${'$'}` is valid Kotlin for a literal dollar, so it compiled, passed review at
a glance, and asked the server for a file called
`${instruction.pictureName.uppercase()}.CPS` — a 404 that only showed up in
the browser at runtime.

**Do not write comments that restate the code.** A comment that says what the
next line already says out loud is noise — delete it. Examples of comments that
must NOT be written:

```kotlin
// file picker keeps a fixed column on the left
LazyColumn(modifier = Modifier.width(FILE_LIST_WIDTH)) { ... }

// same filled style as the other buttons
Button(onClick = { ... })
```

**This applies to KDoc too.** A doc comment is not documentation-by-default —
it is subject to the same test, and a well-named declaration usually fails it.
Ask whether you would write the comment if the name were something else:

```kotlin
// no — the name already says it
/** The bigger font the menus are set in. */
private val menuFont: Font? = null,

/** What clicking a line of a menu means. */
sealed class MenuChoice
```

You would not write `/** The font monsters are drawn with */` over
`monsterFont`, and `menuFont` is no different. Where a KDoc does earn its
place, it is for the same reasons a comment does — a unit that the type does
not give (`savedAt` being milliseconds), a contract that is not visible (an
absent slot meaning an empty one), or why a number is that number.

Write a comment only when it carries information the code cannot: why a
non-obvious constant or workaround exists, or a subtle invariant a reader would
otherwise get wrong. Examples worth keeping:

```kotlin
// the web build's default font has no glyph for "▾"
Text("Debug")

// integer scale only, so the pixels stay square
val scaleFactor = minOf(...).toInt().coerceAtLeast(1)
```

## Don't invoke the original, or any other source

Never write "the original does X", "the engine does X", or anything that
settles a question by pointing somewhere the reader is not. Nobody
cross-references while reading code, and a comment that only makes sense with
another codebase open explains nothing to the person who has this one open.

That includes the cases that look like they earn it:

- **A table that cannot be derived.** The useful fact is not where it came
  from, it is that it is *data* — say that reasoning about it will not check
  it, that a digit changed by eye has nothing to catch it, and what would
  notice if one were.
- **A rule surprising enough to look like a bug.** The useful fact is not that
  somebody else does it too, it is why it is deliberate and what breaks if it
  is "fixed". State the rule and the consequence.

Everywhere else, say what the code does. The comment is about this code, and
the reader is here rather than in a reference implementation:

```kotlin
// no
// The original answers a click by which of four strips of floor it fell in,
// and refuses the whole switch rather than closing on a monster.

// yes
// A click reaches whichever of the four strips of floor it fell in, and a
// switch is refused whole rather than half worked.
```

## Don't name ScummVM symbols in comments

A reader without the ScummVM checkout open can do nothing with
`drawSequenceBitmap`, `_dlgButtonPosX_Def` or `OldDOSFont::load`, and a reader
with it open did not need the pointer. Say what the number *means* instead, and
say it in as few words as the constant needs:

```kotlin
// no
/** Where the frame and the speaker go, from `drawSequenceBitmap`'s `frame*` tables. */

// yes
/** Where the frame and the speaker go. */
```

What is worth recording is that a table is *data* — that it cannot be worked
out, so nobody re-derives it or nudges it by eye — and that belongs once, in
the type's KDoc, not on every constant. When a whole binary layout is being
read, write out the layout itself: the offsets and what each one means. That
is what a reader needs, and it is in front of them.

## Don't explain a general mechanism with one level's story

A KDoc on a shared type, or a comment on a branch that handles one of 29
opcodes, is read by someone who has never seen the level it was written from.
The mechanism outlives the anecdote:

```kotlin
// no
// A speech waits to be read before the script goes on, and what comes next
// can be the point: the clerics slam the door only once their roar has been
// acknowledged.

// yes
// A speech is read rather than answered, and the script waits for that: what
// follows a speech can be the point of it.
```

The same goes for war stories about code that no longer exists — "guessing an
answer would run a branch nobody chose, which is how walking past the priest
used to throw the party down a level" describes a design that was replaced.
State the rule the code follows now.

The exception is a file whose subject really is the level data: `GameFlags.kt`
records what each level's bits mean, and naming levels there is the point.

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
