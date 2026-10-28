# Roadmap

Things worth doing that are not being done yet, and what stands in the way of
each.

## At a glance

One line per entry below, in the same order, so a thing can be picked without
reading the lot. Sizes are rough: XS an hour, S an afternoon, M a few days, L a
piece of work in its own right.

A ticked box is a cue to delete the entry it points at rather than something to
leave ticked — this file is what is *not* done, and a finished thing belongs in
the history instead.

- [ ] **1. The `SetFlag.MonsterFlag` bits nobody has found a meaning for** — one rouses and the rest warn — **XS** each
- [ ] **2. A monster's blow does not stop the whole world** — the other monsters' turns are held now; the doors and the character timers are not — **S**, and still blocked: the pause list names one monster clock twice and another not at all, and which of those is the bug decides whether the rest is worth writing
- [ ] **3. The last thing a monster mode can do** — giving up on a destination, which nothing has to give up on while the only destination is the party — **S**
- [ ] **4. What a script can ask about a thing by name** — the names exist now; the runner is not handed them — **M**
- [ ] **5. The words on the buttons are English constants** — they live in `START.EXE` — **L**
- [ ] **6. A script reads the party where it left them** — and stops writing the whole world back — **M**
- [ ] **7. The screen is composed a boxed pixel at a time** — 3.9ms of a 4.2ms frame — **M**
- [ ] **8. Saved games on a server** — `SaveStore` is already the seam — **L**
- [ ] **9. The rest of the audio** — the intro's music, which is screen work before it is sound work — **M**
- [ ] **10. The nightmare on the third floor** — a rest there is a scene and no rest — **S**
- [ ] **11. Insal leaving in the night** — after a long rest, with some of the party's things — **S**
- [ ] **12. Which of the eighteen the renderer is drawing into is a bare Int** — **S**

## In full

- **The `SetFlag.MonsterFlag` bits nobody has found a meaning for.** One bit
  has a meaning, the one that rouses. The rest still warn rather than falling
  through the same `else` as everything uninteresting, and each wants whatever
  names it.

  It is the last of combat that is not written. A hand swings and rolls against
  armour, the status attacks a kind carries ride on a blow that lands, a bow or
  a sling fires from the hand it is held in and says `NO AMMO` with nothing to
  fire, and a monster shoots and casts what its own table gives it.

- **A monster's blow does not stop the whole world.** An attack on the square
  straight in front of the party freezes every other clock in the original for
  as long as it takes: the other monsters' turn timers, the doors, the
  character timers, and the party. Here the party and the other monsters' turns
  are held; the doors and the character timers carry on underneath it.

  It is about eight ticks, and the turn log is what it should be judged
  against.

  **It will not fix level 5's clerics, and one thing has to be settled before
  it is written.** The four monster clocks are numbered 0x20 to 0x23. The list
  of what an attack pauses names 0x20, 0x21 and **0x22 twice**, and never names
  0x23 — so the fourth group is not paused at all. The clerics are slots 16 and
  17, whose groups work out as 2 and 3, and both groups start at the same
  offset with the same period, so they always come round together and the one
  that would be held is exactly the one the list forgets.

  So either that duplicate is a bug in the original, in which case the pair
  really do strike as one and this changes nothing for them; or it is a slip in
  the reference implementation's transcription and the real list names 0x23, in
  which case this is precisely the fix for that fight. Nothing here can tell
  the two apart — it wants the original binary, or somebody playing the real
  thing and watching whether the pair land together.

  Written faithfully it pauses 0, 1 and 2 only, which is right for most
  monsters on most floors and does nothing whatever for the temple.

- **The last thing a monster mode can do.** The modes are written and sit
  behind "Monsters: hunt" in the Debug menu: hunting, wall-following either
  way, pacing, straying either way, and sleeping until the party come near.
  Being frightened is written too, by the fear spell.

  One of the same shape is not. A monster **giving up** on a destination it
  cannot reach drops into straying, one side or the other at random — which
  cannot happen while the only destination anything is ever given is the
  party's own square, and there is nothing here that holds a destination to
  give up on. So it is that notion first and the mode after it.

  Three of the ten mode numbers are still one case between them, the one that
  does nothing. One of them counts down a spell; nothing tells them apart.

- **What a script can ask about a thing by name.** A lock is answered now — it
  can be shown what kind of thing the hand holds, what it is worth, and which
  slot it is — but `GetPointerItem` has two more questions in it, whether the
  held thing's name contains a word, identified or not. Both are parsed and
  printed, and neither is answered: the names are read now, but the runner is
  handed the kinds alone and the thing a script is shown is a bare item, so
  there is nothing there to match a word against.

