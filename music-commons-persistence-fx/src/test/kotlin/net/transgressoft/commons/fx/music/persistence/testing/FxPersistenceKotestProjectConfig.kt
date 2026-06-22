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

package net.transgressoft.commons.fx.music.persistence.testing

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.engine.concurrency.SpecExecutionMode
import io.kotest.engine.concurrency.TestExecutionMode

/**
 * Kotest project configuration for `music-commons-persistence-fx` tests.
 *
 * Specs run strictly sequentially, mirroring the FX module: the JavaFX toolkit and SQLite
 * fixture state are both process-global resources that cannot be safely shared across
 * concurrent specs.
 */
class FxPersistenceKotestProjectConfig : AbstractProjectConfig() {
    override val specExecutionMode = SpecExecutionMode.Sequential
    override val testExecutionMode = TestExecutionMode.Sequential
}