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
 * @since 1.0
 */
public data object Abstract : Genre("Abstract")

public data object AbstractHipHop : Genre("Abstract Hip Hop")

public data object AbstractRock : Genre("Abstract Rock")

public data object Acapella : Genre("Acapella")

public data object AcidBreakbeat : Genre("Acid Breakbeat")

public data object AcidHouse : Genre("Acid House")

public data object AcidJazz : Genre("Acid Jazz")

public data object AcidRock : Genre("Acid Rock")

public data object Acoustic : Genre("Acoustic")

public data object Alternative : Genre("Alternative")

public data object AlternativeCountry : Genre("Alternative Country")

public data object AlternativeDance : Genre("Alternative Dance")

public data object AlternativeMetal : Genre("Alternative Metal")

public data object AlternativeRock : Genre("Alternative Rock")

public data object Ambient : Genre("Ambient")

public data object Americana : Genre("Americana")

public data object Anime : Genre("Anime")

public data object ArtRock : Genre("Art Rock")

public data object Avantgarde : Genre("Avantgarde")

public data object AvantgardeJazz : Genre("Avantgarde Jazz")

public data object AvantgardeMetal : Genre("Avantgarde Metal")

public data object Bachata : Genre("Bachata")

public data object Baroque : Genre("Baroque")

public data object BassMusic : Genre("Bass Music")

public data object Beatbox : Genre("Beatbox")

public data object Bebop : Genre("Bebop")

public data object BigBand : Genre("Big Band")

public data object BigBeat : Genre("Big Beat")

public data object Bitcore : Genre("Bitcore")

public data object Bitpop : Genre("Bitpop")

public data object BlackMetal : Genre("Black Metal")

public data object Bluegrass : Genre("Bluegrass")

public data object Blues : Genre("Blues")

public data object BluesRock : Genre("Blues Rock")

public data object Boogie : Genre("Boogie")

public data object BoogieWoogie : Genre("Boogie Woogie")

public data object BossaNova : Genre("Bossa Nova")

public data object BrassBand : Genre("Brass Band")

public data object Breakbeat : Genre("Breakbeat")

public data object Breakcore : Genre("Breakcore")

public data object Britpop : Genre("Britpop")

public data object BrokenBeat : Genre("Broken Beat")

public data object BumbaMeuBoi : Genre("Bumba Meu Boi")

public data object Celtic : Genre("Celtic")

public data object CelticFusion : Genre("Celtic Fusion")

public data object CelticMetal : Genre("Celtic Metal")

public data object CelticPunk : Genre("Celtic Punk")

public data object CelticReggae : Genre("Celtic Reggae")

public data object CelticRock : Genre("Celtic Rock")

public data object Chamber : Genre("Chamber")

public data object ChamberJazz : Genre("Chamber Jazz")

public data object ChamberMusic : Genre("Chamber Music")

public data object ChamberPop : Genre("Chamber Pop")

public data object Chanson : Genre("Chanson")

public data object Chant : Genre("Chant")

public data object ChaChaCha : Genre("Cha Cha Cha")

public data object Chicha : Genre("Chicha")

public data object Children : Genre("Children")

public data object Chillout : Genre("Chillout")

public data object Chillwave : Genre("Chillwave")

public data object Chiptune : Genre("Chiptune")

public data object Christian : Genre("Christian")

public data object Christmas : Genre("Christmas")

public data object CityPop : Genre("City Pop")

public data object Classic : Genre("Classic")

public data object Classical : Genre("Classical")

public data object ClassicPop : Genre("Classic Pop")

public data object ClassicRock : Genre("Classic Rock")

public data object Club : Genre("Club")

public data object Comedy : Genre("Comedy")

public data object ContemporaryBlues : Genre("Contemporary Blues")

public data object ContemporaryClassical : Genre("Contemporary Classical")

public data object ContemporaryJazz : Genre("Contemporary Jazz")

