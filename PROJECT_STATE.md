# PROJECT_STATE.md — Floating Apps

## Ringkasan (Pivot v2.0.0)
**Konsep diperbaiki total setelah feedback user.** Floating Apps sekarang
adalah launcher yang menjalankan APLIKASI LAIN di jendela floating/freeform
yang bisa disentuh langsung (bukan overlay catatan buatan sendiri seperti
v1.0.0). Berguna terutama untuk app yang tidak mendukung split-screen/resize
bawaan ("app yang kaku").

## Kenapa Butuh Shizuku (bukan sekadar SYSTEM_ALERT_WINDOW)
Android sengaja membatasi app biasa agar tidak bisa mengontrol/mengirim
sentuhan ke app lain — proteksi keamanan OS, bukan pilihan desain. Tanpa
root, satu-satunya jalan legal & terbuka publik untuk membuat jendela app
lain yang BENAR-BENAR interaktif (native, bukan proyeksi/mirror) adalah
meminta OS melakukannya sendiri lewat `am start --windowingMode 5` (WINDOWING_
MODE_FREEFORM) — perintah level shell yang hanya bisa dijalankan dengan
privilese shell/adb. Shizuku menjembatani itu tanpa root, lewat pairing
Wireless debugging sekali di awal.

## Arsitektur Inti
1. **ShellUserService** (`IShellService.aidl` + `ShellUserService.kt`):
   proses terpisah yang dijalankan Shizuku dengan UID shell. Hanya
   menjalankan `ProcessBuilder` shell command apa adanya (argv array, bukan
   string shell — jadi aman dari masalah escaping) dan mengembalikan output.
2. **ShizukuShellManager**: singleton yang mengurus bind/permission Shizuku,
   dan 2 operasi inti:
   - `enableFreeformSupport()` — sekali per koneksi, menjalankan
     `settings put global enable_freeform_support 1` &
     `settings put global force_resizable_activities 1` lewat shell. Baris
     kedua ini yang membuat app yang mendeklarasikan dirinya non-resizable
     tetap bisa dipaksa floating.
   - `launchFloating(pkg, activity)` — `am start --windowingMode 5 -n pkg/activity`.
3. **MainActivity**: checklist setup (Izin Overlay + Shizuku) di atas,
   pencarian + daftar semua app terpasang di bawah (RecyclerView). Tap app →
   floating langsung.
4. **FloatingBubbleService**: bubble draggable (SYSTEM_ALERT_WINDOW, tidak
   perlu Shizuku) — tap bubble membuka panel picker app yang sama (search +
   list) untuk akses cepat tanpa balik ke app utama.
5. **CrashHandler**: TIDAK berubah dari v1 — MediaStore (API29+)/app storage
   fallback, FIFO 50 log. Lihat versi sebelumnya untuk detail.

## Keterbatasan Jujur (bukan bug, tapi batas platform Android)
- **Shizuku wajib di-pairing ulang setiap device reboot** (kecuali root),
  ini keterbatasan Shizuku sendiri, bukan Floating Apps — user perlu buka
  app Shizuku & pairing lagi lewat Wireless debugging setelah restart HP.
- **Freeform tidak dijamin 100% di semua HP.** `enable_freeform_support`
  butuh dukungan framework Android di build OS tsb; sebagian besar
  Android 10+ AOSP-based mendukung (dipakai infrastruktur split-screen),
  tapi sebagian ROM OEM yang sangat dikunci bisa saja mengabaikannya.
  Ini keterbatasan platform, bukan sesuatu yang bisa "diperbaiki" dari sisi
  app tanpa root.
- **App pre-v11 Shizuku** (sangat jarang, <5% menurut data Shizuku sendiri)
  tidak difallback ke alur permission lama — disederhanakan untuk v2.0.0.

## Protected Assets Checklist (batch ini)
- [x] AndroidManifest.xml — provider Shizuku + `<queries>` package visibility
- [x] app/build.gradle — deps Shizuku 13.1.5 + recyclerview 1.3.2, `aidl true`
- [x] MainActivity.kt — rewrite penuh
- [x] .gitignore, .gitattributes, .github/workflows/release.yml — tidak diubah
- [x] release.yml sudah pakai perbaikan `secrets` context dari batch sebelumnya

## Confidence Rating: 90%
API Shizuku (bindUserService, OnRequestPermissionResultListener, AIDL
destroy()=16777114) diverifikasi lewat web search terhadap dokumentasi resmi
RikkaApps/Shizuku-API & contoh kode publik — bukan hanya dari memori. Risiko
residual terbesar: perilaku freeform window itu sendiri device-dependent
(lihat "Keterbatasan Jujur" di atas) dan belum pernah dikompilasi sungguhan
di sandbox ini (tidak ada Android SDK/network). Build pertama sebaiknya
dites di device yang sudah Shizuku-ready.

## Batch: v2_Batch4 (Crash fix + Favorites)

## Known-Fix Log
- v1_Batch2: `secrets` context tidak boleh dipakai langsung di `if:` pada
  GitHub Actions. Diperbaiki lewat `env:` + cek bash di dalam `run:`.
- v2_Batch3: Ganti total mekanisme dari "bubble+catatan lokal" menjadi
  "launcher app lain ke freeform window via Shizuku", sesuai niat awal user.
- v2_Batch4: Crash `UnsupportedOperationException: Failed to resolve
  attribute` saat membuka panel bubble — root cause: `FloatingBubbleService`
  meng-inflate layout dengan Service context mentah (no theme), sehingga
  `?attr/selectableItemBackground` di `layout_app_list_item.xml` gagal
  di-resolve. FIX: bungkus context dengan `ContextThemeWrapper(this,
  R.style.Theme_FloatingApps)` sebelum inflate di `addBubble()`/`addPanel()`.
  **Aturan baru untuk batch berikutnya**: SEMUA `LayoutInflater.from(...)`
  di dalam Service/non-Activity WAJIB pakai `ContextThemeWrapper`, tidak
  boleh context mentah — cegah kelas bug yang sama terulang.
- v2_Batch4: Ditambahkan Slot Favorit (pin via long-press, max 6, shared
  SharedPreferences `floating_favorites`) — jawab keluhan "searching ribet".
  Minimize memakai kombinasi: title bar native OS (bawaan freeform window)
  + tap ulang dari Favorit untuk bring-to-front app yang sudah floating.
