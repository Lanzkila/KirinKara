# KirinKara 🎤 → 🎼

**KirinKara** ialah aplikasi Android untuk mengurangkan vokal lagu dan mengeksport versi instrumental. Projek masih pada **MVP v0.1.0**.

## Apa yang sudah berfungsi

- 📂 Pilih **satu atau beberapa lagu** dari storan telefon menggunakan Android File Picker.
- 🎧 Terima format yang boleh dinyahkod oleh Android, lazimnya **MP3, M4A/AAC, WAV, FLAC, OGG/Vorbis dan OPUS**. Sokongan bergantung pada versi Android dan codec peranti.
- 🎚️ Dua mod stereo: **Seimbang** (kurangkan vokal sambil lindungi bass rendah) dan **Kuat** (hapus lebih banyak komponen tengah).
- ⚙️ Proses berturutan secara offline dengan peratus kemajuan, pembatalan dan pengendalian ralat setiap fail.
- ▶️ Dengar hasil dalam aplikasi.
- 💾 Simpan fail instrumental **WAV 16-bit stereo** ke lokasi pilihan menggunakan Android File Picker. Tiada akses storan menyeluruh atau akaun diperlukan.

> **Penting tentang kualiti:** Ini ialah algoritma *center-channel attenuation* (mid/side stereo), **bukan AI vocal separation**. Vokal tidak semestinya hilang sepenuhnya. Instrumen yang berada di tengah stereo boleh turut berkurang, dan lagu mono tidak boleh diproses dengan kaedah ini. Kaedah AI seperti Demucs/MDX memerlukan model dan enjin inferens tambahan, yang belum ada dalam MVP ini.

## Skrin aplikasi

1. Tekan **Pilih lagu** dan import satu atau beberapa fail muzik.
2. Pilih **Seimbang** atau **Kuat**.
3. Tekan **Mula proses**. Kekalkan aplikasi di depan sehingga siap (tiada foreground service lagi).
4. Tekan **Dengar** untuk uji hasil.
5. Tekan **Simpan** untuk eksport hasil WAV; setiap lagu disimpan secara berasingan.

## Teknologi

| Bahagian | Pelaksanaan |
|---|---|
| Bahasa | Kotlin |
| Android | minSdk 26 (Android 8), compileSdk 35 |
| Input | `MediaExtractor` + `MediaCodec` decoder Android |
| DSP | `CenterReducer` (mid/side dan pilihan bass-preserving high-pass) |
| Output | Penulis WAV streaming PCM 16-bit |
| Fail | Storage Access Framework (SAF), tiada storage permission |
| Antara muka | Android Views native, tema gelap responsif |

## Bina APK

Repo mempunyai GitHub Actions `.github/workflows/android.yml` yang menjalankan unit test dan membina **APK debug** pada setiap push ke `main`. Buka tab **Actions** → `Android debug APK` → run berjaya → muat turun artifact `KirinKara-debug-apk`. Artifact ialah binaan ujian, **bukan** release bertandatangan.

Untuk Android Studio, buka folder projek ini, gunakan **JDK 17**, **Gradle 8.13** dan SDK Android 35. Jika Gradle belum terpasang, gunakan pemasangan Gradle IDE atau `gradle :app:assembleDebug` selepas SDK disediakan.

Jalankan ujian logik:

```bash
gradle :app:testDebugUnitTest
```

## Langkah seterusnya

- Integrasi model **AI source separation** sebenar (vokal/instrumental berasingan) dan pilihan pemprosesan pada peranti berprestasi tinggi.
- Export MP3/AAC/FLAC, job service latar belakang, batch save dan sejarah projek.
- Waveform, trim dan latihan nyanyian karaoke dengan lirik.

KirinKara tidak memuat naik lagu ke mana-mana pelayan. Hanya proses fail yang anda berhak gunakan.
