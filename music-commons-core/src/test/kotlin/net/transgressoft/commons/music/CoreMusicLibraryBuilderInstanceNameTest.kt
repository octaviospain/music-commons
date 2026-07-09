package net.transgressoft.commons.music

import net.transgressoft.commons.music.testing.registryIsolation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

@DisplayName("CoreMusicLibrary.Builder instanceName")
internal class CoreMusicLibraryBuilderInstanceNameTest : StringSpec({

    registryIsolation()

    "CoreMusicLibrary.Builder instanceName returns the explicitly set name verbatim" {
        val library = CoreMusicLibrary.builder().instanceName("my-library").build()
        try {
            library.instanceName shouldBe "my-library"
        } finally {
            library.close()
        }
    }

    "CoreMusicLibrary.Builder instanceName yields a non-blank auto-generated default when not set" {
        val library = CoreMusicLibrary.builder().build()
        try {
            library.instanceName.shouldNotBeBlank()
        } finally {
            library.close()
        }
    }

    "CoreMusicLibrary.Builder instanceName generates distinct defaults for two libraries built without explicit name" {
        val first = CoreMusicLibrary.builder().build()
        first.close()
        val second = CoreMusicLibrary.builder().build()
        try {
            first.instanceName shouldNotBe second.instanceName
        } finally {
            second.close()
        }
    }

    "CoreMusicLibrary.Builder instanceName rejects a blank name" {
        shouldThrow<IllegalArgumentException> { CoreMusicLibrary.builder().instanceName("") }
        shouldThrow<IllegalArgumentException> { CoreMusicLibrary.builder().instanceName("   ") }
    }
})