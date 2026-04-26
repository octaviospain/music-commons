package net.transgressoft.commons.music.common

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OsDetectorTest : StringSpec({
    isolationMode = IsolationMode.SingleInstance

    "OsDetector.isWindows reflects real os.name when no override is active" {
        val expected = System.getProperty("os.name").lowercase().contains("windows")
        OsDetector.isWindows shouldBe expected
    }

    "OsDetector.withOverriddenIsWindows flips isWindows to true inside block" {
        OsDetector.withOverriddenIsWindows(true) {
            OsDetector.isWindows shouldBe true
        }
    }

    "OsDetector.withOverriddenIsWindows flips isWindows to false inside block" {
        OsDetector.withOverriddenIsWindows(false) {
            OsDetector.isWindows shouldBe false
        }
    }

    "OsDetector.withOverriddenIsWindows restores state after normal block completion" {
        val before = OsDetector.isWindows
        OsDetector.withOverriddenIsWindows(!before) {
            OsDetector.isWindows shouldBe !before
        }
        OsDetector.isWindows shouldBe before
    }

    "OsDetector.withOverriddenIsWindows restores state after block throws" {
        val before = OsDetector.isWindows
        val ex =
            runCatching {
                OsDetector.withOverriddenIsWindows(!before) {
                    throw RuntimeException("test")
                }
            }.exceptionOrNull()
        ex?.message shouldBe "test"
        OsDetector.isWindows shouldBe before
    }
})