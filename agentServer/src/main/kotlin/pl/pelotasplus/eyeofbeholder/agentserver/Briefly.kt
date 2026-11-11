package pl.pelotasplus.eyeofbeholder.agentserver

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * An observation without what each champion carries in their pack and wears:
 * those rarely change between two steps, and they are most of its size.
 * Everything a move or a fight is decided on stays.
 */
internal fun briefly(seen: JsonObject): JsonObject {
    val party = seen["party"] as? JsonArray ?: return seen
    val trimmed = JsonArray(party.map { JsonObject(it.jsonObject - LEFT_OUT) })
    return JsonObject(seen + ("party" to trimmed))
}

private val LEFT_OUT = setOf("pack", "worn")
