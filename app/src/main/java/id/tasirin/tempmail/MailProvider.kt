package id.tasirin.tempmail

// Satu layanan email sekali pakai. Layanan berbasis sesi menyimpan
// keadaan sesinya sendiri di dalam objek provider.
//
// KONTRAK BENANG (issue #65): alamatAcak, alamatCustom, fetchInbox,
// fetchDetail, dan domains() provider BERSESI boleh jaringan — panggil
// HANYA dari thread latar. Yang boleh dari thread UI hanya:
//   - domains() provider TANPA sesi (wajib daftar lokal konstan),
//   - alamatAktif() dan alamatServer() (wajib baca field @Volatile saja).
interface MailProvider {

    // Id stabil untuk simpanan preferensi, mis. "plus".
    val id: String

    // Nama tampil di pemilih layanan, mis. "tempmail.plus".
    val nama: String

    // True bila alamat tersimpan bisa dipakai lagi pada kunjungan
    // berikutnya (layanan tanpa sesi). False = tiap mulai, alamat baru
    // dibuat dari sesi baru.
    val tanpaSesi: Boolean

    // True bila pengguna bisa memilih nama alamat sendiri.
    val bisaCustom: Boolean

    // Domain yang bisa dipilih pengguna (untuk alamat acak/custom).
    // Kosong = tidak ada pilihan domain (pemilih domain disembunyikan).
    fun domains(): List<String>

    // Buat alamat acak baru dan jadikan alamat aktif provider.
    // domain = pilihan pengguna bila ada, boleh null.
    fun alamatAcak(domain: String?): String

    // Buat alamat dengan nama pilihan pengguna (hanya bila bisaCustom).
    fun alamatCustom(namaBersih: String, domain: String): String

    // Ambil daftar kotak masuk untuk alamat aktif.
    fun fetchInbox(alamat: String): List<MailItem>

    // Ambil isi lengkap satu email.
    fun fetchDetail(alamat: String, item: MailItem): MailDetail

    // Alamat yang dilaporkan server saat ini, bila berbeda dari yang
    // ditampilkan (mis. tempmail.now memutar alamat yang kedaluwarsa).
    // Null = tidak ada laporan.
    fun alamatServer(): String? = null

    // Alamat aktif provider saat ini (layanan berbasis sesi yang
    // sesinya masih hidup di proses ini). Null = belum ada.
    fun alamatAktif(): String? = null
}

// Layanan asal: tempmail.plus (API JSON, tanpa sesi — alamat bebas
// dipakai siapa pun yang tahu alamatnya).
class PlusProvider : MailProvider {

    override val id = "plus"
    override val nama = "tempmail.plus"
    override val tanpaSesi = true
    override val bisaCustom = true

    override fun domains(): List<String> = AddressGenerator.DOMAINS

    override fun alamatAcak(domain: String?): String {
        val d = domain?.takeIf { it in AddressGenerator.DOMAINS }
            ?: AddressGenerator.DOMAINS.random()
        return AddressGenerator.randomLocalPart() + "@" + d
    }

    override fun alamatCustom(namaBersih: String, domain: String): String {
        // Konsisten dengan alamatAcak: domain di luar daftar resmi
        // ditolak, bukan dirangkai buta jadi alamat yang inbox-nya
        // tidak akan pernah ada (issue #62).
        if (domain !in AddressGenerator.DOMAINS) {
            throw IllegalStateException("Domain tidak didukung tempmail.plus: $domain")
        }
        return "$namaBersih@$domain"
    }

    override fun fetchInbox(alamat: String): List<MailItem> = TempMailApi.fetchInbox(alamat)

    override fun fetchDetail(alamat: String, item: MailItem): MailDetail =
        TempMailApi.fetchDetail(alamat, item.id)
}

// Daftar semua layanan, urut tampil di pemilih.
// tempmail.net + tempail.com DICABUT di v0.7.2 atas keputusan owner:
// gerbang bot Cloudflare keduanya menolak aplikasi dari perangkat
// (HTTP 403) walau header sudah disamarkan seperti peramban, dan
// email uji ke tempmail.net tidak pernah tiba.
object Layanan {

    val semua: List<MailProvider> = listOf(
        PlusProvider(),
        IsProvider(),
        NowProvider()
    )

    fun cari(id: String): MailProvider = semua.firstOrNull { it.id == id } ?: semua[0]
}
