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

package net.transgressoft.commons.music.audio

/**
 * Represents a music genre as a sealed type hierarchy.
 *
 * Known genres are predefined [data object] singletons from the
 * [whatlastgenre whitelist](https://github.com/YetAnotherNerd/whatlastgenre).
 * Unrecognized genre strings are preserved via [Custom] instead of being
 * mapped to a lossy fallback.
 *
 * @property name display-friendly genre name (e.g. "Hip Hop", "Drum And Bass")
 */
sealed class Genre(open val name: String) {

    data object Abstract : Genre("Abstract")

    data object AbstractHipHop : Genre("Abstract Hip Hop")

    data object AbstractRock : Genre("Abstract Rock")

    data object Acapella : Genre("Acapella")

    data object AcidBreakbeat : Genre("Acid Breakbeat")

    data object AcidHouse : Genre("Acid House")

    data object AcidJazz : Genre("Acid Jazz")

    data object AcidRock : Genre("Acid Rock")

    data object Acoustic : Genre("Acoustic")

    data object Alternative : Genre("Alternative")

    data object AlternativeCountry : Genre("Alternative Country")

    data object AlternativeDance : Genre("Alternative Dance")

    data object AlternativeMetal : Genre("Alternative Metal")

    data object AlternativeRock : Genre("Alternative Rock")

    data object Ambient : Genre("Ambient")

    data object Americana : Genre("Americana")

    data object Anime : Genre("Anime")

    data object ArtRock : Genre("Art Rock")

    data object Avantgarde : Genre("Avantgarde")

    data object AvantgardeJazz : Genre("Avantgarde Jazz")

    data object AvantgardeMetal : Genre("Avantgarde Metal")

    data object Bachata : Genre("Bachata")

    data object Baroque : Genre("Baroque")

    data object BassMusic : Genre("Bass Music")

    data object Beatbox : Genre("Beatbox")

    data object Bebop : Genre("Bebop")

    data object BigBand : Genre("Big Band")

    data object BigBeat : Genre("Big Beat")

    data object Bitcore : Genre("Bitcore")

    data object Bitpop : Genre("Bitpop")

    data object BlackMetal : Genre("Black Metal")

    data object Bluegrass : Genre("Bluegrass")

    data object Blues : Genre("Blues")

    data object BluesRock : Genre("Blues Rock")

    data object Boogie : Genre("Boogie")

    data object BoogieWoogie : Genre("Boogie Woogie")

    data object BossaNova : Genre("Bossa Nova")

    data object BrassBand : Genre("Brass Band")

    data object Breakbeat : Genre("Breakbeat")

    data object Breakcore : Genre("Breakcore")

    data object Britpop : Genre("Britpop")

    data object BrokenBeat : Genre("Broken Beat")

    data object BumbaMeuBoi : Genre("Bumba Meu Boi")

    data object Celtic : Genre("Celtic")

    data object CelticFusion : Genre("Celtic Fusion")

    data object CelticMetal : Genre("Celtic Metal")

    data object CelticPunk : Genre("Celtic Punk")

    data object CelticReggae : Genre("Celtic Reggae")

    data object CelticRock : Genre("Celtic Rock")

    data object Chamber : Genre("Chamber")

    data object ChamberJazz : Genre("Chamber Jazz")

    data object ChamberMusic : Genre("Chamber Music")

    data object ChamberPop : Genre("Chamber Pop")

    data object Chanson : Genre("Chanson")

    data object Chant : Genre("Chant")

    data object ChaChaCha : Genre("Cha Cha Cha")

    data object Chicha : Genre("Chicha")

    data object Children : Genre("Children")

    data object Chillout : Genre("Chillout")

    data object Chillwave : Genre("Chillwave")

    data object Chiptune : Genre("Chiptune")

    data object Christian : Genre("Christian")

    data object Christmas : Genre("Christmas")

    data object CityPop : Genre("City Pop")

    data object Classic : Genre("Classic")

    data object Classical : Genre("Classical")

    data object ClassicPop : Genre("Classic Pop")

    data object ClassicRock : Genre("Classic Rock")

    data object Club : Genre("Club")

    data object Comedy : Genre("Comedy")

    data object ContemporaryBlues : Genre("Contemporary Blues")

    data object ContemporaryClassical : Genre("Contemporary Classical")

    data object ContemporaryJazz : Genre("Contemporary Jazz")

    data object CoolJazz : Genre("Cool Jazz")

    data object Country : Genre("Country")

    data object CountryPop : Genre("Country Pop")

    data object CountryRock : Genre("Country Rock")

    data object Crossover : Genre("Crossover")

    data object Crunk : Genre("Crunk")

    data object Crunkcore : Genre("Crunkcore")

    data object CrustPunk : Genre("Crust Punk")

    data object CGothic : Genre("C Gothic")

    data object CHipHop : Genre("C Hip Hop")

    data object CJazz : Genre("C Jazz")

    data object CPop : Genre("C Pop")

    data object CRock : Genre("C Rock")

    data object CSka : Genre("C Ska")

    data object CTrance : Genre("C Trance")

    data object Dance : Genre("Dance")

    data object Dancehall : Genre("Dancehall")

    data object DarkAmbient : Genre("Dark Ambient")

    data object DarkCore : Genre("Dark Core")

    data object DarkPop : Genre("Dark Pop")

    data object DarkStep : Genre("Dark Step")

    data object DarkWave : Genre("Dark Wave")

    data object Deathcore : Genre("Deathcore")

