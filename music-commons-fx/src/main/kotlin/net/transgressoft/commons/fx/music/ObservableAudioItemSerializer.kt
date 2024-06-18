package net.transgressoft.commons.fx.music

import net.transgressoft.commons.music.audio.*
import com.neovisionaries.i18n.CountryCode
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

internal object ObservableAudioItemSerializer : AudioItemSerializerBase<ObservableAudioItem>() {

    override fun createInstance(propertiesList: List<Any?>): ObservableAudioItem {
        return FXAudioItem(propertiesList[0] as Path,
            propertiesList[1] as Int,
            propertiesList[2] as String,
            propertiesList[3] as Duration,
            propertiesList[4] as Int,
            ImmutableArtist.of(propertiesList[5] as String, CountryCode.getByCode(propertiesList[6] as String)),
            ImmutableAlbum(propertiesList[7] as String, ImmutableArtist.of(propertiesList[8] as String), propertiesList[9] as Boolean,
                propertiesList[10] as Short, ImmutableLabel(propertiesList[11] as String)
            ),
            Genre.parseGenre(propertiesList[12] as String),
            propertiesList[13] as String?,
            propertiesList[14] as Short,
            propertiesList[15] as Short,
            propertiesList[16] as Float,
            propertiesList[17] as String?,
            propertiesList[18] as String?,
            propertiesList[19] as LocalDateTime,
            propertiesList[20] as LocalDateTime
        )
    }
}
