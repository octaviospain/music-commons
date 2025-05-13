package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AudioItemSerializerBase
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.ImmutableLabel
import com.neovisionaries.i18n.CountryCode
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

val ObservableAudioItemMapSerializer: KSerializer<Map<Int, ObservableAudioItem>> = MapSerializer(Int.serializer(), ObservableAudioItemSerializer)

internal object ObservableAudioItemSerializer : AudioItemSerializerBase<ObservableAudioItem>() {

    override fun createInstance(propertiesList: List<Any?>): ObservableAudioItem =
        FXAudioItem(
            // path
            propertiesList[0] as Path,
            // id
            propertiesList[1] as Int,
            // title
            propertiesList[2] as String,
            // duration
            propertiesList[3] as Duration,
            // bitRate
            propertiesList[4] as Int,
            // artist name and artist country code
            ImmutableArtist.of(propertiesList[5] as String, CountryCode.getByCode(propertiesList[6] as String)),
            ImmutableAlbum(
                // album name
                propertiesList[7] as String,
                // album artist name
                ImmutableArtist.of(propertiesList[8] as String),
                // album isCompilation
                propertiesList[9] as Boolean,
                // album year
                propertiesList[10] as Short?,
                // album label name
                ImmutableLabel.of(propertiesList[11] as String)
            ),
            // genre
            Genre.parseGenre(propertiesList[12] as String),
            // comments
            propertiesList[13] as String?,
            // trackNumber
            propertiesList[14] as Short?,
            // discNumber
            propertiesList[15] as Short?,
            // bpm
            propertiesList[16] as Float?,
            // encoder
            propertiesList[17] as String?,
            // encoding
            propertiesList[18] as String?,
            // dateOfCreation
            propertiesList[19] as LocalDateTime,
            // lastDateModified
            propertiesList[20] as LocalDateTime,
            // playCount
            propertiesList[21] as Short
        )
}