package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import java.util.*

internal open class MutableDiscography {

    private val logger = KotlinLogging.logger {}

    private val albums: MutableMap<String, MutableAlbum> = mutableMapOf()

    val size: Int
        get() = albums.values.stream().flatMap { it.audioItems.stream() }.count().toInt()

    fun addAudioItem(audioItem: MutableAudioItem): Boolean {
        return albums.merge(audioItem.album.name, MutableAlbum(audioItem)) { album, _ ->
            album.addAudioItem(audioItem)
            logger.debug { "AudioItem $audioItem was added to album ${audioItem.album}" }
            album
        }.let { it?.audioItems?.size!! > 1 }
    }

    fun removeAudioItem(audioItem: MutableAudioItem): Boolean {
        return albums[audioItem.album.name]?.removeAudioItem(audioItem) ?: false .also {
            logger.debug { "AudioItem $audioItem was removed from album ${audioItem.album}" }
        }
    }

    fun findAlbum(albumName: String): Optional<Album> = Optional.ofNullable(albums[albumName])

    fun containsAudioItem(audioItem: AudioItem) = albums[audioItem.album.name]?.audioItems?.contains(audioItem) ?: false
}