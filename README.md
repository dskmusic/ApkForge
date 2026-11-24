<div align="center">

<img src="docs/icon.png" width="96" alt="ApkForge">

# ApkForge

### Turn your website or HTML project into a signed APK, right on your phone. No Android Studio needed.

[🇪🇸 Español](README.es.md) · [🇬🇧 English](README.md) · [🌐 GitHub](https://github.com/dskmusic/ApkForge)

![Android](https://img.shields.io/badge/Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
[![License](https://img.shields.io/badge/License-Apache%202.0-D22128?style=for-the-badge&logo=apache&logoColor=white)](LICENSE)

[**⬇ Download the latest version**](https://github.com/dskmusic/ApkForge/releases/latest/download/ApkForge.apk)

</div>

---

## ✨ What it does

- Builds a **signed, installable APK** from a **ZIP/APK**, a **folder** or an **online website**.
- App name, package, version, icon (custom, site favicon or default), orientation, fullscreen, theme…
- **Install**, **Share** (one or several) or **Save as…** from the list of generated APKs.
- Everything runs on the device: no server.

## 📥 Install

1. Download `ApkForge.apk` from [Releases](https://github.com/dskmusic/ApkForge/releases/latest).
2. Open it and allow "install unknown apps" for your browser or file manager.

> Official releases are signed with the public **Android debug key**. Fine for using and sharing the app, but it cannot be updated over a build signed with a different key.

## 🔑 Signing the APKs you generate

- **By default** a public debug keystore bundled in the app is used: works with zero setup.
- **To publish your apps**, import yours in **⚙ Settings → Change keystore…** (`.jks`, `.keystore` or `.p12`), enter alias and password **once** and they are remembered.

## 🛠 Build

```bash
git clone https://github.com/dskmusic/ApkForge.git
```

Open it with Android Studio (JDK 17) or run `./gradlew assembleRelease`.

- **Sign with your key locally:** copy `keystore.properties.example` to `keystore.properties` and fill it in. Without it, the debug key is used (this is what GitHub Actions does).
- **Your private keystore as the app default (optional, local only):** put your file at `app/src/main/assets/dskmusic.keystore` (git-ignored). If present, the app uses it instead of the debug one.
- **GitHub release:** *Actions → Build release APK → Run workflow*. Bump the version in `version.properties` first.

## 📄 License

[Apache 2.0](LICENSE) · Keep the [NOTICE](NOTICE) file and credit **ApkForge by DSK** if you redistribute or derive from this project.

Made with ❤ by [DSK](https://www.dskmusic.com/dsk_dev_redirect.php)
