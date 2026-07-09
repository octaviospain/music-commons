/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.music.event

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.FlowEventPublisher
import net.transgressoft.lirp.event.StandardCrudEvent
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Confirming test that a throwing subscriber does not cancel peer subscribers on either the sync
 * or the async event transport provided by [FlowEventPublisher].
 *
 * On the **sync transport**, [FlowEventPublisher] wraps each callback invocation in a try/catch, so
 * an exception thrown by one callback is logged and swallowed without stopping delivery to the
 * remaining callbacks in the same dispatch cycle.
 *
 * On the **async transport**, each subscriber runs in its own coroutine launched on
 * [net.transgressoft.lirp.event.ReactiveScope.flowScope], which is backed by a [SupervisorJob].
 * A cancellation-free exception in one coroutine's collect block is caught and logged; it does not
 * propagate to the shared job hierarchy and therefore cannot cancel sibling subscriber coroutines.
 *
 * These tests confirm the isolation property rather than add new containment — it is a lirp
 * invariant and no mc-side wrapper is needed.
 */
@DisplayName("SubscriberIsolationTest")
@ExperimentalCoroutinesApi
internal class SubscriberIsolationTest : StringSpec({

    val reactive = reactiveScope(failOnUncaughtExceptions = false)

    "sync subscriber — throwing callback does not prevent peer subscriber from receiving the event" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<Int, AudioItem>>("IsolationSync")
        publisher.activateEvents(CREATE)

        val peerCount = AtomicInteger(0)

        // Subscriber that always throws on event delivery.
        publisher.subscribe { throw RuntimeException("Deliberate sync failure") }

        // Peer subscriber that counts received events — must not be affected by the throwing sibling.
        publisher.subscribe { peerCount.incrementAndGet() }

        val entity = mockk<AudioItem>(relaxed = true) { every { id } returns 1 }
        publisher.emitAsync(StandardCrudEvent.Create(entity))
        reactive.advance()

        peerCount.get() shouldBe 1
    }

    "async subscriber — throwing action does not cancel peer subscriber coroutine" {
        val publisher = FlowEventPublisher<CrudEvent.Type, CrudEvent<Int, AudioItem>>("IsolationAsync")
        publisher.activateEvents(CREATE)

        val peerCount = AtomicInteger(0)

        // Async subscriber that always throws on event delivery.
        publisher.subscribeAsync { throw RuntimeException("Deliberate async failure") }

        // Peer async subscriber that counts received events — the SupervisorJob on ReactiveScope.flowScope
        // ensures a sibling failure does not cancel this coroutine.
        publisher.subscribeAsync { peerCount.incrementAndGet() }

        val entity = mockk<AudioItem>(relaxed = true) { every { id } returns 1 }
        publisher.emitAsync(StandardCrudEvent.Create(entity))
        reactive.advance()

        eventually(2.seconds) {
            peerCount.get() shouldBe 1
        }
    }
})