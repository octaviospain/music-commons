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

package net.transgressoft.commons.music

/**
 * Marks API elements whose stability is not yet guaranteed for a future release.
 * Using these APIs requires explicit opt-in — either at the call-site with
 * `@OptIn(MusicCommonsExperimentalApi::class)` or at the module level in your build.
 *
 * APIs under this marker may change or be removed in a future minor version.
 *
 * @since 1.0
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message =
        "This API is experimental and may change or be removed in a future release. " +
            "Opt in explicitly with @OptIn(MusicCommonsExperimentalApi::class)."
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.TYPEALIAS
)
public annotation class MusicCommonsExperimentalApi