public data object CoolJazz : Genre("Cool Jazz")

public data object Country : Genre("Country")

public data object CountryPop : Genre("Country Pop")

public data object CountryRock : Genre("Country Rock")

public data object Crossover : Genre("Crossover")

public data object Crunk : Genre("Crunk")

public data object Crunkcore : Genre("Crunkcore")

public data object CrustPunk : Genre("Crust Punk")

public data object CGothic : Genre("C Gothic")

public data object CHipHop : Genre("C Hip Hop")

public data object CJazz : Genre("C Jazz")

public data object CPop : Genre("C Pop")

public data object CRock : Genre("C Rock")

public data object CSka : Genre("C Ska")

public data object CTrance : Genre("C Trance")

public data object Dance : Genre("Dance")

public data object Dancehall : Genre("Dancehall")

public data object DarkAmbient : Genre("Dark Ambient")

public data object DarkCore : Genre("Dark Core")

public data object DarkPop : Genre("Dark Pop")

public data object DarkStep : Genre("Dark Step")

public data object DarkWave : Genre("Dark Wave")

public data object Deathcore : Genre("Deathcore")

public data object Deathgrind : Genre("Deathgrind")

public data object DeathMetal : Genre("Death Metal")

public data object DeepFunk : Genre("Deep Funk")

public data object DeepHouse : Genre("Deep House")

public data object DeepSoul : Genre("Deep Soul")

public data object Disco : Genre("Disco")

public data object DiscoHouse : Genre("Disco House")

public data object DixielandJazz : Genre("Dixieland Jazz")

public data object Doomcore : Genre("Doomcore")

public data object DoomMetal : Genre("Doom Metal")

public data object DooWop : Genre("Doo Wop")

public data object Downtempo : Genre("Downtempo")

public data object DreamPop : Genre("Dream Pop")

public data object Drone : Genre("Drone")

public data object DroneMetal : Genre("Drone Metal")

public data object DrumAndBass : Genre("Drum And Bass")

public data object Dub : Genre("Dub")

public data object Dubstep : Genre("Dubstep")

public data object DubHouse : Genre("Dub House")

public data object Dubtronica : Genre("Dubtronica")

public data object Ebm : Genre("Ebm")

public data object Edm : Genre("Edm")

public data object Electro : Genre("Electro")

public data object Electronic : Genre("Electronic")

public data object Electronica : Genre("Electronica")

public data object ElectronicDance : Genre("Electronic Dance")

public data object Electropop : Genre("Electropop")

public data object ElectroSwing : Genre("Electro Swing")

public data object ElectroWave : Genre("Electro Wave")

public data object Emo : Genre("Emo")

public data object EmoPop : Genre("Emo Pop")

public data object EmoRap : Genre("Emo Rap")

public data object Enka : Genre("Enka")

public data object Ethnic : Genre("Ethnic")

public data object EuroDance : Genre("Euro Dance")

public data object EuroDisco : Genre("Euro Disco")

public data object EuroHouse : Genre("Euro House")

public data object EuroPop : Genre("Euro Pop")

public data object EuroTrance : Genre("Euro Trance")

public data object Experimental : Genre("Experimental")

public data object ExperimentalNoise : Genre("Experimental Noise")

public data object ExperimentalPop : Genre("Experimental Pop")

public data object ExperimentalRock : Genre("Experimental Rock")

public data object FemaleVocalist : Genre("Female Vocalist")

public data object Fingerstyle : Genre("Fingerstyle")

public data object Flamenco : Genre("Flamenco")

public data object Folk : Genre("Folk")

public data object FolkHop : Genre("Folk Hop")

public data object FolkMetal : Genre("Folk Metal")

public data object FolkPop : Genre("Folk Pop")

public data object FolkPunk : Genre("Folk Punk")

public data object FolkRock : Genre("Folk Rock")

public data object Folktronica : Genre("Folktronica")

