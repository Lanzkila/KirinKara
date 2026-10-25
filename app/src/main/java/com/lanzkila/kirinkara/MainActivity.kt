package com.lanzkila.kirinkara

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lanzkila.kirinkara.audio.AudioConverter
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

/** No network permission and no storage permission: all imports/exports use Android's file picker. */
class MainActivity : Activity() {
    private data class Track(
        val uri: Uri,
        val name: String,
        var status: String = "Menunggu",
        var progress: Int = 0,
        var output: File? = null,
    )

    companion object {
        private const val OPEN_AUDIO = 1001
        private const val SAVE_WAV = 1002
        private val BG = 0xFF0D1020.toInt()
        private val PANEL = 0xFF181D35.toInt()
        private val PANEL_2 = 0xFF212741.toInt()
        private val TEXT = 0xFFF7F8FF.toInt()
        private val MUTED = 0xFFA2ABC9.toInt()
        private val PURPLE = 0xFF9F8CFF.toInt()
        private val MINT = 0xFF63E8D4.toInt()
    }

    private val tracks = mutableListOf<Track>()
    @Volatile private var cancelRequested = false
    private var busy = false
    private var saveTarget: Track? = null
    private var player: MediaPlayer? = null
    private var playTarget: Track? = null
    private var amount = 0.75f
    private var preserveBass = true

