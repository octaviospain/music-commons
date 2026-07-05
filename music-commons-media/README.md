# Music Commons Media

JavaFX-free audio playback engine and reactive waveform generation, built on the
`javax.sound.sampled` SPI. This module has no JavaFX dependency, so it can be used from headless
services and, through `music-commons-fx`, from desktop applications.

## Overview

Two independent capabilities live here:

- **Playback** — `CoreAudioItemPlayer` streams decoded PCM in bounded chunks to a
  `SourceDataLine`, driven by a small set of focused collaborators.
- **Waveforms** — `ScalableAudioWaveform` is a reactive waveform entity with on-demand amplitude
  generation and cached scaling; its JSON serializer ships alongside it (waveforms are JSON-only,
  so no separate persistence module is needed).

## Key Components

### `player`

- **`CoreAudioItemPlayer`** — headless player supporting MP3, FLAC, OGG (Vorbis and Opus),
  AAC/M4A, ALAC/M4A, and WAV via prioritized JavaSound SPI decoders. Constructable directly
  (`CoreAudioItemPlayer()`); it publishes `AudioItemPlayerEvent`s and exposes an
  `AudioItemPlayer.Status` lifecycle (including `STALLED` for streaming starvation).
- Internal collaborators keep the player a thin coordinator: `PlaybackPump` (pump loop and gain
  application), `StallDetector` (starvation detection and recovery), `DurationProber` (duration
  resolution with full-decode fallback for AAC/M4A), `PcmVolume` (in-place linear gain for
  8/16/24/32-bit PCM), and the per-format `PcmStreamSeeker` implementations (`FlacPcmStreamSeeker`,
  `Mp3PcmStreamSeeker`, `OggPcmStreamSeeker`).

### `waveform`

- **`ScalableAudioWaveform`** — reactive `AudioWaveform` implementation. Generates normalized
  amplitudes on demand and caches them with their display width, so same-width requests return
  without re-reading the audio file and height-only changes apply linear scaling from cache.

### `persistence.waveform`

- **`AudioWaveformMapSerializer`** — kotlinx-serialization map serializer for a lirp
  `JsonFileRepository`, preserving the cached width and amplitudes.

## Usage

```kotlin
import net.transgressoft.commons.media.player.CoreAudioItemPlayer
import java.time.Duration

val player = CoreAudioItemPlayer()
player.play(audioItem)
player.setVolume(0.8)
player.seek(Duration.ofSeconds(30))
player.stop()
player.dispose()
```

Per-codec seek precision and the full playback lifecycle are described on the
[Audio Playback](https://github.com/octaviospain/music-commons/wiki/Audio-Playback) wiki page.

## Dependencies

- **music-commons-api** — the `AudioItemPlayer`, `AudioWaveform`, and `ReactiveAudioItem` contracts
- **kotlinx-coroutines-core** — asynchronous waveform generation
- **JavaSound SPI providers** — pure-Java decoders for MP3, FLAC, OGG Vorbis/Opus, AAC/M4A, and ALAC

## License

Copyright © 2025-2026 Octavio Calleya Garcia.

This program is free software under the terms of the GNU General Public License version 3 or
(at your option) any later version.
