package net.transgressoft.commons.music.audio

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import net.transgressoft.commons.query.JsonFileRepository
import java.io.File

private lateinit var jsonFile: File
private lateinit var repository: JsonFileRepository<AudioItemBase>

internal class AudioItemJsonFileRepositoryTest: StringSpec({

    beforeEach {
        jsonFile = tempfile("json-repository-test", ".json").also { it.deleteOnExit() }
        repository = AudioItemJsonRepository.initialize(jsonFile)
    }

    "Repository serializes itself to file when audio item is added" {

    }

    "Repository updates an updated audio item" {

    }
})