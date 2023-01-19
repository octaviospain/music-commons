package net.transgressoft.commons.music

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.m4aFile
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.mp3File
import net.transgressoft.commons.music.audio.AudioItemTestUtil2.wavFile
import java.io.File
import javax.imageio.ImageIO

class AudioUtilsTest : FunSpec({

    data class AudioFile(val audioFile: File)

    context("Create waveform image from") {
        withData(
            mapOf(
                "a wav file" to AudioFile(wavFile),
                "a mp3 file" to AudioFile(mp3File),
                "a m4aFile" to AudioFile(m4aFile)
            )
        ) {
            val pngTempFile = tempfile(suffix = ".png")
            AudioUtils.extractWaveformToImage(it.audioFile.toPath(), pngTempFile, 780, 335)
            pngTempFile.exists() shouldBe true

            pngTempFile.extension shouldBe "png"
            pngTempFile.length() shouldNotBe null

            val bufferedImage = withContext(Dispatchers.IO) {
                ImageIO.read(pngTempFile)
            }
            bufferedImage.width shouldBe 780
            bufferedImage.height shouldBe 335
        }
    }

    data class AudioItemFields(
        val titleField: String = "",
        val artistField: String = "",
        val albumArtistField: String = "",
        val expectedArtists: Set<String>,
    )
    context("Artists involved") {

        context("In artist field only") {
            context("Just one artist name") {
                withData(
                    mapOf(
                        "Simplest case" to AudioItemFields(
                            artistField = "Dvs1",
                            expectedArtists = setOf("Dvs1")
                        ),
                        "Simplest with trail spaces" to AudioItemFields(
                            artistField = "Adam Beyer    ",
                            expectedArtists = setOf("Adam Beyer")
                        ),
                        "Simplest with spaces in between words" to AudioItemFields(
                            artistField = "Adam      Beyer",
                            expectedArtists = setOf("Adam Beyer")
                        ),
                        "Simplest with leading and trailing spaces" to AudioItemFields(
                            artistField = "   Adam Beyer    ",
                            expectedArtists = setOf("Adam Beyer")
                        ),
                        "Simplest with leading and trailing spaces" to AudioItemFields(
                            artistField = "   Adam    Beyer    ",
                            expectedArtists = setOf("Adam Beyer")
                        )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by comma") {
                withData(
                    mapOf(
                        "Two names" to AudioItemFields(
                            artistField = "adam Beyer, ida engberg",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                        "Two names with trailing spaces" to AudioItemFields(
                            artistField = "Adam Beyer  , Ida Engberg   ",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                        "Two names with leading spaces" to AudioItemFields(
                            artistField = "Adam    Beyer, Ida   Engberg",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                        "Three names" to AudioItemFields(
                            artistField = "Adam Beyer, Ida Engberg, UMEK",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                        ),
                        "Three names with leading and trailing spaces" to AudioItemFields(
                            artistField = "Adam    Beyer  ,   Ida  Engberg ,   UMEK ",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                        ),
                        "Two repeated names" to AudioItemFields(
                            artistField = "Adam Beyer, Adam Beyer",
                            expectedArtists = setOf("Adam Beyer")
                        )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by &") {
                withData(
                    mapOf(
                        "Two names" to AudioItemFields(
                            artistField = "adam Beyer & ida engberg",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                        "Two names with leading and trailing spaces" to AudioItemFields(
                            artistField = "Adam   Beyer  &     Ida Engberg ",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                        "Three names" to AudioItemFields(
                            artistField = "Adam Beyer & Ida Engberg & UMEK",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                        ),
                        "Three names with leading and trailing spaces" to AudioItemFields(
                            artistField = "adam   beyer  & ida  engberg &  uMEK ",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                        ),
                        "Two repeated names" to AudioItemFields(
                            artistField = "Adam Beyer & Adam Beyer",
                            expectedArtists = setOf("Adam Beyer")
                        )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by 'vs'") {
                withData(
                    mapOf(
                        "Two names" to AudioItemFields(
                            artistField = "Adam Beyer vs Ida Engberg",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                        "Three names" to AudioItemFields(
                            artistField = "Adam Beyer vs Ida Engberg VS UMEK",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                        ),
                        "Two repeated names" to AudioItemFields(
                            artistField = "Adam Beyer vs Adam Beyer",
                            expectedArtists = setOf("Adam Beyer")
                        )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by 'versus'") {
                withData(
                    mapOf(
                        "Two names" to AudioItemFields(
                            artistField = "Adam Beyer versus Ida Engberg",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                        "Three names" to AudioItemFields(
                            artistField = "Adam Beyer versus Ida Engberg Versus UMEK",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                        ),
                        "Two repeated names" to AudioItemFields(
                            artistField = "Adam Beyer versus Adam Beyer",
                            expectedArtists = setOf("Adam Beyer")
                        )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by 'vs.'") {
                withData(
                    mapOf(
                        "Two names" to AudioItemFields(
                            artistField = "Adam Beyer vs. Ida Engberg",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                        "Three names" to AudioItemFields(
                            artistField = "Adam Beyer vs. Ida Engberg VS. UMEK",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "UMEK")
                        ),
                        "Two repeated names" to AudioItemFields(
                            artistField = "Adam Beyer vs. Adam Beyer",
                            expectedArtists = setOf("Adam Beyer")
                        )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by 'Feat.'") {
                withData(
                    mapOf(
                        "Two names" to AudioItemFields(
                            artistField = "Adam Beyer Feat. Ida Engberg",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg")
                        ),
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by Ft") {
                withData(
                    mapOf(
                        "Two names" to AudioItemFields(
                            artistField = "Ludacris Ft. Shawnna",
                            expectedArtists = setOf("Ludacris", "Shawnna")
                        ),
                        "Two names by Ft." to AudioItemFields(
                            artistField = "Ludacris Ft Shawnna",
                            expectedArtists = setOf("Ludacris", "Shawnna")
                        )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by comma and &") {
                withData(
                    mapOf(
                        "Three names" to AudioItemFields(
                            artistField = "Adam Beyer, Ida Engberg & Ansome",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "Ansome")
                        ),
                        "Four names" to AudioItemFields(
                            artistField = "Adam Beyer & Ida Engberg, UMEK & Ansome",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "Ansome", "UMEK")
                        ),
                        "Five names with spaces" to AudioItemFields(
                            artistField = "Adam    Beyer, UMEK   & Showtek, Ida  Engberg &   Ansome",
                            expectedArtists = setOf("Adam Beyer", "Ida Engberg", "Ansome", "UMEK", "Showtek")
                        ),
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }

            context("Separated by Feat and &") {
                withData(
                    mapOf(
                        "Three names" to AudioItemFields(
                            artistField = "Laidback Luke Feat. Chuckie & Martin Solveig",
                            expectedArtists = setOf("Laidback Luke", "Chuckie", "Martin Solveig")
                        ),
                        "Three names by Feat. and &" to AudioItemFields(
                            artistField = "Laidback Luke Feat. Chuckie & Martin Solveig",
                            expectedArtists = setOf("Laidback Luke", "Chuckie", "Martin Solveig")
                        )
                    )
                ) {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }
        }

        context("In title field") {
            withData(
                mapOf(
                    "Just the track name" to AudioItemFields(
                        titleField = "Nothing Left, Part 1",
                        expectedArtists = emptySet()
                    ),
                    "Original mix" to AudioItemFields(
                        titleField = "Song Title (Original mix)",
                        expectedArtists = emptySet()
                    ),
                    "Edit version" to AudioItemFields(
                        titleField = "Song Title (Special time edit)",
                        expectedArtists = emptySet()
                    ),
                    "Ends with 'Remix'" to AudioItemFields(
                        titleField = "Song   Title  (  adam  beyer  remix)",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Starts with 'Remix by'" to AudioItemFields(
                        titleField = "Song Title   (Remix by Adam Beyer)",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Has 'Ft outside parenthesis" to AudioItemFields(
                        titleField = "Song Title  ft Adam  Beyer)",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Has 'Ft' inside parenthesis" to AudioItemFields(
                        titleField = "Song Title   (  ft Adam Beyer)",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Has 'Feat' outside parenthesis" to AudioItemFields(
                        titleField = "Song Title feat Adam Beyer",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Has 'Feat inside parenthesis" to AudioItemFields(
                        titleField = "Song Title ( Feat Adam Beyer)",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Has 'featuring outside parenthesis" to AudioItemFields(
                        titleField = "Song Title featuring Adam Beyer",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Has 'featuring inside parenthesis" to AudioItemFields(
                        titleField = "Song Title (featuring Adam Beyer)",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Has 'with'" to AudioItemFields(
                        titleField = "Song Title (with Adam Beyer)",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Has 'featuring outside parenthesis" to AudioItemFields(
                        titleField = "Song Title featuring Adam Beyer)",
                        expectedArtists = setOf("Adam Beyer")
                    ),
                    "Two names separated by '&' ending with 'Remix" to AudioItemFields(
                        titleField = "Song Title (Adam beyer & pete tong remix)",
                        expectedArtists = setOf("Adam Beyer", "Pete Tong")
                    ),
                    "Two names separated by 'vs' ending with 'Remix'" to AudioItemFields(
                        titleField = "Fall (M83 vs Big Black Delta Remix)",
                        expectedArtists = setOf("M83", "Big Black Delta")
                    ),
                    "Four names separated by comma and & starting with 'feat'" to AudioItemFields(
                        titleField = "Jet Blue Jet (feat Leftside, GTA, Razz & Biggy)",
                        expectedArtists = setOf("Leftside", "GTA", "Razz", "Biggy")
                    )
                )
            ) {
                AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
            }
        }

        context("In album artist field") {
            withData(
                mapOf(
                    "Two names separated by commas" to AudioItemFields(
                        albumArtistField = "Adam Beyer, UMEK",
                        expectedArtists = setOf("Adam Beyer", "UMEK")
                    ),
                    "Two names separated by &" to AudioItemFields(
                       albumArtistField = "Adam Beyer & UMEK",
                       expectedArtists = setOf("Adam Beyer", "UMEK")
                    ),
                    "Two names separated by & and comma" to AudioItemFields(
                        albumArtistField = "Adam Beyer, Pete Tong & UMEK",
                        expectedArtists = setOf("Adam Beyer", "Pete Tong", "UMEK")
                    )
                )
            ) {
                AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
            }
        }

        context("In all fields") {
            withData(
                mapOf(
                    "Simple name, one artist, same album artist" to AudioItemFields(
                        titleField = "Song title",
                        artistField = "Pete Tong",
                        albumArtistField = "Pete Tong",
                        expectedArtists = setOf("Pete Tong")
                    ),
                    "Simple name, one artist, one album artist" to AudioItemFields(
                        titleField = "Song title",
                        artistField = "Pete Tong",
                        albumArtistField = "Jeff Mills",
                        expectedArtists = setOf("Pete Tong", "Jeff Mills")
                    ),
                    "Simple name, two artists, same album artist" to AudioItemFields(
                        titleField = "Song title",
                        artistField = "Pete Tong, UMEK",
                        albumArtistField = "Pete Tong",
                        expectedArtists = setOf("Pete Tong", "UMEK")
                    ),
                    "Name with 'Remix', one artist, no album artist" to AudioItemFields(
                        titleField = "Song title (Ansome Remix)",
                        artistField = "Pete Tong",
                        expectedArtists = setOf("Pete Tong", "Ansome")
                    ),
                    "Name with featuring, two artists with comma, one repeated album artist" to AudioItemFields(
                        titleField = "Song title featuring Lulu Perez",
                        artistField = "Pete Tong & Ansome",
                        albumArtistField = "Pete Tong",
                        expectedArtists = setOf("Pete Tong", "Ansome", "Lulu Perez")
                    ),
                    "Name with 'Remix by', two artists with &, one other album artist" to AudioItemFields(
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

        xcontext("Has 'ft' and ends with Remix inside parenthesis") {
            AudioUtils.getArtistsNamesInvolved("Pretendingtowalkslow ft Zeroh", "", "") shouldBe setOf("Zeroh", "M. Constant Remix")
        }
    }
})