    data object Deathgrind : Genre("Deathgrind")

    data object DeathMetal : Genre("Death Metal")

    data object DeepFunk : Genre("Deep Funk")

    data object DeepHouse : Genre("Deep House")

    data object DeepSoul : Genre("Deep Soul")

    data object Disco : Genre("Disco")

    data object DiscoHouse : Genre("Disco House")

    data object DixielandJazz : Genre("Dixieland Jazz")

    data object Doomcore : Genre("Doomcore")

    data object DoomMetal : Genre("Doom Metal")

    data object DooWop : Genre("Doo Wop")

    data object Downtempo : Genre("Downtempo")

    data object DreamPop : Genre("Dream Pop")

    data object Drone : Genre("Drone")

    data object DroneMetal : Genre("Drone Metal")

    data object DrumAndBass : Genre("Drum And Bass")

    data object Dub : Genre("Dub")

    data object Dubstep : Genre("Dubstep")

    data object DubHouse : Genre("Dub House")

    data object Dubtronica : Genre("Dubtronica")

    data object Ebm : Genre("Ebm")

    data object Edm : Genre("Edm")

    data object Electro : Genre("Electro")

    data object Electronic : Genre("Electronic")

    data object Electronica : Genre("Electronica")

    data object ElectronicDance : Genre("Electronic Dance")

    data object Electropop : Genre("Electropop")

    data object ElectroSwing : Genre("Electro Swing")

    data object ElectroWave : Genre("Electro Wave")

    data object Emo : Genre("Emo")

    data object EmoPop : Genre("Emo Pop")

    data object EmoRap : Genre("Emo Rap")

    data object Enka : Genre("Enka")

    data object Ethnic : Genre("Ethnic")

    data object EuroDance : Genre("Euro Dance")

    data object EuroDisco : Genre("Euro Disco")

    data object EuroHouse : Genre("Euro House")

    data object EuroPop : Genre("Euro Pop")

    data object EuroTrance : Genre("Euro Trance")

    data object Experimental : Genre("Experimental")

    data object ExperimentalNoise : Genre("Experimental Noise")

    data object ExperimentalPop : Genre("Experimental Pop")

    data object ExperimentalRock : Genre("Experimental Rock")

    data object FemaleVocalist : Genre("Female Vocalist")

    data object Fingerstyle : Genre("Fingerstyle")

    data object Flamenco : Genre("Flamenco")

    data object Folk : Genre("Folk")

    data object FolkHop : Genre("Folk Hop")

    data object FolkMetal : Genre("Folk Metal")

    data object FolkPop : Genre("Folk Pop")

    data object FolkPunk : Genre("Folk Punk")

    data object FolkRock : Genre("Folk Rock")

    data object Folktronica : Genre("Folktronica")

    data object FreakFolk : Genre("Freak Folk")

    data object Funk : Genre("Funk")

    data object Fusion : Genre("Fusion")

    data object FusionJazz : Genre("Fusion Jazz")

    data object FutureBass : Genre("Future Bass")

    data object FutureJazz : Genre("Future Jazz")

    data object Garage : Genre("Garage")

    data object GarageRock : Genre("Garage Rock")

    data object GlamMetal : Genre("Glam Metal")

    data object GlamPunk : Genre("Glam Punk")

    data object GlamRock : Genre("Glam Rock")

    data object Glitch : Genre("Glitch")

    data object GlitchHop : Genre("Glitch Hop")

    data object GloFi : Genre("Glo Fi")

    data object Goa : Genre("Goa")

    data object Gospel : Genre("Gospel")

    data object Gothic : Genre("Gothic")

    data object GothicMetal : Genre("Gothic Metal")

    data object GothicRock : Genre("Gothic Rock")

    data object Grime : Genre("Grime")

    data object Grindcore : Genre("Grindcore")

    data object Grunge : Genre("Grunge")

    data object GypsyJazz : Genre("Gypsy Jazz")

    data object GypsyPunk : Genre("Gypsy Punk")

    data object GFunk : Genre("G Funk")

    data object Hardcore : Genre("Hardcore")

    data object Hardstyle : Genre("Hardstyle")

    data object HardBop : Genre("Hard Bop")

    data object HardHouse : Genre("Hard House")

    data object HardRock : Genre("Hard Rock")

    data object HardTrance : Genre("Hard Trance")

    data object HeavyMetal : Genre("Heavy Metal")

    data object HipHop : Genre("Hip Hop")

    data object Horrorcore : Genre("Horrorcore")

    data object HorrorPunk : Genre("Horror Punk")

    data object House : Genre("House")

    data object Idm : Genre("Idm")

    data object Illbient : Genre("Illbient")

    data object Impressionist : Genre("Impressionist")

    data object Incidental : Genre("Incidental")

    data object Indie : Genre("Indie")

    data object IndieFolk : Genre("Indie Folk")

    data object IndiePop : Genre("Indie Pop")

    data object IndieRock : Genre("Indie Rock")

    data object Indietronic : Genre("Indietronic")

    data object Industrial : Genre("Industrial")

    data object IndustrialMetal : Genre("Industrial Metal")

    data object IndustrialRock : Genre("Industrial Rock")

    data object Instrumental : Genre("Instrumental")

    data object InstrumentalHipHop : Genre("Instrumental Hip Hop")

    data object InstrumentalRock : Genre("Instrumental Rock")

    data object Jazz : Genre("Jazz")

    data object JazzFusion : Genre("Jazz Fusion")

