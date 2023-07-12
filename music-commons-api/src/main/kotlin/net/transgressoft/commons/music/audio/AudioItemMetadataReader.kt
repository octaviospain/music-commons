package net.transgressoft.commons.music.audio

import java.nio.file.Path

interface AudioItemMetadataReader<I: AudioItem> {

    fun readAudioItem(id: Int,  audioItemPath: Path): I
}
