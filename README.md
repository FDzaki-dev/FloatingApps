# Floating Apps

## 🟢 Status Terkini — v2.5.0 (2026-08-17)
- **Terbaru:** satu banner status terpadu di beranda — langsung kelihatan
  kalau semua siap, baterai belum full-exempt, izin belum lengkap, device
  kemungkinan tidak mendukung floating, atau ada error — tanpa harus coba
  launch dulu buat tahu.
- **Terbaru:** hotfix crash v2.4.1 (regex `TaskIdParser`) — lihat
  `PROJECT_STATE.md` untuk detail root cause.
- Detail lengkap & alasan desain: lihat `PROJECT_STATE.md` (paling atas =
  paling baru). Item besar berikutnya (resize/reposition/maximize window
  internal) menyusul di batch berikutnya — lihat
  `FloatingApps_v2_2_0_Final_Gap_Audit.md`.
- Rilis APK terbaru: lihat tab **Releases** di sidebar repo ini.

## Status Sebelumnya — v2.4.0
- Favorit yang sudah floating punya bring-to-front yang benar (diverifikasi,
  bukan ditebak), bisa ditutup lewat tekan-tahan, dan riwayat sesi bertahan
  lintas restart app.

## Status Sebelumnya — v2.3.0
- Launch punya registry & verifikasi nyata — app tidak lagi dianggap
  "berhasil floating" hanya karena command shell tidak error.
- Deteksi kemampuan freeform device (best-effort).

## Status Sebelumnya — v2.2.0
- Arsitektur bubble dirombak modular (`core/overlay`, `core/power`,
  `core/touch`, `core/ipc`) — anti-crash, drag boundary-constrained +
  snap-to-edge, tahan rotasi, exemption baterai OEM.
- Fixed: beranda sekarang bisa di-scroll penuh ke bawah.
- Fixed: nama file APK di GitHub Release sekarang deskriptif
  (`FloatingApps-v2.2.0-build<run>.apk`), bukan `app-release.apk` generik.

## Cara Pakai
1. **Izin Overlay**: tap "Izinkan Tampil di Atas Aplikasi Lain" → aktifkan
   di halaman Settings yang terbuka.
2. **Shizuku** (wajib untuk sentuhan interaktif):
   - Belum install → tap "Install Shizuku" (buka Play Store/halaman resmi).
   - Sudah install, belum aktif → tap "Buka Shizuku", lalu di app Shizuku:
     aktifkan lewat **Wireless debugging** (Android 11+: Settings →
     Developer options → Wireless debugging → pairing, lalu buka Shizuku
     dan tap Start). Sekali setup, ~1 menit.
   - Sudah aktif, belum kasih izin → tap "Minta Izin Shizuku" → izinkan.
3. **Latar Belakang & Baterai** (baru di v2.2.0): tap "Izinkan Berjalan di
   Background" agar OS tidak mematikan bubble demi hemat baterai. Di HP
   Xiaomi/Oppo/Vivo/Huawei/Samsung, tombol yang sama berubah jadi jalan
   pintas opsional ke pengaturan Autostart/App Battery Management OEM.
4. Setelah semua langkah ✓, cari app di kolom pencarian, tap → app terbuka
   di jendela floating yang bisa digeser/di-resize & disentuh langsung.
5. Opsional: tap "Mulai Bubble Akses Cepat" untuk bubble draggable
   (boundary-constrained, snap ke tepi layar) yang bisa dipakai
   floating-in-app tanpa balik ke Floating Apps.

## Catatan Jujur
- Shizuku butuh pairing ulang tiap restart HP (kecuali root) — keterbatasan
  Shizuku sendiri.
- Freeform window bergantung dukungan OS/OEM device kamu. Sebagian besar
  Android 10+ mendukung; sebagian ROM yang sangat dikunci mungkin tidak.
- Exemption baterai standar (Doze) dijamin API Android; jalan pintas
  Autostart OEM di Langkah 3 best-effort — komponennya tidak didokumentasikan
  resmi dan bisa berbeda antar versi ROM.
- Menutup app lewat tekan-tahan Favorit memakai `am force-stop` — mematikan
  SELURUH proses app tsb (bukan cuma satu window), jadi data yang belum
  disimpan app itu bisa hilang. Ini trade-off sadar demi keandalan lintas
  versi Android.
- Riwayat sesi yang bertahan lintas restart app HANYA riwayat, bukan
  jaminan window masih hidup — setelah restart, app tidak bisa tahu status
  window sebenarnya tanpa cek ulang.

## Build APK
Otomatis lewat GitHub Actions → **GitHub Release** setiap push ke `main`.
APK yang dipublikasikan bernama `FloatingApps-v<versi>-build<nomor>.apk`
(bukan nama generik AGP). Tanpa secret keystore, APK tetap dibuat dengan
debug signing (untuk testing).

## Struktur Proyek
Lihat `PROJECT_STATE.md` untuk arsitektur & keputusan teknis lengkap (info
terbaru selalu di paling atas file itu), `FILE_MANIFEST.txt` untuk daftar
file, `CHANGELOG.md` untuk riwayat rilis (terbaru di atas).
