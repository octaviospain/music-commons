package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

@DisplayName("ImmutableAlbum")
internal class ImmutableAlbumTest : StringSpec({

    "ImmutableAlbum equals compares all fields" {
        val artist = ImmutableArtist.of("Artist", CountryCode.US)
        val label = ImmutableLabel.of("Label", CountryCode.US)
        val album1 = ImmutableAlbum("Album Name", artist, false, 2000, label)
        val album2 = ImmutableAlbum("Album Name", artist, false, 2000, label)

        album1 shouldBe album2
    }

    "ImmutableAlbum equals returns false for different name" {
        val artist = ImmutableArtist.of("Artist")
        val album1 = ImmutableAlbum("Album One", artist)
        val album2 = ImmutableAlbum("Album Two", artist)

        album1 shouldNotBe album2
    }

    "ImmutableAlbum equals returns false for different albumArtist" {
        val artist1 = ImmutableArtist.of("Artist One")
        val artist2 = ImmutableArtist.of("Artist Two")
        val album1 = ImmutableAlbum("Album", artist1)
        val album2 = ImmutableAlbum("Album", artist2)

        album1 shouldNotBe album2
    }

    "ImmutableAlbum equals returns false for different isCompilation flag" {
        val artist = ImmutableArtist.of("Artist")
        val album1 = ImmutableAlbum("Album", artist, isCompilation = true)
        val album2 = ImmutableAlbum("Album", artist, isCompilation = false)

        album1 shouldNotBe album2
    }

    "ImmutableAlbum hashCode is consistent with equals" {
        val artist = ImmutableArtist.of("HashArtist")
        val label = ImmutableLabel.of("HashLabel")
        val album1 = ImmutableAlbum("HashAlbum", artist, false, 1999, label)
        val album2 = ImmutableAlbum("HashAlbum", artist, false, 1999, label)

        album1 shouldBe album2
        album1.hashCode() shouldBe album2.hashCode()
    }

    "ImmutableAlbum compareTo orders by label then year then artist then name" {
        val artistA = ImmutableArtist.of("ArtistA")
        val labelA = ImmutableLabel.of("LabelA")
        val labelB = ImmutableLabel.of("LabelB")

        val albumLabelA = ImmutableAlbum("Album", artistA, label = labelA)
        val albumLabelB = ImmutableAlbum("Album", artistA, label = labelB)

        // label ordering
        (albumLabelA.compareTo(albumLabelB) < 0) shouldBe true
        (albumLabelB.compareTo(albumLabelA) > 0) shouldBe true
    }

    "ImmutableAlbum compareTo orders by year when labels are equal" {
        val artist = ImmutableArtist.of("Artist")
        val label = ImmutableLabel.of("Label")
        val albumOld = ImmutableAlbum("Album", artist, year = 1990, label = label)
        val albumNew = ImmutableAlbum("Album", artist, year = 2000, label = label)

        (albumOld.compareTo(albumNew) < 0) shouldBe true
        (albumNew.compareTo(albumOld) > 0) shouldBe true
    }

    "ImmutableAlbum compareTo orders by artist when label and year are equal" {
        val artistA = ImmutableArtist.of("ArtistA")
        val artistB = ImmutableArtist.of("ArtistB")
        val label = ImmutableLabel.of("Label")
        val albumArtistA = ImmutableAlbum("Album", artistA, year = 2000, label = label)
        val albumArtistB = ImmutableAlbum("Album", artistB, year = 2000, label = label)

        (albumArtistA.compareTo(albumArtistB) < 0) shouldBe true
    }

    "ImmutableAlbum UNKNOWN has empty name and UNKNOWN artist" {
        ImmutableAlbum.UNKNOWN.name shouldBe ""
        ImmutableAlbum.UNKNOWN.albumArtist shouldBe ImmutableArtist.UNKNOWN
    }
})