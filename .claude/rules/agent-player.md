# The agent player

The game can be driven by an agent — you — through the `eob` MCP server
(`agentServer/`, registered in `.mcp.json`). This is how to play it, and how
to keep it working.

## Using it

- The server must be built: `./gradlew :agentServer:installDist`. A session
  started before it was built has no `eob` tools; build, then start a new one.
- The user switches **Agent** on in the game's Debug menu. Until then
  `list_games` says no game is connected — ask them to, don't guess.
- Tools: `list_games`, `observe`, `frame`, `act`, `wait`, `release`.
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
- Tests: `WhatTheAgentIsToldTest`, `WhatTheAgentCanDoTest`, `AgentTimeTest`.
  The server and the connection were checked end to end by hand; there is no
  automated test of them, since one needs a built server.
