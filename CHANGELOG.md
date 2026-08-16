# CHANGELOG
> Terbaru selalu di paling atas.

## [2.4.1] — v2_Batch7-Hotfix1 — Crash Fix (Launch Verification)
Ditemukan dari crash log bawaan app (Crash Logger), dianalisa tanpa perlu
Logcat/ADB.

### Fixed
- **Crash total saat launch** sejak v2.4.0: `ExceptionInInitializerError` →
  `PatternSyntaxException` di `TaskIdParser.kt` — regex `t(\d+)}` punya `}`
  tanpa escape, ditolak regex engine ICU Android 16 (desktop JVM toleran,
  device tidak). Fix: `t(\d+)\}`.
- Hardening: panggilan `TaskIdParser.findTaskId()` di `LaunchVerification`
  dan `FloatingWindowController.bringToFront()` sekarang dibungkus
  try-catch, konsisten dengan pola defensive-wrapping di seluruh codebase —
  mencegah kelas kegagalan sama terulang di masa depan.

### Changed
- `versionCode 7` / `versionName "2.4.1"`.

## [2.4.0] — v2_Batch7 — True Bring-to-Front, Window Close, Session Persistence
Atomic Change (15 file) menjawab P0 #4 (penuh), separuh P0 #3 (close +
switch, belum resize/reposition/maximize), dan P0 #7 (persistensi riwayat
sesi, cakupan jujur — bukan posisi/ukuran window) dari
`FloatingApps_v2_2_0_Final_Gap_Audit.md`. **Bukan** rewrite total — fondasi
Batch6 (session registry, capability manager, launch verification) tidak
dibongkar. Detail lengkap di `PROJECT_STATE.md`.

### Added
- `core/window/`: `TaskIdParser` (ekstraksi taskId best-effort dari
  dumpsys, 3 pola regex fallback), `FloatingWindowController`
  (`bringToFront()` — relaunch + verifikasi ulang taskId sebelum-vs-sesudah,
  bukan asumsi buta; `close()` — via `am force-stop`).
- `core/session/SessionPersistence` — riwayat sesi ke SharedPreferences,
  FIFO cap 20, dipulihkan sebagai state `RESTORED` baru saat proses start
  (BUKAN posisi/ukuran window — app tidak memiliki rendering window itu).
- String baru: `bringing_to_front`, `closed_success`, `closed_failed`,
  `favorites_long_press_hint`.
- Long-press pada Favorit yang sedang floating sekarang menutup app
  (`am force-stop`, lewat `FloatingWindowController`).

### Fixed
- Favorit yang tap ulang pada app yang sudah floating sebelumnya SELALU
  relaunch buta (harap OS men-dedupe window). Sekarang dicek dulu lewat
  `FloatingSessionManager.sessionForApp()` — kalau app sudah
  `VERIFIED_FLOATING`, di-route ke `bringToFront()` yang memverifikasi
  taskId sebelum/sesudah, bukan tebakan.
- Registry sesi sebelumnya reset total tiap restart proses. Sekarang riwayat
  sesi (bukan live state) bertahan lintas restart lewat `SessionPersistence`.

### Changed
- `versionCode 6` / `versionName "2.4.0"`. Tidak ada dependency baru.

### Sengaja belum (lihat PROJECT_STATE.md untuk alasan penuh)
- P0 #3 sisa: resize, reposition, maximize internal — butuh control-chrome
  overlay per-window, subsistem UI tersendiri.
- Indikator visual "sedang floating" di Favorit/app list.

Detail desain, alasan tiap keputusan, dan daftar item yang SENGAJA belum
dikerjakan: lihat `PROJECT_STATE.md` bagian "STATUS TERKINI — v2.4.0 /
Batch7" (paling atas file itu).

## [2.3.0] — v2_Batch6 — Session Registry, Capability Detection, Launch Verification
Atomic Change (16 file) menjawab P0 #1, #2, #5, #6, #8 dari
`FloatingApps_v2_2_0_Final_Gap_Audit.md`, mengikuti urutan kerja yang
direkomendasikan audit (Session Manager → Capability Manager → Launch
Verification). **Bukan** rewrite total; P0 #3/#4/#7 dan semua P1/P2
sengaja belum dikerjakan — detail lengkap di `PROJECT_STATE.md`.

### Added
- `core/session/`: `FloatingSessionManager` (registry in-memory,
  `StateFlow<Map<String, FloatingSession>>`), `FloatingSessionState` (enum
  `LAUNCHING/VERIFIED_FLOATING/FAILED_NOT_FLOATING/FAILED_LAUNCH/CLOSED`),
  `LaunchVerification` (poll `dumpsys activity activities` best-effort
  untuk konfirmasi windowingMode freeform), `FloatingLaunchCoordinator`
  (satu pintu masuk launch, dipakai MainActivity & FloatingBubbleService).
- `core/capability/`: `CapabilityManager` + `SystemReadiness` (enum
  `READY/DEGRADED/ACTION_REQUIRED/UNSUPPORTED/ERROR`) — gabungkan Overlay +
  Shizuku + Battery + deteksi Freeform (statis via
  `FEATURE_FREEFORM_WINDOW_MANAGEMENT` + empiris dari hasil
  LaunchVerification nyata) jadi satu snapshot readiness.
