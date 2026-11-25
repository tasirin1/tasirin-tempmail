# Changelog

Semua perubahan penting proyek ini dicatat di berkas ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versi mengikuti [Semantic Versioning](https://semver.org/).

## [0.7.1] - 2026-11-25

### Fixed

- tempail.com menolak aplikasi dengan HTTP 403 (gerbang bot
  Cloudflare) sehingga alamat gagal dibuat di perangkat. Header
  permintaan layanan berbasis sesi kini menyerupai peramban Android
  beneran (User-Agent Chrome + Accept + Accept-Language), bukan
  lagi identitas aplikasi polos.

## [0.7.0] - 2026-11-25

### Added

- Multi-layanan: kini ada 5 layanan email sekali pakai yang bisa
  dipilih di kartu alamat — tempmail.plus (bawaan), tempmail.is,
  tempmail.now, tempmail.net, dan tempail.com. Semua API dipetakan
  dan diuji langsung dengan email uji beneran sebelum dibangun.
- tempmail.is mendukung alamat nama sendiri (domain vgmale.com,
  ogzmail.com, ozvmail.com) seperti tempmail.plus.
- Layanan berbasis sesi (tempmail.now, tempmail.net, tempail.com)
  memakai alamat acak dari server; blok "Pakai nama sendiri"
  disembunyikan untuk layanan yang tidak mendukungnya.
- Alamat tempmail.now yang kedaluwarsa (±30 menit) otomatis
  mengikuti alamat baru yang dilaporkan server.

### Notes

- Keluarga tempmail.net/tempail.com tidak menampilkan judul email
  sama sekali dari servernya; deteksi kode verifikasi tetap jalan
  dari isi email.
- Kedatangan email uji ke tempmail.now dan tempmail.net belum
  terkonfirmasi saat rilis (inbox tetap kosong selama pengujian);
  parser keduanya dibangun dari format yang terbukti di situs
  yang sama/keluarga yang sama dan perlu verifikasi pemakaian nyata.

## [0.6.3] - 2026-11-25

### Fixed

- Tepian layar dibuat lebih lega atas masukan uji perangkat
  v0.6.2 ("tepian terlalu mepet"): jarak tepi luar 16dp → 24dp,
  jarak tepi dalam kartu alamat 14dp → 18dp, dan jarak kartu dari
  kepala layar ikut dilonggarkan.

## [0.6.2] - 2026-11-25

### Fixed

- Ikon status bar (jam, sinyal, baterai) kini gelap sehingga terbaca
  jelas di atas latar terang aplikasi — sebelumnya putih dan nyaris
  tidak terlihat (terlihat pada screenshot uji perangkat v0.6.1).

## [0.6.1] - 2026-11-25

### Fixed

- Perbaikan kosmetik dari uji perangkat di HP (screenshot 2026-11-25):
- Kepala layar tidak lagi tertutup jam status bar di Android 15
  (jarak aman sistem di tata letak akar).
- Baris "Pakai nama sendiri" ditumpuk vertikal: pilihan domain
  selebar kartu, kolom nama lega di baris bawahnya — hint
  "nama sendiri" tidak terpotong lagi.
- Tombol Segarkan kini biru dengan teks putih, seragam dengan
  tombol lain (sebelumnya abu-abu bawaan).

## [0.6.0] - 2026-11-25

### Fixed

- Maraton Audit-4: 1 temuan diperbaiki (issue #35).
- Nomor urut kunci email tanpa mail_id kini dihitung dari ekor daftar,
  jadi kedatangan email identik baru tidak menggeser kunci email lama —
  email lama tidak diperiksa ulang dan kodenya tidak tampil lagi
  seolah baru (#35).

## [0.5.0] - 2026-11-25

### Fixed

- Maraton Audit-3: 2 temuan diperbaiki (issue #33–#34).
- Kode verifikasi salin-otomatis kini hanya untuk kode yang tiba sesudah
  sapuan awal inbox lama tuntas seluruhnya — kode lama dari putaran
  lanjutan sapuan awal tidak lagi tersalin seolah baru tiba (#33).
- Kunci penanda email tanpa mail_id ditambah nomor urut kemunculan,
  sehingga dua email identik tanpa id pun masing-masing diperiksa (#34).

## [0.4.0] - 2026-11-25

### Fixed

- Maraton Audit-2: 8 temuan diperbaiki (issue #25–#32).
- Daftar inbox kini tampil segera; pemindaian kode menyusul di belakang
  dan dibatasi 5 pengambilan isi per putaran (#25). Ambil isi yang gagal
  menyerah sesudah 3x percobaan, tidak dicoba ulang selamanya (#26).
- htmlToText: entitas numerik diterjemahkan per titik kode penuh (emoji
  utuh) dan nilai mustahil tidak lagi membuat email gagal dibuka (#27).
- Baca email satu pada satu waktu — ketukan beruntun tidak menumpuk
  dialog (#28). fetchDetail memeriksa field result seperti fetchInbox
  (#29). Email tanpa mail_id memakai kunci gabungan (#30). Rantai
  polling tidak bisa ganda dari onResume (#31). Catatan rilis kini
  diambil dari CHANGELOG versi bersangkutan (#32).

## [0.3.0] - 2026-11-25

### Fixed

- Maraton Audit-1: 24 temuan diperbaiki (issue #1–#24).
- Kode verifikasi tidak hilang permanen bila ambil isi email gagal sesaat
  (#1); deteksi kode memeriksa semua email, bukan cuma teratas (#2);
  dialog baca email aman dari crash saat Activity berakhir (#3); angka
  4 digit acak tidak lagi dianggap kode dan tersalin otomatis (#4); kode
  lebih dari 8 digit tidak terpotong (#5); tahun sesudah kata pemicu
  bukan kode (#6).
- Keadaan polling disinkronkan antar-thread + penanda generasi alamat
  (#7); baca email memakai executor sendiri (#8); koneksi API selalu
  dilepas lewat finally (#9); jawaban result=false dilaporkan sebagai
  gagal, bukan "0 email" (#10); jeda polling mundur saat gagal (#11);
  workflow rilis: versi manual dari versionName + tag wajib cocok (#12);
  htmlToText membuang isi script/style + entitas numerik diterjemahkan
  (#13); body respons dibatasi 2 juta karakter (#14).
- Dependensi tak terpakai dibuang (#15); rilis memakai minify + shrink
  (#16); izin ACCESS_NETWORK_STATE dibuang (#17); User-Agent mengikuti
  versi aplikasi (#18); drawable merujuk @color (#19); daftar inbox hanya
  dibangun ulang bila berubah (#20); nama lokal dirapatkan + dibatasi
  64 karakter (#21); asersi tes hampa dibuang (#22); allowBackup dimatikan
  (#23); panggilan executor dilindungi sesudah shutdown (#24).

## [0.2.0] - 2026-11-25

### Added

- Logo aplikasi: ikon adaptif amplop putih di atas gradasi biru
  (layar utama HP + kepala layar di dalam aplikasi).

### Changed

- Tampilan dipoles: latar biru pucat, alamat dan kotak masuk kini di
  dalam kartu putih sudut membulat, tombol biru berteks putih, spanduk
  kode verifikasi jadi blok biru dengan kode putih besar di tengah,
  kepala layar berisi logo + nama + keterangan layanan.

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