    private lateinit var trackList: LinearLayout
    private lateinit var summary: TextView
    private lateinit var processButton: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var info: TextView
    private lateinit var balancedButton: TextView
    private lateinit var strongButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0
        buildScreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Retain the running conversion and queue on screen rotations.
        buildScreen()
    }

    private fun buildScreen() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(30), dp(20), dp(42))
        }
        rootScroll.addView(root)
        setContentView(rootScroll)

        root.addView(text("KIRIN • AUDIO LAB", 12f, MINT, true, 1.2f))
        root.addView(text("KirinKara", 34f, TEXT, true).apply { topMargin(8) })
        root.addView(text("Ubah lagu menjadi instrumental terus di telefon.", 14f, MUTED)
            .apply { topMargin(4) })

        val hero = panel().apply { topMargin(26) }
        root.addView(hero)
        hero.addView(text("✦  STUDIO OFFLINE", 12f, MINT, true))
        hero.addView(text("Import lagu", 23f, TEXT, true).apply { topMargin(9) })
        hero.addView(text("Pilih fail MP3, M4A, AAC, FLAC, WAV, OGG atau OPUS. Sokongan sebenar bergantung pada peranti.", 13f, MUTED)
            .apply { topMargin(6) })
        hero.addView(action("＋  Pilih lagu", PURPLE, BG) { openAudioPicker() }.apply { topMargin(18) })

        val modeCard = panel().apply { topMargin(16) }
        root.addView(modeCard)
        modeCard.addView(text("Kekuatan buang vokal", 18f, TEXT, true))
        modeCard.addView(text("Kaedah stereo asas • bukan AI", 12f, MUTED).apply { topMargin(5) })
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; topMargin(14) }
        modeCard.addView(modeRow)
        balancedButton = option("Seimbang", modeRow) { amount = 0.75f; preserveBass = true; refreshMode() }
        strongButton = option("Kuat", modeRow) { amount = 0.97f; preserveBass = false; refreshMode() }
        modeCard.addView(text("Seimbang menjaga bunyi bass; Kuat membuang lebih banyak bunyi di tengah, termasuk sesetengah instrumen.", 12f, MUTED)
            .apply { topMargin(12) })
        refreshMode()

        val queueTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            topMargin(27)
        }
        root.addView(queueTitle)
        queueTitle.addView(text("Senarai lagu", 20f, TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        summary = text("0 lagu", 12f, MUTED)
        queueTitle.addView(summary)
        trackList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; topMargin(12) }
        root.addView(trackList)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(MINT)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(PANEL_2)
            topMargin(20)
        }
        root.addView(progressBar, LinearLayout.LayoutParams(-1, dp(7)))
        info = text("Pilih lagu untuk mula.", 12f, MUTED).apply { topMargin(10) }
        root.addView(info)
        processButton = action("▶  Mula proses", MINT, BG) { startOrCancel() }.apply { topMargin(17) }
        root.addView(processButton)
        root.addView(text("Fail output: WAV stereo 16-bit. Proses berjalan pada peranti; tiada muat naik internet.", 12f, MUTED)
            .apply { topMargin(16) })
        refreshQueue()
    }

    private fun openAudioPicker() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, OPEN_AUDIO)
    }

    @Deprecated("Handled here to keep the app free of third-party UI dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SAVE_WAV && (resultCode != RESULT_OK || data?.data == null)) {
            saveTarget = null
        }
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            OPEN_AUDIO -> {
                val newUris = buildList {
                    data.data?.let { add(it) }
                    data.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
                    }
                }.distinct()
                for (uri in newUris) {
                    if (tracks.any { it.uri == uri }) continue
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // The transient URI grant remains sufficient for this session.
                    }
                    tracks.add(Track(uri, displayName(uri)))
                }
                refreshQueue()
            }
            SAVE_WAV -> {
                val item = saveTarget
                val destination = data.data
                saveTarget = null
                if (item == null || destination == null) return
                val file = item.output ?: return
                thread(name = "kirinkara-save") {
                    try {
                        contentResolver.openOutputStream(destination, "wt")?.use { output ->
                            file.inputStream().use { it.copyTo(output, 64 * 1024) }
                        } ?: error("Tidak dapat membuka lokasi simpanan")
                        runOnUiThread {
                            item.status = "Disimpan"
                            refreshQueue()
                            toast("Instrumental berjaya disimpan")
                        }
                    } catch (e: Exception) {
                        runOnUiThread { toast("Gagal simpan: ${e.message}") }
                    }
                }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment ?: "Lagu"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "Lagu"
        }
    }

    private fun refreshMode() {
        decorateOption(balancedButton, preserveBass)
        decorateOption(strongButton, !preserveBass)
        balancedButton.alpha = if (busy) 0.5f else 1f
        strongButton.alpha = if (busy) 0.5f else 1f
    }

    private fun refreshQueue() {
        if (!::trackList.isInitialized) return
        summary.text = "${tracks.size} lagu"
        trackList.removeAllViews()
        if (tracks.isEmpty()) {
            trackList.addView(text("Belum ada lagu. Tekan ‘Pilih lagu’ untuk tambah.", 13f, MUTED)
                .apply { setPadding(dp(6), dp(17), dp(6), dp(17)) })
        }
        tracks.forEachIndexed { index, item ->
            val card = panel(PANEL).apply { topMargin(if (index == 0) 0 else 9) }
            trackList.addView(card)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            card.addView(row)
            row.addView(text("♫", 24f, PURPLE, true).apply {
                gravity = Gravity.CENTER
                background = shape(PANEL_2, 13f)
            }, LinearLayout.LayoutParams(dp(46), dp(46)))
            val songMeta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            row.addView(songMeta, LinearLayout.LayoutParams(0, -2, 1f))
            songMeta.addView(text(item.name, 14f, TEXT, true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            songMeta.addView(text("${item.status} ${if (item.status == "Memproses") "${item.progress}%" else ""}", 12f,
                if (item.output != null) MINT else MUTED).apply { topMargin(4) })

            if (item.output != null) {
                val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; topMargin(10) }
                card.addView(buttons)
                buttons.addView(smallButton(if (playTarget == item) "■ Henti" else "▷ Dengar") {
                    preview(item)
                }, LinearLayout.LayoutParams(0, dp(40), 1f))
                buttons.addView(smallButton("↓ Simpan") { save(item) },
                    LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(8) })
            } else if (!busy) {
                card.addView(smallButton("Buang dari senarai") {
                    tracks.remove(item)
                    refreshQueue()
                }.apply { topMargin(9) })
            }
        }
        processButton.text = if (busy) "■  Batal proses" else "▶  Proses ${tracks.count { it.output == null }} lagu"
        processButton.alpha = if (!busy && tracks.none { it.output == null }) 0.55f else 1f
    }

    private fun startOrCancel() {
        if (busy) {
            cancelRequested = true
            info.text = "Membatalkan selepas blok audio semasa…"
            return
        }
        val pending = tracks.filter { it.output == null }
        if (pending.isEmpty()) {
            toast("Pilih lagu baharu dahulu")
            return
        }
        stopPreview()
        busy = true
        cancelRequested = false
        refreshMode()
        refreshQueue()
        val chosenAmount = amount
        val chosenBass = preserveBass
        thread(name = "kirinkara-convert") {
            for ((index, item) in pending.withIndex()) {
                if (cancelRequested) break
                val directory = File(cacheDir, "instrumentals").apply { mkdirs() }
                val file = File(directory, "${UUID.randomUUID()}.wav")
                runOnUiThread {
                    item.status = "Memproses"
                    item.progress = 0
                    info.text = "Lagu ${index + 1}/${pending.size}: ${item.name}"
                    progressBar.progress = 0
                    refreshQueue()
                }
                try {
                    AudioConverter(applicationContext).convert(
                        item.uri, file, chosenAmount, chosenBass,
                        cancelled = { cancelRequested },
                        onProgress = { pct ->
                            runOnUiThread {
                                item.progress = pct
                                progressBar.progress = pct
                                // Refresh only every 10% to avoid recreating all song cards constantly.
                                if (pct % 10 == 0 || pct == 100) refreshQueue()
                            }
                        },
                    )
                    runOnUiThread {
                        item.output = file
                        item.status = "Sedia untuk disimpan"
                        refreshQueue()
                    }
                } catch (_: AudioConverter.Cancelled) {
                    runOnUiThread { item.status = "Dibatalkan"; refreshQueue() }
                    break
                } catch (e: Exception) {
                    runOnUiThread {
                        item.status = "Ralat: ${e.message ?: "gagal baca audio"}"
                        refreshQueue()
                    }
                }
            }
            runOnUiThread {
                busy = false
                info.text = if (cancelRequested) "Proses dihentikan." else "Selesai. Dengar dan simpan hasil di atas."
                refreshMode()
                refreshQueue()
            }
        }
    }

    private fun preview(item: Track) {
        if (playTarget == item) {
            stopPreview()
            refreshQueue()
            return
        }
        stopPreview()
        val file = item.output ?: return
        try {
            val newPlayer = MediaPlayer()
            newPlayer.setDataSource(file.absolutePath)
            newPlayer.prepare()
            newPlayer.setOnCompletionListener { stopPreview(); refreshQueue() }
            newPlayer.start()
            player = newPlayer
            playTarget = item
        } catch (e: Exception) {
            stopPreview()
            toast("Tidak dapat memainkan hasil: ${e.message}")
        }
        refreshQueue()
    }

    private fun stopPreview() {
        player?.release()
        player = null
        playTarget = null
    }

    private fun save(item: Track) {
        if (saveTarget != null) return
        saveTarget = item
        val filename = item.name.substringBeforeLast('.', item.name)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "KirinKara" } + "_instrumental.wav"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        try {
            startActivityForResult(intent, SAVE_WAV)
        } catch (e: Exception) {
            saveTarget = null
            toast("Tiada aplikasi fail untuk simpan: ${e.message}")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, spacing: Float = 0f) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            letterSpacing = spacing / 10
            includeFontPadding = true
        }

    private fun panel(color: Int = PANEL): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = shape(color, 21f)
    }

    private fun shape(color: Int, radius: Float, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun action(label: String, bg: Int, fg: Int, onClick: () -> Unit) = text(label, 15f, fg, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(16), dp(12), dp(16))
        background = shape(bg, 16f)
        setOnClickListener { onClick() }
        isClickable = true
        isFocusable = true
    }

    private fun option(label: String, parent: LinearLayout, click: () -> Unit): TextView {
        val view = text(label, 13f, TEXT, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(13), dp(7), dp(13))
            setOnClickListener { if (!busy) click() }
            isFocusable = true
        }
        parent.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        return view
    }

    private fun decorateOption(button: TextView, checked: Boolean) {
        button.background = shape(if (checked) PANEL_2 else PANEL, 12f, if (checked) PURPLE else 0xFF353B55.toInt())
        button.setTextColor(if (checked) PURPLE else MUTED)
    }

    private fun smallButton(label: String, click: () -> Unit) = text(label, 12f, MINT, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = shape(PANEL_2, 12f)
        setOnClickListener { click() }
        isFocusable = true
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun View.topMargin(value: Int) {
        // A child without layout params gets its margins when added to a LinearLayout.
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(value) }
    }

    override fun onStop() {
        super.onStop()
        stopPreview()
    }

    override fun onDestroy() {
        cancelRequested = true
        stopPreview()
        super.onDestroy()
    }
}
