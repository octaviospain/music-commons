package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

open class AudioItemTestUtil {
    var audioItemTestFactory = AudioItemTestFactory()

    protected fun createTestAudioItem(): AudioItem {
        return audioItemTestFactory.createTestAudioItem()
    }

    protected fun createTestAudioItem(name: String): AudioItem {
        return audioItemTestFactory.createTestAudioItem(name)
    }

    protected fun createTestAudioItem(id: Int, album: Album): AudioItem {
        return audioItemTestFactory.createTestAudioItem(id, album)
    }

    protected fun createTestAudioItem(album: Album): AudioItem {
        return audioItemTestFactory.createTestAudioItem(album)
    }

    protected fun createTestAudioItem(name: String, duration: Duration): AudioItem {
        return audioItemTestFactory.createTestAudioItem(name, duration)
    }

    protected fun createTestAudioItemsSet(size: Int): List<AudioItem> {
        return audioItemTestFactory.createTestAudioItemsList(size)
    }
}

class AudioItemTestFactory {
    private var testCounter = 400
    private val defaultAudioItem: ImmutableAudioItem

    init {
        defaultAudioItem = ImmutableAudioItem(
            id = -1,
            path = DEFAULT_PATH,
            title = "400 title",
            duration = DEFAULT_DURATION,
            bitRate = 320,
            dateOfCreation = LocalDateTime.now(),
            lastDateModified = LocalDateTime.now()
        )
    }

    fun createTestAudioItem(): AudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, path = DEFAULT_PATH.resolve("$id.mp3"))
    }

    fun createTestAudioItem(title: String): AudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, title = title)
    }

    fun createTestAudioItem(album: Album): AudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, album = album)
    }

    fun createTestAudioItem(id: Int, album: Album): AudioItem {
        return defaultAudioItem.copy(id = id, album = album)
    }

    fun createTestAudioItem(title: String, duration: Duration): AudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, duration = duration, title = title)
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