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
        val artist1 = ImmutableArtist.of("TestArtist", CountryCode.US)
        val artist2 = ImmutableArtist.of("TestArtist", CountryCode.US)

        artist1 shouldBeSameInstanceAs artist2
    }

    "ImmutableArtist of() returns distinct instances for different names" {
        val artistA = ImmutableArtist.of("ArtistA")
        val artistB = ImmutableArtist.of("ArtistB")

        artistA shouldNotBeSameInstanceAs artistB
    }

    "ImmutableArtist of() trims name before cache lookup" {
        val artistTrimmed = ImmutableArtist.of("Trimmed")
        val artistWithSpaces = ImmutableArtist.of("  Trimmed  ")

        artistTrimmed shouldBeSameInstanceAs artistWithSpaces
    }

    "ImmutableArtist of() with different country codes returns distinct instances" {
        val artistUS = ImmutableArtist.of("SameArtist", CountryCode.US)
        val artistGB = ImmutableArtist.of("SameArtist", CountryCode.GB)

        artistUS shouldNotBeSameInstanceAs artistGB
    }

    "ImmutableArtist UNKNOWN is singleton" {
        ImmutableArtist.UNKNOWN shouldBeSameInstanceAs ImmutableArtist.UNKNOWN
    }

    "ImmutableArtist UNKNOWN has empty name" {
        ImmutableArtist.UNKNOWN.name shouldBe ""
    }

    "ImmutableArtist equals compares name and countryCode" {
        val artist1 = ImmutableArtist.of("Same", CountryCode.US)
        val artist2 = ImmutableArtist.of("Same", CountryCode.US)

        artist1 shouldBe artist2
    }

    "ImmutableArtist hashCode is consistent with equals" {
        val artist1 = ImmutableArtist.of("HashArtist", CountryCode.DE)
        val artist2 = ImmutableArtist.of("HashArtist", CountryCode.DE)

        artist1 shouldBe artist2
        artist1.hashCode() shouldBe artist2.hashCode()
    }

    "ImmutableArtist compareTo orders by name then countryCode" {
        val artistA = ImmutableArtist.of("AArtist")
        val artistB = ImmutableArtist.of("BArtist")

        (artistA.compareTo(artistB) < 0) shouldBe true
        (artistB.compareTo(artistA) > 0) shouldBe true
    }

    "ImmutableArtist compareTo orders by countryCode when names are equal" {
        val artistUS = ImmutableArtist.of("SameName", CountryCode.US)
        val artistFR = ImmutableArtist.of("SameName", CountryCode.FR)

        (artistUS.compareTo(artistFR) != 0) shouldBe true
        (artistFR.compareTo(artistUS) != 0) shouldBe true
        (artistUS.compareTo(artistFR)) shouldNotBe 0
    }
})