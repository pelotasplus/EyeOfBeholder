package pl.pelotasplus.eyeofbeholder.agentserver

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The games that have called in, each over a WebSocket of its own.
 *
 * A game calls the server and never the other way round: it is the game that
 * knows whether it may be played by an agent. Each is given a number for as
 * long as its connection lasts, which is what an agent picks it by.
 */
class Games(private val port: Int) {

    class Game(val number: Int, val name: String, private val session: DefaultWebSocketServerSession) {
        private val nextRequest = AtomicLong()
        private val waiting = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()

        /** Asks the game [method] and waits for its answer, which is `{"result"}` or `{"error"}`. */
        suspend fun ask(method: String, params: JsonObject = JsonObject(emptyMap()), within: Long = ANSWER_WITHIN): JsonObject {
            val id = nextRequest.incrementAndGet()
            val answer = CompletableDeferred<JsonObject>()
            waiting[id] = answer
            try {
                session.send(
                    Frame.Text(
                        buildJsonObject {
                            put("id", id)
                            put("method", method)
                            put("params", params)
                        }.toString(),
                    ),
                )
                return withTimeout(within) { answer.await() }
            } finally {
                waiting.remove(id)
            }
        }

        fun answered(message: JsonObject) {
            val id = message["id"]?.jsonPrimitive?.long ?: return
            waiting[id]?.complete(message)
        }
    }

    private val numbers = AtomicInteger()
    private val connected = ConcurrentHashMap<Int, Game>()

    val all: List<Game> get() = connected.values.sortedBy { it.number }

    /** Starts listening on this machine only. A port already taken is said, and the tools say no game is there. */
    fun listen() {
        try {
            embeddedServer(CIO, host = "127.0.0.1", port = port) {
                install(WebSockets) {
                    maxFrameSize = Long.MAX_VALUE
                }
                routing {
                    webSocket("/game") { take(this) }
                }
            }.start(wait = false)
            log("listening for games on 127.0.0.1:$port")
        } catch (e: Exception) {
            log("cannot listen on port $port (${e.message}) — is another agent server running?")
        }
    }

    private suspend fun take(session: DefaultWebSocketServerSession) {
        val hello = (session.incoming.receive() as? Frame.Text)?.readText()
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val name = hello?.get("hello")?.jsonObject?.get("game")?.jsonPrimitive?.content ?: "a game"

        val game = Game(numbers.incrementAndGet(), name, session)
        connected[game.number] = game
        log("game ${game.number} connected: $name")
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val message = runCatching { json.parseToJsonElement(frame.readText()).jsonObject }.getOrNull() ?: continue
                game.answered(message)
            }
        } finally {
            connected.remove(game.number)
            log("game ${game.number} gone")
        }
    }

    companion object {
        /** How long a game has to answer before the agent is told it did not. */
        const val ANSWER_WITHIN = 10_000L

        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Everything the server says for itself goes to standard error: standard out is the agent's. */
fun log(line: String) = System.err.println("eob-agent: $line")
