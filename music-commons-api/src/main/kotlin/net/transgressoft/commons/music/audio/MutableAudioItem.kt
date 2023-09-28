package net.transgressoft.commons.music.audio

interface MutableAudioItem : AudioItem {
    override var title: String
    override var artist: Artist
    override var album: Album
    override var genre: Genre
    override var comments: String?
    override var trackNumber: Short?
    override var discNumber: Short?
    override var bpm: Float?
    override var coverImageBytes: ByteArray?

    override fun update(change: AudioItemChange): MutableAudioItem

    override fun update(changeAction: AudioItemChange.() -> Unit): MutableAudioItem

    fun toImmutableAudioItem(): AudioItem
}