# PROJECT_STATE.md — Floating Apps

## Ringkasan
Aplikasi Android native (Kotlin) yang menampilkan floating bubble draggable
di atas aplikasi lain (gaya chat-head), dengan panel catatan cepat, crash
logger bawaan, dan CI/CD otomatis via GitHub Actions ke GitHub Release.

## Identitas Proyek
- Nama App: Floating Apps
- Package/applicationId: com.floatingapps.app
- rootProject.name: FloatingApps
- Versi: 1.0.0 (versionCode 1)
- Batch: v1_Batch2 (Fix)

## Known-Fix Log
- v1_Batch2: `secrets` context tidak boleh dipakai langsung di `if:` pada
  GitHub Actions ("Unrecognized named-value: 'secrets'"). Diperbaiki di
  `.github/workflows/release.yml` — pengecekan secret dipindah ke dalam
  `run:` (bash `if [ -n "$VAR" ]`) via `env:`, bukan YAML-level `if:`.

## Stack & Kompatibilitas (fixed, jangan diubah tanpa alasan kuat)
- AGP 8.5.0, Gradle 8.7, Kotlin 1.9.24, JDK 17
- compileSdk 34, targetSdk 34, minSdk 26 (Android 8.0+)
- minSdk sengaja 26 agar bisa pakai TYPE_APPLICATION_OVERLAY & Adaptive Icon
  langsung tanpa cabang kode legacy (mengurangi risiko bug).

## Keputusan Arsitektur Penting
1. **gradlew/gradle-wrapper.jar TIDAK disertakan dalam ZIP.** File jar biner
   tidak bisa dibuat aman di sandbox pembuatan proyek ini. Ini TIDAK
   menghalangi workflow: skrip Termux user hanya melakukan git init/commit/push,
   bukan build lokal. Build APK sepenuhnya terjadi di GitHub Actions memakai
   Gradle sistem (`gradle/actions/setup-gradle`), bukan wrapper. Jika project
   dibuka di Android Studio, wrapper akan digenerate otomatis saat Sync.
2. **Signing kondisional**: app/build.gradle mengecek keberadaan
   `keystore.properties` di root. Jika ada → signed release build. Jika
   tidak ada → fallback ke debug signing agar APK tetap ter-build & ter-install
   (tidak pernah gagal build hanya karena belum setup keystore).
3. **Foreground service type**: `specialUse` (Android 14 mewajibkan tipe
   eksplisit; tidak ada kategori resmi untuk overlay bubble).
4. **CrashHandler** (lihat bagian Crash Logger).
5. Tidak ada NavGraph / DB Schema — di luar scope v1 (app tidak memakai
   Jetpack Navigation maupun database lokal).

## Crash Logger (sesuai spesifikasi)
- Entry point: `Thread.setDefaultUncaughtExceptionHandler` di `App.kt`.
- API 29+: tulis ke MediaStore → `Documents/FloatingApps/logs/crash_<timestamp>_<uuid>.txt`,
  tanpa permission legacy.
- API 26–28: fallback ke `getExternalFilesDir("logs")` (juga tanpa permission).
- Fail-safe: seluruh proses penulisan log dibungkus try-catch; error selalu
  diteruskan ke default handler agar sistem tetap bisa menampilkan crash dialog.
- Retention: FIFO, maksimal 50 file log, file tertua dihapus otomatis.
- Metadata per log: versionName, OS release+SDK, manufacturer+model,
  timestamp, nama thread, full stack trace.

## Fitur v1.0.0
- MainActivity: request izin overlay (Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
  request POST_NOTIFICATIONS (API 33+), toggle start/stop service, indikator status.
- FloatingBubbleService: bubble draggable via WindowManager, tap → buka/tutup
  panel catatan cepat (tersimpan di SharedPreferences), notifikasi foreground
  dengan tombol Stop langsung.
- Semua operasi WindowManager (add/update/remove view) dibungkus try-catch
  agar tidak crash bila izin overlay dicabut saat service berjalan.

## Protected Assets Checklist (batch ini)
- [x] AndroidManifest.xml — permissions & komponen sinkron dengan kode Kotlin
- [x] build.gradle (root) & app/build.gradle
- [x] settings.gradle
- [x] MainActivity.kt, App.kt (Application)
- [x] .gitignore (melindungi release.keystore & keystore.properties)
- [x] .gitattributes
- [x] .github/workflows/release.yml — publish ke **GitHub Release** (bukan
      sekadar Actions artifact), APK otomatis muncul di sidebar repo
- [ ] release.keystore — belum ada (user generate sendiri via keytool, lihat README)
- [ ] NavGraph / DB Schema — N/A, tidak dipakai di v1

## Confidence Rating: 96%
Seluruh kode mengikuti pola Android/Kotlin standar & versi tool yang sudah
matang (AGP 8.5/Gradle 8.7/Kotlin 1.9.24 — kombinasi yang sudah lama stabil).
Belum dijalankan lewat compiler sungguhan (sandbox ini tidak punya Android
SDK/network) — build pertama di GitHub Actions sebaiknya dipantau. Tidak ada
Core/Protected Assets yang hilang, dotfiles utuh, manifest sinkron dengan kode.

## File Count
Total file dalam ZIP ini: 32 (project baru, dikecualikan dari batas 10
file/batch karena ini Atomic Change — scaffold proyek awal).
