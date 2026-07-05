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
 * Predefined standard genre constants sourced from the
 * [whatlastgenre whitelist](https://github.com/YetAnotherNerd/whatlastgenre).
 *
 * Each singleton is a [Genre] subtype keyed by its display name. Use these constants when
 * constructing or comparing genre values to avoid raw string allocation and to benefit from
 * identity-level equality checks. Genres not present here should use [Genre.Custom].
 */
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