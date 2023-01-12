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
                )  {
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
                )  {
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
                )  {
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
                )  {
                    AudioUtils.getArtistsNamesInvolved(it.titleField, it.artistField, it.albumArtistField) shouldBe it.expectedArtists
                }
            }
        }
    }
})
