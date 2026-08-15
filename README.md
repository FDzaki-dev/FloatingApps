# Floating Apps

Jalankan aplikasi lain di jendela floating/freeform yang bisa disentuh
langsung — untuk app yang tidak mendukung split-screen/resize bawaan.

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
3. Setelah kedua langkah ✓, cari app di kolom pencarian, tap → app terbuka
   di jendela floating yang bisa digeser/di-resize & disentuh langsung.
4. Opsional: tap "Mulai Bubble Akses Cepat" untuk bubble draggable yang bisa
   dipakai floating-in-app tanpa balik ke Floating Apps.

## Catatan Jujur
- Shizuku butuh pairing ulang tiap restart HP (kecuali root) — keterbatasan
  Shizuku sendiri.
- Freeform window bergantung dukungan OS/OEM device kamu. Sebagian besar
  Android 10+ mendukung; sebagian ROM yang sangat dikunci mungkin tidak.

## Build APK
Otomatis lewat GitHub Actions → GitHub Release setiap push ke `main`. Tanpa
secret keystore, APK tetap dibuat dengan debug signing (untuk testing).

## Struktur Proyek
Lihat `PROJECT_STATE.md` untuk arsitektur & keputusan teknis lengkap,
`FILE_MANIFEST.txt` untuk daftar file.
