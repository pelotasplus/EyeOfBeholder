package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The scripts a floor sets off, run one after another and none of them lost.
 *
 * A script is set off by something that has already happened — a foot on a
 * plate, a thing coming down on one, the floor's own clock coming round — and
 * there is no undoing that. Several of them falling in the same turn of the
 * clock is ordinary rather than exceptional: something lands on a square in
 * the same turn the clock comes round, and both have to be answered.
 *
 * So a script raised while another is running waits for it. What it must not
 * do is take its place: a run that never happens is a plate that stays up
 * under something lying on it, and nothing on screen says so — the thing is
 * drawn where it landed, and the door it should have opened simply never
 * does.
 *
 * Each run reads the world when its turn comes rather than when it was
 * raised, so what the one before it changed is what the next one sees.
 */
class ScriptsInTurn(private val scope: CoroutineScope) {

    private var last: Job? = null

    /** Whether one is running, which is how a script owns the screen. */
    val running: Boolean get() = last?.isActive == true

    /** Puts [run] at the back of the queue, and answers with its own job. */
    fun next(run: suspend () -> Unit): Job {
        val before = last

        return scope.launch {
            before?.join()
            run()
        }.also { last = it }
    }
}
