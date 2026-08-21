# 🏛️ Arsitektur Teknis & Dokumentasi Kode Bloklan

Dokumen ini menjelaskan arsitektur internal, alur data level rendah (*low-level packet flow*), manipulasi biner protokol DNS & IPv4, mekanisme sinkhole, serta interaksi antar modul di dalam aplikasi **Bloklan**.

---

## 📐 Arsitektur Sistem Tingkat Tinggi

Bloklan dibangun dengan pola arsitektur **Clean Architecture & Unidirectional Data Flow (UDF)** yang dipadukan dengan pemrosesan paket jaringan asinkron non-blocking berbasis **Kotlin Coroutines**.

```
┌──────────────────────────────────────────────────────────┐
│                   UI LAYER (Jetpack Compose)             │
│   HomeScreen  NativePlayerScreen  LogScreen Rules Settings│
└────────────────────────────┬─────────────────────────────┘
                             │ Observes StateFlow / Emits Events
┌────────────────────────────▼─────────────────────────────┐
│                 DATA LAYER (AppRepository)                │
│    _isVpnActive  _stats  _queryLogs  _activeCategories   │
└──────────────┬─────────────────────────────┬─────────────┘
               │                             │
┌──────────────▼─────────────┐ ┌─────────────▼─────────────┐
│    NETWORK & VPN ENGINE    │ │        RULE ENGINE        │
│ • BloklanVpnService (TUN)  │ │ • RuleEngine.kt           │
│ • DnsPacketParser (IPv4)   │ │ • DefaultRules.kt         │
│ • DnsRelay (UDP Socket)    │ │ • Subdomain Trie / Matcher│
└──────────────┬─────────────┘ └───────────────────────────┘
               │
┌──────────────▼─────────────┐
│      STREAMING ENGINE      │
│ • YouTubeExtractor (API)   │
│ • ExoPlayer (Media3 View)  │
│ • AdBlockWebViewClient     │
└────────────────────────────┘
```

---

## 🔬 1. Mekanisme VPN & Pemrosesan Paket Jaringan

