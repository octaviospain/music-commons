package net.transgressoft.commons.fx.music

import net.transgressoft.commons.music.audio.*
import com.neovisionaries.i18n.CountryCode
import io.kotest.core.spec.style.FunSpec
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

internal const val expectedTitle = "Yesterday"
internal const val expectedArtistName = "The Beatles"
internal val expectedLabel: Label = ImmutableLabel("EMI", CountryCode.US)
internal const val expectedAlbumName = "Help!"
internal const val expectedAlbumArtistName = "The Beatles Band"
internal const val expectedIsCompilation = true
internal const val expectedYear: Short = 1965
internal const val expectedBpm = 120f
internal const val expectedTrackNumber: Short = 13
internal const val expectedDiscNumber: Short = 1
internal const val expectedComments = "Best song ever!"
internal val expectedGenre = Genre.ROCK
internal const val expectedEncoder = "transgressoft"
internal val expectedDateOfCreation = LocalDateTime.now()
internal val expectedArtist = ImmutableArtist.of(expectedArtistName, CountryCode.UK)
internal val expectedAlbumArtist = ImmutableArtist.of(expectedAlbumArtistName, CountryCode.UK)
internal val expectedAlbum = ImmutableAlbum(expectedAlbumName, expectedAlbumArtist, expectedIsCompilation, expectedYear, expectedLabel)

internal class ObservableAudioItemTest : FunSpec({

    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(ObservableAudioItem::class, ObservableAudioItemSerializer)
        }
        prettyPrint = true
    }

    fun AudioItemTestAttributes.setExpectedAttributes() {
        title = expectedTitle
        artist = expectedArtist
        album = expectedAlbum
        bpm = expectedBpm
        trackNumber = expectedTrackNumber
        discNumber = expectedDiscNumber
        comments = expectedComments
        genre = expectedGenre
        encoder = expectedEncoder
        dateOfCreation = expectedDateOfCreation
    }

    context("should create an ObservableAudioItem and be serializable") {

    }
})
