package net.transgressoft.commons.music.audio

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import net.transgressoft.commons.query.JsonFileRepository
import java.io.File

internal lateinit var jsonFile: File
internal lateinit var repository: JsonFileRepository<AudioItemBase>

internal class AudioItemJsonFileRepositoryTest: StringSpec({

    beforeEach {
        jsonFile = tempfile("json-repository-test", ".json").also { it.deleteOnExit() }
        repository = AudioItemJsonFileRepository.initialize(jsonFile)
    }

    "Repository serializes itself to file when audio item is added" {

    }
})