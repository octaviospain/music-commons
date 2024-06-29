package net.transgressoft.commons.fx.music

import net.transgressoft.commons.fx.music.FXAudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.fx.music.FXAudioItemTestUtil.asJsonKeyValue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.milliseconds

internal class ObservableAudioItemJsonRepositoryTest : StringSpec({

    "should create an observable audio item ann serialize itself" {
        val jsonFile = tempfile("observableAudioItemRepository-test", ".json").also { it.deleteOnExit() }
        val observableAudioItemRepository = ObservableAudioItemJsonRepository("ObservableAudioItemRepo", jsonFile)

        val fxAudioItem = observableAudioItemRepository.createFromFile(arbitraryMp3File.next().toPath())

        eventually(100.milliseconds) { jsonFile.readText().shouldEqualJson(fxAudioItem.asJsonKeyValue()) }

        fxAudioItem.title = "New title"

        eventually(100.milliseconds) {
            jsonFile.readText() should {
                it.shouldEqualJson(fxAudioItem.asJsonKeyValue())

                it.shouldContainOnlyOnce("title\": \"New title")
            }
        }
    }
})
