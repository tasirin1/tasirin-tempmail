# Changelog

Semua perubahan penting proyek ini dicatat di berkas ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versi mengikuti [Semantic Versioning](https://semver.org/).

## [0.8.4] - 2026-11-25

Hasil Maraton-10 (Audit-10: 4 temuan, issue #125–#128 — semuanya
Rendah: sisa id sintetis plus, gerbang lifecycle jadwal refresh,
dan kelengkapan penjaga kode pos).

### Fixed

- tempmail.plus: id sintetis email tanpa `mail_id` dibedakan per
  posisi daftar (dua email kembar tidak lagi tertelan penanda yang
  sama), dan membuka email tanpa id kini memberi pesan jujur
  "tidak memberi id — isinya tidak tersedia", bukan "sudah
  terhapus" (issue #125).
- Jadwal refresh tertunda digerbang keadaan polling di titik
  penjadwalan DAN titik nyala — penyelesaian refresh yang terlambat
  sesudah layar dijeda tidak lagi melahirkan pengambilan di latar
  (issue #126).
- Penjaga OTP: sufiks posesif "-kau/-anda" ikut dikenali, dan
  penjaga label kode pos kini berlaku juga di jalur cadangan
  (angka 6 digit dan angka terpencar) — "Kode pos: 123456" tidak
  lagi tampil sebagai kode verifikasi (issue #127, #128).

### Tests

- Tes baru: "kode poskau/posanda" dilewati; "Kode pos: 123456",
  "Kode pos: 123 456", dan "ZIP code: 123 456" dilewati.

## [0.8.3] - 2026-11-25

Hasil Maraton-9 (Audit-9: 6 temuan, issue #119–#124 — semuanya
Rendah: konsistensi antarlaysan dan celah tepi penjaga OTP/teks).

### Fixed

- tempmail.is: satu elemen `messages[]` yang bukan objek dilewati,
  bukan menggagalkan seluruh pengambilan inbox dengan pesan JSON
  mentah (issue #119).
- tempmail.plus: item tanpa `mail_id` memakai kunci sidik isi,
  bukan id kosong bersama yang membuka email yang salah (issue #120).
- Refresh tertunda terjadwal menjadi Runnable bernama: dibatalkan
  saat layar dijeda/ditutup dan tidak bertumpuk (issue #121).
- Penjaga OTP: frasa "zip code" yang mendahului pemicu lain tidak
  lagi menelan kode yang sah, dan sufiks posesif "kode posku/posmu"
  ikut dikenali sebagai kode pos (issue #122, #124).

### Changed

- `htmlToText` menerjemahkan entitas dalam SATU lintasan (regex
  gabungan numerik/hex/bernama): hasil decode tidak dipindai ulang,
  jadi teks berkode ganda tetap tampil sebagai teks, dan skema
  karakter penanda yang tertukar di karakter langka dibuang total
  (issue #123).

### Tests

- Tes baru: kode sesudah "zip code" tetap terdeteksi, "kode
  posku/posmu" dilewati, dan decode satu lintasan tidak menukar
  karakter U+E000/U+FFFE milik pengirim maupun hasil decode-nya.

## [0.8.2] - 2026-11-25

Hasil Maraton-8 (Audit-8: 13 temuan, issue #106–#118 — self-review
Maraton-7: cabang gagal pemulihan sesi, kebersihan laporan alamat,
dan penajaman tepi penjaga OTP).

### Fixed

- Pemulihan sesi/klien yang ikut gagal kini tetap melaporkan alamat
  barunya, jadi polling tidak terbungkam permanen oleh penjaga alamat
  basi (issue #106).
- Laporan alamat hasil pemulihan tempmail.is dibersihkan saat alamat
  dicatat lewat jalur biasa — ganti alamat manual tidak lagi ditarik
  kembali ke alamat pemulihan (issue #107).
- Pemicu pemulihan klien tempmail.is dipersempit ke HTTP 401/403/404;
  5xx/429 tidak lagi membuang alamat yang sehat (issue #108).
- Satu elemen `emails[]` tempmail.now yang bukan objek dilewati,
  bukan menggagalkan seluruh pengambilan inbox (issue #109).
- Sisa pesan JSONException mentah di tempmail.is (daftar domain,
  alamat acak/custom) dibungkus pesan yang bisa dipahami (issue #110).
- Pesan tempmail.is tanpa kolom id memakai kunci sidik isi, bukan
  runtuh ke id "0" bersama (issue #111).
- Penilaian kematian kuki: Max-Age mendahului Expires sesuai
  RFC 6265 (issue #112).
- Refresh tertunda sesudah kegagalan menunggu jeda backoff polling —
  tidak lagi beruntun tanpa jeda saat jaringan mati (issue #113).
- Percobaan ulang pembuatan alamat menjadi Runnable bernama yang
  dibatalkan saat ganti layanan/destroy, dan berjalan diam-diam
  tanpa toast berulang (issue #114, #115).
- Penjaga OTP: frasa "kode posnya" ikut dikenali sebagai kode pos,
  dan kata berakhiran "zip" (unzip/gzip) tidak lagi menolak kode
  yang sah (issue #116, #117).
- Karakter U+E000 asli di isi email tidak lagi berubah menjadi "&"
  saat ditampilkan (issue #118).

### Tests

- Tes baru: "kode posnya" dilewati, "unzip code" tetap terdeteksi,
  dan karakter penanda asli pengirim utuh sesudah htmlToText.

## [0.8.1] - 2026-11-25

Hasil Maraton-7 (Audit-7: 26 temuan, issue #80–#105 — self-review
Maraton-6: pemulihan sesi yang aman, penjaga OTP yang lebih ketat,
pemulihan pembuatan alamat, dan penguncian tes).

### Fixed

- tempmail.now: pemulihan sesi hanya jalan untuk penolakan sesi oleh
  server (HTTP 401/403/404) — timeout jaringan biasa tidak lagi
  membuang alamat yang sehat, dan alamat hasil pemulihan kini
  dilaporkan ke layar (issue #80).
- tempmail.is: klien yang uuid-nya ditolak server kini dipulihkan
  dengan klien baru dan alamatnya dilaporkan ke layar (issue #82);
  delta kotak masuk dua fase — kursor hanya maju sesudah seluruh
  batch teruraikan, satu pesan rusak tidak lagi menghilangkan pesan
  lain (issue #83); daftar delta dibatasi 100 pesan dengan dedupe
  O(1) (issue #86).
- Penjaga alamat basi babak kedua di kedua provider sesi: sesi baru
  yang beralamat berbeda tidak diserahkan untuk label alamat lama
  (issue #91); cache isi tempmail.now dibersihkan saat ganti alamat
  (issue #92).
- OTP: penjaga "kode pos" tidak lagi bisa dilewati pemicu lain yang
  mendahului, varian "Kode-pos", maupun "ZIP code"/"postal code";
  pola terpencar menolak nomor telepon berpemisah spasi (issue #85,
  #99, #100, #101).
- UI: ketukan Pakai/Ganti saat pembuatan alamat berjalan memberi
  umpan balik dan nama yang diketik tidak terhapus (issue #84);
  refresh yang tertelan refresh lain kini diingat dan dijalankan
  sesudahnya (issue #94); domain layanan lama dikosongkan saat ganti
  layanan (issue #95); baris status dibersihkan saat ganti
  layanan/alamat (issue #96); handler gagal refresh memeriksa
  provider (issue #97); pembuatan alamat awal yang gagal dicoba
  ulang otomatis sampai 3 kali dan tombol Segarkan memicu pembuatan
  alamat (issue #98); eviksi penanda email yang sudah dicek memakai
  kunci yang sama dengan penandaan (issue #93).
- tempmail.plus: polling dan buka detail tidak lagi saling menunggu
  di satu kunci HTTP (issue #87).
- Sisa jalur JSONException mentah dibungkus pesan yang bisa dipahami
  (issue #88); Expires kuki diuraikan sungguhan (issue #89);
  KotakIsi sepenuhnya tersinkron (issue #90); htmlToText tidak lagi
  men-decode ampersand numerik dua kali (issue #102).

### Changed

- Entri changelog [0.8.0] yang tertinggal dari commit rilis kini
  masuk riwayat, dan catatan rilis v0.8.0 di GitHub diperbarui
  (issue #81).
- Workflow Release tidak lagi bisa di-dispatch manual dari ref lain
  (mencegah APK rilis terbit tertimpa build commit lain) (issue #105).

### Tests

- Tes komposisi PlusProvider sungguhan menggantikan tes tautologi;
  perilaku encPath terkunci tes unit (issue #103, #104).

## [0.8.0] - 2026-11-25

Hasil Maraton-6 (Audit-6: 44 temuan, issue #36–#79 — kestabilan
layanan berbasis sesi, keamanan pasangan alamat+layanan, efisiensi).

### Fixed

- tempmail.is: pembuatan klien kini dikunci — dua thread tidak lagi
  bisa membuat dua klien berbeda (alamat tampil selalu cocok dengan
  inbox yang dicek); respons sukses-sebagian tidak lagi merusak
  keadaan provider secara permanen.
- tempmail.now: sesi yang mati di server kini dibuang dan dibuat baru
  secara otomatis (dulu macet permanen sampai aplikasi dipaksa
  berhenti); token CSRF disegarkan saat ganti alamat ditolak 403.
- Ganti layanan tidak lagi mencampur layanan baru dengan alamat lama:
  kode OTP layanan baru tidak tersalin di bawah alamat lama, dan
  dialog email alamat lama tidak muncul di atas alamat baru.
- Polling tempmail.is memakai delta (known_message_id): isi email
  lama tidak diunduh ulang tiap 8 detik.
- Cache isi email diganti atomik dan disatukan (KotakIsi): tidak ada
  lagi "email tidak ditemukan" palsu atau isi tertukar antar email.
- Ekstraktor OTP tidak lagi menelan kode pos ("Kode pos: 40286") dan
  potongan nomor telepon (0812-345-678) sebagai kode verifikasi.
- Waktu email tempmail.is ditampilkan dalam zona waktu perangkat
  (dulu UTC mentah, meleset 7 jam untuk WIB).
- Tombol Ganti Email/Pakai tidak mengantre di belakang pindaian OTP
  polling (executor aksi terpisah) dan dijaga dari ketukan beruntun.
- Keadaan alamat kosong dijaga: tombol Salin tidak menyalin string
  kosong, polling tidak membuat sesi yatim.
- Kuki penghapusan dari server dihormati; alihan HTTP hanya diikuti
  untuk GET satu host; permintaan per sesi diserialkan.
- Respons non-JSON dari server dilaporkan dengan pesan yang jelas.

### Changed

- TempMailApi kini memakai HttpSesi: satu tumpukan HTTP untuk semua
  layanan (kebijakan timeout/batas body/tampang permintaan seragam).
- Semua teks statis layout dipindah ke strings.xml.
- CI: unit test tidak lagi jalan dua kali tiap rilis; artefak debug
  beretensi 7 hari; langkah terbit rilis idempoten; semua action
  GitHub di-pin ke SHA commit.

### Removed

- Kode mati: AddressGenerator.randomAddress() dan sisa komentar basi
  pasca-pencabutan layanan net.

## [0.7.2] - 2026-11-25

### Removed

- Layanan tempmail.net dan tempail.com dicabut atas keputusan owner.
  Gerbang bot Cloudflare keduanya tetap menolak aplikasi dari
  perangkat (HTTP 403) walau header sudah disamarkan seperti
  peramban di v0.7.1, dan email uji ke tempmail.net tidak pernah
  tiba. Layanan yang tersisa: tempmail.plus, tempmail.is,
  tempmail.now.

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
