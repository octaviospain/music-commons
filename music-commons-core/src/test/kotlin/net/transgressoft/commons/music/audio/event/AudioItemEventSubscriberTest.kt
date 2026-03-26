package net.transgressoft.commons.music.audio.event

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Tests for [AudioItemEventSubscriber] verifying event reaction and subscription cancellation.
 */
@ExperimentalCoroutinesApi
internal class AudioItemEventSubscriberTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var library: DefaultAudioLibrary

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        repository = VolatileRepository("TestRepo")
        library = DefaultAudioLibrary(repository)
    }

    afterEach {
        library.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "AudioItemEventSubscriber reacts to CREATE event" {
        var createEventReceived = false
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.CREATE) {
            createEventReceived = true
        }
        repository.subscribe(subscriber)

        library.createFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

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
        testDispatcher.scheduler.advanceUntilIdle()

        library.remove(audioItem)
        testDispatcher.scheduler.advanceUntilIdle()

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
        testDispatcher.scheduler.advanceUntilIdle()

        val countAfterFirst = eventCount

        subscriber.cancelSubscription()

        library.createFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        eventCount shouldBe countAfterFirst
    }

    "AudioItemEventSubscriber toString includes name" {
        val subscriber = AudioItemEventSubscriber<AudioItem>("TestSubscriber")

        subscriber.toString() shouldContain "TestSubscriber"
    }
})