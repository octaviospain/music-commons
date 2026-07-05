package net.transgressoft.commons.music.audio.event

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [AudioItemEventSubscriber] verifying event reaction and subscription cancellation.
 */
@ExperimentalCoroutinesApi
internal class AudioItemEventSubscriberTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var library: DefaultAudioLibrary

    beforeEach {
        repository = VolatileRepository("TestRepo")
        library = DefaultAudioLibrary(repository, files.metadataIO)
    }

    afterEach {
        library.close()
    }

    withData(
        nameFn = { "AudioItemEventSubscriber reacts to ${it.eventType} event" },
        EventReactionCase(CrudEvent.Type.CREATE) {
            library.createFromFile(files.virtualAudioFile().next())
            reactive.advance()
        },
        EventReactionCase(CrudEvent.Type.DELETE) {
            val audioItem = library.createFromFile(files.virtualAudioFile().next())
            reactive.advance()
            library.remove(audioItem)
            reactive.advance()
        }
    ) { case ->
        var eventReceived = false
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")
        subscriber.addOnNextEventAction(case.eventType) {
            eventReceived = true
        }
        repository.subscribe(subscriber)

        case.trigger()

        eventReceived shouldBe true
    }

    "AudioItemEventSubscriber cancelSubscription stops event delivery" {
        var eventCount = 0
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.CREATE) {
            eventCount++
        }
        repository.subscribe(subscriber)

        library.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val countAfterFirst = eventCount

        subscriber.cancelSubscription()

        library.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        eventCount shouldBe countAfterFirst
    }

    "AudioItemEventSubscriber toString includes name" {
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")

        subscriber.toString() shouldContain "TestSubscriber"
    }
})

private class EventReactionCase(
    val eventType: CrudEvent.Type,
    val trigger: () -> Unit
)