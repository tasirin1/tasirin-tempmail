# Tasirin TempMail

Aplikasi Android email sekali pakai (temporary email) berbasis layanan
[tempmail.plus](https://tempmail.plus), ditulis dari awal dengan kode orisinal
(Kotlin). Gratis dan open source.

## Fitur

- **Salin email** — alamat acak langsung dibuat, satu ketuk tersalin.
- **Rotasi email** — tombol "Ganti Email" membuat alamat baru; domain bisa
  dipilih (mailto.plus, fexpost.com, fexbox.org, fextemp.com, merepost.com,
  rover.info, any.pink) dan nama sendiri bisa diketik.
- **Deteksi kode verifikasi otomatis** — kotak masuk dicek otomatis tiap
  8 detik selama aplikasi terbuka; kode OTP di judul/isi email terdeteksi,
  ditampilkan besar, dan siap disalin dengan satu ketuk. Ada pemberitahuan
  di dalam aplikasi begitu kode baru tiba.
- **Baca email** — ketuk email di daftar untuk membaca isinya; tombol
  "Salin Kode" muncul di dalam bacaan bila ada kode terdeteksi.
- Tema terang biru-putih, UI Bahasa Indonesia.

## Catatan

- Alamat dan email dikelola oleh tempmail.plus; masa simpan kotak masuk
  mengikuti layanan itu (TTL sekitar 48 jam).
- Aplikasi hanya menerima email (bukan mengirim), sama seperti layanannya.
- Deteksi kode berjalan selama aplikasi terbuka di layar.

## Unduh

APK ada di halaman
[Releases](https://github.com/tasirin1/tasirin-tempmail/releases).