    data object JazzHop : Genre("Jazz Hop")

    data object Jumpstyle : Genre("Jumpstyle")

    data object Jungle : Genre("Jungle")

    data object JFusion : Genre("J Fusion")

    data object JGothic : Genre("J Gothic")

    data object JHipHop : Genre("J Hip Hop")

    data object JJazz : Genre("J Jazz")

    data object JPop : Genre("J Pop")

    data object JRock : Genre("J Rock")

    data object JSka : Genre("J Ska")

    data object JTrance : Genre("J Trance")

    data object Krautrock : Genre("Krautrock")

    data object KGothic : Genre("K Gothic")

    data object KHipHop : Genre("K Hip Hop")

    data object KJazz : Genre("K Jazz")

    data object KPop : Genre("K Pop")

    data object KRock : Genre("K Rock")

    data object KSka : Genre("K Ska")

    data object KTrance : Genre("K Trance")

    data object Latin : Genre("Latin")

    data object LatinJazz : Genre("Latin Jazz")

    data object LatinPop : Genre("Latin Pop")

    data object LatinRock : Genre("Latin Rock")

    data object LiquidFunk : Genre("Liquid Funk")

    data object LoFi : Genre("Lo Fi")

    data object Lounge : Genre("Lounge")

    data object Mambo : Genre("Mambo")

    data object Marimba : Genre("Marimba")

    data object Mathcore : Genre("Mathcore")

    data object MathRock : Genre("Math Rock")

    data object Medieval : Genre("Medieval")

    data object MedievalMetal : Genre("Medieval Metal")

    data object MedievalRock : Genre("Medieval Rock")

    data object MelodicMetal : Genre("Melodic Metal")

    data object MelodicMetalcore : Genre("Melodic Metalcore")

    data object MelodicTrance : Genre("Melodic Trance")

    data object Merengue : Genre("Merengue")

    data object Metal : Genre("Metal")

    data object Metalcore : Genre("Metalcore")

    data object Microhouse : Genre("Microhouse")

    data object Minimal : Genre("Minimal")

    data object ModalJazz : Genre("Modal Jazz")

    data object ModernClassical : Genre("Modern Classical")

    data object ModernRock : Genre("Modern Rock")

    data object Motown : Genre("Motown")

    data object Mpb : Genre("Mpb")

    data object Musical : Genre("Musical")

    data object MushroomJazz : Genre("Mushroom Jazz")

    data object Ndw : Genre("Ndw")

    data object NeoClassical : Genre("Neo Classical")

    data object NeoFolk : Genre("Neo Folk")

    data object NeoMedieval : Genre("Neo Medieval")

    data object NeoProgressive : Genre("Neo Progressive")

    data object NeoPsychedelic : Genre("Neo Psychedelic")

    data object NeoSoul : Genre("Neo Soul")

    data object Nerdcore : Genre("Nerdcore")

    data object NewAge : Genre("New Age")

    data object NewWave : Genre("New Wave")

    data object Newgrass : Genre("Newgrass")

    data object Noise : Genre("Noise")

    data object NoisePop : Genre("Noise Pop")

    data object NoiseRock : Genre("Noise Rock")

    data object NuDisco : Genre("Nu Disco")

    data object NuJazz : Genre("Nu Jazz")

    data object NuMetal : Genre("Nu Metal")

    data object NuSoul : Genre("Nu Soul")

    data object Nwa : Genre("Nwa")

    data object Nwobhm : Genre("Nwobhm")

    data object Nyhc : Genre("Nyhc")

    data object Oldies : Genre("Oldies")

    data object Opera : Genre("Opera")

    data object Orchestral : Genre("Orchestral")

    data object Oriental : Genre("Oriental")

    data object OutlawCountry : Genre("Outlaw Country")

    data object Pop : Genre("Pop")

    data object PopPunk : Genre("Pop Punk")

    data object PopRock : Genre("Pop Rock")

    data object PostBritpop : Genre("Post Britpop")

    data object PostDisco : Genre("Post Disco")

    data object PostGrunge : Genre("Post Grunge")

    data object PostHardcore : Genre("Post Hardcore")

    data object PostIndustrial : Genre("Post Industrial")

    data object PostMetal : Genre("Post Metal")

    data object PostPunk : Genre("Post Punk")

    data object PostRock : Genre("Post Rock")

    data object PowerMetal : Genre("Power Metal")

    data object PowerNoise : Genre("Power Noise")

    data object PowerPop : Genre("Power Pop")

    data object ProgressiveBluegrass : Genre("Progressive Bluegrass")

    data object ProgressiveCountry : Genre("Progressive Country")

    data object ProgressiveHouse : Genre("Progressive House")

    data object ProgressiveMetal : Genre("Progressive Metal")

    data object ProgressiveRock : Genre("Progressive Rock")

    data object ProgressiveTrance : Genre("Progressive Trance")

    data object Protopunk : Genre("Protopunk")

    data object Psychedelic : Genre("Psychedelic")

    data object PsychedelicPop : Genre("Psychedelic Pop")

    data object PsychedelicRock : Genre("Psychedelic Rock")

    data object PsyTrance : Genre("Psy Trance")

    data object Punk : Genre("Punk")

    data object PunkRock : Genre("Punk Rock")

    data object PFunk : Genre("P Funk")

    data object Ragga : Genre("Ragga")

    data object Rap : Genre("Rap")

    data object Rapcore : Genre("Rapcore")

    data object Reggae : Genre("Reggae")

