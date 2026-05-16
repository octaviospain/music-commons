package net.transgressoft.commons.music.audio.event

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [AudioItemEventSubscriber] verifying event reaction and subscription cancellation.
 */
@ExperimentalCoroutinesApi
internal class AudioItemEventSubscriberTest : StringSpec({

    val reactive = reactiveScope()
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var library: DefaultAudioLibrary

    beforeEach {
        repository = VolatileRepository("TestRepo")
        library = DefaultAudioLibrary(repository)
    }

    afterEach {
        library.close()
    }

    "AudioItemEventSubscriber reacts to CREATE event" {
        var createEventReceived = false
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.CREATE) {
            createEventReceived = true
        }
        repository.subscribe(subscriber)

        library.createFromFile(Arb.virtualAudioFile().next())
        reactive.advance()

        createEventReceived shouldBe true
    }

    "AudioItemEventSubscriber reacts to DELETE event" {
        var deleteEventReceived = false
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.DELETE) {
            deleteEventReceived = true
        }
        repository.subscribe(subscriber)

        val audioItem = library.createFromFile(Arb.virtualAudioFile().next())
        reactive.advance()

        library.remove(audioItem)
        reactive.advance()

        deleteEventReceived shouldBe true
    }

    "AudioItemEventSubscriber cancelSubscription stops event delivery" {
        var eventCount = 0
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.CREATE) {
            eventCount++
        }
        repository.subscribe(subscriber)

        library.createFromFile(Arb.virtualAudioFile().next())
        reactive.advance()

        val countAfterFirst = eventCount

        subscriber.cancelSubscription()

        library.createFromFile(Arb.virtualAudioFile().next())
        reactive.advance()

        eventCount shouldBe countAfterFirst
    }

    "AudioItemEventSubscriber toString includes name" {
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")

        subscriber.toString() shouldContain "TestSubscriber"
    }
})