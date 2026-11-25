package id.tasirin.tempmail

// Cache isi email menurut id pesan, dipakai bersama oleh provider
// berbasis sesi (dulu pola ini diduplikasi nyaris verbatim di tiap
// provider — issue #57).
//
// Aman lintas thread: peta diganti utuh (tukar referensi @Volatile),
// tidak pernah clear()+isi ulang di tempat — thread pembaca tidak
// pernah melihat keadaan setengah kosong (issue #40/#43).
class KotakIsi {

    @Volatile
    private var peta: Map<String, MailDetail> = emptyMap()

    operator fun get(id: String): MailDetail? = peta[id]

    // Ganti seluruh isi (provider yang mengambil daftar penuh).
    // Di dalam monitor yang sama dengan taruh/buang: tanpa ini, taruh
    // yang membaca peta lama bisa menuliskan gabungan basi SESUDAH
    // gantiSemua/kosongkan — entri yang dibuang hidup lagi (issue #90).
    fun gantiSemua(baru: Map<String, MailDetail>) {
        synchronized(this) { peta = baru }
    }

    // Tambahkan/gabung entri baru (provider delta: entri lama tetap).
    fun taruh(id: String, detail: MailDetail) {
        synchronized(this) { peta = peta + (id to detail) }
    }

    // Buang satu entri (pemangkasan daftar delta — issue #86).
    fun buang(id: String) {
        synchronized(this) { peta = peta - id }
    }

    fun kosongkan() {
        synchronized(this) { peta = emptyMap() }
    }
}
