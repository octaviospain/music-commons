package net.transgressoft.commons.music.playlist.event

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.playlist.DefaultPlaylistHierarchy
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [AudioPlaylistEventSubscriber] verifying event reaction and subscription cancellation.
 */
@ExperimentalCoroutinesApi
internal class AudioPlaylistEventSubscriberTest : StringSpec({

    val reactive = reactiveScope()
    lateinit var hierarchy: DefaultPlaylistHierarchy

    beforeEach {
        hierarchy = DefaultPlaylistHierarchy()
    }

    afterEach {
        hierarchy.close()
    }

    "AudioPlaylistEventSubscriber reacts to CREATE event" {
        var createEventReceived = false
        val subscriber = AudioPlaylistEventSubscriber<MutableAudioPlaylist, AudioItem>("TestPlaylistSubscriber")
        subscriber.addOnNextEventAction(CrudEvent.Type.CREATE) {
            createEventReceived = true
        }
        hierarchy.subscribe(subscriber)

        hierarchy.createPlaylist("My Playlist")
        reactive.advance()

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
        reactive.advance()

        hierarchy.remove(playlist)
        reactive.advance()

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
        reactive.advance()

        val countAfterFirst = eventCount

        subscriber.cancelSubscription()

        hierarchy.createPlaylist("Playlist Two")
        reactive.advance()

        eventCount shouldBe countAfterFirst
    }

    "AudioPlaylistEventSubscriber toString includes name" {
        val subscriber = AudioPlaylistEventSubscriber<MutableAudioPlaylist, AudioItem>("TestPlaylistSubscriber")

        subscriber.toString() shouldContain "TestPlaylistSubscriber"
    }
})