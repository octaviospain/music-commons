package net.transgressoft.commons.music.event

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.PLAYED
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.FlowEventPublisher
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [PlayedEventSubscriber] verifying event reaction and subscription cancellation.
 */
@ExperimentalCoroutinesApi
internal class PlayedEventSubscriberTest : StringSpec({

    val reactive = reactiveScope()

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
        publisher.emitAsync(AudioItemPlayerEvent.Played(audioItem))
        reactive.advance()

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
        publisher.emitAsync(AudioItemPlayerEvent.Played(audioItem))
        reactive.advance()

        val countAfterFirst = eventCount

        subscriber.cancelSubscription()

        publisher.emitAsync(AudioItemPlayerEvent.Played(audioItem))
        reactive.advance()

        eventCount shouldBe countAfterFirst
    }

    "PlayedEventSubscriber toString includes name" {
        val subscriber = PlayedEventSubscriber()

        subscriber.toString() shouldContain "PlayedEventSubscriber"
    }
})