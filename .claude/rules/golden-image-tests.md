# Golden-Image Rendering Tests

The 3D viewport renderer is guarded by golden-image tests in
`composeApp/src/jvmTest/kotlin/.../rendering/ViewPortGoldenTest.kt`. Each test
renders a known player position with the real game assets and byte-compares
the 176×120 frame against a reference PNG in `composeApp/src/jvmTest/goldens/`.
On failure, the actual frame and a red-highlighted diff mask are written to
`composeApp/build/golden-failures/` for visual inspection.

**Workflow rules for rendering changes:**

- Every change to the rendering pipeline must be verified with
  `./gradlew :composeApp:jvmTest` BEFORE committing.
- Pure refactors must show **0 pixels changed**. Behavior changes must show
  exactly which goldens changed, and each one needs explicit user approval.
- New render features follow: render → freeze the candidate as a golden →
  review it there.
- Adding a scene is one line: `checkGolden("name", "LEVELX.INF", x, y, direction)`.
- For risky refactors, prove equivalence: generate the golden with the new
  code, `git stash` back to the old code, and run the test against it — a
  byte-identical pass proves the refactor changed nothing.
- Commits and pushes always wait for explicit user approval.
- **Regenerate the affected goldens and say so. Do not ask first.** Overwriting
  a golden that is already committed is how it gets reviewed: the IDE shows the
  image diff against the committed version, which is a better view of the change
  than any mask we could render. Nothing is lost — `git checkout` puts it back
  if the answer is no.
- **A verdict is only ever asked on a file in `composeApp/src/jvmTest/goldens/`.**
  Never point at `composeApp/build/`, a temp directory or a scratch directory,
  and never offer a pixel count or a diff mask in place of the regenerated file.
  A pixel count says something moved; it does not say whether it should have.
- `UPDATE_GOLDENS=1` rewrites **every** golden, including ones already failing
  for unrelated reasons, which silently launders them into the commit. Accept a
  known set instead, and check `git status` afterwards to confirm only that set
  moved:

  ```bash
  UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest \
      --tests 'pl.pelotasplus.eyeofbeholder.rendering.ViewPortGoldenTest.level3 5x3 north'
  ```
- A golden must be a render the committed code actually produces. One frozen
  from a working tree that was later reverted looks authoritative and can never
  pass; if a golden has never matched its own commit, that is the bug.

The scenes are whatever sits in `composeApp/src/jvmTest/goldens/`; read the
directory rather than a list here, which goes stale the first time one is
added.

A scene that is meant to move — a teleporter's flicker — is frozen at each of
its phases and rendered from an explicit one, never from a clock.
