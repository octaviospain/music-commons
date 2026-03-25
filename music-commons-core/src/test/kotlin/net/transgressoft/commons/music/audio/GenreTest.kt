package net.transgressoft.commons.music.audio

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

@ExperimentalKotest
class GenreTest : ShouldSpec({
    context("capitalize()") {

        should("capitalize single word genre") {
            Genre.ROCK.capitalize() shouldBe "Rock"
        }

        should("capitalize multiple word genre") {
            Genre.ALTERNATIVE_ROCK.capitalize() shouldBe "Alternative Rock"
        }

        should("capitalize three-word genres") {
            Genre.BUMBA_MEU_BOI.capitalize() shouldBe "Bumba Meu Boi"
        }

        should("always capitalize the first letter of the first word") {
            Genre.entries.forEach { genre ->
                genre.name.first().isUpperCase() shouldBe true
            }
        }
    }

    context("parseGenres()") {
        should("match uppercase input") {
            Genre.parseGenre("ROCK") shouldBe Genre.ROCK
        }

        should("match lowercase input") {
            Genre.parseGenre("rock") shouldBe Genre.ROCK
        }

        should("match mixed case input") {
            Genre.parseGenre("rOcK") shouldBe Genre.ROCK
        }

        should("match multiple words separated by underscores") {
            Genre.parseGenre("ALTERNATIVE_ROCK") shouldBe Genre.ALTERNATIVE_ROCK
        }

        should("match multiple words separated by spaces") {
            Genre.parseGenre("ALTERNATIVE ROCK") shouldBe Genre.ALTERNATIVE_ROCK
        }

        should("return UNDEFINED if the genre is not recognized") {
            Genre.parseGenre("UNKNOWN") shouldBe Genre.UNDEFINED
        }

        should("parse every genre's name back to itself") {
            Genre.entries.forEach { genre ->
                Genre.parseGenre(genre.name) shouldBe genre
            }
        }
    }
})