# Eye of the Beholder II - Compose Multiplatform Remake

This project aims to bring the classic first-person dungeon crawler to modern platforms while staying faithful to the original VGA DOS experience. Built entirely in Kotlin, it runs on Android, iOS, Desktop, and Web.

It started in Flutter, as a way to learn it, and moved to Compose Multiplatform to go faster. Thanks to Claude Code there is now something to actually look at — and to play, in your browser:

**[eob2.pelotasplus.dev →](https://eob2.pelotasplus.dev/)**

## Features

- Binary file parsing for original game assets (MAZ, VCN, VMP, PAL, INF, CPS, DEC, DCR, ITEM.DAT)
- LCW and run-length decompression
- 3D viewport rendering with wall depth, decorations, doors, items and monsters
- Level script interpreter (doors, triggers, plates, spawns, set pieces)
- Combat, items, resting, saving and loading
- Full-screen cut scenes, the ending sequence and the credit roll
- AdLib sound rendered ahead of time to samples
- A debug panel: jump to any level and entry point, walk through walls, freeze
  the monsters, and a viewer for the CPS graphics
- **Spells (in progress)** — wands, scrolls and the spells the dungeon casts at
  the party all work; what is missing is the party's own spellbook: memorising
  at rest, praying, scribing scrolls, and the casting UI that goes with them
- Bugs, bugs, and more bugs. Help me squash them!

## Platforms

| Platform | Status |
|----------|--------|
| **[Web (Wasm/JS)](https://eob2.pelotasplus.dev/)** | Deployed and played — the only one that gets tested |
| Desktop (JVM) | Built, not play-tested |
| Android | Built, not play-tested |
| iOS | Built, not play-tested |

Everything is one Kotlin Multiplatform codebase, so all four targets build — but
the web build is the one actually played, and it is where any bug will have been
found. The other three are expected to work rather than known to.

## Quick Start

```bash
# Run desktop application
./gradlew :composeApp:run

# Run the web build, rebuilding on save
./gradlew :composeApp:wasmJsBrowserDevelopmentRun --continuous
```

### Tests

Most of the suite reads the game's own data files, which are not in this
repository, so there are two ways to run it:

```bash
# Everything, for a checkout with the game files beside it
./gradlew :composeApp:jvmTest

# Only what needs no game data - what CI can run
./gradlew :composeApp:jvmTest -PwithoutGameData
```

Rendering is guarded by golden-image tests: each one draws a known frame with
the real assets and compares it byte for byte against a reference PNG in
`composeApp/src/jvmTest/goldens/`.

## Credits

This project would not have been possible without the people who worked out
how Westwood's file formats and engine behave, and then wrote it all down.

- **[ScummVM](https://www.scummvm.org/)** — its `engines/kyra/` is a
  reverse-engineered reimplementation of the Eye of the Beholder engines, and
  it is what made the hard bugs findable. When a wall drew wrong, or a script
  did something that made no sense, there was somewhere to go and see how it
  was supposed to behave. Decades of patient work by people who did it for the
  love of these games, and freely given away; this project would have stalled a
  dozen times over without it. ScummVM is licensed GPL-2.0-or-later; it is used
  here as documentation rather than as a source of code.
- **[The Shikadi ModdingWiki](https://moddingwiki.shikadi.net/wiki/Category:Westwood_Studios_File_Formats)**
  — community documentation of the Westwood Studios file formats, including
  the LCW/Format80 compression these assets are packed with.
- **[Eye of the Beholder file formats](https://bitbucket.org/JackAsser/eye-of-the-beholder-file-formats)**
  by JackAsser — notes on the MAZ, VMP, VCN, DEC and INF formats specifically.

And, of course, **Westwood Associates** and **SSI**, who made the game in 1991.

## Legal

This is a fan project for educational purposes. The game's own data files are
not in this repository — you need your own copy of Eye of the Beholder II to
run it.

Two things derived from the game are here, and both are here because the tests
need them:

- the golden PNGs in `composeApp/src/jvmTest/goldens/`, which are frames this
  renderer drew from the original artwork and are compared against to catch
  rendering regressions;
- short strings from the game's own text — screen messages, and the dialogue
  of the ending — quoted in the source so that what is drawn can be asserted.

*Eye of the Beholder* and *Advanced Dungeons & Dragons* are trademarks of their
respective owners; this project is not affiliated with or endorsed by any of
them.

## License

MIT, covering the code in this repository. The rendered frames and quoted
strings described above are derived from the original game and are not ours to
license.
