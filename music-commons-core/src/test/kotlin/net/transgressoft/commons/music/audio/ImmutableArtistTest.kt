package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

@DisplayName("ImmutableArtist")
internal class ImmutableArtistTest : StringSpec({

    "ImmutableArtist of() returns cached instance for same name and country code" {
        val artist1 = Artist.of("TestArtist", CountryCode.US)
        val artist2 = Artist.of("TestArtist", CountryCode.US)

        artist1 shouldBeSameInstanceAs artist2
    }

    "ImmutableArtist of() returns distinct instances for different names" {
        val artistA = Artist.of("ArtistA")
        val artistB = Artist.of("ArtistB")

        artistA shouldNotBeSameInstanceAs artistB
    }

    "ImmutableArtist of() trims name before cache lookup" {
        val artistTrimmed = Artist.of("Trimmed")
        val artistWithSpaces = Artist.of("  Trimmed  ")

        artistTrimmed shouldBeSameInstanceAs artistWithSpaces
    }

    "ImmutableArtist of() with different country codes returns distinct instances" {
        val artistUS = Artist.of("SameArtist", CountryCode.US)
        val artistGB = Artist.of("SameArtist", CountryCode.GB)

        artistUS shouldNotBeSameInstanceAs artistGB
    }

    "ImmutableArtist UNKNOWN is singleton" {
        Artist.UNKNOWN shouldBeSameInstanceAs Artist.UNKNOWN
    }

    "ImmutableArtist UNKNOWN has empty name" {
        Artist.UNKNOWN.name shouldBe ""
    }

    "ImmutableArtist equals compares name and countryCode" {
        val artist1 = Artist.of("Same", CountryCode.US)
        val artist2 = Artist.of("Same", CountryCode.US)

        artist1 shouldBe artist2
    }

    "ImmutableArtist hashCode is consistent with equals" {
        val artist1 = Artist.of("HashArtist", CountryCode.DE)
        val artist2 = Artist.of("HashArtist", CountryCode.DE)

        artist1 shouldBe artist2
        artist1.hashCode() shouldBe artist2.hashCode()
    }

    "ImmutableArtist compareTo orders by name then countryCode" {
        val artistA = Artist.of("AArtist")
        val artistB = Artist.of("BArtist")

        (artistA.compareTo(artistB) < 0) shouldBe true
        (artistB.compareTo(artistA) > 0) shouldBe true
    }

    "ImmutableArtist compareTo orders by countryCode when names are equal" {
        val artistUS = Artist.of("SameName", CountryCode.US)
        val artistFR = Artist.of("SameName", CountryCode.FR)

        (artistUS.compareTo(artistFR) != 0) shouldBe true
        (artistFR.compareTo(artistUS) != 0) shouldBe true
        (artistUS.compareTo(artistFR)) shouldNotBe 0
    }
})