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
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.checkAll
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

    withData(
        nameFn = { (input, expected) -> "constructor coerces seekRatio $input to $expected" },
        0.0 to 0.0,
        0.5 to 0.5,
        1.0 to 1.0,
        -0.5 to 0.0,
        1.5 to 1.0
    ) { (input, expected) ->
        SeekEvent(source, target, input).seekRatio shouldBe expected
    }

    "constructor clamps every finite seekRatio into [0.0, 1.0]" {
        checkAll(Arb.double(-10.0..10.0).filter { it.isFinite() }) { input ->
            SeekEvent(source, target, input).seekRatio shouldBe input.coerceIn(0.0, 1.0)
        }
    }

    withData(
        nameFn = { "constructor rejects non-finite seekRatio $it" },
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY
    ) { ratio ->
        shouldThrow<IllegalArgumentException> { SeekEvent(source, target, ratio) }
    }

    "event reports the configured event type" {
        SeekEvent(source, target, 0.25).eventType shouldBe SeekEvent.SEEK
    }
})