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
  exactly which goldens changed; review the diff images and get explicit user
  approval before accepting them with `UPDATE_GOLDENS=1`.
- New render features follow: render → visually review the candidate PNG in
  `build/golden-failures/` → freeze it as a golden.
- Adding a scene is one line: `checkGolden("name", "LEVELX.INF", x, y, direction)`.
- For risky refactors, prove equivalence: generate the golden with the new
  code, `git stash` back to the old code, and run the test against it — a
  byte-identical pass proves the refactor changed nothing.
- Commits and pushes always wait for explicit user approval.

The scenes are whatever sits in `composeApp/src/jvmTest/goldens/`; read the
directory rather than a list here, which goes stale the first time one is
added.

A scene that is meant to move — a teleporter's flicker — is frozen at each of
its phases and rendered from an explicit one, never from a clock.
