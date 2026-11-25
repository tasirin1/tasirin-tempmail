# Changelog

Semua perubahan penting proyek ini dicatat di berkas ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versi mengikuti [Semantic Versioning](https://semver.org/).

## [0.1.2] - 2026-11-25

### Added

- Kode verifikasi yang BARU tiba langsung tersalin otomatis ke clipboard
  (toast "Kode tiba & tersalin") — tinggal tempel, tanpa ketuk apa pun.
  Kode lama yang baru terbaca saat aplikasi dibuka tidak ikut tersalin.

## [0.1.1] - 2026-11-25

### Changed

- Tombol "Ganti Email" (rotasi) dan "Pakai" kini langsung menyalin alamat
  baru ke clipboard — tidak perlu menekan tombol Salin lagi.

## [0.1.0] - 2026-11-25

### Added

- Rilis pertama Tasirin TempMail: klien Android untuk tempmail.plus.
- Alamat acak + tombol Salin Email dan Ganti Email (rotasi), pilihan
  7 domain resmi tempmail.plus, dan nama lokal ketikan sendiri.
- Kotak masuk otomatis dicek tiap 8 detik selama aplikasi terbuka,
  daftar email, dan layar baca isi email.
- Deteksi kode verifikasi (OTP) otomatis dari judul/isi email dengan
  spanduk kode besar + tombol Salin Kode, termasuk di layar baca.
- Logika murni AddressGenerator dan OtpExtractor dengan unit test JVM.
