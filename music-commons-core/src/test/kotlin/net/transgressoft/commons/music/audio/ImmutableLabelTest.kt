package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

@DisplayName("ImmutableLabel")
internal class ImmutableLabelTest : StringSpec({

    "ImmutableLabel of() returns cached instance for same name and country code" {
        val label1 = Label.of("TestLabel", CountryCode.US)
        val label2 = Label.of("TestLabel", CountryCode.US)

        label1 shouldBeSameInstanceAs label2
    }

    "ImmutableLabel of() returns distinct instances for different names" {
        val labelA = Label.of("LabelA")
        val labelB = Label.of("LabelB")

        labelA shouldNotBeSameInstanceAs labelB
    }

    "ImmutableLabel of() trims name before cache lookup" {
        val labelTrimmed = Label.of("Trimmed")
        val labelWithSpaces = Label.of("  Trimmed  ")

        labelTrimmed shouldBeSameInstanceAs labelWithSpaces
    }

    "ImmutableLabel of() with different country codes returns distinct instances" {
        val labelUS = Label.of("SameLabel", CountryCode.US)
        val labelGB = Label.of("SameLabel", CountryCode.GB)

        labelUS shouldNotBeSameInstanceAs labelGB
    }

    "ImmutableLabel UNKNOWN is singleton" {
        Label.UNKNOWN shouldBeSameInstanceAs Label.UNKNOWN
    }

    "ImmutableLabel UNKNOWN has empty name" {
        Label.UNKNOWN.name shouldBe ""
    }

    "ImmutableLabel equals compares name and countryCode" {
        val label1 = Label.of("Same", CountryCode.US)
        val label2 = Label.of("Same", CountryCode.US)

        label1 shouldBe label2
    }

    "ImmutableLabel hashCode is consistent with equals" {
        val label1 = Label.of("HashLabel", CountryCode.DE)
        val label2 = Label.of("HashLabel", CountryCode.DE)

        label1 shouldBe label2
        label1.hashCode() shouldBe label2.hashCode()
    }

    "ImmutableLabel compareTo orders by name then countryCode" {
        val labelA = Label.of("ALabel")
        val labelB = Label.of("BLabel")

        (labelA.compareTo(labelB) < 0) shouldBe true
        (labelB.compareTo(labelA) > 0) shouldBe true
    }

    "ImmutableLabel compareTo orders by countryCode when names are equal" {
        val labelUS = Label.of("SameName", CountryCode.US)
        val labelFR = Label.of("SameName", CountryCode.FR)

        (labelUS.compareTo(labelFR)) shouldNotBe 0
        (labelFR.compareTo(labelUS)) shouldNotBe 0
    }
})