/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.fx.music.waveform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import javafx.event.EventTarget

internal class SeekEventTest : StringSpec({

    val source = "test-source"
    // EventTarget is a single-abstract-method interface; a SAM-conversion lambda avoids
    // mocking it. Mocking JavaFX module-private types fails on JDK 17+ without
    // `--add-opens javafx.base/javafx.event=ALL-UNNAMED`, which is not configured in CI.
    val target = EventTarget { it }

    "SEEK event type is namespaced to avoid collisions" {
        SeekEvent.SEEK.name shouldBe "net.transgressoft.commons.fx.music.waveform.SEEK"
    }

    "constructor stores a finite seekRatio unchanged when inside [0.0, 1.0]" {
        SeekEvent(source, target, 0.5).seekRatio shouldBe 0.5
        SeekEvent(source, target, 0.0).seekRatio shouldBe 0.0
        SeekEvent(source, target, 1.0).seekRatio shouldBe 1.0
    }

    "constructor clamps values below 0.0 up to 0.0" {
        SeekEvent(source, target, -0.5).seekRatio shouldBe 0.0
    }

    "constructor clamps values above 1.0 down to 1.0" {
        SeekEvent(source, target, 1.5).seekRatio shouldBe 1.0
    }

    "constructor rejects NaN seekRatio" {
        shouldThrow<IllegalArgumentException> { SeekEvent(source, target, Double.NaN) }
    }

    "constructor rejects positive infinite seekRatio" {
        shouldThrow<IllegalArgumentException> { SeekEvent(source, target, Double.POSITIVE_INFINITY) }
    }

    "constructor rejects negative infinite seekRatio" {
        shouldThrow<IllegalArgumentException> { SeekEvent(source, target, Double.NEGATIVE_INFINITY) }
    }

    "event reports the configured event type" {
        SeekEvent(source, target, 0.25).eventType shouldBe SeekEvent.SEEK
    }
})