public data object FreakFolk : Genre("Freak Folk")

public data object Funk : Genre("Funk")

public data object Fusion : Genre("Fusion")

public data object FusionJazz : Genre("Fusion Jazz")

public data object FutureBass : Genre("Future Bass")

public data object FutureJazz : Genre("Future Jazz")

public data object Garage : Genre("Garage")

public data object GarageRock : Genre("Garage Rock")

public data object GlamMetal : Genre("Glam Metal")

public data object GlamPunk : Genre("Glam Punk")

public data object GlamRock : Genre("Glam Rock")

public data object Glitch : Genre("Glitch")

public data object GlitchHop : Genre("Glitch Hop")

public data object GloFi : Genre("Glo Fi")

public data object Goa : Genre("Goa")

public data object Gospel : Genre("Gospel")

public data object Gothic : Genre("Gothic")

public data object GothicMetal : Genre("Gothic Metal")

public data object GothicRock : Genre("Gothic Rock")

public data object Grime : Genre("Grime")

public data object Grindcore : Genre("Grindcore")

public data object Grunge : Genre("Grunge")

public data object GypsyJazz : Genre("Gypsy Jazz")

public data object GypsyPunk : Genre("Gypsy Punk")

public data object GFunk : Genre("G Funk")

public data object Hardcore : Genre("Hardcore")

public data object Hardstyle : Genre("Hardstyle")

public data object HardBop : Genre("Hard Bop")

public data object HardHouse : Genre("Hard House")

public data object HardRock : Genre("Hard Rock")

public data object HardTrance : Genre("Hard Trance")

public data object HeavyMetal : Genre("Heavy Metal")

public data object HipHop : Genre("Hip Hop")

public data object Horrorcore : Genre("Horrorcore")

public data object HorrorPunk : Genre("Horror Punk")

public data object House : Genre("House")

public data object Idm : Genre("Idm")

public data object Illbient : Genre("Illbient")

public data object Impressionist : Genre("Impressionist")

public data object Incidental : Genre("Incidental")

public data object Indie : Genre("Indie")

public data object IndieFolk : Genre("Indie Folk")

public data object IndiePop : Genre("Indie Pop")

public data object IndieRock : Genre("Indie Rock")

public data object Indietronic : Genre("Indietronic")

public data object Industrial : Genre("Industrial")

public data object IndustrialMetal : Genre("Industrial Metal")

public data object IndustrialRock : Genre("Industrial Rock")

public data object Instrumental : Genre("Instrumental")

public data object InstrumentalHipHop : Genre("Instrumental Hip Hop")

public data object InstrumentalRock : Genre("Instrumental Rock")

public data object Jazz : Genre("Jazz")

public data object JazzFusion : Genre("Jazz Fusion")

public data object JazzHop : Genre("Jazz Hop")

public data object Jumpstyle : Genre("Jumpstyle")

public data object Jungle : Genre("Jungle")

public data object JFusion : Genre("J Fusion")

public data object JGothic : Genre("J Gothic")

public data object JHipHop : Genre("J Hip Hop")

public data object JJazz : Genre("J Jazz")

public data object JPop : Genre("J Pop")

public data object JRock : Genre("J Rock")

public data object JSka : Genre("J Ska")

public data object JTrance : Genre("J Trance")

public data object Krautrock : Genre("Krautrock")

public data object KGothic : Genre("K Gothic")

public data object KHipHop : Genre("K Hip Hop")

public data object KJazz : Genre("K Jazz")

public data object KPop : Genre("K Pop")

public data object KRock : Genre("K Rock")

public data object KSka : Genre("K Ska")

public data object KTrance : Genre("K Trance")

public data object Latin : Genre("Latin")

public data object LatinJazz : Genre("Latin Jazz")

public data object LatinPop : Genre("Latin Pop")

public data object LatinRock : Genre("Latin Rock")

