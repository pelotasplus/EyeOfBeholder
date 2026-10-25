package pl.pelotasplus.eyeofbeholder.agentserver

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The agent server: an MCP server on standard in and out, which an agent
 * reaches the game through, and a WebSocket on this machine, which games
 * call in on.
 *
 * MCP here is the little of it the tools need — a JSON-RPC message a line,
 * `initialize`, `tools/list`, `tools/call` and `ping` — written out rather
 * than taken from a library.
 */
fun main(args: Array<String>) = runBlocking {
    val port = args.firstOrNull()?.toIntOrNull() ?: DEFAULT_PORT
    val games = Games(port).apply { listen() }
    val tools = Tools(games)

    while (true) {
        val line = readlnOrNull() ?: break
        if (line.isBlank()) continue
        val message = runCatching { Games.json.parseToJsonElement(line).jsonObject }.getOrNull()
        if (message == null) {
            reply(JsonNull, error = rpcError(PARSE_ERROR, "not JSON"))
            continue
        }

        val id = message["id"] ?: continue // a notification wants no answer
        when (message["method"]?.jsonPrimitive?.content) {
            "initialize" -> reply(id, result = buildJsonObject {
                put("protocolVersion", message["params"]?.jsonObject?.get("protocolVersion") ?: JsonPrimitive(PROTOCOL))
                putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
                putJsonObject("serverInfo") {
                    put("name", "eob-agent")
                    put("version", "0.1")
                }
                put("instructions", Tools.INSTRUCTIONS)
            })

            "ping" -> reply(id, result = JsonObject(emptyMap()))
            "tools/list" -> reply(id, result = buildJsonObject { put("tools", Tools.LIST) })
            "tools/call" -> {
                val params = message["params"]?.jsonObject
                val name = params?.get("name")?.jsonPrimitive?.content.orEmpty()
                val arguments = params?.get("arguments") as? JsonObject ?: JsonObject(emptyMap())
                reply(id, result = tools.call(name, arguments))
            }

            else -> reply(id, error = rpcError(METHOD_NOT_FOUND, "no such method"))
        }
    }
}

private fun reply(id: JsonElement, result: JsonElement? = null, error: JsonObject? = null) {
    val message = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        if (error != null) put("error", error) else put("result", result ?: JsonNull)
    }
    synchronized(System.out) {
        println(message.toString())
        System.out.flush()
    }
}

private fun rpcError(code: Int, text: String) = buildJsonObject {
    put("code", code)
    put("message", text)
}

/** What the agent can call, and how each is passed on to the game it picks. */
class Tools(private val games: Games) {

    suspend fun call(name: String, arguments: JsonObject): JsonObject {
        if (name == "list_games") {
            val listed = games.all.joinToString("\n") { "${it.number}: ${it.name}" }
            return text(listed.ifEmpty { NO_GAMES })
        }

        val game = pick(arguments) ?: return failed(
            if (games.all.isEmpty()) NO_GAMES else "Several games are connected; say which with \"game\": ${games.all.map { it.number }}",
        )

        return try {
            when (name) {
                "observe" -> answerOf(game.ask("observe")) { text(pretty(it)) }
                "frame" -> answerOf(game.ask("frame")) { image(it.jsonPrimitive.content) }
                "act" -> answerOf(game.ask("act", buildJsonObject { put("action", arguments["action"] ?: JsonNull) })) { text(pretty(it)) }
                "wait" -> {
                    val ms = (arguments["ms"]?.jsonPrimitive?.long ?: 0L).coerceIn(0L, MOST_A_WAIT)
                    answerOf(game.ask("wait", buildJsonObject { put("ms", ms) }, within = ms + Games.ANSWER_WITHIN)) { text("waited $ms ms") }
                }
                "release" -> answerOf(game.ask("release")) { text("the game runs on its own again") }
                else -> failed("no such tool: $name")
            }
        } catch (e: Exception) {
            failed("the game did not answer: ${e.message}")
        }
    }

    private fun pick(arguments: JsonObject): Games.Game? {
        val asked = arguments["game"]?.jsonPrimitive?.intOrNull
        return if (asked != null) games.all.firstOrNull { it.number == asked } else games.all.singleOrNull()
    }

    private inline fun answerOf(answer: JsonObject, ok: (JsonElement) -> JsonObject): JsonObject {
        answer["error"]?.let { return failed(it.jsonPrimitive.content) }
        return ok(answer["result"] ?: JsonNull)
    }