- `ShizukuShellManager.dumpActivityState()` — wrapper dumpsys untuk probe
  verifikasi.
- String baru: `launch_not_floating_warning`, `freeform_unsupported_warning`.

### Fixed
- Sebelumnya "command shell tidak error" dianggap = "berhasil floating".
  Sekarang ada pembeda eksplisit lewat verifikasi async — user diberi tahu
  (toast, sekali per session) kalau app terbuka tapi TIDAK dalam mode
  floating.
- Duplikasi logic launch antara MainActivity & FloatingBubbleService
  (dua copy hampir identik) disatukan lewat `FloatingLaunchCoordinator`.

### Changed
- `versionCode 5` / `versionName "2.3.0"`. Tidak ada dependency baru.

Detail desain, alasan tiap keputusan, dan daftar item yang SENGAJA belum
dikerjakan: lihat `PROJECT_STATE.md` bagian "STATUS TERKINI — v2.3.0 /
Batch6" (paling atas file itu).

## [2.2.0] — v2_Batch5 — Scroll Fix, APK Naming, Docs Reorder, Modular Anti-Crash Architecture
### Fixed
- **Beranda tidak bisa di-scroll ke bawah**: root layout `activity_main.xml`
  diganti dari `LinearLayout` polos jadi `NestedScrollView` — sebelumnya
  tidak ada scroll container untuk seluruh halaman sama sekali, hanya
  `rvApps` yang scroll internal, sehingga di layar pendek header bisa
  overflow/terpotong tanpa cara menggulir ke sana.
- **Nama file APK GitHub Release generik**: `release.yml` tidak lagi publish
  `app-release.apk`. APK sekarang di-rename ke
  `FloatingApps-v<versionName>-build<run_number>.apk` sebelum dipublish.
- **Dokumentasi info lama di atas, info baru di bawah**: `PROJECT_STATE.md`
  dirombak jadi "info terbaru di paling atas"; `README.md` dapat section
  Status Terkini baru di paling atas.

### Added — Arsitektur Modular Floating App (anti-crash, production-ready)
- `core/overlay/`: `OverlayPermissionHelper`, `OverlayWindowController`
  (satu titik terpusat untuk semua operasi `WindowManager`, anti-crash).
- `core/power/BatteryOptimizationHelper`: exemption Doze standar + deep-link
  best-effort ke Autostart 5 OEM populer.
- `core/touch/`: `ScreenMetricsProvider` (boundary bounds + clamp),
  `SnapEdgeAnimator` (snap-to-edge), `FloatingDragTouchListener`
  (drag+boundary+snap+tap-vs-drag pakai touch slop platform).
- `core/ipc/BubbleStateBus`: `StateFlow` in-process untuk Service↔Activity
  state, dikonsumsi lifecycle-safe lewat `repeatOnLifecycle`.
- `FloatingBubbleService`: `onConfigurationChanged` (re-clamp saat rotasi),
  `onTaskRemoved` no-op sengaja (process survival), `CoroutineScope`
  service-scoped yang di-cancel di `onDestroy`.
- `MainActivity`: Langkah 3 baru di UI setup — "Latar Belakang & Baterai".
- Dependency baru: `androidx.lifecycle:lifecycle-runtime-ktx`,
  `kotlinx-coroutines-android`. `versionCode 4` / `versionName 2.2.0`.
- Manifest: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

Detail desain & alasan tiap keputusan: lihat `PROJECT_STATE.md` bagian
"STATUS TERKINI — v2.2.0 / Batch5" (paling atas file itu).

## [2.1.0] — v2_Batch4 — Crash Fix + Favorites Slots
### Fixed
- **Crash saat buka panel bubble** (`InflateException` di
  `layout_app_list_item` → `UnsupportedOperationException: Failed to resolve
  attribute`). Akar masalah: `FloatingBubbleService` meng-inflate layout
  pakai Service context mentah (tanpa tema), jadi `?attr/selectableItem
  Background` gagal di-resolve. Diperbaiki dengan `ContextThemeWrapper(this,
  R.style.Theme_FloatingApps)` di `addBubble()` dan `addPanel()`. Ditemukan
  langsung dari crash log yang dikirim user — bukti crash logger bawaan
  berfungsi seperti dirancang.

### Added
- **Slot Favorit** (6 slot): tekan & tahan aplikasi di daftar untuk pin;
  tap slot terisi = langsung floating tanpa perlu cari lagi. Tersedia di
  MainActivity dan panel bubble, tersimpan di SharedPreferences (bersama).
- **Hint minimize**: jendela freeform native sudah punya kontrol minimize/
  maximize/close di title bar bawaan OS. Tap ulang app yang sama (termasuk
  dari slot Favorit) membawanya kembali ke depan jika sedang berjalan —
  jadi Favorit sekaligus berfungsi sebagai cara cepat "un-minimize".

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
