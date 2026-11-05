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
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

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

    private val holdsThePort = AtomicBoolean(false)

    /** Whether another server holds the port, so no game can call this one. */
    val portIsTaken: Boolean get() = !holdsThePort.get()

    /**
     * Starts listening on this machine only, and keeps trying for as long as
     * another server holds the port: one session's leftover server would
     * otherwise keep every later one deaf until someone killed it by hand.
     */
    fun listen() {
        thread(isDaemon = true, name = "agent-listen") {
            var said = false
            while (!holdsThePort.get()) {
                if (tryToListen()) {
                    holdsThePort.set(true)
                    log("listening for games on 127.0.0.1:$port")
                } else {
                    if (!said) log("port $port is held by another agent server; waiting for it")
                    said = true
                    Thread.sleep(PORT_AGAIN)
                }
            }
        }
    }

    /**
     * CIO binds after `start` returns and reports the failure to nobody we
     * can reach, so whether the port is ours is decided by looking.
     */
    private fun tryToListen(): Boolean {
        if (!portIsFree()) return false
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            install(WebSockets) {
                maxFrameSize = Long.MAX_VALUE
            }
            routing {
                webSocket("/game") { take(this) }
            }
        }
        return try {
            server.start(wait = false)
            Thread.sleep(SETTLES_IN)
            if (portIsFree()) {
                server.stop()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            log("cannot listen on port $port (${e.message})")
            false
        }
    }

    private fun portIsFree(): Boolean = try {
        ServerSocket(port, 1, InetAddress.getLoopbackAddress()).close()
        true
    } catch (e: IOException) {
        false
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

        private const val PORT_AGAIN = 1_000L
        private const val SETTLES_IN = 200L

        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Everything the server says for itself goes to standard error: standard out is the agent's. */
fun log(line: String) = System.err.println("eob-agent: $line")