    private fun pretty(element: JsonElement) = prettyJson.encodeToString(JsonElement.serializer(), element)

    private fun text(said: String) = buildJsonObject {
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", said)
            }
        }
    }

    private fun image(base64: String) = buildJsonObject {
        putJsonArray("content") {
            addJsonObject {
                put("type", "image")
                put("data", base64)
                put("mimeType", "image/png")
            }
        }
    }

    private fun failed(why: String) = buildJsonObject {
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", why)
            }
        }
        put("isError", true)
    }

    companion object {
        private const val NO_GAMES =
            "No game is connected. In the game, open the Debug menu and switch \"Agent\" on; it connects within a few seconds."

        /** The longest one wait may be, so that an agent cannot hand a game a day of real time by mistake. */
        private const val MOST_A_WAIT = 60_000L

        private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }

        const val INSTRUCTIONS =
            "Plays Eye of the Beholder II, the recreation, through a game that has connected to this server. " +
                "The game stands still between your actions while you act; it runs only when you call wait. " +
                "Look with observe (what the party can see and know, in words) and frame (the screen as a 320x200 picture). " +
                "Act with act. Many actions take a moment in the game: after a step, wait about 250 ms before the next one; " +
                "an act that cannot be done yet says why. Call release to let the game run on its own again."

        private fun gameArgument() = buildJsonObject {
            put("type", "integer")
            put("description", "Which game, by the number list_games gives it. Not needed when only one is connected.")
        }

        private fun tool(name: String, description: String, properties: JsonObject = JsonObject(emptyMap()), required: List<String> = emptyList()) =
            buildJsonObject {
                put("name", name)
                put("description", description)
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        properties.forEach { (key, value) -> put(key, value) }
                        put("game", gameArgument())
                    }
                    if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
                }
            }

        val LIST: JsonArray = buildJsonArray {
            add(
                buildJsonObject {
                    put("name", "list_games")
                    put("description", "The games connected to this server, by number and name.")
                    putJsonObject("inputSchema") {
                        put("type", "object")
                        putJsonObject("properties") {}
                    }
                },
            )
            add(
                tool(
                    "observe",
                    "What the party can see and know right now, as JSON: the squares in sight as they look (faces near/left/right/far, " +
                        "what is on the floor, monsters by the picture they are drawn with and which way they face, items as the party know them), " +
                        "the party (hit points, food, conditions, what each hand holds, ready spells), what is on the pointer, the messages, " +
                        "any open menu and the open spellbook. Squares are counted 'ahead' from the party (0 is their own) and 'across' (negative left).",
                ),
            )
            add(tool("frame", "The screen as it was last drawn, as a 320x200 PNG."))
            add(
                tool(
                    "act",
                    "Do what a player does. action is an object with a \"type\" and its fields: " +
                        "{type: step, way: FORWARD|BACK|LEFT|RIGHT}, {type: turn, toThe: LEFT|RIGHT}, {type: front_rank_strikes}, " +
                        "{type: use_hand, champion: 0-5, hand: 0|1} (swing, read, drink, eat, open the book), " +
                        "{type: take_hand, champion, hand} (swap what the hand holds with what is on the pointer), {type: camp}, " +
                        "{type: choose, label} (a line of the open menu by its words), {type: answer, button: 0..} (a dialogue button), " +
                        "{type: cast_on, champion}, {type: book_tab, page: 0-5}, {type: book_line, line: 0-5} (casts it), {type: book_abort}, " +
                        "{type: click, x, y} and {type: use, x, y} (left and right click anywhere on the 320x200 screen). " +
                        "Answers done, or refused with the reason.",
                    properties = buildJsonObject {
                        putJsonObject("action") {
                            put("type", "object")
                            put("description", "The action, as described above.")
                        }
                    },
                    required = listOf("action"),
                ),
            )
            add(
                tool(
                    "wait",
                    "Let the game run for ms milliseconds of its own time, up to a minute. Nothing happens between your calls otherwise.",
                    properties = buildJsonObject {
                        putJsonObject("ms") {
                            put("type", "integer")
                            put("description", "How long, in milliseconds.")
                        }
                    },
                    required = listOf("ms"),
                ),
            )
            add(tool("release", "Stop driving the game: it runs in real time on its own again until you act or wait."))
        }
    }
}

private const val DEFAULT_PORT = 47110
private const val PROTOCOL = "2025-06-18"
private const val PARSE_ERROR = -32700
private const val METHOD_NOT_FOUND = -32601
