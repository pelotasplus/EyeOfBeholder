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

- **Opening a door with a key.** A keyhole is a decoration like any other and
  is already clicked correctly; what fails is everything the script asks next.
  `Conditional.GetPointerItem` — what kind of thing is in the hand, what it is
  worth, whether the hand holds anything at all — is read and named and has no
  branch in the runner, and neither does `ConsumeItem`, which is how the key is
  used up. So the lock is asked what the player is holding and cannot be told.

  Level 2 at **16x19** is the place to try it, and it exercises every branch.
  Stand at 17x19 facing west: the keyhole is decoration 48 on the east face of
  16x19, and clicking it runs the square's script, which works the door at
  18x19. The script asks, in order, for a key of type 38 whose value is 99 —
  sound, door, and the key deleted out of the hand; then for any other key of
  type 38 — *"the key doesn't fit this lock."* and a different sound; then for
  lock picks, type 28, which want a thief in the party and then one throw in
  three, and on success open the door with *"you pick the lock!"*.

  Two keys are within reach on level 2 for the wrong-key path — the Skull Key
  at 9x4 and the Dark Moon Key at 14x3 — so it can be seen refusing before
  anything can make it open.

  Nothing opens it with a key. No item in ITEM.DAT is worth the 99 this lock
  asks for; the keys run from 1 to 13. Eight scripts across the game make a
  key and every one of them is a plain copy that keeps the number it copied,
  so no script mints one either. The lock is picked or it stays shut, and 99
  reads as a way of writing that down rather than as a key nobody has found.
  Worth confirming against the original before anyone builds to it, since a
  key handed over some way that is not a script would not show up in that
  count.

  What the hand is asked is settled, though: the original reads the item the
  hand holds and takes its type, its slot number, or — for anything else asked
  — its value, which is what the three named conditions already mean. Note
  that an empty hand is slot zero rather than nothing at all, so a script
  asking an empty hand what it holds is answered with whatever the table keeps
  in its first slot; that decides what "the hand is empty" has to mean here.

- **The party's own square.** Nothing is drawn on the square the party stand
  on. `viewBlockRows` stops one row ahead and `blockScreenCoords` carries the
  three blocks of the near row marked unused, so anything lying at their feet
  is invisible until they step off it and it becomes the square in front.

  A script is the ordinary way to meet this: `NewItem`'s second special
  destination puts a thing down underfoot, which is how the old woman on level
  4 leaves her parchment, and the player sees an empty floor until they walk
  away and turn round. The screen coordinates for the row are already in the
  table, taken from the original; what is missing is drawing it, and it wants
  goldens of its own because everything at that distance is drawn largest.

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
