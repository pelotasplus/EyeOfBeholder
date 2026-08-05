package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import pl.pelotasplus.eyeofbeholder.data.WavReader
import pl.pelotasplus.eyeofbeholder.data.model.PcmClip
import pl.pelotasplus.eyeofbeholder.data.model.SoundBank
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex

/**
 * Finds the sound a level means when it asks for a track of its bank.
 *
 * The banks are scores for a chip this project does not emulate, so each of
 * their tracks was rendered to samples ahead of time. Effects repeat across
 * banks note for note — a door is a door on every level — so identical
 * renderings were folded into one file, and [clips] is what puts the numbering
 * back: which file a given bank's given track turned out to be.
 *
 * A track with no entry is one that rendered to silence. Asking for it is not
 * an error; there is simply nothing to hear.
 */
interface SoundRepository {
    suspend fun clip(bank: SoundBank, track: TrackIndex): Result<PcmClip?>
}

class SoundRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : SoundRepository {

    private var clips: Map<SoundBank, Map<Int, String>>? = null
    private val loaded = mutableMapOf<String, PcmClip>()

    override suspend fun clip(bank: SoundBank, track: TrackIndex): Result<PcmClip?> = runCatching {
        if (!track.audible) return@runCatching null

        val name = table()[bank]?.get(track.value) ?: return@runCatching null

        loaded[name]?.let { return@runCatching it }

        val clip = WavReader.read(resourceRepository.readResource("$DIRECTORY/$name"))

        // The effects are played over and over and are worth keeping; the
        // handful of long tunes are megabytes each and are not.
        if (clip.seconds <= WORTH_KEEPING_SECONDS) loaded[name] = clip

        clip
    }.onFailure {
        Logger.w(TAG, it) { "No sound for $bank track $track" }
    }

    private suspend fun table(): Map<SoundBank, Map<Int, String>> {
        clips?.let { return it }

        val text = resourceRepository.readResource("$DIRECTORY/$TABLE")
            .toByteArray()
            .decodeToString()

        val parsed = Json.decodeFromString<Map<String, Map<String, String>>>(text)
            .entries
            .associate { (bank, tracks) ->
                SoundBank(bank) to
                        tracks.entries.associate { (track, clip) -> track.toInt() to clip }
            }

        return parsed.also { clips = it }
    }

    private companion object {
        const val TAG = "SoundRepository"
        const val DIRECTORY = "files/audio"
        const val TABLE = "clips.json"

        /** Long enough for every effect, short enough to exclude the music. */
        const val WORTH_KEEPING_SECONDS = 5f
    }
}