- **The words on the buttons are English constants.** The word that turns a
  page and the word that closes one are written into the source, where the
  original reads them from a table in its own executable — so a copy of the
  game in another language says something else and this would not.

  That table is the catch. It is in `START.EXE` and in no data file, at an
  offset that differs between releases, which is why the reference
  implementation ships a tool that extracts it per version rather than reading
  it at run time. Doing this properly means shipping the binary as an asset and
  finding the table in it; there is no cheap version, and the two words are the
  smallest part of what is in there.

- **A script reads the party where it left them.** The party can walk away
  while a script runs, and the script goes on holding the world it was handed —
  so one that asks where they are, mid-run, is told where they were when it
  started. Every script in the game that asks is one that also moves them, and
  moving them takes them over, so nothing shipped can currently notice. Fixing
  it means the runner reading the party live rather than out of its own copy.

  The other half of the same shape is worse and easy to walk into: the runner
  writes its whole copy back when it ends, so anything else that changed in
  between is quietly undone. Anything that ticks while a script runs therefore
  cannot live in the world the runner holds — the party's step counter had to
  be moved out for exactly this, having been restored to mid-step every time a
  script finished, which left the party unable to walk. Reading live would fix
  both.

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
  button are all heard.

  A swing, a champion taking damage or going down, gaining a level, turning
  undead, eating, and anything thrown or fired are all heard now, and a monster
  plays its own two track numbers — what it sounds like attacking and what it
  sounds like moving — attenuated by how far off it is.

  Two numbers are easy to read wrong, so: 11 and 26 are what a thrown
  or fired object sounds like — both come from launching one, 26 when it is of
  the type darts are — and neither is an item being set down. Nothing sounds
  when an item is put on the floor, because the game has no such sound.

  Doors used to be listed here as another of those, on the grounds that one
  slides in silence and the button beside it is the whole of the noise. That
  was wrong: 3 is a door travelling up, 4 travelling down and 5 the position a
  closing one lands on, and the original plays one at every position rather
  than once for the journey, which is what makes a stone door grind. All three
  are wired now. Forcing a door really does only print a line.

  The intro's music is what is left. The finale's tune and the credits' both
  play; the long tune that belongs to the intro waits on the intro, which is
  screen work before it is sound work. A browser will not sound until the player has
  touched the page, which is handled: the first event that is not the game
  starting wakes the speaker, whatever it was — a key, a click, an answer —
  since it is the pressing rather than what it did that a browser waits for.

  What is given up by rendering rather than synthesising is per-effect volume
  ramps and anything the driver triggers on its own. Taking that back means the
  expensive route — a synthesiser and the driver that drives it, roughly four
  thousand lines in the reference implementation — and it costs nothing
  architecturally to change one's mind later, because it would replace what
  fills a buffer and `AudioSink` would not notice. The rendered clips are what
  it would be proved right against.

- **The nightmare on the third floor.** Asking to rest anywhere on the third
  floor plays a scene instead, and the rest does not happen at all — it is
  asked before anything else about resting, monsters near or not. Nothing
  answers it here, so a party sleeps there like anywhere else. The scene is
  the work; the rule is one line in front of the rest.

- **Insal leaving in the night.** A rest of more than four hours, with Insal
  in a party of five or more, ends with him gone: taken out of the party, the
  slots closed up behind him, some of what the party carry gone with him and
  a note left on the floor in his place, and two lines said about it. He can
  be met and taken along now; nothing takes him away again, so the one
  companion the game means to lose is a companion for good. Wants the
  stripping table read rather than guessed, and the note's text.

- **Which of the eighteen the renderer is drawing into is a bare Int.** A
  square in the maze is a `Location` and an absolute thing; where that square
  lands on screen is a slot in the view cone, means nothing without knowing
  where the party stand and which way they face, and is passed everywhere as
  `blockIndex: Int`. It is exactly the sort of small integer space the rest of
  the codebase gives a type to — palette indices, tile indices, scale steps —
  and there is nothing to stop one being handed where a maze coordinate was
  meant, or the other way about.

  A handful of declarations and about fifty uses, across the viewport, the view
  windows, the block table and the repository that drives them. It is a rename
  and nothing more, which is the argument for doing it and the reason it keeps
  not being done.

