/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.fx.util

import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener

/**
 * A [SimpleObjectProperty] that runs [onObservation] whenever the property is observed — a listener
 * is attached, or its value is read. This drives lazy, on-demand resolution of a value the first time
 * UI code binds to or reads the property, without the owner exposing an explicit load call: a plain
 * property would never know it is being observed.
 *
 * The callback fires on every observation, so it must be idempotent — typically guarding with an
 * "already triggered" flag and dispatching the actual work (e.g. a disk read) off the observing
 * thread, then publishing the resolved value back into this property.
 *
 * Exposing the property as a `ReadOnlyObjectProperty` keeps the lazy-resolution contract while
 * preventing external mutation.
 *
 * @param T the property value type
 */
internal class LazyObservationObjectProperty<T>(
    bean: Any,
    name: String,
    initialValue: T,
    private val onObservation: () -> Unit
) : SimpleObjectProperty<T>(bean, name, initialValue) {

    override fun addListener(listener: InvalidationListener) {
        super.addListener(listener)
        onObservation()
    }

    override fun addListener(listener: ChangeListener<in T>) {
        super.addListener(listener)
        onObservation()
    }

    override fun get(): T {
        onObservation()
        return super.get()
    }
}