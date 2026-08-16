# PROJECT_STATE.md — Floating Apps
> Info terbaru SELALU di paling atas file ini. Jangan tambah entri baru di
> bawah — sisipkan di atas "Known-Fix Log" section, entri lama tetap ada
> di bawahnya untuk histori.

## 🟢 STATUS TERKINI — v2.4.0 / Batch7 (2026-08-16)
**Confidence Rating: 94%**

Batch ini adalah **Atomic Change** (15 file > limit 10 — lihat alasan di
bawah) yang menjawab **P0 #4** (True Bring-to-Front, penuh), separuh **P0
#3** (True Window Management: close + switch/raise; resize/reposition/
maximize BELUM), dan **P0 #7** (Persistent Floating State, cakupan jujur —
lihat batasan di bawah), melanjutkan persis "Urutan Kerja yang Benar" di
`FloatingApps_v2_2_0_Final_Gap_Audit.md` (langkah 4: Window/Task State
Layer). **BUKAN** rewrite total. Fondasi Batch6 (`FloatingSessionManager`,
`CapabilityManager`, `LaunchVerification`, `FloatingLaunchCoordinator`)
tidak dibongkar — batch ini menambah layer `core/window/` di atasnya.

**Kenapa Atomic**: bring-to-front butuh session registry TAHU task ID
existing (`FloatingSessionManager` +field baru) DAN kemampuan mengecek ulang
task ID setelah re-launch (`core/window/` baru) DAN `FloatingLaunchCoordinator`
harus tahu kapan memilih raise-vs-relaunch — tiga hal ini saling bergantung
langsung, tidak bisa dipecah tanpa menyisakan state antara yang salah.
Kedua entry point (`MainActivity` & `FloatingBubbleService`) juga harus
di-update bersamaan lagi, sama seperti Batch6, demi alasan yang sama
(mencegah drift).