    data object Reggaeton : Genre("Reggaeton")

    data object ReggaeFusion : Genre("Reggae Fusion")

    data object Renaissance : Genre("Renaissance")

    data object Rock : Genre("Rock")

    data object Rockabilly : Genre("Rockabilly")

    data object Rocksteady : Genre("Rocksteady")

    data object RockAndRoll : Genre("Rock And Roll")

    data object RootsReggae : Genre("Roots Reggae")

    data object RootsRock : Genre("Roots Rock")

    data object RAndB : Genre("R And B")

    data object Salsa : Genre("Salsa")

    data object Samba : Genre("Samba")

    data object Schlager : Genre("Schlager")

    data object Schranz : Genre("Schranz")

    data object Screamo : Genre("Screamo")

    data object Shoegaze : Genre("Shoegaze")

    data object SingerSongwriter : Genre("Singer Songwriter")

    data object Ska : Genre("Ska")

    data object Skacore : Genre("Skacore")

    data object SkaPunk : Genre("Ska Punk")

    data object Skatepunk : Genre("Skatepunk")

    data object Skiffle : Genre("Skiffle")

    data object Slowcore : Genre("Slowcore")

    data object SludgeMetal : Genre("Sludge Metal")

    data object SmoothJazz : Genre("Smooth Jazz")

    data object SoftRock : Genre("Soft Rock")

    data object Soul : Genre("Soul")

    data object Soundtrack : Genre("Soundtrack")

    data object SouthernRock : Genre("Southern Rock")

    data object SpaceRock : Genre("Space Rock")

    data object SpeedGarage : Genre("Speed Garage")

    data object SpeedMetal : Genre("Speed Metal")

    data object Speedcore : Genre("Speedcore")

    data object StageAndScreen : Genre("Stage And Screen")

    data object StonerMetal : Genre("Stoner Metal")

    data object StonerRock : Genre("Stoner Rock")

    data object SwampBlues : Genre("Swamp Blues")

    data object SwampPop : Genre("Swamp Pop")

    data object SwampRock : Genre("Swamp Rock")

    data object Swing : Genre("Swing")

    data object SymphonicMetal : Genre("Symphonic Metal")

    data object SymphonicRock : Genre("Symphonic Rock")

    data object Synthwave : Genre("Synthwave")

    data object SynthPop : Genre("Synth Pop")

    data object SynthPunk : Genre("Synth Punk")

    data object Tango : Genre("Tango")

    data object TechnicalMetal : Genre("Technical Metal")

    data object Techno : Genre("Techno")

    data object Techstep : Genre("Techstep")

    data object TechHouse : Genre("Tech House")

    data object TechTrance : Genre("Tech Trance")

    data object ThrashMetal : Genre("Thrash Metal")

    data object Trance : Genre("Trance")

    data object Trap : Genre("Trap")

    data object TribalHouse : Genre("Tribal House")

    data object TripHop : Genre("Trip Hop")

    data object TripRock : Genre("Trip Rock")

    data object TropicalHouse : Genre("Tropical House")

    data object Turntablism : Genre("Turntablism")

    data object TwoStep : Genre("Two Step")

    data object VocalHouse : Genre("Vocal House")

    data object VocalJazz : Genre("Vocal Jazz")

    data object VocalTrance : Genre("Vocal Trance")

    data object World : Genre("World")

    data object WorldFusion : Genre("World Fusion")

    data class Custom(override val name: String) : Genre(name) {
        init {
            require("," !in name) { "Custom genre name must not contain commas: '$name'" }
        }
    }

