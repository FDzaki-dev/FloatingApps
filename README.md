# Floating Apps

Bubble melayang (chat-head style) di atas aplikasi lain, dengan panel
catatan cepat, dibangun native Kotlin.

## Cara Pakai
1. Buka app → tap **"Izinkan Tampil di Atas Aplikasi Lain"** → aktifkan
   toggle di halaman Settings yang terbuka → kembali ke app.
2. Tap **"Mulai Floating Bubble"**. Bubble biru akan muncul, bisa digeser
   bebas ke mana saja di layar.
3. Tap bubble (tanpa menggeser) untuk membuka panel catatan cepat.
4. Stop bubble lewat tombol di app, atau tombol "Hentikan Floating Bubble"
   pada notifikasi.

## Build APK
APK dibangun otomatis oleh GitHub Actions setiap push ke `main`, lalu
dipublikasikan sebagai **GitHub Release** (muncul di sidebar repo).
Tanpa secret keystore → APK tetap dibuat, ditandatangani dengan debug key
(bisa diinstall untuk testing, tidak untuk distribusi publik).

### Opsional: Signing APK Release Asli
Generate keystore sendiri (contoh via Termux):
```
keytool -genkey -v -keystore release.keystore -alias floatingapps -keyalg RSA -keysize 2048 -validity 10000
```
Lalu set 4 secret di GitHub repo (lihat skrip "Secrets" pada laporan
setup) sebelum push pertama.

## Struktur Proyek
Lihat `PROJECT_STATE.md` untuk detail arsitektur, keputusan teknis, dan
checklist protected assets. Lihat `FILE_MANIFEST.txt` untuk daftar lengkap
file dalam ZIP ini.

## Catatan
`gradlew`/`gradle-wrapper.jar` sengaja tidak disertakan (lihat
PROJECT_STATE.md poin 1) — tidak memengaruhi build CI maupun workflow
Termux (git-only, tidak build lokal).
