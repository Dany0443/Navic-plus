# Sonora

A modern, privacy-friendly Material 3 music player for Android built with Kotlin Multiplatform.

![License](https://img.shields.io/github/license/Dany0443/Sonora)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-blue)
![Material3](https://img.shields.io/badge/Material-3-blueviolet)

---

## Overview

Sonora is a feature-rich, high-performance music application for Android designed around modern Material 3 guidelines and privacy-conscious software principles. Built using Kotlin Multiplatform, Sonora offers a fluid and responsive listening experience whether you are streaming from a remote Subsonic-compatible server or playing files stored locally on your device.

---

## Key Features

- **Material 3 Interface**: Clean design built with Jetpack Compose, featuring dynamic color (Material You), smooth micro-animations, and fluid transitions.
- **Subsonic & Navidrome Integration**: Full remote library support for Subsonic and Navidrome servers.
- **Local Media Playback**: High-performance local audio scanner and device library management.
- **Insights & Listening Statistics**: Comprehensive stats dashboard featuring heatmaps, top artists, top tracks, listening time, and streak tracking.
- **Multi-Provider Scrobbling**: Real-time scrobbling and history synchronization with Last.fm and ListenBrainz.
- **Synchronized Lyrics**: Embedded and remote lyrics support with timestamped line tracking.
- **Offline Mode & Caching**: Cache remote tracks and albums locally for seamless offline playback.
- **Queue & Playlist Management**: Advanced queue control, smart playlists, shuffle modes, and repeat options.
- **Modern Audio Engine**: Powered by Android's Media3 / ExoPlayer stack with gapless playback, crossfade, and hardware audio offloading support.
- **Kotlin Multiplatform Architecture**: Decoupled domain and data layers engineered for modularity and high performance.

---

## Insights & Statistics

Sonora includes a dedicated **Insights** engine designed to give you detailed analytics into your listening habits:

- **Provider Data Synchronization**: Effortlessly import and synchronize listening history from supported platforms:
  - **Last.fm**
  - **ListenBrainz**
- **Rich Visualizations**: Explore interactive listening heatmaps, top artist and track rankings, listening streaks, and activity trends.
- **Extensible Architecture**: Built on a provider-agnostic framework, allowing future listening statistics providers to be added seamlessly.

---

## Installation

1. Go to the **Releases** section of the repository.
2. Download the latest `app-release.apk` asset.
3. Install the APK on your Android device (enable *Install from unknown sources* in system settings if prompted).

> [!NOTE]
> Future releases are signed with the official Sonora application signing key.

---

## Building from Source

### Prerequisites

- **Android Studio** (Jellyfish 2024.1.1 or newer recommended)
- **JDK 21**
- **Gradle** (managed via the included wrapper)

### Build Commands

Clone the repository and run the Gradle tasks:

```bash
# Build the debug APK
./gradlew assembleDebug

# Build and install directly onto a connected Android device
./gradlew installDebug
```

---

## Contributing

Contributions are welcome! Whether you are fixing a bug, suggesting a feature, improving documentation, or discussing architecture:

- **Issues**: Report bugs or request enhancements via GitHub Issues.
- **Pull Requests**: Submit clean, well-tested PRs targeted at the main branch.
- **Discussions**: Share ideas and join community conversations.

---

## License

Sonora is distributed under the terms of the **GNU General Public License v3.0** (`GPL-3.0-only`). See the `LICENSE` file for details.

---

## Credits

Sonora originally began as a fork of [Navic](https://github.com/ssalggnikool/Navic). We extend our gratitude to the original authors and contributors of [Navic](https://github.com/ssalggnikool/Navic) for establishing the foundation upon which Sonora has been developed.