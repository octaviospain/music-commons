package net.transgressoft.commons.music.audio

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec

@DisplayName("ImmutableLabel")
internal class ImmutableLabelTest : StringSpec({

    include(flyweightValueTypeContract("ImmutableLabel", Label::of, Label.UNKNOWN) { it.name })
})