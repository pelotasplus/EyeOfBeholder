package pl.pelotasplus.eyeofbeholder.agent

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The game's line to an agent: a connection out to the agent server on this
 * machine, which an agent playing reaches the game through.
 *
 * The game always calls out and never listens, so nothing beyond this
 * machine can reach it, and it calls only while [allow] has been given. With
 * no server there it tries again every few seconds, and asks nothing of
 * anybody meanwhile.
 *
 * What the server asks is answered by [Game] — the play field — on the main
 * thread, exactly as a player's own click is. Asking it to act or to wait
 * takes the game's clock over (see [AgentTime]); losing the connection hands
 * it back, so a game left by its agent goes on in real time.
 */
class AgentLink(private val time: AgentTime, private val port: Int = PORT) {

    /** What the link answers from: the play field. */
    interface Game {
        fun observation(): Observation?
        fun frame(): ByteArray?
        fun act(action: AgentAction): String?

        /** QA mode only: the world behind the view. */
        fun inspection(): Inspection?

        /** QA mode only: a shortcut to where a check starts. Says why it could not, or nothing. */
        fun setUp(setup: QaSetup): String?
    }

    private var game: Game? = null
    private var calling: Job? = null

    /** Lives as long as the game does, which is longer than whatever switched the link on. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _allowed = MutableStateFlow(false)
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _qa = MutableStateFlow(false)

    /**
     * Whether the agent is in QA mode, told everything and allowed shortcuts
     * — see [QaSetup]. Off is fair play. Only the person at the game sets it;
     * nothing the agent sends can.
     */
    val qa: StateFlow<Boolean> = _qa.asStateFlow()

    fun allowQa(allowed: Boolean) {
        _qa.value = allowed
        if (allowed) Diagnostics.listen()
    }

    fun attach(game: Game) {
        this.game = game
    }

    /** Starts calling the server, or stops and lets the game go. */
    fun allow(allowed: Boolean) {
        _allowed.value = allowed
        calling?.cancel()
        calling = null
        if (!allowed) {
            dropped()
            return
        }
        calling = scope.launch { keepCalling() }
    }

    private suspend fun keepCalling() {
        val client = HttpClient { install(WebSockets) }
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    client.webSocket(host = HOST, port = port, path = PATH) {
                        _connected.value = true
                        Logger.i(TAG) { "An agent server is there" }
                        send(Frame.Text(hello()))
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            send(Frame.Text(answer(frame.readText())))
                        }
                    }
                } catch (e: Exception) {
                    if (!currentCoroutineContext().isActive) throw e
                    Logger.d(TAG) { "No agent server: ${e.message}" }
                }
                dropped()
                delay(TRY_AGAIN)
            }
        } finally {
            dropped()
            client.close()
        }
    }

    private fun dropped() {
        if (_connected.value) Logger.i(TAG) { "The agent server is gone; the game runs on its own" }
        _connected.value = false
        time.handBack()
    }

    private fun hello(): String = buildJsonObject {
        put("hello", buildJsonObject { put("game", GAME_NAME) })
    }.toString()

    /** A request — `{"id", "method", "params"}` — and its answer, `{"id", "result"}` or `{"id", "error"}`. */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun answer(request: String): String {
        val asked = runCatching { json.parseToJsonElement(request).jsonObject }.getOrNull()
            ?: return reply(JsonNull, error = "not a request")
        val id = asked["id"] ?: JsonNull
        val params = asked["params"] as? JsonObject ?: JsonObject(emptyMap())
        val game = game ?: return reply(id, error = "the game is not ready")

        return try {
            when (asked["method"]?.jsonPrimitive?.content) {
                "observe" -> {
                    val seen = onMain { game.observation() } ?: return reply(id, error = "no level is open")
                    reply(id, json.encodeToJsonElement(Observation.serializer(), seen))
                }

                "frame" -> {
                    val png = onMain { game.frame() } ?: return reply(id, error = "nothing has been drawn yet")
                    reply(id, JsonPrimitive(Base64.encode(png)))
                }

                "act" -> {
                    time.takeOver()
                    val action = json.decodeFromJsonElement(AgentAction.serializer(), params["action"] ?: JsonNull)
                    val refused = onMain { game.act(action) }
                    reply(id, buildJsonObject { if (refused == null) put("done", true) else put("refused", refused) })
                }

                "wait" -> {
                    time.takeOver()
                    time.advance(params["ms"]?.jsonPrimitive?.long ?: 0L)
                    reply(id, buildJsonObject { put("waited", true) })
                }

                "release" -> {
                    time.handBack()
                    reply(id, buildJsonObject { put("released", true) })
                }

                "diagnostics", "inspect", "setup" -> {
                    if (!_qa.value) return reply(id, error = NOT_IN_QA)
                    when (asked["method"]?.jsonPrimitive?.content) {
                        "diagnostics" -> {
                            val after = params["after"]?.jsonPrimitive?.long ?: 0L
                            reply(id, json.encodeToJsonElement(ListSerializer(Diagnostics.Note.serializer()), Diagnostics.since(after)))
                        }

                        "inspect" -> {
                            val seen = onMain { game.inspection() } ?: return reply(id, error = "no level is open")
                            reply(id, json.encodeToJsonElement(Inspection.serializer(), seen))
                        }

                        else -> {
                            val setup = json.decodeFromJsonElement(QaSetup.serializer(), params["setup"] ?: JsonNull)
                            val refused = onMain { game.setUp(setup) }
                            reply(id, buildJsonObject { if (refused == null) put("done", true) else put("refused", refused) })
                        }
                    }
                }

                else -> reply(id, error = "no such method")
            }
        } catch (e: Exception) {
            reply(id, error = e.message ?: e.toString())
        }
    }

    private suspend fun <T> onMain(work: () -> T): T = withContext(Dispatchers.Main) { work() }

    private fun reply(id: JsonElement, result: JsonElement? = null, error: String? = null): String =
        buildJsonObject {
            put("id", id)
            if (error != null) put("error", error) else put("result", result ?: JsonNull)
        }.toString()

    companion object {
        /** Where the agent server listens. The server and the game agree on it, and nothing else does. */
        const val PORT = 47110
        private const val HOST = "127.0.0.1"
        private const val PATH = "/game"

        private const val GAME_NAME = "Eye of the Beholder II"
        private const val NOT_IN_QA =
            "QA mode is off: the agent knows only what the party know. Whoever is at the game can switch it on in the Debug menu."
        private const val TRY_AGAIN = 2_000L
        private const val TAG = "AgentLink"

        // Empty faces and lists left out: an agent reads every character it is sent.
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            classDiscriminator = "type"
        }
    }
}
