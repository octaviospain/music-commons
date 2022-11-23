package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.LocalDateTime

internal class JAudioTaggerMetadataReader(private val audioItemPath: Path): JAudioTaggerMetadataReaderBase<AudioItem>(audioItemPath) {

    override fun readAudioItem(id: Int): AudioItem = ImmutableAudioItem(
        id, audioItemPath, title, duration, bitRate, artist, album, genre, comments, trackNumber, discNumber,
        bpm, encoder, encoding, LocalDateTime.now(), LocalDateTime.now()
    )
}