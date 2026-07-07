# Third-Party Licenses

Music Commons is distributed under the [GNU General Public License v3.0](LICENSE) (GPL-3.0).

The `music-commons-media` module bundles the following third-party SPI (Service Provider Interface)
decoders at runtime. These libraries are loaded by JavaSound's `AudioSystem` service-loader
mechanism to provide audio decoding for MP3, FLAC, OGG Vorbis, AAC/M4A, ALAC, Opus, and
WAV resampling. All of them are compatible with GPL-3.0 as described below.

## Bundled SPI Decoders

| Dependency | Version | License | GPL-3.0 Compatible | Notes |
|---|---|---|---|---|
| `com.googlecode.soundlibs:mp3spi` | 1.9.5.4 | LGPL-2.1 | Yes | Weak-copyleft; FSF confirms LGPL-2.1 is compatible with GPL-3.0 |
| `com.googlecode.soundlibs:tritonus-share` | 0.3.7.4 | LGPL-2.1 | Yes | See compatibility analysis below |
| `com.tianscar.javasound:javasound-flac` | 1.4.1 | LGPL-2.1 AND Apache-2.0 | Yes | Both licenses are GPL-3.0 compatible |
| `com.tianscar.javasound:javasound-vorbis` | 2.1.0 | LGPL-2.0 AND Xiph.Org BSD | Yes | Both permissive/weak-copyleft; GPL-3.0 compatible |
| `com.tianscar.javasound:javasound-aac` | 0.9.8 | Apache-2.0 | Yes | FSF confirms Apache-2.0 is compatible with GPL-3.0 |
| `com.tianscar.javasound:javasound-alac` | 0.2.3 | BSD-3-Clause | Yes | Permissive license; fully compatible |
| `com.tianscar.javasound:javasound-resloader` | 0.1.3 | Apache-2.0 | Yes | Stream reload utility used internally by the Tianscar SPI providers |
| `de.sfuhrm:jaad` | 0.8.7 | Public Domain | Yes | No restrictions; compatible with any license |
| `io.github.jseproject:jse-spi-opus` | 1.1.0 | Xiph.Org Variant of BSD | Yes | Permissive BSD variant; GPL-3.0 compatible |

## tritonus-share LGPL-2.1 / GPL-3.0 Compatibility Analysis

`tritonus-share` (version 0.3.7.4, `com.googlecode.soundlibs:tritonus-share`) is licensed under
the GNU Lesser General Public License version 2.1 (LGPL-2.1). Music Commons is licensed under
GPL-3.0. The LGPL-2.1 / GPL-3.0 interaction is compatible for the following reasons:

1. **Weak copyleft permits linking**: LGPL-2.1 is a "weak copyleft" license explicitly designed to
   permit linking with programs under other licenses, including GPL-3.0, without imposing the LGPL
   terms on the combined work. The combined program becomes GPL-3.0 as a whole.

2. **"Or any later version" clause**: The standard LGPL-2.1 license text includes the clause
   "either version 2.1 of the License, or (at your option) any later version". This clause allows
   the library to be treated as LGPL-3.0 (which is built on top of GPL-3.0) when that is more
   permissive for the combination.

3. **FSF explicit guidance**: The Free Software Foundation explicitly states that LGPL-2.1 is
   compatible with GPL-3.0. See the [GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html) entry
   "Can I use GPL-incompatible libraries in my GPL'd programs if I use the Library GPL?".

**Conclusion**: `tritonus-share` (LGPL-2.1) is compatible with the project's GPL-3.0 license and
may be combined with it. Compatibility does not extinguish the LGPL's own obligations, which
continue to apply to the `tritonus-share` library itself: its copyright and license notices must
be preserved, its corresponding source (or a written offer for it) must remain available, and
recipients must be able to relink or replace it with a modified version. music-commons satisfies
these by depending on the unmodified, publicly published Maven Central artifact and linking it
dynamically at runtime — it is not modified or statically embedded — so the source-availability
and relinking rights are preserved upstream. No LGPL obligation is triggered that GPL-3.0 does not
already accommodate.

## Other Dependencies

The library also depends on [lirp](https://github.com/octaviospain/lirp),
[JAudioTagger](https://www.jthink.net/jaudiotagger/), [Kotlin](https://kotlinlang.org/),
[Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines), and other libraries.
Their licenses are recorded in the respective project repositories and are compatible with GPL-3.0.
