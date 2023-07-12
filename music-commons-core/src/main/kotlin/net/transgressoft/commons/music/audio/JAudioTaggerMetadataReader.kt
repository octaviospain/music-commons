package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.LocalDateTime

class JAudioTaggerMetadataReader: JAudioTaggerMetadataReaderBase<AudioItemBase>() {

    override fun readAudioItem(id: Int, audioItemPath: Path): AudioItemBase {
        initAudioItemFields(audioItemPath)
        return ImmutableAudioItem(
            id, audioItemPath, title, duration!!, bitRate, artist, album!!, genre, comments, trackNumber, discNumber,
            bpm, encoder, encoding, LocalDateTime.now(), LocalDateTime.now()
        )
    }
}