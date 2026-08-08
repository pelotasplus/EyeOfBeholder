# Roadmap

Things worth doing that are not being done yet, and what stands in the way of
each.

- **Combat, or at least a monster that can die.** Monsters are read from a
  level, placed, drawn at their block and conjured by scripts, but nothing can
  touch them: `MonsterInstance` carries no hit points, and the only thing that
  ever happens to the world's monster list is another one being added to it.

  It is already being asked for. Level 5's clerics guard both of their scenes
  on whether anybody still stands on 13x8 — the approach at 13x9 and the
  doorway at 11x9, each also behind a level flag of its own — and both go quiet
  by themselves the moment the pair can be killed. Until then the party may
  choose to attack, and the two of them go on greeting and turning them away as
  though nothing had happened. The guards are right and the scripts need no
  changing; what they ask about is missing.

  Enough to satisfy them is less than a combat system: hit points on a monster,
  something that takes them off, and dropping a monster out of the list at
  zero. `BlockFlags` already counts the list, so the scripts need nothing.

  One thing to wire while passing: `SetFlag.MonsterFlag` is read and named and
  the runner has no branch for it, so a script rousing a monster does nothing
  and says nothing either — it falls through the same `else` as everything
  uninteresting. `SpecialEvent` warns when it is skipped, and this should too,
  or the next question about a monster that ignored a script gets asked from
  scratch.

- **What a script can ask about a thing by name.** A lock is answered now — it
  can be shown what kind of thing the hand holds, what it is worth, and which
  slot it is — but `GetPointerItem` has two more questions in it, whether the
  held thing's name contains a word, identified or not. Nothing answers those,
  because names live in ITEM.DAT and the world a script is handed does not
  carry them. Whatever needs them will need the names threading through.

- **What lies on the squares beside the party.** The party's own row is three
  squares: the one they stand on and one to either side. Their walls are drawn
  and so is what lies underfoot, but the two beside them hold nothing —
  `viewBlockRows` stops one row ahead and the own square is drawn by a call of
  its own, so no item on either side is ever reached.

  The original draws them, last of all and after everything in front. It is a
  narrow thing to see: their screen x is 128 either way from a viewport 176
  wide, so an icon there is a strip at the very edge, and the original works
  out the strip first and skips the square when nothing of it is left. What
  they never draw is a monster or a door — both are guarded on the square not
  being one of the party's own row — so only items are missing.

- **Reading a parchment where the original reads it.** A letter or a note goes
  up in the box a script speaks from, along the bottom of the screen. The
  original covers the view instead — a box 176 by 175 from the top left corner
  — and prints the page into that. It reads correctly and it is in the wrong
  place, which needs a drawing mode `PlayField` does not have and would replace
  the `parchment-read` golden.

  Two smaller things sit with it. A parchment whose value is negative is a map,
  a picture cut from the `MAP` sheet rather than a page of text, and nothing
  here draws one: reading it does nothing at all, silently. And the words on
  the button that turns a page and the one that closes it are English constants
  in the source, where the original reads them from its own resources, so they
  are wrong in any copy of the game that is not English.

- **Walls a level does not map.** Rendering level 5 near 16x6 logs `Wall index
  55 at (16,6) is not mapped by this level` and draws nothing there. Either
  the sublevel being shown is not the one that square belongs to, or the wall
  is a kind we are not reading. It is one warning from one square, which is
  either a rendering hole or a hint that `subLevelShowing` picks wrongly in
  that corner; nobody has looked.

- **A script reads the party where it left them.** The party can walk away
  while a script runs, and the script goes on holding the world it was handed —
  so one that asks where they are, mid-run, is told where they were when it
  started. Every script in the game that asks is one that also moves them, and
  moving them takes them over, so nothing shipped can currently notice. Fixing
  it means the runner reading the party live rather than out of its own copy.

- **The screen is composed a boxed pixel at a time.** Handing the finished
  pixels to the platform took rasterizing a frame from 23ms to under half a
  millisecond, and what is left is composing it: about 3.9ms of a 4.2ms frame
  on a desktop. `PlayField` allocates 64,000 boxed `RGB` objects per screen and
  `ViewPort` another 21,000, and `getRows` chunks them into 200 more lists
  every time. A packed `IntArray` would drop both and feed the upload path
  without the copy in between. The two `toImageBitmap` tests compare against
  the raw buffer, so the refactor has something to be proved against.

- **Saved games on a server.** `SaveStore` is already the seam — one interface,
  bound once per platform, dealing in text keyed by a slot — so a remote store
  is another implementation of the same four methods and nothing above it
  changes. Firebase fits a project that wants to stay a static bundle: sign up
  once anonymously against the Identity Toolkit REST endpoint, keep the refresh
  token, and `PATCH` a document per slot under `users/{uid}/saves/{slot}`, with
  a rules file saying a player may touch nothing but their own. One Ktor client
  in `commonMain` covers all five targets, which no Firebase SDK does. Local
  should stay the truth and remote a mirror, because drawing the Load Game list
  reads every slot and a heavily played world is not small. The catch is that
  on the web the refresh token lives in the same local storage the saves did,
  so an anonymous account is exactly as evictable as what it replaced — it only
  becomes durable when the player links a real sign-in to the *same* uid, and
  signing in fresh on a second device instead leaves two piles to reconcile.

- **The rest of the audio.** Sound is in: the ten AdLib banks were rendered
  track by track ahead of time, `AudioSink` puts sample buffers out on all five
  targets, and a script's Sound instruction, a step into a wall and a door's
  button are all heard. What is left is the part the rendering cannot do.

  What is still silent is silent because the thing that would make the noise
  does not exist yet, not because the sound is missing. Blows landing and
  missing, a champion taking damage or going down, gaining a level, turning
  undead, eating, and anything thrown or fired all have their track numbers
  waiting in every bank; each becomes one line at the site that finally
  implements it. Monsters carry their own two track numbers and no monster is
  heard at all.

  Two of those numbers are easy to read wrong, so: 11 and 26 are what a thrown
  or fired object sounds like — both come from launching one, 26 when it is of
  the type darts are — and neither is an item being set down. Nothing sounds
  when an item is put on the floor, because the game has no such sound.

  Doors used to be listed here as another of those, on the grounds that one
  slides in silence and the button beside it is the whole of the noise. That
  was wrong: 3 is a door travelling up, 4 travelling down and 5 the position a
  closing one lands on, and the original plays one at every position rather
  than once for the journey, which is what makes a stone door grind. All three
  are wired now. Forcing a door really does only print a line.

  Nothing plays the music, either. The long tunes are rendered and sitting
  there, but they belong to the intro and the finale, which is screen work
  before it is sound work. When a browser is what plays them, note that nothing
  sounds until the player has touched the page: the first key that moves the
  party has to be what wakes the context, and `WebAudioSink.wake` is there for
  it and is not called from anywhere.

  What is given up by rendering rather than synthesising is per-effect volume
  ramps and anything the driver triggers on its own. Taking that back means the
  expensive route — a synthesiser and the driver that drives it, roughly four
  thousand lines in the reference implementation — and it costs nothing
  architecturally to change one's mind later, because it would replace what
  fills a buffer and `AudioSink` would not notice. The rendered clips are what
  it would be proved right against.
