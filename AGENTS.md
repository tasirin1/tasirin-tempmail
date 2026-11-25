# AGENTS.md — Tasirin TempMail

Aturan kerja repo ini (owner: tasirin1):

- Aplikasi Android Kotlin orisinal; paket `id.tasirin.tempmail`.
- UI Bahasa Indonesia, komentar kode Bahasa Indonesia.
- Tema terang biru-putih (seragam Download Manager / VPN Rotasi).
- Build penuh lewat GitHub Actions; lokal hanya edit + cek sintaks ringan.
- Jangan commit secret/keystore. Keystore rilis = secret repo
  (`KEYSTORE_BASE64` + `STORE_PASSWORD`), format PKCS12.
- Perubahan kode wajib entri CHANGELOG (Keep a Changelog).
- Rilis = bump versi di `app/build.gradle.kts` + tag `vX.Y.Z`;
  satu maraton = satu versi rilis.
- API tempmail.plus: `GET /api/mails?email=&epin=&limit=20` dan
  `GET /api/mails/{mail_id}?email=&epin=`. Domain resmi ada di
  `AddressGenerator.DOMAINS` (dari halaman resmi tempmail.plus).