public data object LiquidFunk : Genre("Liquid Funk")

public data object LoFi : Genre("Lo Fi")

public data object Lounge : Genre("Lounge")

public data object Mambo : Genre("Mambo")

public data object Marimba : Genre("Marimba")

public data object Mathcore : Genre("Mathcore")

public data object MathRock : Genre("Math Rock")

public data object Medieval : Genre("Medieval")

public data object MedievalMetal : Genre("Medieval Metal")

public data object MedievalRock : Genre("Medieval Rock")

public data object MelodicMetal : Genre("Melodic Metal")

public data object MelodicMetalcore : Genre("Melodic Metalcore")

public data object MelodicTrance : Genre("Melodic Trance")

public data object Merengue : Genre("Merengue")

public data object Metal : Genre("Metal")

public data object Metalcore : Genre("Metalcore")

public data object Microhouse : Genre("Microhouse")

public data object Minimal : Genre("Minimal")

public data object ModalJazz : Genre("Modal Jazz")

public data object ModernClassical : Genre("Modern Classical")

public data object ModernRock : Genre("Modern Rock")

public data object Motown : Genre("Motown")

public data object Mpb : Genre("Mpb")

public data object Musical : Genre("Musical")

public data object MushroomJazz : Genre("Mushroom Jazz")

public data object Ndw : Genre("Ndw")

public data object NeoClassical : Genre("Neo Classical")

public data object NeoFolk : Genre("Neo Folk")

public data object NeoMedieval : Genre("Neo Medieval")

public data object NeoProgressive : Genre("Neo Progressive")

public data object NeoPsychedelic : Genre("Neo Psychedelic")

public data object NeoSoul : Genre("Neo Soul")

public data object Nerdcore : Genre("Nerdcore")

public data object NewAge : Genre("New Age")

public data object NewWave : Genre("New Wave")

public data object Newgrass : Genre("Newgrass")

public data object Noise : Genre("Noise")

public data object NoisePop : Genre("Noise Pop")

public data object NoiseRock : Genre("Noise Rock")

public data object NuDisco : Genre("Nu Disco")

public data object NuJazz : Genre("Nu Jazz")

public data object NuMetal : Genre("Nu Metal")

public data object NuSoul : Genre("Nu Soul")

public data object Nwa : Genre("Nwa")

public data object Nwobhm : Genre("Nwobhm")

public data object Nyhc : Genre("Nyhc")

public data object Oldies : Genre("Oldies")

public data object Opera : Genre("Opera")

public data object Orchestral : Genre("Orchestral")

public data object Oriental : Genre("Oriental")

public data object OutlawCountry : Genre("Outlaw Country")

public data object Pop : Genre("Pop")

public data object PopPunk : Genre("Pop Punk")

public data object PopRock : Genre("Pop Rock")

public data object PostBritpop : Genre("Post Britpop")

public data object PostDisco : Genre("Post Disco")

public data object PostGrunge : Genre("Post Grunge")

public data object PostHardcore : Genre("Post Hardcore")

public data object PostIndustrial : Genre("Post Industrial")

public data object PostMetal : Genre("Post Metal")

public data object PostPunk : Genre("Post Punk")

public data object PostRock : Genre("Post Rock")

public data object PowerMetal : Genre("Power Metal")

public data object PowerNoise : Genre("Power Noise")

public data object PowerPop : Genre("Power Pop")

public data object ProgressiveBluegrass : Genre("Progressive Bluegrass")

public data object ProgressiveCountry : Genre("Progressive Country")

public data object ProgressiveHouse : Genre("Progressive House")

public data object ProgressiveMetal : Genre("Progressive Metal")

public data object ProgressiveRock : Genre("Progressive Rock")

public data object ProgressiveTrance : Genre("Progressive Trance")

public data object Protopunk : Genre("Protopunk")

public data object Psychedelic : Genre("Psychedelic")

