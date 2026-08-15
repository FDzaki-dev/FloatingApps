# CHANGELOG

## [2.0.0] — v2_Batch3 — Architecture Pivot
### Changed (breaking)
- Konsep app diperbaiki total: dari "bubble + catatan cepat lokal" menjadi
  **launcher yang menjalankan app lain di jendela freeform/floating nyata**
  (native, interaktif penuh), sesuai maksud awal — menjembatani floating
  window untuk app yang tidak mendukungnya secara bawaan.
- Integrasi Shizuku (`dev.rikka.shizuku:api/provider:13.1.5`) untuk privilese
  shell (`am start --windowingMode 5`, `settings put global
  force_resizable_activities 1`) tanpa root.
- MainActivity: checklist setup (Overlay + Shizuku) + pencarian & daftar
  semua app terpasang, tap untuk floating.
- FloatingBubbleService: panel bubble sekarang app picker (bukan catatan).
- Dihapus: fitur catatan cepat (SharedPreferences notes) — tidak lagi relevan.
- versionCode 2 / versionName 2.0.0.

## [1.0.0] — v1_Batch2 — Fix
### Fixed
- `.github/workflows/release.yml`: GitHub Actions menolak workflow karena
  `secrets` context dipakai langsung di `if:` step ("Unrecognized named-value:
  'secrets'") — ini memang tidak didukung GitHub Actions di posisi `if:`.
  Diperbaiki: cek keberadaan secret dipindah ke dalam shell script (`run:`)
  lewat `env:` + `if [ -n "$KS_B64" ]`, bukan YAML-level `if:`.

## [1.0.0] — v1_Batch1 — Initial Setup
### Added
- Struktur proyek Android (Kotlin, AGP 8.5.0, Gradle 8.7, minSdk 26 / targetSdk 34).
- MainActivity: alur izin overlay + izin notifikasi (API 33+) + toggle service.
- FloatingBubbleService: bubble draggable, panel catatan cepat, notifikasi
  foreground dengan tombol Stop.
- CrashHandler bawaan: MediaStore (API 29+) / app storage (API 26-28),
  FIFO retention 50 log, metadata lengkap.
- GitHub Actions workflow: build release APK (signed jika secrets tersedia,
  fallback debug-signed) dan publish otomatis ke GitHub Release.
- .gitignore & .gitattributes melindungi keystore dan build artifacts.
