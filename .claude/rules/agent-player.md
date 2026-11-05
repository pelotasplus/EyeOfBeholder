# The agent player

The game can be driven by an agent — you — through the `eob` MCP server
(`agentServer/`, registered in `.mcp.json`). This is how to play it, and how
to keep it working.

## Using it

- The server must be built: `./gradlew :agentServer:installDist`. A session
  started before it was built has no `eob` tools, and one started before a
  tool was added does not see that tool: build, then start a new session.
- Start the game yourself with the agent already on:
  `./gradlew :composeApp:run -Peob.agent=qa` (or `=on` for fair play only).
  That is the same consent as the Debug menu's **Agent** switch. Stopping the
  Gradle task leaves the game's JVM running — kill
  `pl.pelotasplus.eyeofbeholder.MainKt` before relaunching, or the old build
  keeps answering.
- Tools: `list_games`, `observe`, `frame`, `act`, `wait`, `release`, and the
  batches below.
- **The game stands still between your calls.** It runs only during `wait`.
  After a step or turn, `wait` about 250 ms before the next; after anything
  with an animation — a door, a spell — wait for it before judging the result.
  An `act` the game turns away answers `refused` with the reason.
- `observe` is what the party could know, and nothing more: fair play. Do not
  read the game's data files, the maze or ScummVM to decide a move while
  playing; that is the one thing this mode exists not to do. (Reading them to
  check whether something you *saw* is a bug is fine — that is QA.)
- `frame` is the screen as the player sees it. Use it for what `observe`
  cannot name: a decoration's picture, whether something looks wrong.
- `release` when done, so the game runs on its own.

## Batches, the map and checkpoints

The server's own tools, made of `act`, `wait` and `observe` and nothing
else, so they can do nothing a player could not. Each ends with the
observation it stopped on.

- `walk` — moves as a list; stops when one is refused or goes nowhere, a
  monster comes within two squares, a dialogue or menu comes up, or someone is
  struck. Says what was seen lying about on the way.
- `fight` — the front rank strikes until nothing stands in front; says what
  each hand showed (a number is a hit, MISS, CAN'T REACH).
- `wait_until` — `free`, `dialogue` or `no_dialogue`.
- `map` — the floor as drawn from what `observe` told, north up, with what was
  lying where. It is the party's own paper map, so it is fair; the
  walkthrough's grid does not line up with the maze and cannot stand in for it.
- `checkpoint` / `restore` — a named save through the camp menu, with a copy
  kept beyond the six slots. Map and checkpoints live in `~/.eob-agent/`
  (`EOB_AGENT_HOME` to move it), so a run outlives the session that began it.

## QA mode

Switched on by the person at the game ("Agent knows: everything (QA)" in the
Debug menu); until then the QA tools answer that it is off. It is for finding
what is missing or broken, and breaks fair play on purpose:

- `diagnostics` — the game's warnings and errors, and every place it reached
  something not written yet (a spell with no effect, a hand use that does
  nothing). Pass `after` to get only what is new.
- `inspect` — the whole world: every monster's hit points and mode, true names
  of what the party carry, the spells running.
- `setup` — shortcuts: teleport, give a spell (ready or to learn), give an item
  onto the pointer, spawn a monster ahead, heal.

A QA session sets each case up, plays it, compares what happened with what the
spell or item should do (the audit in the vault, ScummVM, the original's
tables), and writes the findings up — `diagnostics` says where to look, not
whether a number is right.

## Changing it

- The game side is `composeApp/.../agent/`: `AgentTime` (the clock),
  `Observation` (what is told), `AgentAction` (what can be done), `AgentLink`
  (the connection). The view model implements `AgentLink.Game`.
- Every refusal comes from `turnedAway` in the view model — the same checks a
  person's click goes through. Add a new reason there, not in `act`.
- What `observe` tells must stay what a player could know: describe a wall by
  how it looks, never by what it does (an illusory wall is "wall"), leave out
  monsters the party cannot see, name items as the party know them.
- A thing the player does by clicking a place gets a named action that goes
  through the same handler the click does (`reach`, `touch_wall`, `throw`,
  `put_in_pack`). A pixel guess that misses looks exactly like the game
  refusing, so it proves nothing about whether the game can be finished.
- A menu line with nothing written on it is chosen by what it stands for —
  `MenuEntry.spoken`, or the champion a portrait is — through `toldAs`, which
  `observe` and `choose` share.
- Tests: `WhatTheAgentIsToldTest`, `WhatTheAgentCanDoTest`, `AgentTimeTest`,
  and the server's `TheMapTest`. The batches and checkpoints were checked end
  to end by driving a second server over JSON-RPC against a running game.