### A. Inisialisasi Antarmuka Virtual (TUN Interface)
Di dalam [BloklanVpnService.kt](file:///c:/Dishub/experiment/bloklan/app/src/main/java/com/bloklan/core/vpn/BloklanVpnService.kt):
- Menggunakan Android `VpnService.Builder`.
- Mengonfigurasi parameter:
  ```kotlin
  val builder = Builder()
      .setSession("Bloklan Ad Blocker")
      .setMtu(1500)
      .addAddress("10.10.10.2", 32)
      .addDnsServer("10.10.10.1")
      .addRoute("10.10.10.1", 32)
  ```
- **Catatan Penting**: Bloklan **hanya** menambahkan rute ke IP DNS virtual `10.10.10.1/32`. Rute lalu lintas internet umum (TCP/HTTPS) tetap berjalan langsung melalui koneksi seluler/Wi-Fi asli perangkat, sehingga:
  1. Kecepatan download/upload internet tidak berkurang.
  2. Baterai tidak boros karena aplikasi tidak perlu membaca paket data stream besar.
  3. Aplikasi sendiri dikecualikan menggunakan `builder.addDisallowedApplication(packageName)`.

### B. Packet Loop & Non-Blocking I/O
- `ParcelFileDescriptor` yang dihasilkan oleh `builder.establish()` menghasilkan `FileDescriptor` antarmuka TUN.
- Coroutine membaca stream dari `FileInputStream(pfd.fileDescriptor)` dalam buffer byte sebesar 32 KB (`ByteArray(32767)`).

---

## 📦 2. Parsing Protokol IPv4, UDP, dan DNS

Modul [DnsPacketParser.kt](file:///c:/Dishub/experiment/bloklan/app/src/main/java/com/bloklan/core/dns/DnsPacketParser.kt) bertanggung jawab atas decoding dan encoding biner level rendah tanpa menggunakan library eksternal, menjaga performa tetap optimal (zero overhead).

### Struktur Paket IPv4 + UDP + DNS:
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|Version|  IHL  |Type of Service|          Total Length         | -> IP Header (20 Bytes)
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         Source IP Address (e.g. 10.10.10.2)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       Destination IP Address (e.g. 10.10.10.1)                |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port (53)   | -> UDP Header (8 Bytes)
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          UDP Length           |        UDP Checksum (0x0000)  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         Transaction ID        |             Flags             | -> DNS Header (12 Bytes)
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          QDCOUNT (1)          |          ANCOUNT (0/1)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                 Question Section (QNAME, QTYPE, QCLASS)       | -> DNS Payload
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Algoritma Pembuatan Respons Sinkhole (Synthetic Blocked Packet)
Jika domain terdeteksi sebagai iklan:
1. Menukar IP Source dan IP Destination (`Src IP = 10.10.10.1`, `Dst IP = 10.10.10.2`).
2. Menukar Port UDP (`Src Port = 53`, `Dst Port = Original Src Port`).
3. Menyusun DNS Response Header:
   - **Tipe A (IPv4)**: Flag `0x8580` (`QR=1`, `AA=1`, `RD=1`, `RA=1`, `RCODE=0/NoError`), `ANCOUNT = 1`.
   - Mengisi DNS Answer Section dengan Record Tipe A bernilai IP `0.0.0.0` dan TTL 300 detik.
   - **Tipe Lain (AAAA, HTTPS, TXT)**: Flag `0x8183` (`RCODE=3 / NXDOMAIN`), `ANCOUNT = 0`.
4. Menghitung ulang **16-bit One's Complement IP Checksum** (RFC 791).
5. Menuliskan paket langsung kembali ke `FileOutputStream(pfd.fileDescriptor)`.

---

## ⚡ 3. DnsRelay & Socket Protection

Di dalam [DnsRelay.kt](file:///c:/Dishub/experiment/bloklan/app/src/main/java/com/bloklan/core/dns/DnsRelay.kt):
- Jika domain diizinkan (tidak diblokir), payload kueri DNS mentah harus dikirim ke DNS Upstream (contoh: Cloudflare `1.1.1.1` atau Google `8.8.8.8`).
- **Krusial:** Socket UDP yang dibuat harus diproteksi dengan memanggil:
  ```kotlin
  val socket = DatagramSocket()
  vpnService.protect(socket)
  ```
- Fungsi `protect(socket)` memberi instruksi ke kernel Linux Android agar paket socket tersebut keluar langsung melalui antarmuka fisik jaringan (Wi-Fi/LTE) dan tidak di-*looping* masuk kembali ke antarmuka VPN Bloklan.

---

## 🎯 4. Logika RuleEngine & Evaluasi Subdomain

Di dalam [RuleEngine.kt](file:///c:/Dishub/experiment/bloklan/app/src/main/java/com/bloklan/core/rules/RuleEngine.kt):
Algoritma pencocokan domain dirancang efisien dengan kompleksitas waktu $O(K)$ di mana $K$ adalah kedalaman label subdomain (bukan $O(N)$ terhadap seluruh total jutaan domain):

```kotlin
private fun isDomainInSet(domain: String, set: Set<String>): Boolean {
    if (set.isEmpty()) return false
    if (set.contains(domain)) return true

    // Evaluasi parent domain secara iteratif:
    // "ad.tracker.mobile.googleads.com" -> "tracker.mobile.googleads.com" -> "mobile.googleads.com" -> "googleads.com"
    var sub = domain
    while (true) {
        val dotIndex = sub.indexOf('.')
        if (dotIndex == -1 || dotIndex == sub.length - 1) break
        sub = sub.substring(dotIndex + 1)
        if (set.contains(sub)) {
            return true
        }
    }
    return false
}
```

---

## 📺 5. Arsitektur Ad-Free YouTube Media Player

Modul streaming video di Bloklan terdiri dari 2 pendekatan:

### A. Pendekatan Native Stream ([YouTubeExtractor.kt](file:///c:/Dishub/experiment/bloklan/app/src/main/java/com/bloklan/core/youtube/YouTubeExtractor.kt) + [NativePlayerScreen.kt](file:///c:/Dishub/experiment/bloklan/app/src/main/java/com/bloklan/ui/screens/NativePlayerScreen.kt))
1. **Ekstraksi Metadata & Stream**:
   - Berkomunikasi dengan instance Invidious API terbuka via OkHttp.
   - Mengambil URL direct video stream `.mp4` atau `.m3u8` (HLS) resolusi adaptif.
2. **Playback Rendering**:
   - Menggunakan **AndroidX Media3 ExoPlayer** (`PlayerView`) yang disematkan ke dalam Compose via `AndroidView`.
   - Mengelola lifecycle pemutaran video secara otomatis (`DisposableEffect` membersihkan resource saat keluar dari layar).
   - Memastikan 0% injeksi script iklan dan 0 telemetry tracking.

### B. Pendekatan Embedded Web Engine ([WebPlayerScreen.kt](file:///c:/Dishub/experiment/bloklan/app/src/main/java/com/bloklan/ui/screens/WebPlayerScreen.kt))
- Menggunakan `WebView` kustom dengan `AdBlockWebViewClient`.
- Mencegat semua request resource web melalui `shouldInterceptRequest()` dan mengembalikan stream byte kosong (`WebResourceResponse`) jika URL menuju ke server iklan (`googleads`, `doubleclick`, `/pagead/`, dll.).
- Menginjeksi script JavaScript `AdBlockScripts.JS_YOUTUBE_AD_SKIPPER` yang memantau DOM menggunakan `MutationObserver` untuk mendeteksi dan melewati tombol iklan secara instan (0 milidetik).

---

## 🔄 6. Manajemen State Reaktif (`AppRepository`)

Seluruh state aplikasi dikelola secara terpusat di [AppRepository.kt](file:///c:/Dishub/experiment/bloklan/app/src/main/java/com/bloklan/data/repository/AppRepository.kt) sebagai **Singleton Pattern** dengan `StateFlow`:

| StateFlow Property | Tipe Data | Deskripsi |
| :--- | :--- | :--- |
| `isVpnActive` | `StateFlow<Boolean>` | Status aktif/tidaknya layanan VPN lokal |
| `stats` | `StateFlow<VpnStats>` | Hitungan total kueri, total blokir, & persentase |
| `queryLogs` | `StateFlow<List<DnsQueryLog>>` | List 200 kueri DNS terakhir |
| `selectedDns` | `StateFlow<DnsServerConfig>` | Server DNS Upstream yang sedang aktif |
| `activeCategories`| `StateFlow<Set<FilterCategory>>` | Kumpulan kategori filter yang diaktifkan user |
| `customWhitelist` | `StateFlow<Set<String>>` | Set domain yang selalu diizinkan |
| `customBlacklist` | `StateFlow<Set<String>>` | Set domain yang selalu diblokir |

---

## 🎨 7. Sistem Desain Antarmuka (UI Design System)

Mengadopsi tema **Cyberpunk Dark Mode**:
- **Background Utama**: `BgDark` (`#0B0F19`)
- **Card Background**: `CardBgDark` (`#151C2E`)
- **Card Border**: `CardBorderDark` (`#222D46`)
- **Primary Accent (Status Aktif / Whitelist)**: `PrimaryNeon` (`#00E676` / Neon Green)
- **Secondary Accent (Info / Upstream)**: `SecondaryNeon` (`#00B0FF` / Neon Cyan)
- **Danger Accent (Status Blokir / Blacklist)**: `DangerRed` (`#FF3D71` / Neon Red)
- **Animasi**: Pulsasi lingkaran pelindung berulang (`rememberInfiniteTransition`) saat VPN aktif untuk memberikan respons visual yang hidup (*alive interface*).

---

## 🧪 8. Panduan Pengujian & Verifikasi

### Pengujian Unit & Logika Jaringan
- **DnsPacketParserTest**: Memverifikasi akurasi parsing header IPv4 dan rekonstruksi biner respons `0.0.0.0`.
- **RuleEngineTest**: Memverifikasi evaluasi pencocokan subdomain bertingkat serta prioritas Whitelist di atas Blacklist.
- **InvidiousExtractorTest**: Memverifikasi respons JSON dan fallback antar-instance ketika salah satu instance publik mengalami timeout.
