package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

open class AudioItemTestUtil {
    var audioItemTestFactory = AudioItemTestFactory()

    protected fun createTestAudioItem() = audioItemTestFactory.createTestAudioItem()
    protected fun createTestAudioItem(name: String) = audioItemTestFactory.createTestAudioItem(name)
    protected fun createTestAudioItem(name: String, artistName: String) = audioItemTestFactory.createTestAudioItem(name, artistName)
    protected fun createTestAudioItem(id: Int, album: Album) = audioItemTestFactory.createTestAudioItem(id, album)
    protected fun createTestAudioItem(album: Album) = audioItemTestFactory.createTestAudioItem(album)
    protected fun createTestAudioItem(name: String, duration: Duration) = audioItemTestFactory.createTestAudioItem(name, duration)
    protected fun createTestAudioItemsList(size: Int) = audioItemTestFactory.createTestAudioItemsList(size)
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

    fun createTestAudioItem(): ImmutableAudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, path = DEFAULT_PATH.resolve("$id.mp3"))
    }

    fun createTestAudioItem(title: String): ImmutableAudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, title = title)
    }

    fun createTestAudioItem(album: Album): ImmutableAudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, album = album)
    }

    fun createTestAudioItem(id: Int, album: Album): ImmutableAudioItem {
        return defaultAudioItem.copy(id = id, album = album)
    }

    fun createTestAudioItem(title: String, artistName: String): ImmutableAudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, title = title, artist = ImmutableArtist(artistName))
    }

    fun createTestAudioItem(title: String, duration: Duration): ImmutableAudioItem {
        val id = testCounter++
        return defaultAudioItem.copy(id = id, duration = duration, title = title)
    }

    fun createTestAudioItemsList(size: Int): List<ImmutableAudioItem> {
        val list = ArrayList<ImmutableAudioItem>()
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