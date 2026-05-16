/******************************************************************************
 *     Copyright (C) 2025  Octavio Calleya Garcia                             *
 *                                                                            *
 *     This program is free software: you can redistribute it and/or modify   *
 *     it under the terms of the GNU General Public License as published by   *
 *     the Free Software Foundation, either version 3 of the License, or      *
 *     (at your option) any later version.                                    *
 *                                                                            *
 *     This program is distributed in the hope that it will be useful,        *
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of         *
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          *
 *     GNU General Public License for more details.                           *
 *                                                                            *
 *     You should have received a copy of the GNU General Public License      *
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>. *
 ******************************************************************************/

/**
 * VENDORED FROM lirp.
 *
 * Source: `lirp-core/src/test/kotlin/net/transgressoft/lirp/testing/ReactiveScopeExtension.kt`
 *
 * Do not edit locally. Upstream lirp does not yet publish a `lirp-testing` Maven artifact,
 * so music-commons cannot consume this through normal Gradle dependencies (it depends on
 * lirp via Maven coordinates, not as a project). The only changes versus the upstream file
 * are the package declaration and this vendor banner.
 *
 * TODO(#100-followup): remove once lirp publishes lirp-testing.
 */
package net.transgressoft.commons.music.testing

import net.transgressoft.lirp.event.ReactiveScope
import io.kotest.core.extensions.SpecExtension
import io.kotest.core.spec.Spec
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

// Process-wide mutex shared by both extensions in this file. Serializes every spec that
// touches [ReactiveScope] against every other spec doing the same, regardless of whether
// it swaps the scope or relies on production defaults.
internal val reactiveScopeMutex = Mutex()

/**
 * Kotest [SpecExtension] that wires a [TestDispatcher] into the process-wide [ReactiveScope]
 * for the duration of a spec and serializes against other specs using either this extension
 * or [ReactiveScopeSerialization].
 *
 * [ReactiveScope] is a global singleton that backs every reactive entity and repository.
 * Mutating [ReactiveScope.flowScope] or [ReactiveScope.ioScope] is unsafe across specs that
 * run in parallel, so this extension brackets the spec body with a shared [Mutex] and
 * restores the default scopes when the spec finishes.
 *
 * The injected [CoroutineScope] uses a [SupervisorJob] to mirror production semantics — a
 * throwing subscriber does not cancel the rest of the reactive system. By default, uncaught
 * exceptions are captured by a [CoroutineExceptionHandler] and surfaced as an [AssertionError]
 * at spec teardown so unexpected throws still fail the build. Specs that intentionally exercise
 * throwing subscribers should pass `failOnUncaughtExceptions = false`.
 *
 * Register per-spec via the [reactiveScope] convenience factory:
 *
 *     class FooReactiveTest : StringSpec({
 *         val reactive = reactiveScope()
 *
 *         "..." {
 *             reactive.scope.launch { /* runs on the wired scope */ }
 *             reactive.advance() // drains the test scheduler
 *         }
 *     })
 *
 * Specs that rely on real concurrent dispatching (stress and timing-sensitive tests) should
 * use [ReactiveScopeSerialization] instead — it shares the same mutex without rewiring.
 *
 * @property dispatcher the test dispatcher whose [CoroutineScope] is bound to
 *   [ReactiveScope.flowScope] and [ReactiveScope.ioScope] while the spec runs.
 * @property failOnUncaughtExceptions when `true` (default), any uncaught exception emitted
 *   by a coroutine launched in the injected scope fails the spec at teardown. Set to `false`
 *   for specs that deliberately verify throwing-subscriber behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactiveScopeExtension(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
    private val failOnUncaughtExceptions: Boolean = true
) : SpecExtension {

    /**
     * The [CoroutineScope] bound to [ReactiveScope.flowScope] and [ReactiveScope.ioScope] for
     * the duration of the spec. Available inside test bodies; reading it before the spec has
     * started is a programming error and throws [UninitializedPropertyAccessException].
     */
    lateinit var scope: CoroutineScope
        private set

    /** Drives the [dispatcher]'s scheduler until idle. Shorthand for the most common test verb. */
    fun advance() {
        dispatcher.scheduler.advanceUntilIdle()
    }

    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) {
        reactiveScopeMutex.withLock {
            val uncaught = ConcurrentLinkedQueue<Throwable>()
            val handler = CoroutineExceptionHandler { _, throwable -> uncaught.add(throwable) }
            scope = CoroutineScope(dispatcher + SupervisorJob() + handler)
            ReactiveScope.flowScope = scope
            ReactiveScope.ioScope = scope
            try {
                execute(spec)
            } finally {
                ReactiveScope.resetDefaultFlowScope()
                ReactiveScope.resetDefaultIoScope()
                // Cancel the per-spec SupervisorJob so any in-flight or leaked coroutines
                // terminate before the mutex is released and the next spec acquires it.
                scope.cancel()
            }
            if (failOnUncaughtExceptions) failOnUncaughtExceptions(spec, uncaught)
        }
    }
}

/**
 * Convenience factory that constructs a [ReactiveScopeExtension], registers it on the spec,
 * and returns it so the test body can access [ReactiveScopeExtension.dispatcher],
 * [ReactiveScopeExtension.scope], and [ReactiveScopeExtension.advance].
 *
 *     class FooTest : StringSpec({
 *         val reactive = reactiveScope()
 *         "test" {
 *             reactive.scope.launch { /* ... */ }
 *             reactive.advance()
 *         }
 *     })
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Spec.reactiveScope(
    dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
    failOnUncaughtExceptions: Boolean = true
): ReactiveScopeExtension {
    val ext = ReactiveScopeExtension(dispatcher, failOnUncaughtExceptions)
    extension(ext)
    return ext
}

/**
 * Kotest [SpecExtension] that serializes specs against [ReactiveScopeExtension] without
 * swapping the [ReactiveScope] dispatchers.
 *
 * Use this for stress and timing-sensitive specs that need the production
 * `Dispatchers.Default + SupervisorJob()` scopes to remain in place but must not run
 * concurrently with specs that rewire [ReactiveScope].
 *
 *     class StressTest : FunSpec({
 *         extension(ReactiveScopeSerialization)
 *         test("...") { /* ReactiveScope keeps its production defaults */ }
 *     })
 */
object ReactiveScopeSerialization : SpecExtension {
    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) {
        reactiveScopeMutex.withLock { execute(spec) }
    }
}

private fun failOnUncaughtExceptions(spec: Spec, uncaught: ConcurrentLinkedQueue<Throwable>) {
    if (uncaught.isEmpty()) return
    val first = uncaught.first()
    throw AssertionError(
        "Uncaught coroutine exceptions during spec ${spec::class.qualifiedName}: " +
            uncaught.joinToString(prefix = "[", postfix = "]") { it.toString() },
        first
    )
}