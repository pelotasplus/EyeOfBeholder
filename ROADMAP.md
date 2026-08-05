# Roadmap

Things worth doing that are not being done yet, and what stands in the way of
each.

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
  when an item is put on the floor, because the game has no such sound. Doors
  are the same kind of trap: one slides open in silence and forcing one only
  prints a line, so the click of the button beside it is the whole of what a
  door has to say.

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
