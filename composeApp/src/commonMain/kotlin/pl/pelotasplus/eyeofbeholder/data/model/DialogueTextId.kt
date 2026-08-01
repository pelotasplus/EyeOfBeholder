package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline

/**
 * Which speech to print, counted from one, into the shared dialogue text file
 * rather than into a level's own messages — a level's script refers to both,
 * with numbers of the same size and shape.
 */
@JvmInline
value class DialogueTextId(val number: Int)

/**
 * Which of a level's own messages to use, counted from zero.
 *
 * These label buttons as well as being printed: an answer's caption and the
 * word on the button that acknowledges a speech are both messages.
 */
@JvmInline
value class MessageId(val index: Int)
