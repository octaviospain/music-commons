package net.transgressoft.commons.fx.music.player

import net.transgressoft.commons.fx.music.audio.FXAudioItem
import net.transgressoft.commons.fx.music.audio.FXAudioLibrary
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioItemMapSerializer
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.PAUSED
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.PLAYING
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.STOPPED
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.UNKNOWN
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import net.transgressoft.lirp.persistence.json.JsonRepository
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import javafx.util.Duration
import org.testfx.api.FxToolkit
import java.io.File
import java.nio.file.Files
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * JavaFxPlayer tests that exercise actual JavaFX MediaPlayer playback (audio output, seek, pause).
 *
 * Class-level [Tags] of `linux-only` and `requires-playback` mean these tests are skipped when
 * either `-PexcludeLinuxOnly=true` or `-PexcludePlaybackTests=true` is set.
 *
 * The JavaFX primary stage is registered in `beforeSpec` via [FxToolkit.registerPrimaryStage] —
 * Kotest does not honor JUnit5 `@ExtendWith(ApplicationExtension)`, so the lifecycle is managed
 * manually here.
 */
@ExperimentalCoroutinesApi
@Tags("linux-only", "requires-playback")
internal class JavaFxPlayerPlaybackTest : StringSpec({
    isolationMode = IsolationMode.SingleInstance

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
        testScope.cancel()
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Playing an audio item increases the play count and serializes the repository" {
        player.subscribe(observableAudioItemRepository.playerSubscriber)
        val audioItemLength = audioItem.duration.toMillis()
        val timeToIncreasePlayCount = (audioItemLength * 0.6).roundToLong()

        player.play(audioItem)

        testDispatcher.scheduler.advanceUntilIdle()

        eventually(timeToIncreasePlayCount.plus(500).milliseconds) {
            audioItem.playCount shouldBe 1
            observableAudioItemRepository.findFirst { it.playCount == 1.toShort() } shouldBePresent { it shouldBe audioItem }

            testDispatcher.scheduler.advanceUntilIdle()

            jsonFile.readText().shouldContainJsonKeyValue("${audioItem.id}.playCount", 1)
        }
    }

    "Playing an audio item modifies its observable properties" {
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
            // Round both sides: javafx.util.Duration.toSeconds() returns Double; java.time.Duration.toSeconds()
            // truncates. Rounding both keeps the comparison consistent and avoids off-by-one races at the boundary.
            player.totalDuration.toSeconds().roundToLong() shouldBe audioItem.duration.toMillis().toDouble().div(1000).roundToLong()
            player.volumeProperty.get() shouldBe 0.5
        }

        player.pause()
        val pausedTime = player.currentTimeProperty.get()

        // Widened from 500ms — JavaFX MediaPlayer dispatches the PAUSED transition asynchronously
        // on the FX thread (UnconfinedTestDispatcher does not drive it), and 500ms is too tight
        // on shared CI runners.
        eventually(2.seconds) {
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
})