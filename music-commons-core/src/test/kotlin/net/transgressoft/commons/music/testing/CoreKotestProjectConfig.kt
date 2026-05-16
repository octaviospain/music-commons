/******************************************************************************
 *     Copyright (C) 2025  Octavio Calleya Garcia                             *
 *                                                                            *
 *     This program is free software: you can redistribute it and/or modify   *
 *     it under the terms of the GNU General Public License as published by   *
 *     the Free Software Foundation, either version 3 of the License, or      *
 *     (at your option) any later version.                                    *
 *                                                                            *
 *     This program is distributed in the hope that it will be useful,        *
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of         *
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          *
 *     GNU General Public License for more details.                           *
 *                                                                            *
 *     You should have received a copy of the GNU General Public License      *
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>. *
 ******************************************************************************/

package net.transgressoft.commons.music.testing

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.engine.concurrency.SpecExecutionMode
import io.kotest.engine.concurrency.TestExecutionMode

/**
 * Kotest project configuration for `music-commons-core` tests.
 *
 * Specs run with bounded concurrency (up to four at a time) while tests inside a spec stay
 * sequential, keeping virtual-scheduler ordering and repository fixtures deterministic.
 *
 * Specs that mutate the process-wide [net.transgressoft.lirp.event.ReactiveScope] register
 * [net.transgressoft.commons.music.testing.ReactiveScopeExtension] (via the `reactiveScope()`
 * factory) — its shared mutex serializes them against each other even when spec-level
 * concurrency is enabled. The same applies to specs mutating other process-wide state
 * (`VirtualFiles` static mocks, `OsDetector`); those carry `concurrency = SingleThread` per
 * Decision 3 in the phase plan.
 */
class CoreKotestProjectConfig : AbstractProjectConfig() {
    override val specExecutionMode = SpecExecutionMode.LimitedConcurrency(4)
    override val testExecutionMode = TestExecutionMode.Sequential
}