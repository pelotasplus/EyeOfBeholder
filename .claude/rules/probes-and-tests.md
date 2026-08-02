# Probes and tests

Most work here starts by not knowing what the right answer is. The game data
decides it, and the only way to find out is to read what a script or a file
actually does. So the first thing written is usually a **probe**: a test with
no assertions that prints what the data says.

A probe is scaffolding. It is named `*Probe`, it prints, it asserts nothing,
and it is not coverage — a green suite full of probes proves nothing at all.
It exists to answer a question, and once answered it is converted into
assertions and deleted.

## Converting a probe into a test

The probe tells you **what to assert about**. It must not tell you **what the
value should be**.

That distinction is the whole rule, and getting it wrong produces a test that
can never fail. A probe printed `party=(15,10)` at the end of a scripted walk,
that number went straight into an assertion, and the test passed for weeks —
it was a photograph of the behaviour, not a statement about the game. The walk
was in fact unfinished: arriving there was supposed to make the temple door
speak, and nothing did.

So the expected value comes from somewhere the code cannot reach:

- the script's own instructions, read opcode by opcode (`if facing == SOUTH →
  ChangeLevel, otherwise → MoveParty(10,3)` is a specification)
- the original engine's behaviour in `../scummvm`
- the file format documentation
- what the player should see on screen

If none of those can say what the value ought to be, the probe has not
finished its job and the assertion is not ready to be written.

## What to keep

- Keep a probe only while its question is open. Record the answer where it
  belongs — assertions for behaviour, KDoc for what a table means, a rule file
  for a convention — and delete the probe in the same commit.
- Prefer asserting the sequence a script produces (what it drew, held, asked)
  over its final state alone. A wrong ending often has a right-looking end
  state; it is the missing beat before it that gives the bug away.
- A golden image only sees frames the script asks to be drawn. Anything that
  happens after the last of those is invisible to it and needs an assertion of
  its own.
