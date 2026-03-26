package net.transgressoft.commons.music.event

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.lirp.event.FlowEventPublisher
import net.transgressoft.lirp.event.ReactiveScope
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Tests for [PlayedEventSubscriber] verifying event reaction and subscription cancellation.
 */
@ExperimentalCoroutinesApi
internal class PlayedEventSubscriberTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "PlayedEventSubscriber reacts to PLAYED event type" {
        var playedEventReceived = false
        val subscriber = PlayedEventSubscriber()
        subscriber.addOnNextEventAction(PLAYED) {
            playedEventReceived = true
        }

        val publisher = FlowEventPublisher<AudioItemPlayerEvent.Type, AudioItemPlayerEvent>("TestPlayerPublisher")
        publisher.activateEvents(PLAYED)
        publisher.subscribe(subscriber)

        val audioItem = mockk<AudioItem>(relaxed = true)
        publisher.emitAsync(AudioItemPlayerEvent.Played<AudioItem>(audioItem))
        testDispatcher.scheduler.advanceUntilIdle()

        playedEventReceived shouldBe true
    }

    "PlayedEventSubscriber cancelSubscription stops event delivery" {
        var eventCount = 0
        val subscriber = PlayedEventSubscriber()
        subscriber.addOnNextEventAction(PLAYED) {
            eventCount++
        }

        val publisher = FlowEventPublisher<AudioItemPlayerEvent.Type, AudioItemPlayerEvent>("TestPlayerPublisher")
        publisher.activateEvents(PLAYED)
        publisher.subscribe(subscriber)

        val audioItem = mockk<AudioItem>(relaxed = true)
        publisher.emitAsync(AudioItemPlayerEvent.Played<AudioItem>(audioItem))
        testDispatcher.scheduler.advanceUntilIdle()

        val countAfterFirst = eventCount

        subscriber.cancelSubscription()

        publisher.emitAsync(AudioItemPlayerEvent.Played<AudioItem>(audioItem))
        testDispatcher.scheduler.advanceUntilIdle()

        eventCount shouldBe countAfterFirst
    }

    "PlayedEventSubscriber toString includes name" {
        val subscriber = PlayedEventSubscriber()

        subscriber.toString() shouldContain "PlayedEventSubscriber"
    }
})