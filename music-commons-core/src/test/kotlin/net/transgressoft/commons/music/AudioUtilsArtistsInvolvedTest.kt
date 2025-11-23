package net.transgressoft.commons.music

import net.transgressoft.commons.music.AudioUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

internal class AudioUtilsArtistsInvolvedTest : FunSpec({

    data class AudioItemFields(
        val titleField: String = "",
        val artistField: String = "",
        val albumArtistField: String = "",
        val expectedArtists: Set<String>
    )
    context("Returns set of artists involved") {

        context("only from the artist field") {
            context("with just one artist name in a") {
                withData(
                    mapOf(
                        "trivial name" to
                            AudioItemFields(
                                artistField = "Dvs1", expectedArtists = setOf("Dvs1")
                            ),
                        "trivial name with trail spaces" to
                            AudioItemFields(
                                artistField = "Adam Beyer    ", expectedArtists = setOf("Adam Beyer")
                            ),
                        "trivial name with spaces in between words" to
                            AudioItemFields(
                                artistField = "Adam      Beyer", expectedArtists = setOf("Adam Beyer")
                            ),
                        "trivial name with leading and trailing spaces" to
                            AudioItemFields(
                                artistField = "   Adam Beyer    ", expectedArtists = setOf("Adam Beyer")
                            ),
                        "trivial name with leading and trailing spaces" to
                            AudioItemFields(
                                artistField = "   Adam    Beyer    ", expectedArtists = setOf("Adam Beyer")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with comma-separated names of") {
                withData(
                    mapOf(
                        "two names" to
                            AudioItemFields(
                                artistField = "adam Beyer, ida engberg", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            ),
                        "two names with trailing spaces" to
                            AudioItemFields(
                                artistField = "Adam Beyer  , Ida Engberg   ", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            ),
                        "two names with leading spaces" to
                            AudioItemFields(
                                artistField = "Adam    Beyer, Ida   Engberg", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            ),
                        "three names" to
                            AudioItemFields(
                                artistField = "Adam Beyer, Ida Engberg, UMEK", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                            ),
                        "three names with leading and trailing spaces" to
                            AudioItemFields(
                                artistField = "Adam    Beyer  ,   Ida  Engberg ,   UMEK ", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                            ),
                        "two repeated names" to
                            AudioItemFields(
                                artistField = "Adam Beyer, Adam Beyer", expectedArtists = setOf("Adam Beyer")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with names separated by '&' of") {
                withData(
                    mapOf(
                        "two names" to
                            AudioItemFields(
                                artistField = "adam Beyer & ida engberg", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            ),
                        "two names with leading and trailing spaces" to
                            AudioItemFields(
                                artistField = "Adam   Beyer  &     Ida Engberg ", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            ),
                        "three names" to
                            AudioItemFields(
                                artistField = "Adam Beyer & Ida Engberg & UMEK", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                            ),
                        "three names with leading and trailing spaces" to
                            AudioItemFields(
                                artistField = "adam   beyer  & ida  engberg &  uMEK ", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                            ),
                        "two repeated names" to
                            AudioItemFields(
                                artistField = "Adam Beyer & Adam Beyer", expectedArtists = setOf("Adam Beyer")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with names separated by 'vs'") {
                withData(
                    mapOf(
                        "two names" to
                            AudioItemFields(
                                artistField = "Adam Beyer vs Ida Engberg", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            ),
                        "three names" to
                            AudioItemFields(
                                artistField = "Adam Beyer vs Ida Engberg VS UMEK", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                            ),
                        "two repeated names" to
                            AudioItemFields(
                                artistField = "Adam Beyer vs Adam Beyer", expectedArtists = setOf("Adam Beyer")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with names separated by 'versus'") {
                withData(
                    mapOf(
                        "two names" to
                            AudioItemFields(
                                artistField = "Adam Beyer versus Ida Engberg", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            ),
                        "three names" to
                            AudioItemFields(
                                artistField = "Adam Beyer versus Ida Engberg Versus UMEK", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                            ),
                        "two repeated names" to
                            AudioItemFields(
                                artistField = "Adam Beyer versus Adam Beyer", expectedArtists = setOf("Adam Beyer")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with names separated by 'vs.'") {
                withData(
                    mapOf(
                        "two names" to
                            AudioItemFields(
                                artistField = "Adam Beyer vs. Ida Engberg", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            ),
                        "three names" to
                            AudioItemFields(
                                artistField = "Adam Beyer vs. Ida Engberg VS. UMEK", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                            ),
                        "two repeated names" to
                            AudioItemFields(
                                artistField = "Adam Beyer vs. Adam Beyer", expectedArtists = setOf("Adam Beyer")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with names separated by 'Feat.'") {
                withData(
                    mapOf(
                        "two names" to
                            AudioItemFields(
                                artistField = "Adam Beyer Feat. Ida Engberg", expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with names separated by Ft") {
                withData(
                    mapOf(
                        "two names" to
                            AudioItemFields(
                                artistField = "Ludacris Ft. Shawnna", expectedArtists = setOf("Ludacris", "Shawnna")
                            ),
                        "two names by Ft." to
                            AudioItemFields(
                                artistField = "Ludacris Ft Shawnna", expectedArtists = setOf("Ludacris", "Shawnna")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with names separated by ',' and '&'") {
                withData(
                    mapOf(
                        "three names" to
                            AudioItemFields(
                                artistField = "Adam Beyer, Ida Engberg & Ansome", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "Ansome")
                            ),
                        "four names" to
                            AudioItemFields(
                                artistField = "Adam Beyer & Ida Engberg, UMEK & Ansome", expectedArtists = setOf("Adam Beyer", "Ida Engberg", "Ansome", "UMEK")
                            ),
                        "five names with spaces" to
                            AudioItemFields(
                                artistField = "Adam    Beyer, UMEK   & Showtek, Ida  Engberg &   Ansome",
                                expectedArtists = setOf("Adam Beyer", "Ida Engberg", "Ansome", "UMEK", "Showtek")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("with names separated by 'Feat' and '&'") {
                withData(
                    mapOf(
                        "three names" to
                            AudioItemFields(
                                artistField = "Laidback Luke Feat. Chuckie & Martin", expectedArtists = setOf("Laidback Luke", "Chuckie", "Martin")
                            ),
                        "three names by Feat. and &" to
                            AudioItemFields(
                                artistField = "Laidback Luke Feat. Chuckie & Martin", expectedArtists = setOf("Laidback Luke", "Chuckie", "Martin")
                            )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }
        }

        context("only from the title field") {
            withData(
                mapOf(
                    "with a trivial track title" to
                        AudioItemFields(
                            titleField = "Nothing Left, Part 1", expectedArtists = emptySet()
                        ),
                    "with '(Original mix)'" to
                        AudioItemFields(
                            titleField = "Song Title (Original mix)", expectedArtists = emptySet()
                        ),
                    "with '(Edit version)'" to
                        AudioItemFields(
                            titleField = "Song Title (Special time edit)", expectedArtists = emptySet()
                        ),
                    "that ends with 'Remix'" to
                        AudioItemFields(
                            titleField = "Song   Title  (  adam  beyer  remix)", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that starts with 'Remix by'" to
                        AudioItemFields(
                            titleField = "Song Title   (Remix by Adam Beyer)", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that has 'Ft outside parenthesis" to
                        AudioItemFields(
                            titleField = "Song Title  ft Adam  Beyer)", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that has 'Ft' inside parenthesis" to
                        AudioItemFields(
                            titleField = "Song Title   (  ft Adam Beyer)", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that has 'Feat' outside parenthesis" to
                        AudioItemFields(
                            titleField = "Song Title feat Adam Beyer", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that has 'Feat' inside parenthesis" to
                        AudioItemFields(
                            titleField = "Song Title ( Feat Adam Beyer)", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that has 'featuring' outside parenthesis" to
                        AudioItemFields(
                            titleField = "Song Title featuring Adam Beyer", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that has 'featuring' inside parenthesis" to
                        AudioItemFields(
                            titleField = "Song Title (featuring Adam Beyer)", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that has 'with'" to
                        AudioItemFields(
                            titleField = "Song Title (with Adam Beyer)", expectedArtists = setOf("Adam Beyer")
                        ),
                    "that has 'featuring' outside parenthesis" to
                        AudioItemFields(
                            titleField = "Song Title featuring Adam Beyer)", expectedArtists = setOf("Adam Beyer")
                        ),
                    "with two names separated by '&' ending with 'Remix" to
                        AudioItemFields(
                            titleField = "Song Title (Adam beyer & pete tong remix)", expectedArtists = setOf("Adam Beyer", "Pete Tong")
                        ),
                    "with two names separated by 'vs' ending with 'Remix'" to
                        AudioItemFields(
                            titleField = "Fall (M83 vs Big Black Delta Remix)", expectedArtists = setOf("M83", "Big Black Delta")
                        ),
                    "with four names separated by comma and & starting with 'feat'" to
                        AudioItemFields(
                            titleField = "Jet Blue Jet (feat Leftside, GTA, Razz & Biggy)", expectedArtists = setOf("Leftside", "GTA", "Razz", "Biggy")
                        )
                )
            ) {
                AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
            }
        }

        context("only from album artist field") {
            withData(
                mapOf(
                    "with two names separated by '," to
                        AudioItemFields(
                            albumArtistField = "Adam Beyer, UMEK", expectedArtists = setOf("Adam Beyer", "UMEK")
                        ),
                    "with two names separated by '&" to
                        AudioItemFields(
                            albumArtistField = "Adam Beyer & UMEK", expectedArtists = setOf("Adam Beyer", "UMEK")
                        ),
                    "with two names separated by '&' and ','" to
                        AudioItemFields(
                            albumArtistField = "Adam Beyer, Pete Tong & UMEK", expectedArtists = setOf("Adam Beyer", "Pete Tong", "UMEK")
                        )
                )
            ) {
                AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
            }
        }

        context("from title, artist and album artist fields") {
            withData(
                mapOf(
                    // the most common case
                    "with a trivial title name, artist and same album artist" to
                        AudioItemFields(
                            titleField = "Song title",
                            artistField = "Pete Tong",
                            albumArtistField = "Pete Tong",
                            expectedArtists = setOf("Pete Tong")
                        ),
                    "with a trivial title, one artist, one different album artist" to
                        AudioItemFields(
                            titleField = "Song title",
                            artistField = "Pete Tong",
                            albumArtistField = "Jeff Mills",
                            expectedArtists = setOf("Pete Tong", "Jeff Mills")
                        ),
                    "with a trivial title, two artists, one of the artists in the album artist" to
                        AudioItemFields(
                            titleField = "Song title",
                            artistField = "Pete, UMEK",
                            albumArtistField = "Pete",
                            expectedArtists = setOf("Pete", "UMEK")
                        ),
                    "with an artist name with 'Remix', one artist, no album artist" to
                        AudioItemFields(
                            titleField = "Song title (Ansome Remix)",
                            artistField = "Pete Tong",
                            expectedArtists = setOf("Pete Tong", "Ansome")
                        ),
                    "with an artist name with 'featuring', two artists with ',', one repeated album artist" to
                        AudioItemFields(
                            titleField = "Song title featuring Lulu Perez",
                            artistField = "Pete Tong & Ansome",
                            albumArtistField = "Pete Tong",
                            expectedArtists = setOf("Pete Tong", "Ansome", "Lulu Perez")
                        ),
                    "with an artist name with 'Remix by', two artists with '&', one other album artist" to
                        AudioItemFields(
                            titleField = "Song title (Remix by Bonobo)",
                            artistField = "Laurent Garnier & Rone",
                            albumArtistField = "Pete Tong",
                            expectedArtists = setOf("Pete Tong", "Bonobo", "Rone", "Laurent Garnier")
                        )
                )
            ) {
                AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
            }
        }

        // TODO
        xcontext("Has 'ft' and ends with Remix inside parenthesis") {
            AudioUtils.getArtistsNamesInvolved("Pretendingtowalkslow ft Zeroh", "", "") shouldBe setOf("Zeroh", "M. Constant Remix")
        }
    }
})