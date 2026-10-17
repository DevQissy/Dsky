# Dsky — DNS Service

<p align="center">
  <strong>Stable. Fast. Seamless.</strong>
  <br />
  An Android DNS application with encrypted DNS connectivity and a redesigned user experience.
</p>

<p align="center">
  <a href="https://github.com/Jigsaw-Code/Intra">Original Project: Intra</a>
  ·
  <a href="https://github.com/DevQissy/Dsky/releases/latest">Latest Release</a>
  ·
  <a href="https://github.com/DevQissy/Dsky/issues">Report an Issue</a>
</p>

---

## About

**Dsky** is an Android DNS application originally based on [Intra](https://github.com/Jigsaw-Code/Intra), an open-source project by Jigsaw.

Since its original foundation, Dsky has undergone substantial development, including a migration of the Android application from Java to Kotlin and a complete redesign of its user interface.

The project combines an independently rebuilt Android interface with a modified Go backend derived in part from Intra.

Dsky is an independent project and is not an official release of, or affiliated with, Jigsaw.

## Project Evolution

### Kotlin Migration

The original Android application was written in Java. Dsky's Android implementation was migrated to Kotlin as part of its development.

### Complete UI Redesign

Dsky's user interface was rebuilt from scratch. It has its own visual design, navigation, and application experience rather than using Intra's original XML-based interface.

The original Intra UI is not used as Dsky's interface.

### Modified Go Backend

Dsky retains a Go backend derived in part from Intra. This backend has been modified for use within Dsky.

The original Intra project remains the source of the upstream code from which those backend components were derived. See the acknowledgements and licensing sections below.

## Features

- **Encrypted DNS:** DNS-over-HTTPS connectivity.
- **DNS Server Selection:** Choose from available DNS servers.
- **Split Tunneling:** Configure which applications use the DNS service.
- **Server Filtering:** Filter available servers using supported criteria, including IP capabilities.
- **Network Customization:** Select DNS configurations according to your needs.
- **Custom Android UI:** A redesigned interface developed specifically for Dsky.

Feature availability may vary by release, device, and configuration.

## Download

Download the latest available version from the official GitHub Releases page.

**[Download Dsky](https://github.com/DevQissy/Dsky/releases/latest)**

If an APK is available, download the release file to your Android device and follow the installation instructions provided by Android.

### Installation Notice

> [!IMPORTANT]
> **Installation warnings and compatibility**
>
> Android may display security warnings or block APK installation depending on your device, Android version, security settings, or the source of the downloaded file.
>
> A warning does not automatically prove that an application is malicious, but the absence of a warning does not guarantee that it is safe either.
>
> Download Dsky only from the official release page. Verify the source and integrity of the APK before installing it. If Google Play Protect or Android identifies a potential threat, do not bypass the warning unless you have independently verified the file and understand the risk.
>
> If installation fails, check Android compatibility, available storage, installation settings, and whether an existing version uses a different signing certificate.

## Build from Source

### Requirements

- Android Studio
- A compatible JDK
- Android SDK and required platform/build tools
- Git
- Go, if required by the backend build configuration
- Any additional dependencies required by the current project setup

### Clone the Repository

```bash
git clone https://github.com/DevQissy/Dsky.git
cd Dsky
```

Open the project in Android Studio and allow Gradle to synchronize the dependencies.

### Build a Debug APK

On Windows, run:

```powershell
.\gradlew.bat assembleDebug
```

On Linux or macOS, run:

```bash
./gradlew assembleDebug
```

If the build completes successfully, the debug APK is normally generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The exact build requirements may depend on the current Gradle configuration and Go backend integration.

### Release Builds

A release build may require additional configuration, including signing credentials and backend build dependencies.

Never commit private signing keys, keystore passwords, API tokens, or other confidential credentials to the repository.

## Acknowledgements

### Intra by Jigsaw

Dsky acknowledges **[Intra](https://github.com/Jigsaw-Code/Intra)** by Jigsaw as the original project on which its development was based.

- **Original repository:** https://github.com/Jigsaw-Code/Intra
- **Original license:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

The Go backend includes code derived in part from Intra. Applicable copyright notices, license terms, attribution notices, and notices identifying modified files must be retained as required by Apache License 2.0.

Dsky's Android UI was rebuilt from scratch and does not use Intra's original XML-based UI as its interface.

Dsky is not an official Intra release and is not affiliated with or endorsed by Jigsaw.

## Licensing

This repository contains code with different licensing terms. The applicable license depends on the origin of each component.

### Original Dsky Contributions

Unless a separate license is explicitly provided, original Dsky code and assets owned by the Dsky author are **All Rights Reserved**.

No general permission is granted to reuse, modify, redistribute, or commercially exploit those original contributions.

### Intra-Derived Backend

The portions of the Go backend derived from Intra remain subject to the applicable terms of the **Apache License 2.0**.

The required upstream license and attribution notices must be preserved. Modified upstream files must be identified as required by the license.

The restrictions stated for original Dsky contributions do not revoke or limit rights granted under Apache License 2.0 for the Intra-derived portions.

### Third-Party Dependencies

Dsky may use additional third-party libraries and components. Those components remain subject to their respective licenses.

Refer to dependency declarations, license files, and `THIRD_PARTY_NOTICES.md` for additional information.

## Disclaimer

Dsky is provided on an "as is" basis, without warranties of any kind, to the extent permitted by applicable law.

DNS server availability, response times, and reliability depend on server operators, network conditions, and other factors beyond the application's control.

Dsky does not guarantee uninterrupted connectivity, complete anonymity, or protection against every form of tracking, surveillance, or network interference.

Users are responsible for complying with applicable laws and the terms governing the services they access.

## Issues and Feedback

Found a bug or have a suggestion?

Please open an issue in the repository:

**[Report an Issue](https://github.com/DevQissy/Dsky/issues)**

When reporting a problem, include relevant details such as the app version, Android version, and steps to reproduce the issue. Do not include passwords, private keys, or other sensitive information.

---

<p align="center">
  Developed by <a href="https://github.com/DevQissy">Dev Qissy</a>
</p>