    companion object {
        // Hardcoded map avoids kotlin-reflect dependency on Genre::class.sealedSubclasses.
        // Lazy initialization is required because data object instances are not yet initialized
        // when the companion object's static initializer runs during class loading.
        private val BY_NAME: Map<String, Genre> by lazy {
            buildMap {
                put(Abstract.name.lowercase(), Abstract)
                put(AbstractHipHop.name.lowercase(), AbstractHipHop)
                put(AbstractRock.name.lowercase(), AbstractRock)
                put(Acapella.name.lowercase(), Acapella)
                put(AcidBreakbeat.name.lowercase(), AcidBreakbeat)
                put(AcidHouse.name.lowercase(), AcidHouse)
                put(AcidJazz.name.lowercase(), AcidJazz)
                put(AcidRock.name.lowercase(), AcidRock)
                put(Acoustic.name.lowercase(), Acoustic)
                put(Alternative.name.lowercase(), Alternative)
                put(AlternativeCountry.name.lowercase(), AlternativeCountry)
                put(AlternativeDance.name.lowercase(), AlternativeDance)
                put(AlternativeMetal.name.lowercase(), AlternativeMetal)
                put(AlternativeRock.name.lowercase(), AlternativeRock)
                put(Ambient.name.lowercase(), Ambient)
                put(Americana.name.lowercase(), Americana)
                put(Anime.name.lowercase(), Anime)
                put(ArtRock.name.lowercase(), ArtRock)
                put(Avantgarde.name.lowercase(), Avantgarde)
                put(AvantgardeJazz.name.lowercase(), AvantgardeJazz)
                put(AvantgardeMetal.name.lowercase(), AvantgardeMetal)
                put(Bachata.name.lowercase(), Bachata)
                put(Baroque.name.lowercase(), Baroque)
                put(BassMusic.name.lowercase(), BassMusic)
                put(Beatbox.name.lowercase(), Beatbox)
                put(Bebop.name.lowercase(), Bebop)
                put(BigBand.name.lowercase(), BigBand)
                put(BigBeat.name.lowercase(), BigBeat)
                put(Bitcore.name.lowercase(), Bitcore)
                put(Bitpop.name.lowercase(), Bitpop)
                put(BlackMetal.name.lowercase(), BlackMetal)
                put(Bluegrass.name.lowercase(), Bluegrass)
                put(Blues.name.lowercase(), Blues)
                put(BluesRock.name.lowercase(), BluesRock)
                put(Boogie.name.lowercase(), Boogie)
                put(BoogieWoogie.name.lowercase(), BoogieWoogie)
                put(BossaNova.name.lowercase(), BossaNova)
                put(BrassBand.name.lowercase(), BrassBand)
                put(Breakbeat.name.lowercase(), Breakbeat)
                put(Breakcore.name.lowercase(), Breakcore)
                put(Britpop.name.lowercase(), Britpop)
                put(BrokenBeat.name.lowercase(), BrokenBeat)
                put(BumbaMeuBoi.name.lowercase(), BumbaMeuBoi)
                put(Celtic.name.lowercase(), Celtic)
                put(CelticFusion.name.lowercase(), CelticFusion)
                put(CelticMetal.name.lowercase(), CelticMetal)
                put(CelticPunk.name.lowercase(), CelticPunk)
                put(CelticReggae.name.lowercase(), CelticReggae)
                put(CelticRock.name.lowercase(), CelticRock)
                put(Chamber.name.lowercase(), Chamber)
                put(ChamberJazz.name.lowercase(), ChamberJazz)
                put(ChamberMusic.name.lowercase(), ChamberMusic)
                put(ChamberPop.name.lowercase(), ChamberPop)
                put(Chanson.name.lowercase(), Chanson)
                put(Chant.name.lowercase(), Chant)
                put(ChaChaCha.name.lowercase(), ChaChaCha)
                put(Chicha.name.lowercase(), Chicha)
                put(Children.name.lowercase(), Children)
                put(Chillout.name.lowercase(), Chillout)
                put(Chillwave.name.lowercase(), Chillwave)
                put(Chiptune.name.lowercase(), Chiptune)
                put(Christian.name.lowercase(), Christian)
                put(Christmas.name.lowercase(), Christmas)
                put(CityPop.name.lowercase(), CityPop)
                put(Classic.name.lowercase(), Classic)
                put(Classical.name.lowercase(), Classical)
                put(ClassicPop.name.lowercase(), ClassicPop)
                put(ClassicRock.name.lowercase(), ClassicRock)
                put(Club.name.lowercase(), Club)
                put(Comedy.name.lowercase(), Comedy)
                put(ContemporaryBlues.name.lowercase(), ContemporaryBlues)
                put(ContemporaryClassical.name.lowercase(), ContemporaryClassical)
                put(ContemporaryJazz.name.lowercase(), ContemporaryJazz)
                put(CoolJazz.name.lowercase(), CoolJazz)
                put(Country.name.lowercase(), Country)
                put(CountryPop.name.lowercase(), CountryPop)
                put(CountryRock.name.lowercase(), CountryRock)
                put(Crossover.name.lowercase(), Crossover)
                put(Crunk.name.lowercase(), Crunk)
                put(Crunkcore.name.lowercase(), Crunkcore)
                put(CrustPunk.name.lowercase(), CrustPunk)
                put(CGothic.name.lowercase(), CGothic)
                put(CHipHop.name.lowercase(), CHipHop)
                put(CJazz.name.lowercase(), CJazz)
                put(CPop.name.lowercase(), CPop)
                put(CRock.name.lowercase(), CRock)
                put(CSka.name.lowercase(), CSka)
                put(CTrance.name.lowercase(), CTrance)
                put(Dance.name.lowercase(), Dance)
                put(Dancehall.name.lowercase(), Dancehall)
                put(DarkAmbient.name.lowercase(), DarkAmbient)
                put(DarkCore.name.lowercase(), DarkCore)
                put(DarkPop.name.lowercase(), DarkPop)
                put(DarkStep.name.lowercase(), DarkStep)
                put(DarkWave.name.lowercase(), DarkWave)
                put(Deathcore.name.lowercase(), Deathcore)
                put(Deathgrind.name.lowercase(), Deathgrind)
                put(DeathMetal.name.lowercase(), DeathMetal)
                put(DeepFunk.name.lowercase(), DeepFunk)
                put(DeepHouse.name.lowercase(), DeepHouse)
                put(DeepSoul.name.lowercase(), DeepSoul)
                put(Disco.name.lowercase(), Disco)
                put(DiscoHouse.name.lowercase(), DiscoHouse)
                put(DixielandJazz.name.lowercase(), DixielandJazz)
                put(Doomcore.name.lowercase(), Doomcore)
                put(DoomMetal.name.lowercase(), DoomMetal)
                put(DooWop.name.lowercase(), DooWop)
                put(Downtempo.name.lowercase(), Downtempo)
                put(DreamPop.name.lowercase(), DreamPop)
                put(Drone.name.lowercase(), Drone)
                put(DroneMetal.name.lowercase(), DroneMetal)
                put(DrumAndBass.name.lowercase(), DrumAndBass)
                put(Dub.name.lowercase(), Dub)
                put(Dubstep.name.lowercase(), Dubstep)
                put(DubHouse.name.lowercase(), DubHouse)
                put(Dubtronica.name.lowercase(), Dubtronica)
                put(Ebm.name.lowercase(), Ebm)
                put(Edm.name.lowercase(), Edm)
                put(Electro.name.lowercase(), Electro)
                put(Electronic.name.lowercase(), Electronic)
                put(Electronica.name.lowercase(), Electronica)
                put(ElectronicDance.name.lowercase(), ElectronicDance)
                put(Electropop.name.lowercase(), Electropop)
                put(ElectroSwing.name.lowercase(), ElectroSwing)
                put(ElectroWave.name.lowercase(), ElectroWave)
                put(Emo.name.lowercase(), Emo)
                put(EmoPop.name.lowercase(), EmoPop)
                put(EmoRap.name.lowercase(), EmoRap)
                put(Enka.name.lowercase(), Enka)
                put(Ethnic.name.lowercase(), Ethnic)
                put(EuroDance.name.lowercase(), EuroDance)
                put(EuroDisco.name.lowercase(), EuroDisco)
                put(EuroHouse.name.lowercase(), EuroHouse)
                put(EuroPop.name.lowercase(), EuroPop)
                put(EuroTrance.name.lowercase(), EuroTrance)
                put(Experimental.name.lowercase(), Experimental)
                put(ExperimentalNoise.name.lowercase(), ExperimentalNoise)
                put(ExperimentalPop.name.lowercase(), ExperimentalPop)
                put(ExperimentalRock.name.lowercase(), ExperimentalRock)
                put(FemaleVocalist.name.lowercase(), FemaleVocalist)
                put(Fingerstyle.name.lowercase(), Fingerstyle)
                put(Flamenco.name.lowercase(), Flamenco)
                put(Folk.name.lowercase(), Folk)
                put(FolkHop.name.lowercase(), FolkHop)
                put(FolkMetal.name.lowercase(), FolkMetal)
                put(FolkPop.name.lowercase(), FolkPop)
                put(FolkPunk.name.lowercase(), FolkPunk)
                put(FolkRock.name.lowercase(), FolkRock)
                put(Folktronica.name.lowercase(), Folktronica)
                put(FreakFolk.name.lowercase(), FreakFolk)
                put(Funk.name.lowercase(), Funk)
                put(Fusion.name.lowercase(), Fusion)
                put(FusionJazz.name.lowercase(), FusionJazz)
                put(FutureBass.name.lowercase(), FutureBass)
                put(FutureJazz.name.lowercase(), FutureJazz)
                put(Garage.name.lowercase(), Garage)
                put(GarageRock.name.lowercase(), GarageRock)
                put(GlamMetal.name.lowercase(), GlamMetal)
                put(GlamPunk.name.lowercase(), GlamPunk)
                put(GlamRock.name.lowercase(), GlamRock)
                put(Glitch.name.lowercase(), Glitch)
                put(GlitchHop.name.lowercase(), GlitchHop)
                put(GloFi.name.lowercase(), GloFi)
                put(Goa.name.lowercase(), Goa)
                put(Gospel.name.lowercase(), Gospel)
                put(Gothic.name.lowercase(), Gothic)
                put(GothicMetal.name.lowercase(), GothicMetal)
                put(GothicRock.name.lowercase(), GothicRock)
                put(Grime.name.lowercase(), Grime)
                put(Grindcore.name.lowercase(), Grindcore)
                put(Grunge.name.lowercase(), Grunge)
                put(GypsyJazz.name.lowercase(), GypsyJazz)
                put(GypsyPunk.name.lowercase(), GypsyPunk)
                put(GFunk.name.lowercase(), GFunk)
                put(Hardcore.name.lowercase(), Hardcore)
                put(Hardstyle.name.lowercase(), Hardstyle)
                put(HardBop.name.lowercase(), HardBop)
                put(HardHouse.name.lowercase(), HardHouse)
                put(HardRock.name.lowercase(), HardRock)
                put(HardTrance.name.lowercase(), HardTrance)
                put(HeavyMetal.name.lowercase(), HeavyMetal)
                put(HipHop.name.lowercase(), HipHop)
                put(Horrorcore.name.lowercase(), Horrorcore)
                put(HorrorPunk.name.lowercase(), HorrorPunk)
                put(House.name.lowercase(), House)
                put(Idm.name.lowercase(), Idm)
                put(Illbient.name.lowercase(), Illbient)
                put(Impressionist.name.lowercase(), Impressionist)
                put(Incidental.name.lowercase(), Incidental)
                put(Indie.name.lowercase(), Indie)
                put(IndieFolk.name.lowercase(), IndieFolk)
                put(IndiePop.name.lowercase(), IndiePop)
                put(IndieRock.name.lowercase(), IndieRock)
                put(Indietronic.name.lowercase(), Indietronic)
                put(Industrial.name.lowercase(), Industrial)
                put(IndustrialMetal.name.lowercase(), IndustrialMetal)
                put(IndustrialRock.name.lowercase(), IndustrialRock)
                put(Instrumental.name.lowercase(), Instrumental)
                put(InstrumentalHipHop.name.lowercase(), InstrumentalHipHop)
                put(InstrumentalRock.name.lowercase(), InstrumentalRock)
                put(Jazz.name.lowercase(), Jazz)
                put(JazzFusion.name.lowercase(), JazzFusion)
                put(JazzHop.name.lowercase(), JazzHop)
                put(Jumpstyle.name.lowercase(), Jumpstyle)
                put(Jungle.name.lowercase(), Jungle)
                put(JFusion.name.lowercase(), JFusion)
                put(JGothic.name.lowercase(), JGothic)
                put(JHipHop.name.lowercase(), JHipHop)
                put(JJazz.name.lowercase(), JJazz)
                put(JPop.name.lowercase(), JPop)
                put(JRock.name.lowercase(), JRock)
                put(JSka.name.lowercase(), JSka)
                put(JTrance.name.lowercase(), JTrance)
                put(Krautrock.name.lowercase(), Krautrock)
                put(KGothic.name.lowercase(), KGothic)
                put(KHipHop.name.lowercase(), KHipHop)
                put(KJazz.name.lowercase(), KJazz)
                put(KPop.name.lowercase(), KPop)
                put(KRock.name.lowercase(), KRock)
                put(KSka.name.lowercase(), KSka)
                put(KTrance.name.lowercase(), KTrance)
                put(Latin.name.lowercase(), Latin)
                put(LatinJazz.name.lowercase(), LatinJazz)
                put(LatinPop.name.lowercase(), LatinPop)
                put(LatinRock.name.lowercase(), LatinRock)
                put(LiquidFunk.name.lowercase(), LiquidFunk)
                put(LoFi.name.lowercase(), LoFi)
                put(Lounge.name.lowercase(), Lounge)
                put(Mambo.name.lowercase(), Mambo)
                put(Marimba.name.lowercase(), Marimba)
                put(Mathcore.name.lowercase(), Mathcore)
                put(MathRock.name.lowercase(), MathRock)
                put(Medieval.name.lowercase(), Medieval)
                put(MedievalMetal.name.lowercase(), MedievalMetal)
                put(MedievalRock.name.lowercase(), MedievalRock)
                put(MelodicMetal.name.lowercase(), MelodicMetal)
                put(MelodicMetalcore.name.lowercase(), MelodicMetalcore)
                put(MelodicTrance.name.lowercase(), MelodicTrance)
                put(Merengue.name.lowercase(), Merengue)
                put(Metal.name.lowercase(), Metal)
                put(Metalcore.name.lowercase(), Metalcore)
                put(Microhouse.name.lowercase(), Microhouse)
                put(Minimal.name.lowercase(), Minimal)
                put(ModalJazz.name.lowercase(), ModalJazz)
                put(ModernClassical.name.lowercase(), ModernClassical)
                put(ModernRock.name.lowercase(), ModernRock)
                put(Motown.name.lowercase(), Motown)
                put(Mpb.name.lowercase(), Mpb)
                put(Musical.name.lowercase(), Musical)
                put(MushroomJazz.name.lowercase(), MushroomJazz)
                put(Ndw.name.lowercase(), Ndw)
                put(NeoClassical.name.lowercase(), NeoClassical)
                put(NeoFolk.name.lowercase(), NeoFolk)
                put(NeoMedieval.name.lowercase(), NeoMedieval)
                put(NeoProgressive.name.lowercase(), NeoProgressive)
                put(NeoPsychedelic.name.lowercase(), NeoPsychedelic)
                put(NeoSoul.name.lowercase(), NeoSoul)
                put(Nerdcore.name.lowercase(), Nerdcore)
                put(NewAge.name.lowercase(), NewAge)
                put(NewWave.name.lowercase(), NewWave)
                put(Newgrass.name.lowercase(), Newgrass)
                put(Noise.name.lowercase(), Noise)
                put(NoisePop.name.lowercase(), NoisePop)
                put(NoiseRock.name.lowercase(), NoiseRock)
                put(NuDisco.name.lowercase(), NuDisco)
                put(NuJazz.name.lowercase(), NuJazz)
                put(NuMetal.name.lowercase(), NuMetal)
                put(NuSoul.name.lowercase(), NuSoul)
                put(Nwa.name.lowercase(), Nwa)
                put(Nwobhm.name.lowercase(), Nwobhm)
                put(Nyhc.name.lowercase(), Nyhc)
                put(Oldies.name.lowercase(), Oldies)
                put(Opera.name.lowercase(), Opera)
                put(Orchestral.name.lowercase(), Orchestral)
                put(Oriental.name.lowercase(), Oriental)
                put(OutlawCountry.name.lowercase(), OutlawCountry)
                put(Pop.name.lowercase(), Pop)
                put(PopPunk.name.lowercase(), PopPunk)
                put(PopRock.name.lowercase(), PopRock)
                put(PostBritpop.name.lowercase(), PostBritpop)
                put(PostDisco.name.lowercase(), PostDisco)
                put(PostGrunge.name.lowercase(), PostGrunge)
                put(PostHardcore.name.lowercase(), PostHardcore)
                put(PostIndustrial.name.lowercase(), PostIndustrial)
                put(PostMetal.name.lowercase(), PostMetal)
                put(PostPunk.name.lowercase(), PostPunk)
                put(PostRock.name.lowercase(), PostRock)
                put(PowerMetal.name.lowercase(), PowerMetal)
                put(PowerNoise.name.lowercase(), PowerNoise)
                put(PowerPop.name.lowercase(), PowerPop)
                put(ProgressiveBluegrass.name.lowercase(), ProgressiveBluegrass)
                put(ProgressiveCountry.name.lowercase(), ProgressiveCountry)
                put(ProgressiveHouse.name.lowercase(), ProgressiveHouse)
                put(ProgressiveMetal.name.lowercase(), ProgressiveMetal)
                put(ProgressiveRock.name.lowercase(), ProgressiveRock)
                put(ProgressiveTrance.name.lowercase(), ProgressiveTrance)
                put(Protopunk.name.lowercase(), Protopunk)
                put(Psychedelic.name.lowercase(), Psychedelic)
                put(PsychedelicPop.name.lowercase(), PsychedelicPop)
                put(PsychedelicRock.name.lowercase(), PsychedelicRock)
                put(PsyTrance.name.lowercase(), PsyTrance)
                put(Punk.name.lowercase(), Punk)
                put(PunkRock.name.lowercase(), PunkRock)
                put(PFunk.name.lowercase(), PFunk)
                put(Ragga.name.lowercase(), Ragga)
                put(Rap.name.lowercase(), Rap)
                put(Rapcore.name.lowercase(), Rapcore)
                put(Reggae.name.lowercase(), Reggae)
                put(Reggaeton.name.lowercase(), Reggaeton)
                put(ReggaeFusion.name.lowercase(), ReggaeFusion)
                put(Renaissance.name.lowercase(), Renaissance)
                put(Rock.name.lowercase(), Rock)
                put(Rockabilly.name.lowercase(), Rockabilly)
                put(Rocksteady.name.lowercase(), Rocksteady)
                put(RockAndRoll.name.lowercase(), RockAndRoll)
                put(RootsReggae.name.lowercase(), RootsReggae)
                put(RootsRock.name.lowercase(), RootsRock)
                put(RAndB.name.lowercase(), RAndB)
                put(Salsa.name.lowercase(), Salsa)
                put(Samba.name.lowercase(), Samba)
                put(Schlager.name.lowercase(), Schlager)
                put(Schranz.name.lowercase(), Schranz)
                put(Screamo.name.lowercase(), Screamo)
                put(Shoegaze.name.lowercase(), Shoegaze)
                put(SingerSongwriter.name.lowercase(), SingerSongwriter)
                put(Ska.name.lowercase(), Ska)
                put(Skacore.name.lowercase(), Skacore)
                put(SkaPunk.name.lowercase(), SkaPunk)
                put(Skatepunk.name.lowercase(), Skatepunk)
                put(Skiffle.name.lowercase(), Skiffle)
                put(Slowcore.name.lowercase(), Slowcore)
                put(SludgeMetal.name.lowercase(), SludgeMetal)
                put(SmoothJazz.name.lowercase(), SmoothJazz)
                put(SoftRock.name.lowercase(), SoftRock)
                put(Soul.name.lowercase(), Soul)
                put(Soundtrack.name.lowercase(), Soundtrack)
                put(SouthernRock.name.lowercase(), SouthernRock)
                put(SpaceRock.name.lowercase(), SpaceRock)
                put(SpeedGarage.name.lowercase(), SpeedGarage)
                put(SpeedMetal.name.lowercase(), SpeedMetal)
                put(Speedcore.name.lowercase(), Speedcore)
                put(StageAndScreen.name.lowercase(), StageAndScreen)
                put(StonerMetal.name.lowercase(), StonerMetal)
                put(StonerRock.name.lowercase(), StonerRock)
                put(SwampBlues.name.lowercase(), SwampBlues)
                put(SwampPop.name.lowercase(), SwampPop)
                put(SwampRock.name.lowercase(), SwampRock)
                put(Swing.name.lowercase(), Swing)
                put(SymphonicMetal.name.lowercase(), SymphonicMetal)
                put(SymphonicRock.name.lowercase(), SymphonicRock)
                put(Synthwave.name.lowercase(), Synthwave)
                put(SynthPop.name.lowercase(), SynthPop)
                put(SynthPunk.name.lowercase(), SynthPunk)
                put(Tango.name.lowercase(), Tango)
                put(TechnicalMetal.name.lowercase(), TechnicalMetal)
                put(Techno.name.lowercase(), Techno)
                put(Techstep.name.lowercase(), Techstep)
                put(TechHouse.name.lowercase(), TechHouse)
                put(TechTrance.name.lowercase(), TechTrance)
                put(ThrashMetal.name.lowercase(), ThrashMetal)
                put(Trance.name.lowercase(), Trance)
                put(Trap.name.lowercase(), Trap)
                put(TribalHouse.name.lowercase(), TribalHouse)
                put(TripHop.name.lowercase(), TripHop)
                put(TripRock.name.lowercase(), TripRock)
                put(TropicalHouse.name.lowercase(), TropicalHouse)
                put(Turntablism.name.lowercase(), Turntablism)
                put(TwoStep.name.lowercase(), TwoStep)
                put(VocalHouse.name.lowercase(), VocalHouse)
                put(VocalJazz.name.lowercase(), VocalJazz)
                put(VocalTrance.name.lowercase(), VocalTrance)
                put(World.name.lowercase(), World)
                put(WorldFusion.name.lowercase(), WorldFusion)
            }
        }

        /**
         * Parses a genre string into a set of [Genre] instances.
         *
         * Supports comma-separated genre tags (e.g. "Rock, Alternative"). Each segment is
         * matched case-insensitively against the known genre list. Unrecognized segments
         * are wrapped in [Custom] to preserve their original value.
         *
         * @param value the raw genre string from audio metadata or JSON persistence
         * @return a set of resolved genres, or an empty set if [value] is blank
         */
        @JvmStatic
        fun parseGenre(value: String): Set<Genre> {
            if (value.isBlank()) return emptySet()
            return value.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { part -> BY_NAME[part.lowercase()] ?: Custom(part) }
                .toSet()
        }
    }
}