public data object PsychedelicPop : Genre("Psychedelic Pop")

public data object PsychedelicRock : Genre("Psychedelic Rock")

public data object PsyTrance : Genre("Psy Trance")

public data object Punk : Genre("Punk")

public data object PunkRock : Genre("Punk Rock")

public data object PFunk : Genre("P Funk")

public data object Ragga : Genre("Ragga")

public data object Rap : Genre("Rap")

public data object Rapcore : Genre("Rapcore")

public data object Reggae : Genre("Reggae")

public data object Reggaeton : Genre("Reggaeton")

public data object ReggaeFusion : Genre("Reggae Fusion")

public data object Renaissance : Genre("Renaissance")

public data object Rock : Genre("Rock")

public data object Rockabilly : Genre("Rockabilly")

public data object Rocksteady : Genre("Rocksteady")

public data object RockAndRoll : Genre("Rock And Roll")

public data object RootsReggae : Genre("Roots Reggae")

public data object RootsRock : Genre("Roots Rock")

public data object RAndB : Genre("R And B")

public data object Salsa : Genre("Salsa")

public data object Samba : Genre("Samba")

public data object Schlager : Genre("Schlager")

public data object Schranz : Genre("Schranz")

public data object Screamo : Genre("Screamo")

public data object Shoegaze : Genre("Shoegaze")

public data object SingerSongwriter : Genre("Singer Songwriter")

public data object Ska : Genre("Ska")

public data object Skacore : Genre("Skacore")

public data object SkaPunk : Genre("Ska Punk")

public data object Skatepunk : Genre("Skatepunk")

public data object Skiffle : Genre("Skiffle")

public data object Slowcore : Genre("Slowcore")

public data object SludgeMetal : Genre("Sludge Metal")

public data object SmoothJazz : Genre("Smooth Jazz")

public data object SoftRock : Genre("Soft Rock")

public data object Soul : Genre("Soul")

public data object Soundtrack : Genre("Soundtrack")

public data object SouthernRock : Genre("Southern Rock")

public data object SpaceRock : Genre("Space Rock")

public data object SpeedGarage : Genre("Speed Garage")

public data object SpeedMetal : Genre("Speed Metal")

public data object Speedcore : Genre("Speedcore")

public data object StageAndScreen : Genre("Stage And Screen")

public data object StonerMetal : Genre("Stoner Metal")

public data object StonerRock : Genre("Stoner Rock")

public data object SwampBlues : Genre("Swamp Blues")

public data object SwampPop : Genre("Swamp Pop")

public data object SwampRock : Genre("Swamp Rock")

public data object Swing : Genre("Swing")

public data object SymphonicMetal : Genre("Symphonic Metal")

public data object SymphonicRock : Genre("Symphonic Rock")

public data object Synthwave : Genre("Synthwave")

public data object SynthPop : Genre("Synth Pop")

public data object SynthPunk : Genre("Synth Punk")

public data object Tango : Genre("Tango")

public data object TechnicalMetal : Genre("Technical Metal")

public data object Techno : Genre("Techno")

public data object Techstep : Genre("Techstep")

public data object TechHouse : Genre("Tech House")

public data object TechTrance : Genre("Tech Trance")

public data object ThrashMetal : Genre("Thrash Metal")

public data object Trance : Genre("Trance")

public data object Trap : Genre("Trap")

public data object TribalHouse : Genre("Tribal House")

public data object TripHop : Genre("Trip Hop")

public data object TripRock : Genre("Trip Rock")

public data object TropicalHouse : Genre("Tropical House")

public data object Turntablism : Genre("Turntablism")

public data object TwoStep : Genre("Two Step")

public data object VocalHouse : Genre("Vocal House")

public data object VocalJazz : Genre("Vocal Jazz")

public data object VocalTrance : Genre("Vocal Trance")

public data object World : Genre("World")

public data object WorldFusion : Genre("World Fusion")