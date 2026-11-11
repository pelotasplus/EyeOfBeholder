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
import kotlinx.serialization.json.booleanOrNull
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
class Tools(private val games: Games, private val home: AgentHome = AgentHome.here()) {

    private val map = TheMap(home.mapFile)

    suspend fun call(name: String, arguments: JsonObject): JsonObject {
        if (name == "list_games") {
            val listed = games.all.joinToString("\n") { "${it.number}: ${it.name}" }
            return text(listed.ifEmpty { noGames() })
        }

        val game = pick(arguments) ?: return failed(
            if (games.all.isEmpty()) noGames() else "Several games are connected; say which with \"game\": ${games.all.map { it.number }}",
        )

        return try {
            when (name) {
                "observe" -> answerOf(game.ask("observe")) {
                    (it as? JsonObject)?.let(map::saw)
                    text(pretty(it))
                }
                "walk" -> said(
                    Playing(game, map, home).walk(
                        (arguments["moves"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(),
                    ),
                )
                "fight" -> said(
                    Playing(game, map, home).fight(
                        arguments["rounds"]?.jsonPrimitive?.intOrNull ?: FIGHT_ROUNDS,
                        stand = arguments["stand"]?.jsonPrimitive?.booleanOrNull ?: false,
                    ),
                )
                "wait_until" -> said(
                    Playing(game, map, home).waitUntil(
                        arguments["condition"]?.jsonPrimitive?.content ?: "free",
                        (arguments["ms"]?.jsonPrimitive?.long ?: MOST_A_WAIT).coerceIn(0L, MOST_A_WAIT),
                    ),
                )
                "checkpoint" -> said(
                    Playing(game, map, home).checkpoint(
                        arguments["name"]?.jsonPrimitive?.content ?: return failed("checkpoint wants a name"),
                        arguments["over"]?.jsonPrimitive?.content,
                    ),
                )
                "restore" -> said(
                    Playing(game, map, home).restore(
                        arguments["name"]?.jsonPrimitive?.content ?: return failed("restore wants a name"),
                        arguments["over"]?.jsonPrimitive?.content,
                    ),
                )
                "map" -> {
                    val level = arguments["level"]?.jsonPrimitive?.intOrNull ?: map.currentLevel
                        ?: return failed("No level seen yet; observe first, or say which level.")
                    text(map.drawn(level))
                }
                "frame" -> answerOf(game.ask("frame")) { image(it.jsonPrimitive.content) }
                "act" -> answerOf(game.ask("act", buildJsonObject { put("action", arguments["action"] ?: JsonNull) })) { text(pretty(it)) }
                "wait" -> {
                    val ms = (arguments["ms"]?.jsonPrimitive?.long ?: 0L).coerceIn(0L, MOST_A_WAIT)
                    answerOf(game.ask("wait", buildJsonObject { put("ms", ms) }, within = ms + Games.ANSWER_WITHIN)) { text("waited $ms ms") }
                }
                "release" -> answerOf(game.ask("release")) { text("the game runs on its own again") }
                "diagnostics" -> answerOf(
                    game.ask("diagnostics", buildJsonObject { put("after", arguments["after"]?.jsonPrimitive?.long ?: 0L) }),
                ) { text(pretty(it)) }
                "inspect" -> answerOf(game.ask("inspect")) { text(pretty(it)) }
                "setup" -> answerOf(game.ask("setup", buildJsonObject { put("setup", arguments["setup"] ?: JsonNull) })) { text(pretty(it)) }
                else -> failed("no such tool: $name")
            }
        } catch (e: Exception) {
            failed("the game did not answer: ${e.message}")
        }
    }

    private fun noGames(): String =
        if (games.portIsTaken) PORT_TAKEN else NO_GAMES

    private fun pick(arguments: JsonObject): Games.Game? {
        val asked = arguments["game"]?.jsonPrimitive?.intOrNull
        return if (asked != null) games.all.firstOrNull { it.number == asked } else games.all.singleOrNull()
    }

    private inline fun answerOf(answer: JsonObject, ok: (JsonElement) -> JsonObject): JsonObject {
        answer["error"]?.let { return failed(it.jsonPrimitive.content) }
        return ok(answer["result"] ?: JsonNull)
    }

    private fun pretty(element: JsonElement) = prettyJson.encodeToString(JsonElement.serializer(), element)

    /** What a batch came to, then the observation it ended on, so no second call is needed to see where things stand. */
    private fun said(outcome: Playing.Outcome) =
        text(outcome.done + (outcome.observation?.let { "\n\n" + pretty(it) } ?: ""))

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

        private const val PORT_TAKEN =
            "Another agent server holds the port, so games reach it and not this one. Close the session it belongs to; " +
                "this server takes the port as soon as it is free, and the game reconnects on its own."

        /** The longest one wait may be, so that an agent cannot hand a game a day of real time by mistake. */
        private const val MOST_A_WAIT = 60_000L

        private const val FIGHT_ROUNDS = 10

        private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }

        const val INSTRUCTIONS =
            "Plays Eye of the Beholder II, the recreation, through a game that has connected to this server. " +
                "The game stands still between your actions while you act; it runs only when you call wait. " +
                "Look with observe (what the party can see and know, in words) and frame (the screen as a 320x200 picture). " +
                "Act with act. Many actions take a moment in the game: after a step, wait about 250 ms before the next one; " +
                "an act that cannot be done yet says why. Call release to let the game run on its own again. " +
                "diagnostics, inspect and setup are QA mode: they answer only once the person at the game has " +
                "switched QA on, and otherwise you play knowing only what the party know."

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
                        "{type: take_hand, champion, hand} (swap what the hand holds with what is on the pointer), " +
                        "{type: put_in_pack, champion} (what is on the pointer into the first empty pocket), " +
                        "{type: take_from_pack, champion, item} (the first thing in the pack by the name observe gives it, onto the pointer, swapped with what was there), " +
                        "{type: wear, champion} (what is on the pointer put on, where the body takes it — armour, helmet, bracers, amulet, boots, a ring; what was worn there comes onto the pointer), " +
                        "{type: take_off, champion, item} (what is worn, by the name observe gives it under worn, onto the pointer), " +
                        "{type: reach, ahead: 0|1, side: LEFT|RIGHT} (pick up from, or put down on, the near corners of the party's square or the one ahead; " +
                        "an item 'lying' far_left/far_right at ahead 0 or near_left/near_right at ahead 1 is reachable), " +
                        "{type: throw, side: LEFT|RIGHT} (what is on the pointer), " +
                        "{type: touch_wall} (work what is on the wall ahead — button, lever, niche, keyhole, mouth — with the pointer or an empty hand), " +
                        "{type: camp}, " +
                        "{type: choose, label} (a line of the open menu by its words — a spell in a memorize list by its name, a champion asked for by theirs), " +
                        "{type: answer, button: 0..} (a dialogue button), " +
                        "{type: cast_on, champion}, {type: book_tab, page: 0-5}, {type: book_line, line: 0-5} (casts it), " +
                        "{type: book_scroll, down: true|false} (the other half of the page, when the book says canScroll), {type: book_abort}, " +
                        "{type: type, text} (types it where a save is being named), " +
                        "{type: press, key: ACCEPT|RUBOUT|ABANDON, times} (return, backspace, escape; times is holding it down), " +
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
            add(
                tool(
                    "walk",
                    "Several moves as one call, each done and waited for as act and wait would. Stops early when a move is refused or goes " +
                        "nowhere, a monster comes within two squares, a dialogue or menu comes up, or someone is struck or badly hurt; says " +
                        "what was seen lying about on the way, then the observation it ended on. " +
                        "moves: forward, back, strafe_left, strafe_right, left, right (the last two turn).",
                    properties = buildJsonObject {
                        putJsonObject("moves") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                        }
                    },
                    required = listOf("moves"),
                ),
            )
            add(
                tool(
                    "fight",
                    "The front rank strikes at what stands in front, round after round, until nothing does, someone is badly hurt, " +
                        "a dialogue comes up or the rounds are up; says what each hand showed after each swing (a number is a hit), " +
                        "then the observation it ended on. Each round strikes, steps aside while the hands recover, and steps back in; " +
                        "it stops if neither side is open, unless stand is set.",
                    properties = buildJsonObject {
                        putJsonObject("rounds") {
                            put("type", "integer")
                            put("description", "At most this many, 10 if not said.")
                        }
                        putJsonObject("stand") {
                            put("type", "boolean")
                            put("description", "Stay in front and trade blows instead of stepping aside. False if not said.")
                        }
                    },
                ),
            )
            add(
                tool(
                    "wait_until",
                    "Let the game run until condition holds: free (the party can step again), dialogue (one comes up), no_dialogue " +
                        "(it goes). Gives up after ms, a minute at most. Then the observation it ended on.",
                    properties = buildJsonObject {
                        putJsonObject("condition") { put("type", "string") }
                        putJsonObject("ms") { put("type", "integer") }
                    },
                ),
            )
            add(
                tool(
                    "map",
                    "The map drawn so far from what observe has told, north up, as text: squares seen, walls, doors, walls with " +
                        "something on them, and what was lying where when last seen. Kept on disk across sessions.",
                    properties = buildJsonObject {
                        putJsonObject("level") {
                            put("type", "integer")
                            put("description", "Which level; the one the party are on if not said.")
                        }
                    },
                ),
            )
            add(
                tool(
                    "checkpoint",
                    "Save through the camp menu under name, and keep a copy outside the game's six slots so it can be gone back to " +
                        "however many checkpoints there are. A slot already of that name is saved over, then an empty one, then the one named over, " +
                        "then the oldest save (its kept copy can still be restored).",
                    properties = buildJsonObject {
                        putJsonObject("name") { put("type", "string") }
                        putJsonObject("over") { put("type", "string") }
                    },
                    required = listOf("name"),
                ),
            )
            add(
                tool(
                    "restore",
                    "Go back to a checkpoint by name, putting its kept copy back into a slot first if the slot was saved over " +
                        "(into an empty one, or over the save named over).",
                    properties = buildJsonObject {
                        putJsonObject("name") { put("type", "string") }
                        putJsonObject("over") { put("type", "string") }
                    },
                    required = listOf("name"),
                ),
            )
            add(
                tool(
                    "diagnostics",
                    "QA mode only. What the game noticed about itself: its warnings and errors, and every place it reached " +
                        "something not written yet (a spell with no effect, an item used from a hand that does nothing). " +
                        "Numbered; pass after to get only what came since the last you saw.",
                    properties = buildJsonObject {
                        putJsonObject("after") {
                            put("type", "integer")
                            put("description", "Only the notes numbered higher than this.")
                        }
                    },
                ),
            )
            add(
                tool(
                    "inspect",
                    "QA mode only. The whole world behind the view: every monster on the floor (where, hit points, mode, " +
                        "provoked, held), the party with true names of everything carried, the spells running.",
                ),
            )
            add(
                tool(
                    "setup",
                    "QA mode only. A shortcut to where a check starts. setup is an object with a \"type\": " +
                        "{type: teleport, level, x, y, facing: NORTH|EAST|SOUTH|WEST}, " +
                        "{type: give_spell, champion: 0-5, spell: name, ready: true|false} (onto the page of its level), " +
                        "{type: give_item, name} (a copy of the first item whose true name holds it, onto the pointer), " +
                        "{type: give_scroll, spell: name} (a scroll of any spell, onto the pointer), " +
                        "{type: poison, champion: 0-5}, {type: paralyse, champion: 0-5}, " +
                        "{type: spawn_monster, looksLike, ahead: 1..} (copied from one of that look the level places), {type: heal}.",
                    properties = buildJsonObject {
                        putJsonObject("setup") {
                            put("type", "object")
                            put("description", "The shortcut, as described above.")
                        }
                    },
                    required = listOf("setup"),
                ),
            )
        }
    }
}

private const val DEFAULT_PORT = 47110
private const val PROTOCOL = "2025-06-18"
private const val PARSE_ERROR = -32700
private const val METHOD_NOT_FOUND = -32601
