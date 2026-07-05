package net.transgressoft.commons.music.audio

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec

@DisplayName("ImmutableArtist")
internal class ImmutableArtistTest : StringSpec({

    include(flyweightValueTypeContract("ImmutableArtist", Artist::of, Artist.UNKNOWN) { it.name })
})