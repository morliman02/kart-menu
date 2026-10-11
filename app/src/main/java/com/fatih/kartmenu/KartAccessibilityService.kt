package com.fatih.kartmenu

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Ekranın sol kenarına ince bir tutamak yerleştirir. Kullanıcı bu tutamaktan
 * parmağını sağa kaydırınca, kayıtlı kartları listeleyen yüzen menüyü açar.
 *
 * Servis ekran içeriğini okumaz/kaydetmez; yalnızca kart seçildiğinde o an
 * odakta olan metin alanını bulup içine yazar.
 */
class KartAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var edgeView: View? = null
    private var menuView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private var clearClipboardRunnable: Runnable? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addEdgeHandle()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideMenu()
        edgeView?.let { runCatching { windowManager.removeView(it) } }
        edgeView = null
        super.onDestroy()
    }

    /** Overlay izni verildiyse onu, verilmediyse erişilebilirlik katmanını kullan. */
    private fun overlayType(): Int =
        if (Settings.canDrawOverlays(this)) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    // ---------------------------------------------------------------- kaydırma hareketi

    private fun addEdgeHandle() {
        if (edgeView != null) return
        val width = dp(EDGE_WIDTH_DP)
        val height = dp(EDGE_HEIGHT_DP)

        val handle = View(this).apply { setBackgroundResource(R.drawable.edge_handle) }

        val params = WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 0
            y = -dp(80)
        }

        var startX = 0f
        var startY = 0f
        var triggered = false
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    triggered = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = abs(event.rawY - startY)
                    // Soldan sağa, yataya yakın bir kaydırma.
                    if (!triggered && dx > dp(SWIPE_DISTANCE_DP) && dx > dy * 1.5f) {
                        triggered = true
                        showMenu()
                    }
                }
            }
            true
        }

        // Android 10+ hareketli gezinmede sol kenar "geri" hareketidir; tutamağın
        // olduğu küçük bölgede sistemin geri hareketini devre dışı bırakmayı iste.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            handle.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.systemGestureExclusionRects = listOf(Rect(0, 0, v.width, v.height))
            }
        }

        windowManager.addView(handle, params)
        edgeView = handle
    }

    // ---------------------------------------------------------------- yüzen menü

    private fun showMenu() {
        hideMenu()
        val cards = CardStore(this).getAll()
        val addresses = AddressStore(this).getAll()
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) Color.rgb(0x22, 0x24, 0x28) else Color.WHITE
        val fg = if (dark) Color.WHITE else Color.rgb(0x1A, 0x1A, 0x1A)
        val sub = if (dark) Color.rgb(0xB0, 0xB4, 0xBA) else Color.rgb(0x60, 0x64, 0x6A)
        val brand = Color.rgb(0x1F, 0x6F, 0xEB)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(16).toFloat()
            }
            elevation = dp(8).toFloat()
        }

        // Başlık + kapat
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Kart Menüsü"
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "✕"
            setTextColor(sub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { hideMenu() }
        })
        root.addView(header)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- Kartlar ----
        list.addView(sectionLabel("Kartlar", sub))
        if (cards.isEmpty()) {
            list.addView(emptyLabel("Kayıtlı kart yok.", sub))
        } else {
            cards.forEachIndexed { index, card ->
                val row = menuRow(dark) { autoFill(card) }
                row.addView(rowTitle("Kart ${index + 1}   ${card.masked}", fg))
                row.addView(rowSub("${card.holder} · SKT ${card.expiry}", sub))
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(6), 0, 0)
                }
                actions.addView(chip("Numara", brand) { deliver(card.number, "Kart numarası") })
                actions.addView(chip("SKT", brand) { deliver(card.expiry, "Son kullanma tarihi") })
                actions.addView(chip("İsim", brand) { deliver(card.holder, "Kart üzerindeki isim") })
                row.addView(actions)
                list.addView(row, rowParams())
            }
        }

        // ---- Adresler ----
        list.addView(sectionLabel("Adresler", sub).apply { setPadding(0, dp(14), 0, 0) })
        if (addresses.isEmpty()) {
            list.addView(emptyLabel("Kayıtlı adres yok.", sub))
        } else {
            addresses.forEachIndexed { index, address ->
                val row = menuRow(dark) { autoFill(address) }
                row.addView(rowTitle("Adres ${index + 1}", fg))
                row.addView(rowSub(address.summary, sub))
                list.addView(row, rowParams())
            }
        }

        // Ekle / düzenle için uygulamayı aç.
        list.addView(Button(this).apply {
            text = "Ekle / düzenle"
            setOnClickListener {
                hideMenu()
                startActivity(
                    Intent(this@KartAccessibilityService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        })

        val scroll = ScrollView(this).apply { addView(list) }
        // Ağırlıklı yükseklik: menü ekran sınırına göre kısaltılırsa liste kaydırılabilir kalır.
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Menünün dışına dokununca kapan.
        root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideMenu()
                true
            } else {
                false
            }
        }

        val maxHeight = (resources.displayMetrics.heightPixels * 0.7f).toInt()
        val params = WindowManager.LayoutParams(
            dp(MENU_WIDTH_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // Odak almayan pencere: alttaki uygulamadaki seçili metin alanı odakta kalır,
            // böylece seçilen bilgi doğrudan o alana yazılabilir.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = dp(16)
        }

        windowManager.addView(root, params)
        menuView = root

        // Çok kart varsa menü ekranın %70'ini geçmesin.
        root.post {
            if (root.height > maxHeight) {
                params.height = maxHeight
                runCatching { windowManager.updateViewLayout(root, params) }
            }
        }
    }

    private fun sectionLabel(text: String, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }

    private fun emptyLabel(text: String, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            setPadding(0, dp(2), 0, dp(4))
        }

    private fun rowTitle(text: String, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }

    private fun rowSub(text: String, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

    private fun menuRow(dark: Boolean, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(0x2E, 0x31, 0x36) else Color.rgb(0xF1, 0xF4, 0xF9))
                cornerRadius = dp(12).toFloat()
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun rowParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }

    private fun chip(label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setStroke(dp(1), color)
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun hideMenu() {
        menuView?.let { runCatching { windowManager.removeView(it) } }
        menuView = null
    }

    // ---------------------------------------------------------------- alan tanıma + otomatik doldurma

    /** Doldurulacak tek bir alan: etiket anahtar kelimeleri + yazılacak değer. */
    private class FillTarget(val keys: Array<String>, val value: String)

    /** Ekrandaki düzenlenebilir bir kutu: düğümü, normalize edilmiş etiketi, durumu. */
    private class Field(val node: AccessibilityNodeInfo, val label: String) {
        var filled = false
        var matched = -1 // eşleştiği hedefin listedeki indeksi, yoksa -1
    }

    /**
     * Madde 6 + 8 (kart): Kullanıcı bir karta dokununca ekrandaki kutular tanınıp doldurulur.
     * Tanınan kutu yoksa odaktaki alana numarayı yazar, o da olmazsa panoya kopyalar.
     */
    private fun autoFill(card: Card) {
        hideMenu()
        val root = rootInActiveWindow ?: firstAppRoot()
        if (root == null) {
            copyToClipboard(card.number)
            toast("Ekran okunamadı; kart numarası panoya kopyalandı (60 sn)")
            return
        }
        val fields = collectFields(root)
        // Daha özel olan "son kullanma" numaradan önce gelsin ("kart" kelimesi ikisinde de geçebilir).
        val targets = listOfNotNull(
            FillTarget(EXPIRY_KEYS, card.expiry),
            card.holder.ifBlank { null }?.let { FillTarget(HOLDER_KEYS, it) },
            FillTarget(NUMBER_KEYS, card.number),
        )
        val filled = fillByLabel(fields, targets)
        report(filled, fallbackValue = card.number, fallbackLabel = "Kart numarası")
    }

    /**
     * Aynı mantık Amazon adres ekranı için. Alanlar Amazon.com.tr formuna göre:
     * Adres satırı 1/2/3, Mahalle/Köy, İlçe, Şehir. Posta kodu Amazon'da otomatik
     * (ve çoğu zaman düzenlenemez) olduğu için yazılmaya çalışılır ama zorlanmaz.
     */
    private fun autoFill(address: Address) {
        hideMenu()
        val root = rootInActiveWindow ?: firstAppRoot()
        if (root == null) {
            copyToClipboard(address.line1)
            toast("Ekran okunamadı; adres panoya kopyalandı (60 sn)")
            return
        }
        val fields = collectFields(root)

        // Sıra önemli (ilk eşleşen kazanır): "adres satırı 2/3" önce gelmeli, çünkü
        // genel "adres" anahtarı da onların etiketinde geçer. Posta kodu en sonda.
        val line2T = address.line2.ifBlank { null }?.let { FillTarget(LINE2_KEYS, it) }
        val line3T = address.line3.ifBlank { null }?.let { FillTarget(LINE3_KEYS, it) }
        val line1T = address.line1.ifBlank { null }?.let { FillTarget(LINE1_KEYS, it) }
        // Şehir/İlçe/Mahalle: Amazon'da yazınca altında öneri çıkıp dokununca seçiliyor
        // (ve posta kodu otomatik geliyor). Bunları "otomatik tamamlamalı" işaretliyoruz.
        val cityT = address.city.ifBlank { null }?.let { FillTarget(CITY_KEYS, it) }
        val districtT = address.district.ifBlank { null }?.let { FillTarget(DISTRICT_KEYS, it) }
        val mahalleT = address.mahalle.ifBlank { null }?.let { FillTarget(MAHALLE_KEYS, it) }
        val targets = listOfNotNull(
            line2T,
            line3T,
            line1T,
            mahalleT,
            districtT,
            cityT,
            address.fullName.ifBlank { null }?.let { FillTarget(FULLNAME_KEYS, it) },
            address.phone.ifBlank { null }?.let { FillTarget(PHONE_KEYS, it) },
            address.postalCode.ifBlank { null }?.let { FillTarget(POSTAL_KEYS, it) },
        )
        var filled = fillByLabel(fields, targets)

        // Amazon'un "Adres satırı 2/3" kutularında bazen okunur etiket olmaz; etiketten
        // eşleşmediyse, "Adres satırı 1"i dolduran kutudan sonraki boş kutulara sırayla yaz.
        val line1Idx = targets.indexOf(line1T)
        if (line1Idx >= 0) {
            val anchor = fields.indexOfFirst { it.filled && it.matched == line1Idx }
            if (anchor >= 0) {
                var j = anchor + 1
                fun nextEmpty(): Field? {
                    while (j < fields.size) {
                        val f = fields[j]; j++
                        if (!f.filled) return f
                    }
                    return null
                }
                if (line2T != null && fields.none { it.matched == targets.indexOf(line2T) }) {
                    nextEmpty()?.let { if (setText(it.node, line2T.value)) { it.filled = true; filled++ } }
                }
                if (line3T != null && fields.none { it.matched == targets.indexOf(line3T) }) {
                    nextEmpty()?.let { if (setText(it.node, line3T.value)) { it.filled = true; filled++ } }
                }
            }
        }

        report(filled, fallbackValue = address.line1, fallbackLabel = "Adres")

        // Şehir/İlçe/Mahalle için açılan öneriye otomatik dokunmayı dene (en iyi çaba).
        // Böylece seçim tamamlanır ve posta kodu Amazon tarafından otomatik gelir.
        val autoCompleteItems = listOfNotNull(cityT, districtT, mahalleT).mapNotNull { t ->
            val idx = targets.indexOf(t)
            fields.firstOrNull { it.filled && it.matched == idx }?.let { it.node to t.value }
        }
        if (autoCompleteItems.isNotEmpty()) {
            handler.postDelayed({ selectSuggestions(autoCompleteItems, 0) }, 500)
        }
    }

    /**
     * Otomatik tamamlamalı alanları sırayla işler: alanı odakla + metni yeniden yaz
     * (öneri listesi çıksın), kısa bir bekleme sonrası eşleşen öneriye dokun, sonra sıradakine geç.
     */
    private fun selectSuggestions(items: List<Pair<AccessibilityNodeInfo, String>>, index: Int) {
        if (index >= items.size) return
        val (node, value) = items[index]
        runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            setText(node, value)
        }
        handler.postDelayed({
            val root = rootInActiveWindow ?: firstAppRoot()
            val suggestion = root?.let { findSuggestion(it, normalize(value), node) }
            suggestion?.let { clickable ->
                runCatching { clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            }
            handler.postDelayed({ selectSuggestions(items, index + 1) }, 600)
        }, 650)
    }

    /**
     * Girilen değeri içeren, düzenlenebilir OLMAYAN, tıklanabilir öneri satırını bulur.
     * Giriş kutusunun kendisi hariç tutulur. Tıklanabilir en yakın düğümü döndürür.
     */
    private fun findSuggestion(
        node: AccessibilityNodeInfo?,
        normalizedValue: String,
        inputNode: AccessibilityNodeInfo,
        depth: Int = 0,
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 60) return null
        val text = node.text?.toString()
        if (!node.isEditable && node != inputNode && !text.isNullOrBlank() &&
            normalize(text).contains(normalizedValue)
        ) {
            clickableAncestor(node)?.let { return it }
        }
        for (i in 0 until node.childCount) {
            findSuggestion(node.getChild(i), normalizedValue, inputNode, depth + 1)?.let { return it }
        }
        return null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var hops = 0
        while (current != null && hops < 6) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return null
    }

    private fun collectFields(root: AccessibilityNodeInfo): List<Field> {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectEditable(root, nodes, 0)
        return nodes.map { Field(it, normalize(labelFor(it, root))) }
    }

    /** Her kutuyu etiketine göre bir hedefle eşleyip doldurur; her hedef en fazla bir kez. */
    private fun fillByLabel(fields: List<Field>, targets: List<FillTarget>): Int {
        val usedTargets = HashSet<Int>()
        var filled = 0
        for (f in fields) {
            if (f.filled) continue
            for (i in targets.indices) {
                if (i in usedTargets) continue
                if (containsAny(f.label, targets[i].keys)) {
                    if (setText(f.node, targets[i].value)) {
                        usedTargets += i
                        f.filled = true
                        f.matched = i
                        filled++
                    }
                    break
                }
            }
        }
        return filled
    }

    private fun report(filled: Int, fallbackValue: String, fallbackLabel: String) {
        when {
            filled > 0 -> toast("$filled alan otomatik dolduruldu")
            // Tanınan alan yok: en azından odaktaki alana yedek değeri yaz, olmazsa panoya al.
            fillFocusedField(fallbackValue) ->
                toast("Alan etiketi bulunamadı; $fallbackLabel seçili alana yazıldı")
            else -> {
                copyToClipboard(fallbackValue)
                toast("Alan tanınamadı; $fallbackLabel panoya kopyalandı (60 sn)")
            }
        }
    }

    /** Etkin pencere alınamazsa kendi overlay'imiz dışındaki ilk uygulama penceresini bul. */
    private fun firstAppRoot(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        for (w in windows) {
            if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                val r = w.root ?: continue
                if (r.packageName?.toString() != packageName) return r
            }
        }
        return null
    }

    private fun collectEditable(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (node == null || depth > 60) return
        if (node.isEditable && node.packageName?.toString() != packageName) out += node
        for (i in 0 until node.childCount) collectEditable(node.getChild(i), out, depth + 1)
    }

    /** Bir kutunun etiketini olabildiğince çok kaynaktan toplar. */
    private fun labelFor(node: AccessibilityNodeInfo, root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun add(cs: CharSequence?) { if (!cs.isNullOrBlank()) sb.append(' ').append(cs) }
        add(node.hintText)
        add(node.contentDescription)
        node.viewIdResourceName?.substringAfterLast('/')?.let { add(it.replace('_', ' ')) }
        // Boşsa, kutunun içindeki mevcut metin bazen etiket (placeholder) olur.
        if (sb.isBlank()) add(node.text)
        // Bir TextView bu kutuyu etiketliyorsa onu da al.
        runCatching { add(node.labeledBy?.text) }
        // Düzende hemen önce gelen metin görünümünü etiket say.
        add(precedingLabel(node, root))
        return sb.toString()
    }

    /** Kutunun hemen solundaki/üstündeki metni (yaygın form düzeni) etiket olarak dener. */
    private fun precedingLabel(node: AccessibilityNodeInfo, root: AccessibilityNodeInfo): CharSequence? {
        val parent = node.parent ?: return null
        val index = (0 until parent.childCount).indexOfFirst { parent.getChild(it) == node }
        if (index > 0) {
            val prev = parent.getChild(index - 1)
            val t = prev?.text ?: prev?.contentDescription
            if (!t.isNullOrBlank()) return t
        }
        return null
    }

    private fun containsAny(haystack: String, keys: Array<String>): Boolean =
        keys.any { haystack.contains(it) }

    /** Türkçe'ye uyumlu küçük harfe indirip aksanları sadeleştirir. */
    private fun normalize(s: String): String {
        val lower = s.lowercase(java.util.Locale("tr", "TR"))
        val sb = StringBuilder(lower.length)
        for (c in lower) sb.append(
            when (c) {
                'ı', 'i', 'İ' -> 'i'
                'ş' -> 's'; 'ğ' -> 'g'; 'ü' -> 'u'; 'ö' -> 'o'; 'ç' -> 'c'
                else -> c
            }
        )
        return sb.toString()
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ---------------------------------------------------------------- tek alana iletme (yedek)

    /**
     * Tek bir bilgiyi, o an odakta olan metin alanına yazmayı dener;
     * olmazsa panoya kopyalar ve 60 saniye sonra panoyu temizler.
     */
    private fun deliver(value: String, label: String) {
        hideMenu()
        if (fillFocusedField(value)) {
            toast("$label alana yazıldı")
        } else {
            copyToClipboard(value)
            toast("$label panoya kopyalandı (60 sn sonra silinir)")
        }
    }

    private fun fillFocusedField(value: String): Boolean {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!node.isEditable || node.packageName?.toString() == packageName) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun copyToClipboard(value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Kart bilgisi", value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: panodaki içerik önizlemede gizlensin.
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

        cancelClipboardClear(clipboard)
        clipboard.setPrimaryClip(clip)

        // Kullanıcı bu arada başka bir şey kopyalarsa onun panosunu silme.
        val listener = ClipboardManager.OnPrimaryClipChangedListener { cancelClipboardClear(clipboard) }
        val clear = Runnable {
            clipboard.removePrimaryClipChangedListener(listener)
            clipListener = null
            clearClipboardRunnable = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        // setPrimaryClip'in kendi bildirimini atlamak için dinleyiciyi biraz sonra ekle.
        handler.postDelayed({
            if (clearClipboardRunnable === clear) {
                clipboard.addPrimaryClipChangedListener(listener)
                clipListener = listener
            }
        }, 500)
        clearClipboardRunnable = clear
        handler.postDelayed(clear, CLIPBOARD_CLEAR_MS)
    }

    private fun cancelClipboardClear(clipboard: ClipboardManager) {
        clearClipboardRunnable?.let { handler.removeCallbacks(it) }
        clearClipboardRunnable = null
        clipListener?.let { clipboard.removePrimaryClipChangedListener(it) }
        clipListener = null
    }

    // ---------------------------------------------------------------- yardımcılar

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EDGE_WIDTH_DP = 14
        private const val EDGE_HEIGHT_DP = 160
        private const val SWIPE_DISTANCE_DP = 40
        private const val MENU_WIDTH_DP = 300
        private const val CLIPBOARD_CLEAR_MS = 60_000L

        // Etiket anahtar kelimeleri (normalize edilmiş: küçük harf, Türkçe aksanlar sadeleştirilmiş).
        // Madde 8: hem Türkçe hem İngilizce alan isimleri tanınır.
        private val NUMBER_KEYS = arrayOf(
            "kart numar", "kartnumar", "numara", "kart no", "kartno",
            "card number", "cardnumber", "card no", "cardno", "ccnumber", "cc number",
            "pan", "credit card", "kredi kart"
        )
        private val EXPIRY_KEYS = arrayOf(
            "son kullanma", "skt", "son kul", "gecerlilik", "gecerli",
            "expir", "exp date", "expdate", "valid thru", "valid through",
            "mm/yy", "aa/yy", "aa / yy", "mm / yy", "ay/yil", "ay / yil"
        )
        private val HOLDER_KEYS = arrayOf(
            "uzerindeki", "uzerinde", "kart sahib", "sahibi", "isim", "ad soyad",
            "name on card", "nameoncard", "cardholder", "card holder", "holder name",
            "holder", "full name", "ad ve soyad"
        )

        // Adres formu etiketleri (Amazon.com.tr ve genel formlar; TR + EN).
        private val FULLNAME_KEYS = arrayOf(
            "ad soyad", "adiniz ve soyadiniz", "alici", "ad ve soyad", "isim soyisim",
            "full name", "first and last name", "recipient", "contact name", "ad-soyad"
        )
        private val PHONE_KEYS = arrayOf(
            "telefon", "cep telefon", "cep no", "gsm", "phone", "mobile", "tel no", "tel "
        )
        private val POSTAL_KEYS = arrayOf(
            "posta kodu", "postakodu", "posta kod", "zip", "postal", "post code", "pin code"
        )
        private val DISTRICT_KEYS = arrayOf(
            "ilce", "semt", "district", "county", "town"
        )
        private val CITY_KEYS = arrayOf(
            "sehir", "city", "province", "state"
        )
        private val MAHALLE_KEYS = arrayOf(
            "mahalle", "koy", "neighbourhood", "neighborhood", "mahalle / koy"
        )
        // "Adres satırı 2/3" özel anahtarları (LINE1'deki genel "adres"ten ÖNCE denenir).
        private val LINE2_KEYS = arrayOf(
            "adres satiri 2", "adres satir 2", "adres 2", "address line 2", "addressline2",
            "bina no", "bina", "apartman", "sokak no"
        )
        private val LINE3_KEYS = arrayOf(
            "adres satiri 3", "adres satir 3", "adres 3", "address line 3", "addressline3",
            "daire", "dukkan", "kat", "apartment", "flat"
        )
        private val LINE1_KEYS = arrayOf(
            "adres satiri 1", "adres satir 1", "adres 1", "address line 1", "addressline1",
            "acik adres", "adres", "address", "street", "cadde", "sokak"
        )
    }
}