### Ringkasan perubahan
1. **`core/window/` (baru)** — jawaban P0 #4 penuh + separuh P0 #3:
   - `TaskIdParser.kt` — ekstraksi task ID best-effort dari teks dumpsys
     yang sama dipakai `LaunchVerification` (3 pola regex fallback,
     terdokumentasi sebagai heuristik, BUKAN kontrak stabil — prinsip
     kejujuran yang sama dengan `LaunchVerification`/
     `BatteryOptimizationHelper`).
   - `FloatingWindowController.kt` — `bringToFront()`: re-issue command
     freeform yang sama untuk session yang SUDAH `VERIFIED_FLOATING`, lalu
     verifikasi ulang task ID sebelum-vs-sesudah (bukan asumsi buta seperti
     sebelumnya). Task ID sama = `Confirmed`; beda = `PossibleDuplicate`
     (kemungkinan window baru bukan window lama yang terangkat — dicatat di
     session, TIDAK dianggap gagal karena window tetap ada); tidak ketemu
     sama sekali = `NotFound`. `close()`: `am force-stop` — blunt (kill
     seluruh proses app, bukan cuma satu window) tapi command AM yang
     stabil lintas versi Android, TIDAK seperti command task-scoped
     (`am task`/`am stack`) yang sintaksnya bergeser sejak refactor
     multi-window Android 10 — inilah alasan resize/reposition/maximize
     BELUM diimplementasikan di batch ini (lihat "Sengaja TIDAK
     dikerjakan" di bawah).
2. **`core/session/SessionPersistence.kt` (baru)** — jawaban P0 #7, cakupan
   JUJUR: hanya menyimpan riwayat sesi (package/activity/label/state/
   timestamp) ke SharedPreferences lintas-restart proses, FIFO cap 20 entri
   (prinsip sama seperti retensi crash log). **TIDAK** menyimpan posisi/
   ukuran jendela — app ini tidak memiliki rendering window tsb (OS/native
   freeform chrome yang punya), jadi tidak ada posisi/ukuran lokal untuk
   disimpan sampai layer resize/reposition (P0 #3 sisa) ada.
3. **`FloatingSessionState.kt`** (parsial): tambah `RESTORED` — sesi yang
   dimuat dari disk saat proses baru mulai, SENGAJA dibedakan dari
   `VERIFIED_FLOATING` karena setelah restart proses tidak ada cara
   memastikan window itu masih hidup tanpa cek shell baru. Ini juga
   sekaligus melanjutkan P0 #6 (Failure & Recovery State) ke kasus restart.
4. **`FloatingSession.kt`** (parsial): tambah `taskId: Int?` (metadata dari
   `TaskIdParser`, tidak pernah ditebak) dan `duplicateSuspected: Boolean`.
5. **`FloatingSessionManager.kt`** (parsial): `init(applicationContext)` —
   dipanggil sekali dari `App.onCreate`, menyimpan context proses (bukan
   Activity/Service — anti-leak) + restore riwayat sesi jadi `RESTORED`.
   Tambah `sessionForApp()` (lookup live-verified session per app — dasar
   bring-to-front), `onTaskIdResolved()`, `onBroughtToFront()`,
   `onDuplicateWindowSuspected()`. Setiap transisi sekarang otomatis
   `persist()` ke `SessionPersistence` kalau context sudah di-attach.
6. **`LaunchVerification.kt`** (parsial): saat freeform terverifikasi,
   parse & catat task ID lewat `TaskIdParser` (best-effort, tidak pernah
   memblokir hasil verifikasi utama kalau gagal parse).
7. **`FloatingLaunchCoordinator.kt`** (parsial): sebelum treat tap sebagai
   launch baru, cek `FloatingSessionManager.sessionForApp()` dulu — kalau
   app sudah `VERIFIED_FLOATING` sesi berjalan ini, di-route ke
   `FloatingWindowController.bringToFront()`, bukan relaunch buta seperti
   sebelumnya (perilaku lama Favorit yang jadi keluhan #4 di audit). Outcome
   baru: `LaunchOutcome.BringingToFront`.
8. **`ShizukuShellManager.kt`** (parsial): tambah `forceStop()` — wrapper
   `am force-stop <pkg>` untuk `FloatingWindowController.close()`.
9. **`App.kt`** (parsial, PROTECTED): panggil
   `FloatingSessionManager.init(applicationContext)` di `onCreate` — satu
   titik, jalan terlepas dari MainActivity atau FloatingBubbleService yang
   jadi entry point pertama kali.
10. **`FavoritesRowBinder.kt`** (parsial): tambah parameter
    `onSlotLongClick` (default no-op, tidak breaking call site lama) — dasar
    UI untuk "close" (P0 #3 slice) lewat tekan-tahan Favorit yang sedang
    floating.
11. **`MainActivity.kt`** (parsial, PROTECTED): `launchFloating()` handle
    outcome `BringingToFront` baru; `closeFloatingFavorite()` baru (no-op
    diam kalau slot itu bukan sesi live — lihat batasan UI di bawah), dikait
    ke long-press Favorit.
12. **`FloatingBubbleService.kt`** (parsial): perubahan cermin persis
    MainActivity poin 11, demi konsistensi dua entry point (aturan tetap
    dari Batch5/6).
13. **`strings.xml`**: 4 string baru — `bringing_to_front`,
    `closed_success`, `closed_failed`, `favorites_long_press_hint` (string
    terakhir belum dipakai di layout manapun batch ini — disiapkan untuk
    polish UI hint P1 berikutnya, BUKAN dead code yang menyesatkan, sengaja
    dicatat di sini).
14. **`app/build.gradle`** (PROTECTED, parsial): `versionCode 6` /
    `versionName "2.4.0"`. Tidak ada dependency baru.

### Batasan UI yang jujur (bukan bug)
Favorit yang sedang floating TIDAK punya indikator visual berbeda dari yang
tidak floating (mis. border/dot hijau) — `FavoritesRowBinder` belum tahu
per-slot state sesi. Tekan-tahan untuk close tetap berfungsi (silent no-op
kalau ternyata bukan sesi live), tapi user tidak tahu SECARA VISUAL slot
mana yang aktif tanpa mencoba. Ini P1 #14 "Unified setup/readiness state"
yang sudah disebut Batch6 sebagai utang — masih utang, sengaja tidak
digabung ke sini supaya batch ini tetap fokus state/logic, bukan UI.
Begitu juga `PossibleDuplicate` dari bring-to-front tidak punya toast/UI
sendiri (hanya tercatat di `FloatingSession.duplicateSuspected` untuk
debugging) — polish UX untuk kasus ini menyusul P1.

### Sengaja TIDAK dikerjakan batch ini (lihat audit untuk detail)
- P0 #3 sisa: resize, reposition, maximize internal — butuh command
  task-scoped (`am task resize <id> <L,T,R,B>` secara historis ada, tapi
  keandalannya lintas Android 10+ tidak terverifikasi di batch ini) DAN
  control-chrome overlay per-window (subsistem UI besar tersendiri) — lebih
  aman dikerjakan sebagai batch terpisah dengan testing nyata di device,
  daripada mendorong command belum-teruji sekarang.
- P0 #7 sisa: posisi/ukuran window — lihat penjelasan `SessionPersistence`
  di atas, secara arsitektur baru masuk akal setelah resize/reposition ada.
- Indikator visual "sedang floating" di Favorit/app list (P1 #14 lanjutan).
- Semua P1/P2 lain (smart panel positioning, inset handling, animation
  system, dst).

### Arsip Detail — v2_Batch6 (2026-08-16)
**Confidence Rating: 95%**

Batch ini adalah **Atomic Change** (16 file > limit 10 — lihat alasan di
bawah) yang menjawab **P0 #1, #2, #5, #6, #8** dari
`FloatingApps_v2_2_0_Final_Gap_Audit.md`, mengikuti persis "Urutan Kerja
yang Benar" di audit tsb (langkah 1–3: Session Manager → Capability Manager
→ Launch Verification), **BUKAN** rewrite total dan **BUKAN** mengerjakan
seluruh P0 sekaligus — item #3 (True Window Management), #4 (True
Bring-to-Front), dan #7 (Persistent Floating State penuh) **sengaja belum**
dikerjakan di batch ini, menyusul di batch berikutnya. Fondasi lama
(`OverlayWindowController`, `FloatingDragTouchListener`, dll — lihat daftar
"Fondasi yang Sudah Cukup Baik" di audit) tidak disentuh sama sekali.

**Kenapa Atomic (bukan dipecah)**: `FloatingSessionManager`,
`CapabilityManager`, dan `LaunchVerification` saling bergantung erat —
verification menulis ke session manager DAN membaca capability manager;
`MainActivity` dan `FloatingBubbleService` sama-sama entry point launch yang
harus di-update BERSAMAAN lewat `FloatingLaunchCoordinator` baru, kalau
tidak akan ada 2 alur launch yang tidak konsisten (satu tercatat di
registry, satu tidak).

### Ringkasan perubahan
1. **`core/session/` (baru)** — jawaban P0 #1 "Floating Session Registry"
   + P0 #8 "Launched vs Actually Floating":
   - `FloatingSessionState.kt` — enum eksplisit: `LAUNCHING`,
     `VERIFIED_FLOATING`, `FAILED_NOT_FLOATING`, `FAILED_LAUNCH`, `CLOSED`.
     Ini jawaban P0 #6 "Failure & Recovery State" versi session-level
     (state readiness-level ada di `CapabilityManager`, lihat poin 2).
   - `FloatingSession.kt` — data model 1 entri registry.
   - `FloatingSessionManager.kt` — `StateFlow<Map<String, FloatingSession>>`
     singleton in-memory (scope: proses, BUKAN disk — full persistence
     posisi/ukuran/session lintas-restart adalah P0 #7 terpisah, sengaja
     tidak dicampur di batch ini). Juga berisi extension
     `FloatableApp.sessionKey`.
   - `FloatingLaunchCoordinator.kt` — SATU pintu masuk launch, dipakai
     `MainActivity` & `FloatingBubbleService` (dulu 2 copy logic hampir
     identik, sekarang 1). Mengembalikan `LaunchOutcome` (command-level)
     + memicu `LaunchVerification` async untuk hasil sebenarnya.
   - `LaunchVerification.kt` — jawaban P0 #2 "Launch Result Verification".
     Poll `dumpsys activity activities <pkg>` (via `ShizukuShellManager`,
     shell UID) hingga 5x/400ms, cari token `windowingmode=freeform`/`=5`
     vs `fullscreen`/`=1`. **KETERBATASAN JUJUR (didokumentasikan di kode,
     bukan disembunyikan)**: format teks `dumpsys` bukan API stabil lintas
     OEM — ini heuristik best-effort, BUKAN oracle 100% pasti, sama seperti
     `BatteryOptimizationHelper` untuk OEM autostart. Tetap merupakan
     peningkatan nyata dari v2.2.0 yang menganggap "command tidak error" =
     "berhasil floating".
2. **`core/capability/` (baru)** — jawaban P0 #5 "Freeform Capability
   Detection" + separuh P0 #6 (readiness-level state):
   - `SystemReadiness.kt` — enum `READY / DEGRADED / ACTION_REQUIRED /
     UNSUPPORTED / ERROR` + `CapabilitySnapshot` data class.
   - `CapabilityManager.kt` — gabungkan Overlay + Shizuku + Battery +
     Freeform jadi satu `StateFlow<CapabilitySnapshot>`. Deteksi freeform:
     `PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT` (positif kuat,
     tapi ABSEN tidak membuktikan apa-apa — banyak HP dukung freeform tanpa
     deklarasi fitur ini) dikombinasi dengan **bukti empiris** dari
     `LaunchVerification.recordEmpiricalResult()` — 1x sukses terverifikasi
     = terbukti selamanya (`true` menang & lengket), 1x gagal saja BELUM
     cukup untuk vonis `UNSUPPORTED` (bisa jadi 1 app yang bermasalah, bukan
     platform) kecuali belum pernah ada bukti sukses sama sekali.
3. **`ShizukuShellManager.kt`** (parsial): tambah `dumpActivityState(pkg)`
   — wrapper `dumpsys activity activities <pkg>` untuk `LaunchVerification`.
4. **`MainActivity.kt`** (rewrite parsial, PROTECTED): `launchFloating()`
   sekarang lewat `FloatingLaunchCoordinator`; `refreshUi()`/`onResume()`
   panggil `CapabilityManager.refresh()`; observer baru
   `observeSessionState()` (pola `repeatOnLifecycle` sama seperti
   `observeBubbleState()`) — toast peringatan SEKALI per session kalau
   verifikasi bilang "terbuka tapi tidak floating".
5. **`FloatingBubbleService.kt`** (parsial, bukan protected tapi core
   logic): `launchFloating()` di panel bubble juga lewat
   `FloatingLaunchCoordinator` — dua entry point sekarang konsisten.
6. **`strings.xml`**: 2 string baru —
   `launch_not_floating_warning`, `freeform_unsupported_warning`.
7. **`app/build.gradle`** (PROTECTED, parsial): `versionCode 5` /
   `versionName "2.3.0"`. Tidak ada dependency baru — StateFlow/coroutines
   sudah ada dari v2_Batch5.

### Sengaja TIDAK dikerjakan batch ini (lihat audit untuk detail)
- P0 #3 True Window Management (minimize/restore/resize/maximize internal).
- P0 #4 True Bring-to-Front (Favorit masih relaunch, belum angkat window
  existing) — butuh #3 selesai dulu secara logis.
- P0 #7 Persistent Floating State penuh (disk) — registry saat ini
  in-memory/proses saja.
- Semua P1/P2 (smart panel positioning, inset handling, animation system,
  dst) — menyusul setelah lapisan P0 tuntas, sesuai urutan kerja audit.
- **Tidak ada perubahan UI/layout** di batch ini sama sekali (sengaja,
  demi menahan jumlah file) — status capability baru belum punya
  representasi visual sendiri di layar; ini utang kecil untuk P1 #14
  "Unified setup/readiness state" batch berikutnya.

## Known-Fix Log (terbaru di atas)
- **v2_Batch7** (2026-08-16): `core/window/` (TaskIdParser +
  FloatingWindowController: bringToFront + close) + SessionPersistence +
  FloatingSessionManager.init/sessionForApp + FloatingLaunchCoordinator
  raise-vs-relaunch — lihat "STATUS TERKINI" di atas untuk detail penuh.
- **v2_Batch6** (2026-08-16): FloatingSessionManager + CapabilityManager +
  LaunchVerification + FloatingLaunchCoordinator — lihat "Arsip Detail —
  v2_Batch6" di atas untuk detail penuh.
- **v2_Batch5** (2026-08-16): scroll fix beranda, APK naming fix di
  GitHub Release, dokumentasi dirombak jadi latest-on-top, arsitektur
  modular anti-crash (`core/overlay`, `core/power`, `core/touch`,
  `core/ipc`) — detail penuh diarsipkan di bawah ("Arsip Detail —
  v2_Batch5").
- v2_Batch4: Crash `UnsupportedOperationException: Failed to resolve
  attribute` saat membuka panel bubble — root cause: `FloatingBubbleService`
  meng-inflate layout dengan Service context mentah (no theme), sehingga
  `?attr/selectableItemBackground` di `layout_app_list_item.xml` gagal
  di-resolve. FIX: bungkus context dengan `ContextThemeWrapper(this,
  R.style.Theme_FloatingApps)` sebelum inflate di `addBubble()`/`addPanel()`.
  **Aturan tetap berlaku**: SEMUA `LayoutInflater.from(...)` di dalam
  Service/non-Activity WAJIB pakai `ContextThemeWrapper`.
- v2_Batch4: Ditambahkan Slot Favorit (pin via long-press, max 6, shared
  SharedPreferences `floating_favorites`) — jawab keluhan "searching ribet".
  Minimize memakai kombinasi: title bar native OS (bawaan freeform window)
  + tap ulang dari Favorit untuk bring-to-front app yang sudah floating.
- v2_Batch3: Ganti total mekanisme dari "bubble+catatan lokal" menjadi
  "launcher app lain ke freeform window via Shizuku", sesuai niat awal user.
- v1_Batch2: `secrets` context tidak boleh dipakai langsung di `if:` pada
  GitHub Actions. Diperbaiki lewat `env:` + cek bash di dalam `run:`.

### Arsip Detail — v2_Batch5 (2026-08-16)
**Confidence Rating: 96%**

Batch ini adalah **Atomic Change** (>10 file, disengaja — lihat alasan di
Impact Report commit) untuk 3 perbaikan + 1 permintaan arsitektur besar
sekaligus, karena semuanya saling terkait pada `FloatingBubbleService` /
`MainActivity` dan memecahnya jadi batch terpisah akan meninggalkan
state antara yang tidak konsisten (mis. touch listener baru butuh
`OverlayWindowController` baru di batch yang sama).

### Ringkasan perubahan
1. **Scroll fix beranda**: `activity_main.xml` root diganti jadi
   `androidx.core.widget.NestedScrollView` (dari `LinearLayout` polos).
   Root cause: sebelumnya TIDAK ADA scroll container sama sekali di
   beranda — 2 panel setup + tombol + favorit + search adalah konten
   `wrap_content` tetap, dan hanya `rvApps` yang punya scroll (internal,
   `layout_weight=1`). Di layar pendek/landscape, area `rvApps` bisa
   terdesak ke tinggi 0-negatif dan bagian atas jadi tidak terjangkau sama
   sekali — tidak ada cara scroll ke bawah. FIX: `rvApps` sekarang
   `wrap_content` dan ikut nested-scroll bersama seluruh halaman lewat
   `NestedScrollView` (`fillViewport="true"`). Trade-off yang disadari:
   RecyclerView di dalam `wrap_content` meng-inflate semua item sekaligus
   (bukan windowed recycling) — dapat diterima untuk ukuran daftar app
   terpasang di HP (puluhan–ratusan), ini pattern standar Android untuk
   kasus header+list yang harus scroll sebagai satu halaman.
2. **Nama APK GitHub Release**: `.github/workflows/release.yml` tidak lagi
   publish `app-release.apk` generik. Sekarang APK di-copy ke
   `FloatingApps-v<versionName>-build<run_number>.apk` sebelum
   `softprops/action-gh-release` publish — nama file sekarang membawa
   konteks (app, versi, build#) begitu diunduh dari sidebar repo.
3. **Dokumentasi latest-on-top**: `PROJECT_STATE.md` (file ini) dirombak —
   dulu status/histori tersebar & Known-Fix Log menumpuk lama→baru di
   paling bawah. Sekarang section ini (STATUS TERKINI) selalu di atas, dan
   Known-Fix Log di bawahnya sekarang baru→lama. `README.md` dapat section
   "Status Terkini" baru di atas "Cara Pakai".
4. **Arsitektur modular Floating App (permintaan eksplisit)** — package baru
   di bawah `core/`:
   - `core/overlay/OverlayPermissionHelper.kt` — cek/minta izin
     `SYSTEM_ALERT_WINDOW`, satu sumber kebenaran (dipakai Activity & Service).
   - `core/overlay/OverlayWindowController.kt` — SEMUA `WindowManager.
     addView/updateViewLayout/removeView` di app ini wajib lewat sini.
     Menyimpan set view yang berhasil ditambahkan, setiap operasi dibungkus
     try-catch (BadTokenException, izin dicabut di tengah sesi, view yang
     sudah terlepas) — anti-crash terpusat, bukan tersebar & mudah lupa di
     tiap call site seperti sebelumnya.
   - `core/power/BatteryOptimizationHelper.kt` — exemption Doze standar
     (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, dijamin API Android)
     + best-effort deep-link ke halaman Autostart 5 OEM populer (Xiaomi/
     Oppo/Vivo/Huawei/Samsung). Komponen OEM tidak resmi didokumentasikan →
     SELALU dibungkus try-catch per kandidat, gagal = lanjut kandidat
     berikutnya, tidak pernah melempar.
   - `core/touch/ScreenMetricsProvider.kt` — resolusi batas layar
     (`currentWindowMetrics` API30+, fallback `getRealSize` API26-29) +
     fungsi `clamp()` generik untuk boundary constraint.
   - `core/touch/SnapEdgeAnimator.kt` — animasi snap-to-nearest-edge
     (`ValueAnimator`, 220ms, `DecelerateInterpolator`), auto-cancel aman
     kalau view terlepas di tengah animasi.
   - `core/touch/FloatingDragTouchListener.kt` — drag handler bubble:
     tap-vs-drag pakai `ViewConfiguration.scaledTouchSlop` (bukan angka
     hardcode), boundary-clamp tiap `ACTION_MOVE` lewat `ScreenMetricsProvider`,
     snap-to-edge di `ACTION_UP`, plus `onScreenBoundsChanged()` untuk
     dipanggil ulang saat rotasi.
   - `core/ipc/BubbleStateBus.kt` — `StateFlow` singleton in-process untuk
     Service→Activity state (bubble running/tidak). Keputusan desain:
     BUKAN Binder/AIDL — service & activity satu proses yang sama (tidak
     ada `android:process` di manifest), jadi StateFlow singleton adalah
     "IPC boundary" yang benar & idiomatik di sini, bukan over-engineering
     pakai Binder untuk komunikasi dalam satu proses. IPC lintas-proses
     yang SUNGGUHAN tetap ada terpisah untuk `ShellUserService` (proses
     shell UID milik Shizuku) — lihat `IShellService.aidl` +
     `ShizukuShellManager`, tidak berubah.
   - `FloatingBubbleService.kt` dirombak memakai semua di atas + override
     `onConfigurationChanged()` (re-clamp posisi bubble setelah rotasi) +
     override `onTaskRemoved()` sebagai no-op sengaja (service tidak boleh
     berhenti saat app di-swipe dari Recents) + `CoroutineScope(Dispatchers.
     Main + SupervisorJob())` yang di-`cancel()` di `onDestroy()` — cegah
     coroutine bocor melebihi umur service.
   - `MainActivity.kt`: tambah Langkah 3 (Battery/Background) di UI +
     `observeBubbleState()` pakai `lifecycleScope.launch { repeatOnLifecycle
     (STARTED) { ... } }` — pola standar collect StateFlow dari Activity
     tanpa risiko leak (auto-cancel saat Activity distop).
   - `app/build.gradle`: tambah `androidx.lifecycle:lifecycle-runtime-ktx`
     & `kotlinx-coroutines-android` (dibutuhkan modul di atas).
     `versionCode 4` / `versionName "2.2.0"`.
   - `AndroidManifest.xml`: tambah `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### Touch handling: passthrough vs capture (desain, bukan kebetulan)
Bubble & panel adalah window `WRAP_CONTENT` + `FLAG_NOT_FOCUSABLE`. Window
overlay hanya seluas kontennya sendiri, jadi sentuhan di LUAR bounds bubble/
panel otomatis diteruskan (passthrough) ke app di bawahnya — tanpa perlu
`FLAG_NOT_TOUCHABLE` + custom touchable-region. Sentuhan DI DALAM bounds
ditangkap penuh oleh `FloatingDragTouchListener`. Ini didokumentasikan di
sini supaya tidak ada yang "memperbaiki" jadi `FLAG_NOT_TOUCHABLE` di batch
depan tanpa sadar itu breaking change.

### Memory-leak prevention checklist batch ini
- [x] Semua view overlay di-null-kan setelah `OverlayWindowController.remove()`.
- [x] `FloatingDragTouchListener.release()` dipanggil di `removeBubble()` —
      cancel animator + recycle `VelocityTracker`.
- [x] `serviceScope.cancel()` di `onDestroy()` Service.
- [x] `BubbleStateBus` adalah singleton object tanpa referensi ke
      Activity/View — Activity yang collect via `repeatOnLifecycle`, bukan
      sebaliknya, jadi tidak ada Context yang tertahan olehnya.
- [x] `Shizuku.removeRequestPermissionResultListener(this)` di
      `MainActivity.onDestroy()` — tidak berubah dari sebelumnya, tetap benar.

---
## Arsitektur Inti (referensi, jarang berubah)
1. **ShellUserService** (`IShellService.aidl` + `ShellUserService.kt`):
   proses terpisah yang dijalankan Shizuku dengan UID shell. Hanya
   menjalankan `ProcessBuilder` shell command apa adanya (argv array, bukan
   string shell — jadi aman dari masalah escaping) dan mengembalikan output.
2. **ShizukuShellManager**: singleton yang mengurus bind/permission Shizuku,
   dan 2 operasi inti:
   - `enableFreeformSupport()` — sekali per koneksi, menjalankan
     `settings put global enable_freeform_support 1` &
     `settings put global force_resizable_activities 1` lewat shell.
   - `launchFloating(pkg, activity)` — `am start --windowingMode 5 -n pkg/activity`.
3. **MainActivity**: checklist setup (Overlay + Shizuku + Battery) di atas,
   pencarian + daftar semua app terpasang di bawah (RecyclerView, kini di
   dalam NestedScrollView — lihat STATUS TERKINI). Tap app → floating langsung.
4. **FloatingBubbleService**: bubble draggable (boundary-constrained +
   snap-to-edge, `SYSTEM_ALERT_WINDOW`, tidak perlu Shizuku) — tap bubble
   membuka panel picker app yang sama (search + list).
5. **CrashHandler**: MediaStore (API29+)/app storage fallback, FIFO 50 log.

## Kenapa Butuh Shizuku (bukan sekadar SYSTEM_ALERT_WINDOW)
Android sengaja membatasi app biasa agar tidak bisa mengontrol/mengirim
sentuhan ke app lain — proteksi keamanan OS, bukan pilihan desain. Tanpa
root, satu-satunya jalan legal & terbuka publik untuk membuat jendela app
lain yang BENAR-BENAR interaktif (native, bukan proyeksi/mirror) adalah
meminta OS melakukannya sendiri lewat `am start --windowingMode 5`
(WINDOWING_MODE_FREEFORM) — perintah level shell yang hanya bisa dijalankan
dengan privilese shell/adb. Shizuku menjembatani itu tanpa root, lewat
pairing Wireless debugging sekali di awal.

## Keterbatasan Jujur (bukan bug, tapi batas platform Android)
- **Shizuku wajib di-pairing ulang setiap device reboot** (kecuali root).
- **Freeform tidak dijamin 100% di semua HP** — `enable_freeform_support`
  butuh dukungan framework Android di build OS tsb; sebagian ROM OEM yang
  sangat dikunci bisa saja mengabaikannya.
- **Battery/Autostart OEM (Langkah 3 baru)**: exemption Doze dijamin API
  Android, tapi jalan pintas Autostart OEM di `BatteryOptimizationHelper`
  best-effort — komponennya tidak didokumentasikan resmi, bisa berbeda
  antar versi ROM, dan sebagian OEM (terutama yang sangat agresif
  membunuh background process) tetap bisa mematikan service walau semua
  izin sudah diberikan. Ini keterbatasan platform/OEM, bukan sesuatu yang
  bisa "diperbaiki" tuntas dari sisi app tanpa root.
- **App pre-v11 Shizuku** (sangat jarang) tidak difallback ke alur
  permission lama — disederhanakan sejak v2.0.0.
- **Task ID (`TaskIdParser`, v2_Batch7) adalah heuristik, bukan API resmi**
  — parsing teks `dumpsys`, sama seperti `LaunchVerification`. Bisa gagal
  cocok di OEM/versi Android tertentu; kalau gagal, `bringToFront()`
  melaporkan `NotFound`, TIDAK berpura-pura sukses.
- **"Close" (v2_Batch7) memakai `am force-stop`, bukan close per-window** —
  mematikan SELURUH proses app, termasuk state/data yang belum disimpan app
  tsb. Ini trade-off sadar demi keandalan lintas versi Android (lihat
  `FloatingWindowController.close()` doc), bukan kelalaian.
- **Sesi `RESTORED` (v2_Batch7) adalah riwayat, bukan jaminan window masih
  hidup** — setelah restart proses, app TIDAK bisa tahu status window
  sebenarnya tanpa cek shell baru. Jangan treat `RESTORED` sebagai
  `VERIFIED_FLOATING`.

## Protected Assets Checklist (batch v2_Batch7)
- [x] app/build.gradle — version bump saja, 0 dependency baru (parsial)
- [x] App.kt — parsial (1 baris: `FloatingSessionManager.init()`)
- [x] MainActivity.kt — parsial (launchFloating outcome baru +
      closeFloatingFavorite baru; sisa struktur tidak diubah)
- [x] AndroidManifest.xml — TIDAK diubah (force-stop/task-id lewat shell
      Shizuku yang sudah privilese, tidak perlu permission baru)
- [x] settings.gradle, .gitignore, .gitattributes, release.yml — tidak diubah

### Protected Assets Checklist (batch v2_Batch6, histori)
- [x] app/build.gradle — version bump saja, 0 dependency baru (parsial)
- [x] MainActivity.kt — parsial (launchFloating + refreshUi/onResume +
      observeSessionState baru; sisa struktur tidak diubah)
- [x] AndroidManifest.xml — TIDAK diubah (verifikasi lewat shell Shizuku
      yang sudah punya privilese, tidak perlu permission baru)
- [x] settings.gradle, .gitignore, .gitattributes, release.yml — tidak diubah
- [x] App.kt — tidak diubah

### Protected Assets Checklist (batch v2_Batch5, histori)
- [x] AndroidManifest.xml — tambah `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (parsial)
- [x] app/build.gradle — tambah 2 dependency + version bump (parsial)
- [x] MainActivity.kt — rewrite (Langkah 3 + IPC bus observer)
- [x] .gitignore, .gitattributes — tidak diubah
- [x] .github/workflows/release.yml — perbaikan APK naming (parsial, logic
      lain tidak disentuh)
