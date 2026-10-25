package com.lf2ide.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.lf2ide.mobile.core.Crypto
import com.lf2ide.mobile.core.FrameParser
import com.lf2ide.mobile.core.Tokenizer

class MainActivity : AppCompatActivity() {

    /** OpenDocument + izin tulis permanen, supaya "Simpan" bisa menimpa file aslinya. */
    private class OpenDoc : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
    }

    private val openLauncher = registerForActivityResult(OpenDoc()) { uri -> if (uri != null) openUri(uri) }
    private val saveAsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) writeTo(uri, true)
    }

    private lateinit var editor: EditText
    private lateinit var status: TextView
    private var ready = false
    private var loading = false
    private var docUri: Uri? = null
    private var docName = "Baru.dat"
    private var encrypted = false
    private var dirty = false
    private var info = ""
    private var hl = Highlighter("object_dat")
    private var hlA = -1
    private var hlB = -1
    private var needForce = false
    private val handler = Handler(Looper.getMainLooper())
    private val hlRun = Runnable { highlightVisible(needForce); needForce = false }
    private val infoRun = Runnable { computeInfo() }

    private val BG = 0xFF1E2327.toInt()
    private val PANEL = 0xFF262C31.toInt()
    private val FG = 0xFFD6DDE3.toInt()
    private val DIM = 0xFF8A96A0.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }

        val bar = LinearLayout(this).apply { setBackgroundColor(PANEL); setPadding(dp(6), dp(6), dp(6), dp(6)) }
        bar.addView(button("Buka") { openLauncher.launch(arrayOf("*/*")) })
        bar.addView(button("Baru") { v -> newMenu(v) })
        bar.addView(button("Simpan") { save() })
        val barScroll = HorizontalScrollView(this).apply { addView(bar); isHorizontalScrollBarEnabled = false }
        root.addView(barScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        editor = object : EditText(this@MainActivity) {
            override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                super.onSelectionChanged(selStart, selEnd)
                if (ready) updateStatus()
            }
        }.apply {
            setBackgroundColor(BG)
            setTextColor(FG)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.TOP or Gravity.START
            setHorizontallyScrolling(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isVerticalScrollBarEnabled = true
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnScrollChangeListener { _, _, _, _, _ -> scheduleHl(false) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (loading) return
                    dirty = true
                    scheduleHl(true)
                    handler.removeCallbacks(infoRun)
                    handler.postDelayed(infoRun, 900)
                }
            })
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        status = TextView(this).apply {
            setBackgroundColor(PANEL); setTextColor(DIM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(4), dp(10), dp(4)); maxLines = 1
        }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        ready = true
        updateStatus()
    }

    // ---------- UI helper ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: (View) -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick(it) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun newMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        for (n in listOf("Char", "Weapon", "Stage", "Attack", "BG")) pm.menu.add(n)
        pm.setOnMenuItemClickListener { item ->
            val n = item.title.toString()
            val text = assets.open("templates/$n.dat").use { String(it.readBytes(), Charsets.ISO_8859_1) }
            load(text, "Baru_$n.dat", null, false)
            true
        }
        pm.show()
    }

    // ---------- buka / simpan ----------
    private fun openUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) { /* tidak semua provider mendukung */ }
        val bytes = try { contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
        if (bytes == null) { toast("Gagal membuka file"); return }
        val name = DocumentFile.fromSingleUri(this, uri)?.name ?: "file.dat"
        var text = String(bytes, Charsets.ISO_8859_1)
        var enc = false
        val isData = Regex("""\.(dat|txt)$""", RegexOption.IGNORE_CASE).containsMatchIn(name)
        if (isData && !PLAIN.containsMatchIn(text)) { text = Crypto.decrypt(bytes); enc = true }
        load(text, name, uri, enc)
    }

    private fun load(text: String, name: String, uri: Uri?, enc: Boolean) {
        loading = true
        editor.setText(text)
        editor.setSelection(0)
        loading = false
        docUri = uri; docName = name; encrypted = enc; dirty = false; info = ""
        hl = Highlighter(langFor(name, text))
        hlA = -1; hlB = -1
        editor.scrollTo(0, 0)
        editor.post { highlightVisible(true) }
        computeInfo()
        updateStatus()
    }

    private fun langFor(name: String, text: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".as") -> "as"
            n == "data.txt" -> "data_txt"
            "stage" in n || "<phase>" in text -> "stage_dat"
            Regex("""\bbg\b|background""").containsMatchIn(n) -> "bg_dat"
            else -> "object_dat"
        }
    }

    private fun bytesToSave(): ByteArray {
        val t = editor.text.toString()
        return if (encrypted) Crypto.encrypt(t) else t.toByteArray(Charsets.ISO_8859_1)
    }

    private fun save() {
        val u = docUri
        if (u == null) { saveAsLauncher.launch(docName); return }
        if (!writeTo(u, false)) saveAsLauncher.launch(docName)
    }

    private fun writeTo(uri: Uri, isNew: Boolean): Boolean {
        return try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytesToSave()) } ?: return false
            docUri = uri
            if (isNew) docName = DocumentFile.fromSingleUri(this, uri)?.name ?: docName
            dirty = false
            updateStatus()
            toast("Tersimpan: $docName")
            true
        } catch (e: Exception) {
            toast("Gagal menyimpan: ${e.message}")
            false
        }
    }

    // ---------- pewarnaan jendela terlihat ----------
    private fun scheduleHl(force: Boolean) {
        if (force) needForce = true
        handler.removeCallbacks(hlRun)
        handler.postDelayed(hlRun, 60)
    }

    private fun highlightVisible(force: Boolean) {
        val lay = editor.layout ?: return
        val va = lay.getLineForVertical(editor.scrollY)
        val vb = lay.getLineForVertical(editor.scrollY + editor.height)
        val last = lay.lineCount - 1
        val inside = hlA >= 0 && (hlA == 0 || va >= hlA + 10) && (hlB >= last || vb <= hlB - 10)
        if (!force && inside) return
        val a = maxOf(0, va - 40)
        val b = minOf(last, vb + 40)
        hl.apply(editor.text, lay.getLineStart(a), lay.getLineEnd(b))
        hlA = a; hlB = b
    }

    // ---------- status ----------
    private fun computeInfo() {
        val t = editor.text.toString()
        Thread {
            var n = 0
            var bad = 0
            for (fr in Tokenizer.getFrames(t)) {
                n++
                try { if (FrameParser.read(fr) == null) bad++ } catch (e: Exception) { bad++ }
            }
            val s = "$n frame" + (if (bad > 0) ", $bad error" else "") + (if (encrypted) " · terenkripsi" else "")
            runOnUiThread { info = s; updateStatus() }
        }.start()
    }

    private fun updateStatus() {
        val lay = editor.layout
        var ln = 1
        var col = 1
        if (lay != null) {
            val s = maxOf(0, editor.selectionStart)
            val l = lay.getLineForOffset(s)
            ln = l + 1
            col = s - lay.getLineStart(l) + 1
        }
        status.text = "$docName${if (dirty) " ●" else ""}   Ln $ln, Col $col   $info"
    }

    companion object {
        private val PLAIN = Regex("""<bmp_begin>|<frame>|<stage>|<background>|^\s*#|\bname:""", RegexOption.MULTILINE)
    }
}
