/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.music

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.audioItem
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.LirpErrorContext
import net.transgressoft.lirp.event.LirpErrorHandler
import net.transgressoft.lirp.event.LirpOperation
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Verifies that a [LirpErrorHandler] wired on a repository is invoked when an async operation fails,
 * receiving a [LirpErrorContext] that carries operation identity and repository name but never entity
 * field values.
 *
 * Per-subscription onError (D-12 disposition): no `subscribeAsync` lambda calls exist in production
 * main code — all lambda subscriptions in this library perform fast in-memory work and remain on the
 * synchronous `subscribe` overload. The per-subscription `subscribeAsync(action, onError)` path from
 * the framework is exercised here directly to prove the contract, but it is not-applicable as a
 * production wiring point in this library's current code. If a future change introduces a
 * `subscribeAsync` call, per-subscription onError coverage must be revisited at that time.
 */
internal class LirpErrorHandlerTest : StringSpec({

    // failOnUncaughtExceptions = false because the test deliberately exercises a throwing subscriber
    // action, which propagates an uncaught exception to the coroutine scope's exception handler
    // as a side-effect of the async drain path.
    val reactive = reactiveScope(failOnUncaughtExceptions = false)

    "LirpErrorHandler receives LirpErrorContext with operation and repository identity on async failure" {
        val capturedInvocations = CopyOnWriteArrayList<Pair<Throwable, LirpErrorContext>>()
        val latch = CountDownLatch(1)

        val handler =
            LirpErrorHandler { throwable, ctx ->
                capturedInvocations.add(throwable to ctx)
                latch.countDown()
            }

        // Wire the handler at repository construction time (the documented consumer wiring point).
        // The handler is notify-only; the repository continues operating after an async failure.
        val repo = VolatileRepository<Int, AudioItem>(name = "AudioLibrary-error-test", onError = handler)

        // subscribeAsync with a throwing action triggers LirpOperation.EMIT on failure —
        // the per-subscription onError fires with the repository's id as context.repository.
        repo.subscribeAsync(
            action = { throw RuntimeException("simulated async subscriber failure") },
            onError = handler
        )

        repo.add(Arb.audioItem().next())
        reactive.advance()

        latch.await(5, TimeUnit.SECONDS) shouldBe true

        capturedInvocations.shouldNotBeEmpty()
        val (throwable, ctx) = capturedInvocations.first()
        throwable shouldNotBe null
        ctx.operation shouldBe LirpOperation.EMIT
        ctx.repository shouldBe "AudioLibrary-error-test"
        // LirpErrorContext never carries entity field values — only identity information such as
        // primitive ids. Assert no element is a full entity (AudioItem) to lock the contract in.
        ctx.entityIds.forAll { it.shouldNotBeInstanceOf<AudioItem>() }
    }

    "LirpErrorHandler does not alter control flow — library continues operating after failure" {
        val handlerFired = CountDownLatch(1)
        val handler = LirpErrorHandler { _, _ -> handlerFired.countDown() }

        val repo = VolatileRepository<Int, AudioItem>(name = "AudioLibrary-resilience-test", onError = handler)

        // First subscription throws to trigger the handler
        repo.subscribeAsync(
            action = { throw RuntimeException("intended failure") },
            onError = handler
        )

        // Second subscription counts successful deliveries to verify the repo keeps running
        val successfulDeliveries = CopyOnWriteArrayList<Any>()
        val successLatch = CountDownLatch(1)
        repo.subscribeAsync {
            successfulDeliveries.add(it)
            successLatch.countDown()
        }

        repo.add(Arb.audioItem().next())
        reactive.advance()

        handlerFired.await(5, TimeUnit.SECONDS) shouldBe true
        successLatch.await(5, TimeUnit.SECONDS) shouldBe true
        successfulDeliveries.shouldNotBeEmpty()
    }
})