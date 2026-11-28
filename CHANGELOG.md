# Changelog

Semua perubahan penting proyek ini dicatat di berkas ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versi mengikuti [Semantic Versioning](https://semver.org/).

## [0.11.1] - 2026-11-28

### Fixed

- Waktu email Catchmail tampil kosong: API mengirim pecahan
  detik 6 digit (mikro, mis. "...49.753000+00:00") yang ditolak
  parser tanggal ketat sebagai milidetik di luar rentang 0-999,
  sehingga seluruh penguraian waktu gagal. Pecahan kini dipotong
  ke 3 digit milidetik sebelum diurai. Tag v0.11.0 gagal di CI
  tepat pada tes kasus ini dan tidak pernah terbit — rilis ini
  rilis pertama yang memuat layanan Catchmail.

## [0.11.0] - 2026-11-28

### Added

- Layanan ketujuh: **Catchmail** (catchmail.io) — dipilih owner
  dari saran yang diverifikasi langsung (ketiga kandidat disetujui,
  hanya Catchmail yang lolos bukti ujung-ke-ujung). API JSON tanpa
  sesi dan tanpa kunci: daftar kotak masuk dan isi email (teks,
  atau HTML yang diubah ke teks) lewat `api.catchmail.io/api/v1`.
  Alamat nama sendiri di 3 domain: catchmail.io, mailistry.com,
  zeppost.com. Pengirim berbentuk "<alamat>" dibersihkan ke alamat
  polos, waktu ISO 8601 ditampilkan sebagai tanggal + jam lokal.
- Kandidat lain yang disetujui owner tapi BELUM lolos bukti, jadi
  belum dirilis: **inboxes.com** dan **mailforspam.com** — API
  keduanya hidup dan inbox kosong terbaca, tetapi dua email uji
  dari Gmail tidak pernah tiba sesudah 48 kali pengecekan selama
  ±14 menit (tanpa pemberitahuan pantulan dari Gmail). Keduanya
  dicatat sebagai kandidat tertunda, bukan layanan aktif.

### Tests

- CatchmailProviderTest baru: daftar domain, komposisi alamat
  acak/custom + penolakan domain asing, pembersih pengirim kurung
  sudut (contoh persis email uji sungguhan), dan format waktu ISO
  berpecahan mikro (stempel persis email uji sungguhan).

## [0.10.2] - 2026-11-25

### Changed

- Kolom alamat email dipindah tepat ke bawah tombol Salin/Ganti
  Email; pemilih layanan kini sesudah kolom alamat (permintaan
  owner dari uji perangkat v0.10.1). Urutan kartu kini: tombol
  salin/ganti, alamat, layanan, lalu nama sendiri.

## [0.10.1] - 2026-11-25

### Changed

- Tombol **Salin Email** dan **Ganti Email** dipindah ke paling
  atas kartu alamat, di atas pemilih layanan (permintaan owner
  dari uji perangkat v0.10.0). Urutan kartu kini: tombol salin/
  ganti, layanan, alamat, lalu nama sendiri. Id tombol tidak
  berubah, jadi kabel klik di MainActivity tidak berubah.

## [0.10.0] - 2026-11-25

### Added

- Layanan kelima: **DropMail** (dropmail.me) — dipilih owner dari
  saran yang diverifikasi langsung. API GraphQL berbasis token +
  sesi: token gratis dibuat lewat API tanpa akun (umur 1 hari —
  umur panjang ditolak gerbang captcha/credits, jadi token mati
  dibuat ulang diam-diam), sesi ±10 menit yang diperpanjang tiap
  akses dan dipulihkan otomatis bila mati, dengan alamat baru
  dilaporkan ke UI. Nama alamat ditentukan server (domain
  berganti-ganti, mis. emlpro.com / dropmail.me / 10mail.org);
  isi email ikut di daftar sebagai teks polos. HttpSesi kini
  punya postJson — pembuat token dan GraphQL DropMail menolak
  body form (HTTP 400).
- Layanan keenam: **Mailnesia** (mailnesia.com) — dipilih owner
  dari saran yang diverifikasi langsung. Kotak masuk publik
  menurut nama yang dibaca lewat feed RSS: tiap item membawa
  subjek + pengirim, isi teks lengkap, tanggal, dan id pesan,
  jadi tidak ada panggilan detail terpisah. Alamat nama sendiri
  @mailnesia.com (satu domain).
- Keduanya terbukti ujung-ke-ujung sebelum dirilis: email uji
  berkode tiba, terbaca, dan kodenya terdeteksi dari subjek
  maupun isi.

### Tests

- DropMailProviderTest baru: daftar domain kosong, penolakan
  nama sendiri, dan format waktu ISO GraphQL (termasuk stempel
  email uji sungguhan).
- MailnesiaProviderTest baru: daftar domain, komposisi alamat
  acak/custom, penolakan domain asing, format waktu RSS, dan
  pengurai RSS atas salinan persis feed email uji sungguhan
  (termasuk subjek yang memuat pemisah dan id non-angka yang
  jatuh ke id sintetis).

## [0.9.0] - 2026-11-25

### Added

- Layanan keempat: **InboxKitten** (inboxkitten.com) — dipilih
  owner dari saran yang diverifikasi langsung. API JSON tanpa
  sesi dan tanpa kunci; alamat nama sendiri @inboxkitten.com
  (satu domain); daftar inbox membawa kepala email Mailgun
  (pengirim/subjek/waktu epoch), isi diambil per email lewat
  getHtml dan dibersihkan dengan htmlToText yang sama seperti
  jalur HTML tempmail.plus. Terbukti ujung-ke-ujung sebelum
  dirilis: email uji berkode tiba, terbaca, dan kodenya
  terdeteksi dari subjek maupun isi. Kandidat lain yang dicek
  hari yang sama: tempmail.lol kini mewajibkan kunci akun,
  mail.gw tidak terjangkau, 1secmail sudah mati.

### Tests

- KittenProviderTest baru: daftar domain, komposisi alamat
  acak/custom, penolakan domain asing, dan format waktu dari
  epoch detik (termasuk stempel email uji sungguhan).

## [0.8.10] - 2026-11-25

Hasil Maraton-16 (Audit-16: 4 temuan, issue #153–#156 — semuanya
Rendah: konstanta FNV yang keliru tulis dan sisa kalibrasi daftar
tertutup serta elipsis; Area B bersih empat ronde berturut-turut).

### Fixed

- tempmail.plus: konstanta basis fnv1a dibetulkan ke nilai
  FNV-1a standar 0x811C9DC5 — nilai awal sebelumnya salah tulis
  (0xE9A4CA31) sehingga klaim "FNV-1a" tidak cocok dengan
  rujukan mana pun, walau sifat pemisah tabrakannya tetap
  bekerja (issue #153).
- Penjaga OTP: "etc." masuk daftar singkatan bukan-batas —
  terekspos oleh ambang 3 huruf baru (issue #154); penghubung
  baris ditambah yaitu/yakni/namely (issue #155); penilaian
  batas kalimat kini melompati rentetan titik, jadi kalimat
  yang berakhir elipsis tetap memutus ikatan label
  (issue #156).

### Tests

- Tes baru: "etc." bukan batas; penghubung yaitu/yakni
  sesudah baris baru tidak memutus ikatan; elipsis sesudah kata
  penutup tetap batas kalimat.

## [0.8.9] - 2026-11-25

Hasil Maraton-15 (Audit-15: 5 temuan, issue #148–#152 — semuanya
Rendah: sidik ganda plus yang ternyata tidak merdeka, dan tepi
kalibrasi aturan batas kalimat struktural; Area B bersih tiga
ronde berturut-turut).

### Fixed

- tempmail.plus: komponen kedua sidik id sintetis diganti
  FNV-1a. Dua hashCode polinomial basis-31 atas susunan kolom
  berbeda ternyata tidak merdeka — tabrakan satu kolom
  berpanjang sama (subjek "Aa" vs "BB") menembus keduanya
  sekaligus; FNV-1a berstruktur beda sehingga kelas tabrakan
  itu tidak lolos (issue #148).
- Penjaga OTP: ambang kata penutup batas kalimat turun ke
  3 huruf — kalimat berakhir "ini"/"itu" yang sangat umum
  tidak lagi menelan OTP kalimat berikut (issue #149);
  singkatan alamat/administrasi prov, kec, kel, kab, yth, alm
  masuk daftar bukan-batas (issue #151); kalimat baru berhuruf
  kecil sesudah kata penutup panjang (>=5 huruf) ikut dianggap
  batas kalimat (issue #150); kata penghubung tunggal sesudah
  baris baru (adalah/is/ialah) adalah kelanjutan label, bukan
  kalimat baru (issue #152).

### Tests

- Tes baru: kata penutup 3 huruf umum adalah batas; singkatan
  prov/kec bukan batas; kalimat berhuruf kecil sesudah kata
  panjang adalah batas; penghubung tunggal sesudah baris baru
  tidak memutus ikatan label.

## [0.8.8] - 2026-11-25

Hasil Maraton-14 (Audit-14: 5 temuan, issue #143–#147 — semuanya
Rendah: tabrakan sidik id sintetis dan kalibrasi pemutus ikatan
label kode pos; Area B bersih dua ronde berturut-turut).

### Fixed

- tempmail.plus: sidik id sintetis digandakan — dua hash dari
  susunan kolom berbeda plus panjang tripel. Satu hashCode
  32-bit saja bisa bertabrakan antar email berbeda (subjek "Aa"
  vs "BB") dan email kedua lalu tidak pernah dipindai; kini
  tabrakan butuh kedua hash sekaligus (issue #143).
- Penjaga OTP: deteksi batas kalimat menjadi struktural — titik
  hanya batas kalimat bila kata sebelumnya minimal 4 huruf,
  bukan singkatan, dan diikuti huruf kapital/angka/akhir teks;
  singkatan di luar daftar (dsb./dll./tgl./PT.) tidak lagi
  memutus ikatan label secara keliru (issue #144). Titik tanpa
  kata sebelumnya ("Kode pos.", elipsis) bukan batas kalimat
  (issue #145). Penjaga konteks kini memakai pemutus ikatan
  yang sama dengan jalur cadangan termasuk aturan baris baru
  (issue #146), dan baris baru memutus ikatan juga bila kata
  nyata berada sesudahnya — label yang menutup barisnya sendiri
  tidak lagi mengikat kalimat OTP di baris berikut
  (issue #147).

### Tests

- Tes baru: singkatan di luar daftar bukan batas; titik
  menempel sesudah frasa bukan batas; frasa pos baris
  sebelumnya tidak menelan OTP konteks; label penutup baris
  tidak mengikat kalimat baris berikut.

## [0.8.7] - 2026-11-25

Hasil Maraton-13 (Audit-13: 4 temuan, issue #139–#142 — semuanya
Rendah: penyelesaian akar keluarga id sintetis, dan kalibrasi
pemutus batas kalimat di kedua penjaga OTP; Area B bersih).

### Fixed

- tempmail.plus: id sintetis email tanpa `mail_id` kini sidik
  isi saja — pembeda peringkat kembar dibuang. Dua email
  bertripel identik memang tidak bisa dibedakan secara stabil
  (tiga percobaan posisi/peringkat selalu bergeser pada mutasi
  daftar tertentu), dan tidak perlu: kode untuk item tanpa-id
  hanya berasal dari subjek yang menjadi bagian sidik itu
  sendiri. Id kini stabil terhadap semua mutasi daftar
  (issue #139).
- Penjaga OTP: deteksi batas kalimat kini sadar singkatan —
  titik pada "Jl."/"hal."/"No." dan inisial bukan lagi batas
  kalimat, jadi ikatan label kode pos tidak putus oleh singkatan
  (issue #140); baris baru tepat sesudah label ("Postal code:"
  lalu nilainya di baris berikut) bukan pemutus ikatan, hanya
  baris baru yang didahului kata nyata yang memutus
  (issue #141); penjaga jalur konteks kini ikut memutus ikatan
  pada batas kalimat, selaras jalur cadangan — OTP 4 digit
  tanpa pemicu sesudah kalimat kode pos tidak lagi tertelan
  (issue #142).

### Tests

- Tes baru: titik singkatan bukan batas kalimat; baris baru
  sesudah label bukan pemutus; frasa pos kalimat sebelumnya
  tidak menelan OTP konteks.

## [0.8.6] - 2026-11-25

Hasil Maraton-12 (Audit-12: 4 temuan, issue #135–#138 — semuanya
Rendah: identitas id sintetis, satu jalur refresh yang belum
tergerbang, dan dua celah tepi penjaga label kode pos).

### Fixed

- tempmail.plus: id sintetis email tanpa `mail_id` kini memakai
  peringkat di antara email berkembar-isi yang sama, bukan posisi
  daftar — posisi terbukti bukan identitas (indeks ekor ikut
  bergeser saat jendela 20 item meluncur atau item ekor terhapus).
  Item tanpa-id tunggal selalu berperingkat sama apa pun mutasi
  daftarnya (issue #135).
- `setEmail` hanya menyegarkan langsung saat layar aktif —
  penyelesaian latar (pembuatan alamat, rotasi alamat server)
  yang mendarat saat layar terjeda tidak lagi melahirkan
  pengambilan inbox di latar; penyegaran tertunda ke rantai poll
  saat layar kembali (issue #136).
- Penjaga OTP jalur cadangan: jendela pandang-balik label
  diperlebar 40 → 120 karakter (frasa kode pos berpenghubung
  panjang tidak lagi lolos sebagai OTP), dan batas kalimat kini
  memutus ikatan label (frasa kode pos di kalimat sebelumnya
  tidak lagi menelan OTP tanpa pemicu di kalimat ini)
  (issue #137, #138).

### Tests

- Tes baru: kode pos berpenghubung panjang dilewati; batas
  kalimat memutus ikatan label.

## [0.8.5] - 2026-11-25

Hasil Maraton-11 (Audit-11: 6 temuan, issue #129–#134 — semuanya
Rendah: sisa stabilitas id sintetis, satu cabang polling yang belum
tergerbang, dan penyempurnaan penjaga kode pos jalur cadangan).

### Fixed

- tempmail.plus: posisi pada id sintetis email tanpa `mail_id`
  kini dihitung dari EKOR daftar — email baru yang menumpuk di
  kepala tidak lagi menggeser id item lama, jadi kode lama tidak
  tersalin ulang menimpa kode yang lebih baru (issue #129).
- Cabang segera penjadwal refresh tertunda ikut digerbang keadaan
  polling — penyelesaian refresh yang tiba sesudah layar dijeda
  tidak lagi melahirkan satu pengambilan di latar (issue #130).
- Penjaga OTP jalur cadangan disempurnakan: semua kandidat
  ditelusuri (kandidat pertama yang ditolak penjaga tidak lagi
  membunuh kandidat sah berikutnya); label kode pos berbasis
  frasa — label berjarak ("kode pos anda adalah 123456") ikut
  dikenali, label milik angka lain tidak menelan kode berikutnya;
  kata "zip" di dalam unzip/gzip atau sebagai kata biasa bukan
  lagi label, label telanjang ZIP/Postal wajib titik dua; sufiks
  posesif Sunda "-na" ikut dikenali (issue #131, #132, #133,
  #134).

### Tests

- Tes baru: kandidat sah sesudah kode pos tetap ketemu; kode pos
  berjarak dilewati di jalur cadangan; "unzip"/"file zip" tidak
  menelan kode; "kode posna" dilewati.

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
