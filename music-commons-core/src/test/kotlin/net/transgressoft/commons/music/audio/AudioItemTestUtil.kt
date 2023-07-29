package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

open class AudioItemTestUtil {
    var audioItemTestFactory = AudioItemTestFactory()

    fun createTestAudioItem() = audioItemTestFactory.createTestAudioItem()
    protected fun createTestAudioItem(name: String) = audioItemTestFactory.createTestAudioItem(name)
    protected fun createTestAudioItem(name: String, artistName: String) = audioItemTestFactory.createTestAudioItem(name, artistName)
    protected fun createTestAudioItem(id: Int, album: Album) = audioItemTestFactory.createTestAudioItem(id, album)
    protected fun createTestAudioItem(album: Album) = audioItemTestFactory.createTestAudioItem(album)
    protected fun createTestAudioItem(name: String, duration: Duration) = audioItemTestFactory.createTestAudioItem(name, duration)
    protected fun createTestAudioItemsList(size: Int) = audioItemTestFactory.createTestAudioItemsList(size)
}

class AudioItemTestFactory {
    private var testCounter = 400
    private val defaultAudioItem: AudioItemBuilder<AudioItem>

    init {
        defaultAudioItem = ImmutableAudioItemBuilder()
            .id(-1)
            .path(DEFAULT_PATH)
            .title("400 title")
            .duration(DEFAULT_DURATION)
            .bitRate(320)
            .dateOfCreation(LocalDateTime.now())
            .lastDateModified(LocalDateTime.now())
    }

    fun createTestAudioItem(): AudioItem {
        val id = testCounter++
        return defaultAudioItem.id(id).path(DEFAULT_PATH.resolve("$id.mp3")).build()
    }

    fun createTestAudioItem(title: String): AudioItem {
        val id = testCounter++
        return defaultAudioItem.id(id).title(title).build()
    }

    fun createTestAudioItem(album: Album): AudioItem {
        val id = testCounter++
        return defaultAudioItem.id(id).album(album).build()
    }

    fun createTestAudioItem(id: Int, album: Album): AudioItem {
        return defaultAudioItem.id(id).album(album).build()
    }

    fun createTestAudioItem(title: String, artistName: String): AudioItem {
        val id = testCounter++
        return defaultAudioItem.id(id).title(title).artist(ImmutableArtist(artistName)).build()
    }

    fun createTestAudioItem(title: String, duration: Duration): AudioItem {
        val id = testCounter++
        return defaultAudioItem.id(id).duration(duration).title(title).build()
    }

    fun createTestAudioItemsList(size: Int): List<AudioItem> {
        val list = ArrayList<AudioItem>()
        for (i in 0 until size) {
            list.add(createTestAudioItem())
        }
        return list
    }

    companion object {
        private val DEFAULT_PATH = Path.of("Music")
        private val DEFAULT_DURATION = Duration.ofSeconds(32)
    }
}