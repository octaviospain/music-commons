# Contributing to music-commons

Thank you for your interest in contributing to music-commons! This document provides guidelines and instructions for contributing to this project.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Enhancements](#suggesting-enhancements)
- [Pull Requests](#pull-requests)
- [Style Guidelines](#style-guidelines)

## Code of Conduct

This project adheres to a Code of Conduct that sets expectations for participation. By participating, you are expected to uphold this code. Please report unacceptable behavior to [your-email@example.com].

## How Can I Contribute?

### Reporting Bugs

Before creating a bug report, please check the existing issues to avoid duplicates. When you create a bug report, include as many details as possible:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Provide specific examples** (e.g., sample code that demonstrates the bug)
- **Describe the behavior you observed and what you expected to see**
- **Include relevant logs, stack traces, or screenshots**
- **Specify your environment** (OS, JDK version, Kotlin version, dependencies, etc.)

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion:

- **Use a clear and descriptive title**
- **Describe the problem your enhancement would solve**
- **Explain why this enhancement would be meaningful** to the project and its users
- **Provide specific examples of how this enhancement would be used**
- **List any alternatives you've considered**
- **Explain how the current functionality falls short**

## Pull Requests

### Process

1. Fork the repository
2. Create a new branch for your feature or bugfix (`git checkout -b feature/your-feature-name`)
3. Make your changes
4. Add or update tests as necessary
5. Run the tests to ensure all pass (`./gradlew test`)
6. Commit your changes using an appropriate commit message
7. Push to your branch (`git push origin feature/your-feature-name`)
8. Create a Pull Request against the main repository

### PR Requirements

All pull requests should:

- **Address a specific issue** or add a specific feature (create an issue first if none exists)
- **Include a problem statement** explaining what you're trying to solve and why it's meaningful
- **Include tests** that cover the new changes
- **Update documentation** if relevant
- **Pass all CI checks**
- **Be focused on a single objective** (don't mix unrelated changes)

## Development Guidelines

### Problem Statement Requirement

When submitting a PR, always include a clear problem statement that answers:

1. What problem are you trying to solve?
2. Why is this problem meaningful to the project?
3. How does your solution address the problem?
4. What alternatives did you consider?

Example:
```
Problem: The current JsonFileRepository implementation blocks the main thread during file writes, 
causing performance issues with large datasets.

Significance: This affects applications using the repository for high-frequency data changes, 
creating UI stutters in client applications.

Solution: Implemented non-blocking file IO using coroutines to move write operations off the main thread.

Alternatives considered: 
- Thread pool approach: More complex, would require managing thread lifecycle
- Batching writes: Would introduce latency in persistence
```

### Cross-Platform Path Handling

Windows-specific path validation (forbidden characters, reserved names, trailing dots/
spaces, MAX_PATH=260, `\\?\` long-path prefix) activates only when the library runs on a
Windows JVM. Linux and macOS consumers see no behavioral change -- the library remains a
good Linux citizen.

CI verifies behavior on `ubuntu-latest`, `windows-latest`, and `macos-latest` matrix legs
on every push and pull request. The next section documents the test-tag taxonomy and the
Gradle flags that gate platform-specific suites.

### Cross-Platform Testing

Audio decoding and playback now run on pure-JDK JavaSound SPI providers, so the library
itself is cross-platform. The test suite uses four Kotest tags to gate platform-specific
or environment-specific tests. Each tag is paired with a Gradle property that toggles its
filter:

| Tag                 | Purpose                                                      | Default      | Flag to flip default                |
| ------------------- | ------------------------------------------------------------ | ------------ | ----------------------------------- |
| `linux-only`        | Needs Linux-JDK path semantics (assertions written against Linux `Path.toString()` formatting of `\\?\…` prefixes) | included     | `-PexcludeLinuxOnly=true`           |
| `posix-only`        | Needs POSIX semantics (POSIX file permissions like `chmod`) -- works on Linux *and* macOS | included     | `-PexcludePosixOnly=true`           |
| `windows-only`      | Needs a real Windows host (NTFS, Windows JVM filesystem provider) | **EXCLUDED** | `-PincludeWindowsOnly=true`         |
| `audio-hardware`    | Needs a working audio output device (opens a real `SourceDataLine`; fragile in headless CI) | included (see note) | `-PexcludeAudioHardware=true`       |

> **Note on `audio-hardware` in the media module:** `music-commons-media` excludes
> `audio-hardware` **by default** so a bare `gradle :music-commons-media:test` is
> deterministic without relying on CI passing the flag. To run the audio-hardware smoke
> tests locally on a machine with a real output device, opt back in with
> `-PincludeAudioHardware=true`. A `verifyAudioHardwareExclusion` build check fails the
> default run if any audio-hardware-tagged test executed. Other modules keep the
> repository-wide "included" default shown above.

#### Examples

```bash
# Local Linux dev (everything except windows-only)
gradle build

# Local macOS dev (skip Linux-JDK-path-specific tests, no Windows tests)
gradle build -PexcludeLinuxOnly=true

# Local macOS dev (mirrors the macOS CI leg: POSIX kept, linux-only + audio-hardware skipped)
gradle build -PexcludeLinuxOnly=true -PexcludeAudioHardware=true

# Local Windows dev (no POSIX permission tests, no Linux-only tests, include Windows-only tests)
gradle build -PexcludeLinuxOnly=true -PexcludePosixOnly=true -PincludeWindowsOnly=true

# Headless CI on Linux (skip audio-hardware tests)
gradle build -PexcludeAudioHardware=true

# Run the media audio-hardware smoke tests locally (needs a real output device)
gradle :music-commons-media:test -PincludeAudioHardware=true

# Headless CI on Windows (full Windows leg)
gradle build -PexcludeLinuxOnly=true -PexcludePosixOnly=true -PincludeWindowsOnly=true -PexcludeAudioHardware=true
```

CI runs this matrix automatically on `ubuntu-latest`, `windows-latest`, and `macos-latest`; the Linux
leg additionally runs ktlintCheck and SonarQube.

#### Authoring Cross-Platform Tests

Where possible, prefer cross-platform tests over tagging. The codebase uses two patterns
that let tests verify Windows-validation logic on Linux/macOS hosts without skipping:

- **Jimfs in-memory paths.** `Jimfs.newFileSystem(Configuration.unix())` accepts paths
  containing characters that the host OS would reject (e.g., `|` on Windows). Use this
  to construct test inputs that are valid in the in-memory layer but flow through the
  real validator. See `WindowsPathValidatorTest` and `FXAudioItemTest` for examples.
- **`OsDetector.withOverriddenIsWindows(value) { ... }`.** Flips the library's
  `OsDetector.isWindows` flag for the duration of the block, exercising the
  Windows-validation branch on any host. Combine with Jimfs paths for inputs that
  contain Windows-forbidden chars.

Reach for a tag only when neither pattern works: when the test exercises a real OS-level
behavior that cannot be simulated (POSIX permissions, NTFS long-path handling, native
binary dependencies).

## Updating dependency verification metadata

This project commits `gradle/verification-metadata.xml`, which locks every resolved
artifact to a SHA-256 checksum. Gradle enforces these checksums automatically on every
invocation, defeating compromised-mirror and typosquat-at-resolution attacks.

Whenever you change `gradle/libs.versions.toml`, a plugin version in `build.gradle`, or
`gradle/wrapper/gradle-wrapper.properties`, regenerate the metadata in the same commit
as the dependency bump:

```bash
gradle --write-verification-metadata sha256 help
```

Review the diff — it should ONLY contain entries for the bumped artifact and its
transitives. Commit alongside the dependency change:

```bash
git add gradle/libs.versions.toml gradle/verification-metadata.xml
```

**Poisoned-cache warning.** If checksums change for artifacts you did not bump, your
local Gradle cache may be poisoned (a previously downloaded jar was tampered with at
rest). Wipe the cache and regenerate against a fresh download:

```bash
gradle --stop
rm -rf ~/.gradle/caches/modules-2
gradle --refresh-dependencies --write-verification-metadata sha256 \
       compileKotlin compileTestKotlin build cyclonedxBom
```

The full regeneration walk (`compileKotlin compileTestKotlin build cyclonedxBom`) is
required after a full cache wipe so that plugin marker artifacts and KSP-resolved
artifacts are captured in the metadata.

## Style Guidelines

- Use the provided `.editorconfig` and Kotlin style guide settings
- Variable and function names should be descriptive and follow camelCase convention
- Keep functions focused on a single responsibility
- Add *meaningful* documentation comments to public APIs
- Use Kotlin features appropriately (extension functions, lambdas, etc.)
- Avoid unnecessary abbreviations

### Code Formatting

This project uses [ktlint](https://pinterest.github.io/ktlint/) to ensure consistent code formatting. Run this command to check for formatting issues:

```bash
./gradlew ktlintCheck
```

To automatically fix formatting issues:

```bash
./gradlew ktlintFormat
```

## Questions?

If you have any questions or need help with the contribution process, please don't hesitate to open an issue asking for guidance.