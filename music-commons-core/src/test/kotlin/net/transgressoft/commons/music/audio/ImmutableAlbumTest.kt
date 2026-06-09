package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

@DisplayName("ImmutableAlbum")
internal class ImmutableAlbumTest : StringSpec({

    "ImmutableAlbum equals compares all fields" {
        val artist = Artist.of("Artist", CountryCode.US)
        val label = Label.of("Label", CountryCode.US)
        val album1 = Album("Album Name", artist, false, 2000, label)
        val album2 = Album("Album Name", artist, false, 2000, label)

        album1 shouldBe album2
    }

    "ImmutableAlbum equals returns false for different name" {
        val artist = Artist.of("Artist")
        val album1 = Album("Album One", artist)
        val album2 = Album("Album Two", artist)

        album1 shouldNotBe album2
    }

    "ImmutableAlbum equals returns false for different albumArtist" {
        val artist1 = Artist.of("Artist One")
        val artist2 = Artist.of("Artist Two")
        val album1 = Album("Album", artist1)
        val album2 = Album("Album", artist2)

        album1 shouldNotBe album2
    }

    "ImmutableAlbum equals returns false for different isCompilation flag" {
        val artist = Artist.of("Artist")
        val album1 = Album("Album", artist, isCompilation = true)
        val album2 = Album("Album", artist, isCompilation = false)

        album1 shouldNotBe album2
    }

    "ImmutableAlbum hashCode is consistent with equals" {
        val artist = Artist.of("HashArtist")
        val label = Label.of("HashLabel")
        val album1 = Album("HashAlbum", artist, false, 1999, label)
        val album2 = Album("HashAlbum", artist, false, 1999, label)

        album1 shouldBe album2
        album1.hashCode() shouldBe album2.hashCode()
    }

    "ImmutableAlbum compareTo orders by label then year then artist then name" {
        val artistA = Artist.of("ArtistA")
        val labelA = Label.of("LabelA")
        val labelB = Label.of("LabelB")

        val albumLabelA = Album("Album", artistA, label = labelA)
        val albumLabelB = Album("Album", artistA, label = labelB)

        // label ordering
        (albumLabelA.compareTo(albumLabelB) < 0) shouldBe true
        (albumLabelB.compareTo(albumLabelA) > 0) shouldBe true

        // name ordering when all other fields are equal
        val albumNameA = Album("A", artistA, label = labelA)
        val albumNameB = Album("B", artistA, label = labelA)
        (albumNameA.compareTo(albumNameB) < 0) shouldBe true
    }

    "ImmutableAlbum compareTo orders by year when labels are equal" {
        val artist = Artist.of("Artist")
        val label = Label.of("Label")
        val albumOld = Album("Album", artist, year = 1990, label = label)
        val albumNew = Album("Album", artist, year = 2000, label = label)

        (albumOld.compareTo(albumNew) < 0) shouldBe true
        (albumNew.compareTo(albumOld) > 0) shouldBe true
    }

    "ImmutableAlbum compareTo orders by artist when label and year are equal" {
        val artistA = Artist.of("ArtistA")
        val artistB = Artist.of("ArtistB")
        val label = Label.of("Label")
        val albumArtistA = Album("Album", artistA, year = 2000, label = label)
        val albumArtistB = Album("Album", artistB, year = 2000, label = label)

        (albumArtistA.compareTo(albumArtistB) < 0) shouldBe true
    }

    "ImmutableAlbum UNKNOWN has empty name and UNKNOWN artist" {
        Album.UNKNOWN.name shouldBe ""
        Album.UNKNOWN.albumArtist shouldBe Artist.UNKNOWN
    }
})