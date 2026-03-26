package net.transgressoft.commons.music.playlist.event

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.ReactiveScope
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Tests for [AudioPlaylistEventSubscriber] verifying event reaction and subscription cancellation.
 */
@ExperimentalCoroutinesApi
internal class AudioPlaylistEventSubscriberTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var hierarchy: DefaultPlaylistHierarchy

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        hierarchy = DefaultPlaylistHierarchy()
    }

    afterEach {
        hierarchy.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "AudioPlaylistEventSubscriber reacts to CREATE event" {
        var createEventReceived = false
        val subscriber = AudioPlaylistEventSubscriber<MutableAudioPlaylist, AudioItem>("TestPlaylistSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.CREATE) {
            createEventReceived = true
        }
        hierarchy.subscribe(subscriber)

        hierarchy.createPlaylist("My Playlist")
        testDispatcher.scheduler.advanceUntilIdle()

        createEventReceived shouldBe true
    }

    "AudioPlaylistEventSubscriber reacts to DELETE event" {
        var deleteEventReceived = false
        val subscriber = AudioPlaylistEventSubscriber<MutableAudioPlaylist, AudioItem>("TestPlaylistSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.DELETE) {
            deleteEventReceived = true
        }
        hierarchy.subscribe(subscriber)

        val playlist = hierarchy.createPlaylist("My Playlist")
        testDispatcher.scheduler.advanceUntilIdle()

        hierarchy.remove(playlist)
        testDispatcher.scheduler.advanceUntilIdle()

        deleteEventReceived shouldBe true
    }

    "AudioPlaylistEventSubscriber cancelSubscription stops event delivery" {
        var eventCount = 0
        val subscriber = AudioPlaylistEventSubscriber<MutableAudioPlaylist, AudioItem>("TestPlaylistSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.CREATE) {
            eventCount++
        }
        hierarchy.subscribe(subscriber)

        hierarchy.createPlaylist("Playlist One")
        testDispatcher.scheduler.advanceUntilIdle()

        val countAfterFirst = eventCount

        subscriber.cancelSubscription()

        hierarchy.createPlaylist("Playlist Two")
        testDispatcher.scheduler.advanceUntilIdle()

        eventCount shouldBe countAfterFirst
    }

    "AudioPlaylistEventSubscriber toString includes name" {
        val subscriber = AudioPlaylistEventSubscriber<MutableAudioPlaylist, AudioItem>("TestPlaylistSubscriber")

        subscriber.toString() shouldContain "TestPlaylistSubscriber"
    }
})