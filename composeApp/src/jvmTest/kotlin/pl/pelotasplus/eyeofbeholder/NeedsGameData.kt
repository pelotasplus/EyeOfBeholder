package pl.pelotasplus.eyeofbeholder

/**
 * Marks a test that reads the original game data, which is not in the
 * repository — so a checkout that has none of it cannot run one.
 *
 * ```kotlin
 * @Category(NeedsGameData::class)
 * class MonsterRuiningWhatItHitsTest { ... }
 * ```
 *
 * `./gradlew :composeApp:jvmTest` runs everything, which is what a working
 * copy with the game files beside it wants. `-PwithoutGameData` leaves these
 * out, which is all CI can do.
 *
 * A test left unmarked that turns out to need the data fails on CI rather than
 * quietly not running, which is the way round that gets noticed.
 */
interface NeedsGameData
