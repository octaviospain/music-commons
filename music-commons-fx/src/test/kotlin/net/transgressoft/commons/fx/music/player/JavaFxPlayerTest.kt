package net.transgressoft.commons.fx.music.player

import net.transgressoft.commons.fx.music.audio.FXAudioItem
import net.transgressoft.commons.fx.music.audio.FXAudioLibrary
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioItemMapSerializer
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.PAUSED
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.PLAYING
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.STOPPED
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.UNKNOWN
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.json.JsonRepository
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.every
import io.mockk.mockk
import javafx.util.Duration
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.api.FxToolkit
import org.testfx.framework.junit5.ApplicationExtension
import java.io.File
import java.nio.file.Files
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExtendWith(ApplicationExtension::class)
@ExperimentalCoroutinesApi
internal class JavaFxPlayerTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var player: AudioItemPlayer
    lateinit var jsonFile: File
    lateinit var jsonFileRepository: JsonRepository<Int, ObservableAudioItem>
    lateinit var observableAudioItemRepository: FXAudioLibrary
    lateinit var audioItem: FXAudioItem

    beforeSpec {
        FxToolkit.registerPrimaryStage()
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        player = JavaFxPlayer()
        jsonFile = Files.createTempFile("observableAudioLibrary-test", ".json").toFile().apply { deleteOnExit() }
        jsonFileRepository = JsonFileRepository(jsonFile, ObservableAudioItemMapSerializer)
        observableAudioItemRepository = FXAudioLibrary(jsonFileRepository)
        audioItem = observableAudioItemRepository.createFromFile(Arb.realAudioFile(ID3_V_24).next())
    }

    afterEach {
        jsonFileRepository.close()
        player.dispose()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Playing an audio item increases the play count and serializes the repository".config(
        enabledIf = { System.getenv("CI") == null }
    ) {
        player.subscribe(observableAudioItemRepository.playerSubscriber)
        val audioItemLength = audioItem.duration.toMillis()
        val timeToIncreasePlayCount = (audioItemLength * 0.6).roundToLong()

        player.play(audioItem)

        testDispatcher.scheduler.advanceUntilIdle()

        eventually(timeToIncreasePlayCount.plus(500).milliseconds) {
            audioItem.playCount shouldBe 1
            observableAudioItemRepository.findFirst { it.playCount.toInt() == 1 } shouldBePresent { it shouldBe audioItem }

            testDispatcher.scheduler.advanceUntilIdle()

            jsonFile.readText().shouldContainJsonKeyValue("${audioItem.id}.playCount", 1)
        }
    }

    "Playing an audio item modifies its observable properties".config(
        enabledIf = { System.getenv("CI") == null }
    ) {
        player.status() shouldBe UNKNOWN
        player.statusProperty.get() shouldBe UNKNOWN
        player.currentTimeProperty.get() shouldBe Duration.ZERO
        player.totalDuration shouldBe Duration.INDEFINITE
        player.volumeProperty.get() shouldBe 0.0

        player.play(audioItem)
        player.seek(500.0)
        player.setVolume(0.5)

        eventually(1.seconds) {
            player.status() shouldBe PLAYING
            player.statusProperty.get() shouldBe PLAYING
            player.currentTimeProperty.get() shouldBeGreaterThan Duration.millis(500.0)
            player.totalDuration.toSeconds().roundToLong() shouldBe audioItem.duration.toSeconds()
            player.volumeProperty.get() shouldBe 0.5
        }

        player.pause()
        val pausedTime = player.currentTimeProperty.get()

        eventually(500.milliseconds) {
            player.status() shouldBe PAUSED
            player.statusProperty.get() shouldBe PAUSED
            player.currentTimeProperty.get() shouldBe pausedTime
        }

        player.stop()
        eventually(1.seconds) {
            player.status() shouldBe STOPPED
            player.statusProperty.get() shouldBe STOPPED
            player.currentTimeProperty.get() shouldBe Duration.ZERO
        }
    }

    "JavaFxPlayer.isPlayable returns true when encoding and encoder are both null" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "mp3"
                every { encoding } returns null
                every { encoder } returns null
            }

        JavaFxPlayer.isPlayable(item) shouldBe true
    }

    "JavaFxPlayer.isPlayable returns true when encoding is null and encoder has a non-iTunes value" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "mp3"
                every { encoding } returns null
                every { encoder } returns "LAME 3.100"
            }

        JavaFxPlayer.isPlayable(item) shouldBe true
    }

    "JavaFxPlayer.isPlayable returns true when encoder is null and encoding is not Apple Lossless" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "mp3"
                every { encoding } returns "MPEG-1 Audio Layer 3"
                every { encoder } returns null
            }

        JavaFxPlayer.isPlayable(item) shouldBe true
    }

    "JavaFxPlayer.isPlayable returns false when encoding starts with Apple" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "m4a"
                every { encoding } returns "Apple Lossless"
                every { encoder } returns null
            }

        JavaFxPlayer.isPlayable(item) shouldBe false
    }

    "JavaFxPlayer.isPlayable returns false when encoder starts with iTunes" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "m4a"
                every { encoding } returns null
                every { encoder } returns "iTunes 12.9.0.164"
            }

        JavaFxPlayer.isPlayable(item) shouldBe false
    }

    "JavaFxPlayer.play throws UnsupportedAudioPlaybackException when Media source URI is invalid" {
        val nonExistentFile =
            Files.createTempFile("corrupt-audio", ".mp3").also {
                it.toFile().delete() // ensure file does not exist so JavaFX Media throws
            }
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "mp3"
                every { encoding } returns null
                every { encoder } returns null
                every { fileName } returns "corrupt.mp3"
                every { duration } returns java.time.Duration.ofSeconds(0)
                every { path } returns nonExistentFile
            }

        shouldThrow<UnsupportedAudioPlaybackException> {
            player.play(item)
        }
    }
})