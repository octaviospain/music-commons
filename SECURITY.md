# Security Policy

Music Commons is a Kotlin library published to Maven Central under `net.transgressoft`.
This document describes how to report a vulnerability, which versions receive security
fixes, the supply-chain controls in place, and the security considerations that apply to
consumers embedding the library.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues, pull
requests, or discussions.**

Report privately through GitHub's coordinated disclosure workflow:

1. Go to the [Security tab](https://github.com/octaviospain/music-commons/security).
2. Click **Report a vulnerability** to open a private advisory
   ([direct link](https://github.com/octaviospain/music-commons/security/advisories/new)).

When reporting, please include as much of the following as you can:

- The affected module(s) and version(s) (e.g. `music-commons-core:0.7.1`).
- A description of the vulnerability and its impact.
- Steps to reproduce, ideally a minimal proof-of-concept (sample code, a crafted audio
  file, iTunes `library.xml`, or `.m3u` playlist).
- Any known mitigations or workarounds.

### What to expect

- **Acknowledgement** within 5 business days.
- **Initial assessment** (severity, affected versions, whether it is in scope) within
  10 business days.
- Coordinated disclosure: we will agree on a disclosure timeline with you, aiming to
  publish a fix and a GitHub Security Advisory (with a CVE where warranted) before public
  disclosure. Please allow reasonable time for a fix to ship before disclosing publicly.

Credit is given to reporters in the published advisory unless you request otherwise.

## Supported Versions

Security fixes are applied to the latest published minor release. Older minor lines do not
receive backported fixes; upgrade to the latest release to stay protected.

| Version | Supported          |
| ------- | ------------------ |
| Latest minor release | :white_check_mark: |
| Older minor releases | :x: |

Because the library is distributed as immutable, GPG-signed artifacts on Maven Central,
remediation is always delivered as a new release rather than a patched existing artifact.

## Threat Model & Scope

Music Commons is a library, not a service. It runs entirely inside the consuming
application's JVM process and holds no credentials, opens no listening sockets, and owns no
persistence of its own (persistence is opt-in and consumer-configured). The primary attack
surface is therefore **untrusted input parsed on behalf of the host application.**

### In scope

- Parsing of untrusted **audio file metadata** (via JAudioTagger) across the reactive and
  media modules.
- Parsing of untrusted **iTunes `library.xml`** (plist) files during import
  (`ItunesImportService` / `ItunesLibraryParser`).
- Parsing of untrusted **`.m3u` playlists** during import (`M3uImportService`).
- **Audio decoding** of untrusted media through the JavaSound SPI decoders in
  `music-commons-media` (MP3, FLAC, OGG Vorbis/Opus, AAC/ALAC-M4A, WAV).
- **JSON deserialization** of entity state via kotlinx-serialization in the persistence
  modules.
- **Path handling** (including Windows path validation) when the library resolves,
  reads, or writes files supplied by the consumer.
- Vulnerabilities in dependencies that ship on a published module's `runtimeClasspath`.

### Out of scope

- Vulnerabilities in **build-time or plugin-classpath dependencies** that never ship to
  consumers (Gradle plugins, Sonar/JGit, test-only libraries). These are surfaced for
  visibility by the advisory OSV-Scanner job but are not part of the distributed artifact.
- Behavior of the **consumer-supplied SQLite JDBC driver** or any consumer-owned
  persistence backend. The persistence modules only provide mapping; the driver and
  storage are provided and configured by the consumer at runtime.
- Denial-of-service or resource exhaustion caused by the consumer feeding intentionally
  pathological input to a library that is, by design, given full trust of its host
  process. (Genuine parser bugs that crash or corrupt state on realistic input **are** in
  scope — report them.)
- Misconfiguration in the consuming application (e.g. exposing library operations over an
  untrusted network boundary without the consumer's own validation).

## Security Considerations for Consumers

- **Treat imported files as untrusted.** Audio files, iTunes libraries, and `.m3u`
  playlists are parsed as-is. If your application ingests files from untrusted sources,
  apply your own size/type limits and sandboxing appropriate to your threat model.
- **Deserialization.** The persistence modules deserialize JSON entity state. Only
  deserialize data your application produced or otherwise trusts; do not deserialize
  attacker-controlled JSON into the library's entity types.
- **File paths.** File-writing operations (metadata writes, JSON persistence) act on paths
  you supply. Validate and constrain paths before passing them in if any part is
  attacker-influenced.
- **Keep dependencies current.** Upgrade to the latest release promptly so you receive
  fixes for vulnerabilities discovered in transitive dependencies.

## Supply-Chain Security

The project applies defense-in-depth against supply-chain compromise:

- **Dependency verification.** `gradle/verification-metadata.xml` pins every resolved
  artifact to a SHA-256 checksum. Gradle enforces these on every build, defeating
  compromised-mirror and typosquat-at-resolution attacks. Regenerating this ledger is a
  documented, reviewed step on every dependency bump (see `CONTRIBUTING.md`).
- **Pinned GitHub Actions.** All CI actions are pinned to full commit SHAs, not mutable
  tags, preventing tag-repointing attacks on the build pipeline.
- **SBOM + vulnerability scanning.** A CycloneDX SBOM scoped to the shipped
  `runtimeClasspath` is generated and scanned weekly (and on demand) by
  [OSV-Scanner](https://github.com/google/osv-scanner); results are uploaded to GitHub
  Code Scanning. A separate advisory job scans build-time tooling without blocking merges.
- **Dependency Review.** Pull requests to `main` run GitHub's Dependency Review action,
  failing on newly introduced **high**-severity advisories.
- **Static analysis.** Every push to `main` is analyzed by SonarCloud
  (`octaviospain_music-commons`), including security-hotspot review.
- **Signed releases.** Maven Central artifacts are GPG-signed and published through an
  automated release workflow, so consumers can verify artifact provenance.

## Disclosure Policy

We follow coordinated disclosure. Once a fix is available, we publish a GitHub Security
Advisory describing the issue, affected versions, and the fixed version. We request that
reporters likewise refrain from public disclosure until the advisory is published and a
fixed release is available on Maven Central.
