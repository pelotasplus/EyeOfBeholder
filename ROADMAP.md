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

- **Audio.** The game ships ten AdLib banks (`.ADL`, already in the manifest)
  with a `.SND` beside each, and they are not samples: a bank is a score for a
  Yamaha OPL2 chip, so playing one means both a synthesiser to emulate the chip
  and the driver that reads the bank and drives it. That is the expensive part
  — roughly four thousand lines in the reference implementation — and it is why
  this is parked rather than half started. On top of it goes a platform seam
  for pushing samples out, the same `expect`/`actual` shape as `SaveStore`:
  Web Audio, `AudioTrack`, `javax.sound.sampled`, `AVAudioEngine`. The cheap
  alternative worth weighing first is rendering the ten tracks to ordinary
  audio files offline and playing those, which buys the music for almost
  nothing and buys none of the effects.
