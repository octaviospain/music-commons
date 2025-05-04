package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.ArbitraryAudioFile.getResourceAsFile
import io.mockk.mockk
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavInfoTag
import org.jaudiotagger.tag.wav.WavTag
import java.io.File

internal val mp3File = getResourceAsFile("/testfiles/testeable.mp3")
internal val m4aFile = getResourceAsFile("/testfiles/testeable.m4a")
internal val flacFile = getResourceAsFile("/testfiles/testeable.flac")
internal val wavFile = getResourceAsFile("/testfiles/testeable.wav")

enum class AudioFileTagType {

    ID3_V_24(AudioFileType.MP3, { mockk<ID3v24Tag>() }, { ID3v24Tag() }, mp3File),
    FLAC(AudioFileType.FLAC, { mockk<FlacTag>() }, { FlacTag() }, flacFile),
    MP4_INFO(AudioFileType.M4A, { mockk<Mp4Tag>() }, { Mp4Tag() }, m4aFile),
    WAV(
        AudioFileType.WAV,
        { mockk<WavTag>() },
        {
            WavTag(WavOptions.READ_ID3_ONLY).apply {
                iD3Tag = ID3v24Tag()
                infoTag = WavInfoTag()
            }
        },
        wavFile
    );

    val fileType: AudioFileType
    val newMockedTag: () -> Tag
    val newActualTag: () -> Tag
    internal val testFile: File

    constructor(fileType: AudioFileType, mockedTagFunction: () -> Tag, realTagFunction: () -> Tag, testFile: File) {
        this.fileType = fileType
        this.newMockedTag = mockedTagFunction
        this.newActualTag = realTagFunction
        this.testFile = testFile
    }
}