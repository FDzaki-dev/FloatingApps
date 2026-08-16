# Floating Apps

## 🟢 Status Terkini — v2.2.0 (2026-08-16)
- **Terbaru:** arsitektur bubble dirombak modular (`core/overlay`, `core/power`,
  `core/touch`, `core/ipc`) — anti-crash, drag boundary-constrained +
  snap-to-edge, tahan rotasi, exemption baterai OEM. Detail lengkap & alasan
  desain: lihat `PROJECT_STATE.md` (paling atas = paling baru).
- **Fixed:** beranda sekarang bisa di-scroll penuh ke bawah (sebelumnya
  konten header bisa overflow tanpa cara menggulir).
- **Fixed:** nama file APK di GitHub Release sekarang deskriptif
  (`FloatingApps-v2.2.0-build<run>.apk`), bukan `app-release.apk` generik.
- Rilis APK terbaru: lihat tab **Releases** di sidebar repo ini.

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

## Build APK
Otomatis lewat GitHub Actions → **GitHub Release** setiap push ke `main`.
APK yang dipublikasikan bernama `FloatingApps-v<versi>-build<nomor>.apk`
(bukan nama generik AGP). Tanpa secret keystore, APK tetap dibuat dengan
debug signing (untuk testing).

## Struktur Proyek
Lihat `PROJECT_STATE.md` untuk arsitektur & keputusan teknis lengkap (info
terbaru selalu di paling atas file itu), `FILE_MANIFEST.txt` untuk daftar
file, `CHANGELOG.md` untuk riwayat rilis (terbaru di atas).
