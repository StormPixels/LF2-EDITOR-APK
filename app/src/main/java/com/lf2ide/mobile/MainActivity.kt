package com.lf2ide.mobile

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import android.widget.ImageButton
import android.widget.ImageView
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

    /** Pilih folder game dengan izin permanen, supaya sprite bisa dicari otomatis dari jalur di .dat. */
    private class OpenTree : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent =
            super.createIntent(context, input).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
    }

    private val treeLauncher = registerForActivityResult(OpenTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Exception) { }
            setRoot(uri, true)
        }
    }

    private val pickSprites = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        for (u in uris) sprites.addFromUri(u) { design.refresh() }
    }

    private lateinit var content: LinearLayout
    private var rootDoc: DocumentFile? = null
    private val dirCache = HashMap<String, List<DocumentFile>>()
    private var nameIndex: HashMap<String, DocumentFile>? = null
    private lateinit var sprites: SpriteStore
    private lateinit var design: DesignPanel
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var titleName: TextView
    private lateinit var titleStatus: TextView
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

    private val BG = 0xFF0B1220.toInt()
    private val PANEL = 0xFF111B2E.toInt()
    private val FG = 0xFFE2E8F0.toInt()
    private val DIM = 0xFF94A3B8.toInt()
    private val ACC = 0xFF22D3EE.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }

        titleName = TextView(this).apply { setTextColor(FG); textSize = 16f; setTypeface(typeface, Typeface.BOLD); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        titleStatus = TextView(this).apply { textSize = 12f; setPadding(dp(8), 0, dp(8), 0); maxLines = 1 }
        val titleBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(titleName, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(titleStatus)
        }
        val appBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(PANEL); setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(iconBtn(R.drawable.ic_menu) { v -> mainMenu(v) })
            addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconBtn(R.drawable.ic_folder) { treeLauncher.launch(null) })
            addView(iconBtn(R.drawable.ic_save) { save() })
            addView(iconBtn(R.drawable.ic_more) { v -> moreMenu(v) })
        }
        root.addView(appBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        editor = CodeEditText(this@MainActivity).apply {
            onSel = { if (ready) { updateStatus(); design.follow() } }
            setBackgroundColor(BG)
            setTextColor(FG)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.TOP or Gravity.START
            setHorizontallyScrolling(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isVerticalScrollBarEnabled = true
            setOnScrollChangeListener { _, _, _, _, _ -> scheduleHl(false) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (loading) return
                    dirty = true
                    if (ready) design.follow()
                    scheduleHl(true)
                    handler.removeCallbacks(infoRun)
                    handler.postDelayed(infoRun, 900)
                }
            })
        }
        sprites = SpriteStore(this)
        design = DesignPanel(this, sprites, editor) { pickSprites.launch(arrayOf("image/*")) }
        sprites.finder = { path -> findSprite(path) }
        getSharedPreferences("lf2", MODE_PRIVATE).getString("root", null)?.let { s ->
            val u = Uri.parse(s)
            if (contentResolver.persistedUriPermissions.any { it.uri == u }) setRoot(u, false)
        }
        content = LinearLayout(this)
        content.addView(design.root)
        content.addView(editor)
        applyOrientation()
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val keys = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(PANEL); setPadding(dp(4), dp(2), dp(4), dp(2)) }
        keys.addView(keyBtn("↶") { editor.onTextContextMenuItem(android.R.id.undo) })
        keys.addView(keyBtn("↷") { editor.onTextContextMenuItem(android.R.id.redo) })
        for ((lab, ins) in listOf("Tab" to "   ", "<" to "<", ">" to ">", ":" to ":", "_" to "_", "-" to "-", "#" to "#")) keys.addView(keyBtn(lab) { insertText(ins) })
        root.addView(HorizontalScrollView(this).apply { addView(keys); isHorizontalScrollBarEnabled = false },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        status = TextView(this).apply {
            setBackgroundColor(PANEL); setTextColor(DIM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(4), dp(10), dp(4)); maxLines = 1
        }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        ready = true
        updateStatus()
    }

    // ---------- folder game & pencarian sprite ----------
    private fun setRoot(uri: Uri, fresh: Boolean) {
        rootDoc = DocumentFile.fromTreeUri(this, uri)
        synchronized(dirCache) { dirCache.clear() }
        nameIndex = null
        if (fresh) {
            getSharedPreferences("lf2", MODE_PRIVATE).edit().putString("root", uri.toString()).apply()
            sprites.reset()
            toast("Folder game: ${rootDoc?.name}")
        }
        design.folderSet = rootDoc != null
        design.refresh()
    }

    private fun kids(d: DocumentFile): List<DocumentFile> = synchronized(dirCache) {
        dirCache.getOrPut(d.uri.toString()) { d.listFiles().toList() }
    }

    /** Cari file di folder game mengikuti jalur dari .dat (tanpa membedakan huruf besar/kecil); cadangan: cari nama file saja. */
    private fun findSprite(path: String): ByteArray? {
        val root = rootDoc ?: return null
        val segs = path.trim().replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (segs.isEmpty()) return null
        var cur: DocumentFile? = root
        for (s in segs) {
            val d = cur ?: break
            cur = kids(d).firstOrNull { it.name.equals(s, ignoreCase = true) }
        }
        var file: DocumentFile? = if (cur != null && cur.isFile) cur else null
        if (file == null) file = byName(segs.last())
        val f = file ?: return null
        return contentResolver.openInputStream(f.uri)?.use { it.readBytes() }
    }

    @Synchronized
    private fun byName(n: String): DocumentFile? {
        nameIndex?.let { return it[n.lowercase()] }
        val r = rootDoc ?: return null
        val m = HashMap<String, DocumentFile>()
        fun walk(d: DocumentFile, depth: Int) {
            for (c in kids(d)) {
                if (c.isDirectory) { if (depth < 5) walk(c, depth + 1) } else c.name?.let { m[it.lowercase()] = c }
            }
        }
        walk(r, 0)
        nameIndex = m
        return m[n.lowercase()]
    }

    private fun applyOrientation() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        content.orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val lp = { if (land) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
        editor.layoutParams = lp()
        design.root.layoutParams = lp()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation()
    }

    // ---------- UI helper ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: (View) -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick(it) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun iconBtn(res: Int, onClick: (View) -> Unit) = ImageButton(this).apply {
        setImageResource(res)
        setBackgroundColor(0)
        scaleType = ImageView.ScaleType.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick(it) }
    }

    private fun keyBtn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(FG)
        setBackgroundColor(BG)
        minWidth = 0
        minimumWidth = dp(44)
        minHeight = 0
        minimumHeight = dp(36)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { setMargins(dp(2), 0, dp(2), 0) }
        setOnClickListener { onClick() }
    }

    private fun insertText(t: String) {
        val a = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val b = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        editor.text.replace(a, b, t)
        editor.setSelection(a + t.length)
    }

    private fun mainMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add("Buka file")
        for (n in listOf("Char", "Weapon", "Stage", "Attack", "BG")) pm.menu.add("Baru: $n")
        pm.setOnMenuItemClickListener { item ->
            val t = item.title.toString()
            if (t == "Buka file") openLauncher.launch(arrayOf("*/*")) else loadTemplate(t.removePrefix("Baru: "))
            true
        }
        pm.show()
    }

    private fun moreMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add("Sprite…")
        pm.menu.add("Simpan sebagai…")
        pm.menu.add("Tampilkan/sembunyikan Design")
        pm.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Sprite…" -> pickSprites.launch(arrayOf("image/*"))
                "Simpan sebagai…" -> saveAsLauncher.launch(docName)
                else -> {
                    design.root.visibility = if (design.root.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    design.refresh()
                }
            }
            true
        }
        pm.show()
    }

    private fun loadTemplate(n: String) {
        val text = assets.open("templates/$n.dat").use { String(it.readBytes(), Charsets.ISO_8859_1) }
        load(text, "Baru_$n.dat", null, false)
    }

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
        titleName.text = docName
        titleStatus.text = if (dirty) "● Belum disimpan" else "● Tersimpan"
        titleStatus.setTextColor(if (dirty) 0xFFF59E0B.toInt() else ACC)
        status.text = "Ln $ln, Col $col   $info"
    }

    companion object {
        private val PLAIN = Regex("""<bmp_begin>|<frame>|<stage>|<background>|^\s*#|\bname:""", RegexOption.MULTILINE)
    }
}
