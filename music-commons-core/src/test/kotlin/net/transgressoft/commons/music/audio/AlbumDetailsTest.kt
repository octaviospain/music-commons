package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

@DisplayName("AlbumDetails")
internal class AlbumDetailsTest : StringSpec({

    "AlbumDetails equals compares all fields" {
        val artist = Artist.of("Artist", CountryCode.US)
        val label = Label.of("Label", CountryCode.US)
        val album1 = AlbumDetails("Album Name", artist, false, 2000, label)
        val album2 = AlbumDetails("Album Name", artist, false, 2000, label)

        album1 shouldBe album2
    }

    val base = AlbumDetails("Album", Artist.of("Artist"), isCompilation = false, year = 2000, label = Label.of("Label"))

    data class InequalityCase(val label: String, val other: AlbumDetails)

    withData(
        nameFn = { "AlbumDetails equals returns false when ${it.label} differs" },
        InequalityCase("name", base.copy(name = "Other")),
        InequalityCase("albumArtist", base.copy(albumArtist = Artist.of("Other"))),
        InequalityCase("isCompilation", base.copy(isCompilation = true)),
        InequalityCase("year", base.copy(year = 1999)),
        InequalityCase("label", base.copy(label = Label.of("Other")))
    ) { case ->
        base shouldNotBe case.other
    }

    "AlbumDetails hashCode is consistent with equals" {
        val artist = Artist.of("HashArtist")
        val label = Label.of("HashLabel")
        val album1 = AlbumDetails("HashAlbum", artist, false, 1999, label)
        val album2 = AlbumDetails("HashAlbum", artist, false, 1999, label)

        album1 shouldBe album2
        album1.hashCode() shouldBe album2.hashCode()
    }

    data class OrderingCase(val label: String, val smaller: AlbumDetails, val greater: AlbumDetails)

    withData(
        nameFn = { it.label },
        OrderingCase(
            "AlbumDetails compareTo orders by label first",
            AlbumDetails("Album", Artist.of("ArtistA"), label = Label.of("LabelA")),
            AlbumDetails("Album", Artist.of("ArtistA"), label = Label.of("LabelB"))
        ),
        OrderingCase(
            "AlbumDetails compareTo orders by year when labels are equal",
            AlbumDetails("Album", Artist.of("Artist"), year = 1990, label = Label.of("Label")),
            AlbumDetails("Album", Artist.of("Artist"), year = 2000, label = Label.of("Label"))
        ),
        OrderingCase(
            "AlbumDetails compareTo orders by artist when label and year are equal",
            AlbumDetails("Album", Artist.of("ArtistA"), year = 2000, label = Label.of("Label")),
            AlbumDetails("Album", Artist.of("ArtistB"), year = 2000, label = Label.of("Label"))
        ),
        OrderingCase(
            "AlbumDetails compareTo orders by name when label, year and artist are equal",
            AlbumDetails("A", Artist.of("ArtistA"), year = 2000, label = Label.of("Label")),
            AlbumDetails("B", Artist.of("ArtistA"), year = 2000, label = Label.of("Label"))
        ),
        OrderingCase(
            "AlbumDetails compareTo falls back to isCompilation when all else is equal",
            AlbumDetails("Album", Artist.of("Artist"), isCompilation = false, year = 2000, label = Label.of("Label")),
            AlbumDetails("Album", Artist.of("Artist"), isCompilation = true, year = 2000, label = Label.of("Label"))
        )
    ) { (_, smaller, greater) ->
        smaller shouldBeLessThan greater
        greater shouldBeGreaterThan smaller
    }

    "AlbumDetails UNKNOWN has empty name and UNKNOWN artist" {
        AlbumDetails.UNKNOWN.name shouldBe ""
        AlbumDetails.UNKNOWN.albumArtist shouldBe Artist.UNKNOWN
    }
})