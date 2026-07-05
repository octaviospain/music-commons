package net.transgressoft.commons.music.audio

import com.neovisionaries.i18n.CountryCode
import io.kotest.core.spec.style.stringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/**
 * Shared behavioural contract for cached, flyweight value types keyed by name and country code.
 *
 * [Artist] and [Label] are structural twins: both expose a cached [of] factory that trims and
 * deduplicates by (name, countryCode), a singleton `UNKNOWN` sentinel, structural equality, and a
 * name-then-country-code ordering. This factory encodes those shared expectations once so each type's
 * own spec can [io.kotest.core.spec.DslDrivenSpec.include] it as a regression fence rather than
 * duplicating the assertions.
 *
 * @param typeName label used in test names to identify the type under test
 * @param of the cached factory under test
 * @param unknown the type's `UNKNOWN` sentinel
 * @param nameOf accessor for the value's name
 */
fun <T : Comparable<T>> flyweightValueTypeContract(
    typeName: String,
    of: (String, CountryCode) -> T,
    unknown: T,
    nameOf: (T) -> String
) = stringSpec {
    "$typeName of() returns cached instance for same name and country code" {
        of("Test$typeName", CountryCode.US) shouldBeSameInstanceAs of("Test$typeName", CountryCode.US)
    }

    "$typeName of() returns distinct instances for different names" {
        of("${typeName}A", CountryCode.UNDEFINED) shouldNotBeSameInstanceAs of("${typeName}B", CountryCode.UNDEFINED)
    }

    "$typeName of() trims name before cache lookup" {
        of("Trimmed", CountryCode.UNDEFINED) shouldBeSameInstanceAs of("  Trimmed  ", CountryCode.UNDEFINED)
    }

    "$typeName of() with different country codes returns distinct instances" {
        of("Same$typeName", CountryCode.US) shouldNotBeSameInstanceAs of("Same$typeName", CountryCode.GB)
    }

    "$typeName UNKNOWN is singleton" {
        unknown shouldBeSameInstanceAs unknown
    }

    "$typeName UNKNOWN has empty name" {
        nameOf(unknown) shouldBe ""
    }

    "$typeName equals compares name and countryCode" {
        of("Same", CountryCode.US) shouldBe of("Same", CountryCode.US)
    }

    "$typeName hashCode is consistent with equals" {
        val first = of("Hash$typeName", CountryCode.DE)
        val second = of("Hash$typeName", CountryCode.DE)

        first shouldBe second
        first.hashCode() shouldBe second.hashCode()
    }

    "$typeName compareTo orders by name then countryCode" {
        val a = of("A$typeName", CountryCode.UNDEFINED)
        val b = of("B$typeName", CountryCode.UNDEFINED)

        a shouldBeLessThan b
        b shouldBeGreaterThan a
    }

    "$typeName compareTo orders by countryCode when names are equal" {
        // Country codes are compared by enum ordinal, which follows the alphabetical declaration
        // order of CountryCode, so FR precedes US.
        val us = of("SameName", CountryCode.US)
        val fr = of("SameName", CountryCode.FR)

        us shouldBeGreaterThan fr
        fr shouldBeLessThan us
    }
}