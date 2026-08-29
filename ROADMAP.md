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

- [ ] **1. Combat** — the blow and the monster's turn are written; what is left rides on them
  - [ ] 1a. Status attacks on a landed blow: poison, paralysis, a pocket picked — **S**
  - [ ] 1b. Thrown and fired weapons; the only route to `NO AMMO` — **L**
  - [ ] 1c. Monsters casting spells — **L**
  - [ ] 1d. `HACK` and `BASH`, which want a wall that gives under a weapon — **S**, and no floor has one
  - [ ] 1e. The `SetFlag.MonsterFlag` bits nobody has found a meaning for — **XS** each
- [ ] **2. A monster's blow does not stop the world** — the original freezes every other clock for the length of one — **S**, but blocked: the pause list names one monster clock twice and another not at all, and which of those is the bug decides whether this is worth writing
- [ ] **3. The last two things a monster mode can do** — fear, and giving up on a destination. Both have a branch waiting and nothing to trigger them — **S**
- [ ] **4. What a script can ask about a thing by name** — needs ITEM.DAT names threaded through — **M**
- [ ] **5. The words on the buttons are English constants** — they live in `START.EXE` — **L**
- [ ] **6. A script reads the party where it left them** — and stops writing the whole world back — **M**
- [ ] **7. The screen is composed a boxed pixel at a time** — 3.9ms of a 4.2ms frame — **M**
- [ ] **8. Saved games on a server** — `SaveStore` is already the seam — **L**
- [ ] **9. The rest of the audio** — mostly waiting on the features that would make the noise
  - [ ] 9a. The music, which is screen work before it is sound work — **M**
- [ ] **10. Somebody met when the party are already six** — who has to leave to let them in — **S**

## In full

- **Combat.** A monster can be killed now, and the party can do it: a hand
  swings, rolls against armour, takes hit points off, and the thing flashes and
  leaves the world when they run out.

  Two of the six things a slot can report are unreachable: `HACK` and `BASH`,
  which are what it says when a weapon is *swung* at a wall with nothing in
  front of it. That is a different path from using a weapon on one, which is
  written: a wall answers a swing only where its special type is 8 or 9, and
  all that decides is which of the two words appears — the wall that actually
  gives is special type 255, and no floor so far has one. `NO AMMO` needs
  something that fires.

  Two things worth not re-deriving. There is no sound for a blow landing or a
  monster dying: this game plays one sound for the swing and nothing else, and
  the two track numbers a monster carries are what *it* sounds like attacking
  and moving, which belong to a monster taking its turn. And a struck monster
  flashes for a moment rather than fading, so it is one wait and one redraw and
  not a clock.

  Armour is worked out from what is worn now, which is worth knowing for what
  it explains rather than for what is left to do: the quick-start party are at
  0 and 1, and a wolf's to-hit number of 19 means it needs an 18 or better.
  Three of them being nearly untouchable by an animal is the arithmetic working.

  Riding on a landed blow there are the status attacks a kind can carry:
  poison, paralysis, and having something taken out of a pocket. Each is a flag
  on the kind and a branch nobody has written.

  Thrown and fired weapons are a piece of their own — nothing launches anything
  yet — and so is a monster casting a spell, which is the other half of what
  the clerics ought to be able to do.

  `SetFlag.MonsterFlag` carries one bit anybody has found a meaning for, the
  one that rouses. The rest still warn rather than falling through the same
  `else` as everything uninteresting, and each wants whatever names it.

- **A monster's blow does not stop the world.** An attack on the square
  straight in front of the party freezes every other clock in the original for
  as long as it takes: the other monsters' turn timers, the doors, the
  character timers, and the party. Here only the party are held, so the rest
  carry on underneath it.

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

- **The last two things a monster mode can do.** The modes are written and sit
  behind "Monsters: hunt" in the Debug menu: hunting, wall-following either
  way, pacing, straying either way, and sleeping until the party come near.

  Two of the same shape are not, and each has its branch waiting where the
  original has one. A **frightened** monster refuses any step that takes it
  nearer the party, and nothing frightens anything yet. And a monster **giving
  up** on a destination it cannot reach drops into straying, one side or the
  other at random — which cannot happen while the only destination anything is
  ever given is the party's own square.

  Three of the ten mode numbers are still one case between them, the one that
  does nothing. One of them counts down a spell; nothing tells them apart.

- **What a script can ask about a thing by name.** A lock is answered now — it
  can be shown what kind of thing the hand holds, what it is worth, and which
  slot it is — but `GetPointerItem` has two more questions in it, whether the
  held thing's name contains a word, identified or not. Nothing answers those,
  because names live in ITEM.DAT and the world a script is handed does not
  carry them. Whatever needs them will need the names threading through.

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
  before it is sound work. A browser will not sound until the player has
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

- **Somebody met when the party are already six.** Whoever is met asks to come
  along, and the answer is only ever taken when there is a slot free. The
  original asks a second question when there is not: it says the party is full
  and puts the six names up with a way out beside them, and whoever is picked
  leaves so the newcomer can take their place. Picking the way out is the join
  refused, and what was said to being let along has already been read by then.

  So it is one dialogue of seven answers, and dropping a champion — which is
  the half that does not exist yet. A champion dropped is not a champion
  deleted: they keep their slot's items and the party's own table has to let go
  of them without the item table losing what they carried, which is the only
  part of this worth being careful about.

- **Which of the eighteen the renderer is drawing into is a bare Int.** A
  square in the maze is a `Location` and an absolute thing; where that square
  lands on screen is a slot in the view cone, means nothing without knowing
  where the party stand and which way they face, and is passed everywhere as
  `blockIndex: Int`. It is exactly the sort of small integer space the rest of
  the codebase gives a type to — palette indices, tile indices, scale steps —
  and there is nothing to stop one being handed where a maze coordinate was
  meant, or the other way about.

  Eleven declarations and about fifty uses, across the viewport, the view
  windows, the block table and the repository that drives them. It is a rename
  and nothing more, which is the argument for doing it and the reason it keeps
  not being done.

- **A champion dead for good still looks like themselves.** Below nothing a
  champion is lying there dying, and at ten below they are past raising — and
  the game draws that. At exactly ten below the portrait is not tinted or
  crossed out but replaced: a different picture goes in the box and nothing
  else is drawn over it, no name and no bar. Between the two, above ten below
  but under one, the face stays and a grid is hatched across it.

  Both are cut from `DECORATE.CPS`, which is already loaded for the teleporter
  blobs — the dead one at nought by eighty-eight, the grid beside it at
  thirty-two, both thirty-two square. So there is no new art to find, only two
  rectangles and the rule that the box holds nothing else once the first of
  them is in it.

  It only became reachable when a blow was given a floor under it: a champion
  who could be driven to minus twenty-seven never matched minus ten exactly,
  and the picture would have sat there unused.

