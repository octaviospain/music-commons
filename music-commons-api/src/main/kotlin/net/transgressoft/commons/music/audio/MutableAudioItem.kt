package net.transgressoft.commons.music.audio

import java.nio.file.Path

interface MutableAudioItem : AudioItem {
    override var path: Path
    override var title: String
    override var artist: Artist
    override var album: Album
    override var genre: Genre
    override var comments: String?
    override var trackNumber: Short?
    override var discNumber: Short?
    override var bpm: Float?
    override var coverImage: ByteArray?

    override fun update(change: AudioItemMetadataChange): MutableAudioItem

    override fun update(changeAction: AudioItemMetadataChange.() -> Unit): MutableAudioItem

    fun toImmutableAudioItem(): AudioItem
}