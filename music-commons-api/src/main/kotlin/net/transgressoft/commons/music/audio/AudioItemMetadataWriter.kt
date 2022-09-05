package net.transgressoft.commons.music.audio

/**
 * @author Octavio Calleya
 */
interface AudioItemMetadataWriter {

    @Throws(AudioItemManipulationException::class)
    fun writeMetadata(serializableAudioItem: AudioItem)
}