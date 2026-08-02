package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Who a message names where it leaves a gap for a name.
 *
 * Not the leader, and not the same champion twice running: the original rolls
 * a die for a slot and walks forward from there to the first who can answer,
 * so a remark about a draft from the west comes from whoever happens to pipe
 * up. Being knocked out does not stop a champion speaking — only dying for
 * good, or being turned to stone, takes them out of the running.
 *
 * The roll arrives rather than being taken here, so that what a script says
 * can be asserted.
 */
fun List<Champion>.speakerFrom(slot: Int): Champion? =
    indices.firstNotNullOfOrNull { step ->
        this[(slot + step) % size].takeIf { it.canSpeak }
    }

/** Puts a champion's name where a message left room for one. */
fun String.spokenBy(speaker: Champion?): String =
    replace(SPEAKER, speaker?.name.orEmpty())

private const val SPEAKER = "%s"
