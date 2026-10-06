package it.metro.tube

import android.app.Activity
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.Html
import android.text.TextUtils
import android.text.format.DateUtils
import android.content.Intent
import android.util.LruCache
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

// >>> Inserisci qui la tua chiave YouTube Data API v3 <<<
const val API_KEY = "INSERISCI_LA_TUA_CHIAVE"

class MainActivity : Activity() {
    private val DARK = 0xFF1B1B1B.toInt(); private val BG = 0xFFEDF1EE.toInt()
    private val BLUE = 0xFF0B58A6.toInt(); private val RED = 0xFFC4302B.toInt()
    private val base = "https://www.googleapis.com/youtube/v3/"
    private val regions = listOf("IT", "US", "GB", "DE", "FR", "ES", "CA", "BR")
    private val ui = Handler(Looper.getMainLooper())
    private val cache = LruCache<String, Bitmap>(80)
    private val stack = ArrayList<View>()
    private val prefs by lazy { getSharedPreferences("metro", 0) }
    private var region: String
        get() = prefs.getString("region", "IT")!!
        set(v) = prefs.edit().putString("region", v).apply()
    private var safe: Boolean
        get() = prefs.getBoolean("safe", false)
        set(v) = prefs.edit().putBoolean("safe", v).apply()

    private val key: String
        get() = prefs.getString("key", "")!!.ifEmpty { API_KEY }

    private lateinit var content: FrameLayout
    private lateinit var drawer: LinearLayout
    private lateinit var logoBox: FrameLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private fun get(url: String, cb: (JSONObject?) -> Unit) = thread {
        val r = try { JSONObject(URL(url).readText()) } catch (e: Exception) { null }
        ui.post { cb(r) }
    }
    private fun load(iv: ImageView, url: String) {
        cache.get(url)?.let { iv.setImageBitmap(it); return }
        thread {
            try { val b = BitmapFactory.decodeStream(URL(url).openStream()); cache.put(url, b); ui.post { iv.setImageBitmap(b) } } catch (_: Exception) {}
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = DARK; window.navigationBarColor = DARK
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        root.addView(topBar(), LinearLayout.LayoutParams(-1, dp(56)))
        val mid = FrameLayout(this)
        content = FrameLayout(this)
        mid.addView(content, FrameLayout.LayoutParams(-1, -1))
        drawer = drawerView().apply { visibility = View.GONE }
        mid.addView(drawer, FrameLayout.LayoutParams(dp(280), -1))
        root.addView(mid, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomBar(), LinearLayout.LayoutParams(-1, dp(64)))
        setContentView(root)
        home()
    }

    // ---------- Barre ----------
    private fun logo() = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        addView(tv("You", 22f, Color.WHITE, true))
        addView(tv("Tube", 22f, Color.WHITE, true).apply {
            setPadding(dp(5), 0, dp(5), 0)
            background = GradientDrawable().apply { setColor(RED); cornerRadius = dp(5).toFloat() }
        })
    }
    private fun topBar(): View {
        val b = FrameLayout(this).apply { setBackgroundColor(DARK) }
        b.addView(Icon(this, 0).apply { tilt(this); setOnClickListener { toggleDrawer() } }, FrameLayout.LayoutParams(dp(56), -1, Gravity.START))
        logoBox = FrameLayout(this).also { it.addView(logo()) }
        b.addView(logoBox, FrameLayout.LayoutParams(-1, -1).apply { marginStart = dp(64); marginEnd = dp(64) })
        b.addView(Icon(this, 1).apply { tilt(this); setOnClickListener { openSearch() } }, FrameLayout.LayoutParams(dp(56), -1, Gravity.END))
        return b
    }
    private fun openSearch() {
        val e = EditText(this).apply {
            hint = "Search"; setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt()); setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { v, _, _ ->
                val q = v.text.toString().trim()
                if (q.isNotEmpty()) {
                    (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                    list("Search", "\"$q\"", "${base}search?part=snippet&type=video&maxResults=25&q=${enc(q)}&regionCode=$region&safeSearch=${if (safe) "strict" else "none"}&key=$key", false)
                    logoBox.removeAllViews(); logoBox.addView(logo())
                }
                true
            }
        }
        logoBox.removeAllViews(); logoBox.addView(e, FrameLayout.LayoutParams(-1, -1)); e.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(e, 0)
    }
    private fun bottomBar(): View {
        val b = LinearLayout(this).apply { setBackgroundColor(DARK); gravity = Gravity.CENTER_VERTICAL }
        listOf(2 to { home() }, 3 to { cat("Trending", null) }, 4 to { cat("Music", "10") }, 5 to { cat("Gaming", "20") }).forEach { (k, a) ->
            b.addView(Icon(this, k).apply { tilt(this); setOnClickListener { a() } }, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginStart = dp(20) })
        }
        b.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        b.addView(tv("•••", 18f, Color.WHITE, true).apply { setPadding(dp(8), 0, dp(18), dp(18)); setOnClickListener { settings() } })
        return b
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun tilt(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(.95f).scaleY(.95f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }
    private fun fmt(iso: String): String {
        val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(iso) ?: return ""
        val (h, mi, sec) = m.destructured
        val mm = mi.ifEmpty { "0" }.toInt(); val ss = sec.ifEmpty { "0" }.toInt()
        return if (h.isNotEmpty()) "%d:%02d:%02d".format(h.toInt(), mm, ss) else "%d:%02d".format(mm, ss)
    }
    private fun badge(d: String) = tv(d, 12f, Color.WHITE, true).apply {
        setPadding(dp(5), dp(1), dp(5), dp(1)); background = GradientDrawable().apply { setColor(0xCC000000.toInt()); cornerRadius = dp(3).toFloat() }
    }
    private fun ago(iso: String) = try {
        DateUtils.getRelativeTimeSpanString(java.time.Instant.parse(iso).toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    } catch (e: Exception) { "" }

    private fun drawerView(): LinearLayout {
        val d = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF222222.toInt()) }
        val items = listOf("Home" to null, "Trending" to "", "Music" to "10", "Entertainment" to "24", "Sports" to "17", "Comedy" to "23", "Film & Animation" to "1", "Gaming" to "20")
        d.addView(tv("Sign in", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setBackgroundColor(0xFF6B8FE6.toInt()); setOnClickListener { Toast.makeText(this@MainActivity, "Sign-in not available yet", Toast.LENGTH_SHORT).show() } }, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(14), dp(14), dp(14), 0) })
        d.addView(tv("BEST OF YOUTUBE", 12f, 0xFF888888.toInt(), true).apply { setPadding(dp(16), dp(18), 0, dp(8)) })
        items.forEach { (n, c) ->
            d.addView(tv(n, 19f, 0xFFCCCCCC.toInt(), true).apply {
                setPadding(dp(20), dp(15), 0, dp(15))
                setOnClickListener { toggleDrawer(); if (c == null) home() else cat(n, c.ifEmpty { null }) }
            })
        }
        d.addView(tv("Settings", 19f, 0xFFCCCCCC.toInt(), true).apply { setPadding(dp(20), dp(15), 0, dp(15)); setOnClickListener { toggleDrawer(); settings() } })
        return d
    }
    private fun toggleDrawer() {
        drawer.visibility = if (drawer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        drawer.translationX = -dp(280).toFloat(); drawer.animate().translationX(0f).setDuration(180).start()
    }

    // ---------- Schermate ----------
    private fun show(v: View, push: Boolean = true) {
        if (!push) stack.clear()
        stack.add(v); content.removeAllViews(); content.addView(v)
        v.translationX = dp(40).toFloat(); v.alpha = 0f; v.animate().translationX(0f).alpha(1f).setDuration(200).start()
    }
    override fun onBackPressed() {
        when {
            drawer.visibility == View.VISIBLE -> drawer.visibility = View.GONE
            stack.size > 1 -> { stack.removeAt(stack.size - 1); content.removeAllViews(); content.addView(stack.last()) }
            else -> super.onBackPressed()
        }
    }
    private fun trendUrl(cat: String?) = "${base}videos?part=snippet,statistics,contentDetails&chart=mostPopular&maxResults=25&regionCode=$region" +
        (cat?.let { "&videoCategoryId=$it" } ?: "") + "&key=$key"
    private fun home() = list("Trending", "Popular videos · $region", trendUrl(null), true, push = false)
    private fun cat(name: String, id: String?) = list(name, "Popular videos · $region", trendUrl(id), true)

    private fun list(title: String, sub: String, url: String, featured: Boolean, push: Boolean = true) {
        val sv = ScrollView(this); val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(col)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(DARK); setPadding(dp(18), dp(14), dp(18), dp(14))
            addView(tv(title, 22f, Color.WHITE, true)); addView(tv(sub, 15f, 0xFFCCCCCC.toInt()))
        })
        val status = tv("Loading…", 16f, 0xFF555555.toInt()).apply { setPadding(dp(18), dp(18), 0, 0) }
        col.addView(status)
        show(sv, push)
        get(url) { j ->
            val arr = j?.optJSONArray("items")
            if (arr == null) {
                status.text = when {
                    key.startsWith("INSERISCI") -> "Tap here and add your free YouTube API key to start."
                    else -> j?.optJSONObject("error")?.optString("message") ?: "Network error. Tap to open Settings."
                }
                status.setOnClickListener { settings() }
                return@get
            }
            col.removeView(status)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i); val sn = o.getJSONObject("snippet")
                val idv = o.get("id"); val id = if (idv is JSONObject) idv.optString("videoId") else idv.toString()
                if (id.isEmpty()) continue
                val views = o.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull()?.let { "%,d views".format(it) }
                val t = Html.fromHtml(sn.getString("title"), 0).toString(); val ch = sn.getString("channelTitle")
                val dur = o.optJSONObject("contentDetails")?.optString("duration")?.let { fmt(it) } ?: ""
                col.addView(if (i == 0 && featured) banner(id, t, ch, views, dur) else row(id, t, ch, views, dur),
                    LinearLayout.LayoutParams(-1, -2).apply { if (!(i == 0 && featured)) topMargin = dp(4) })
            }
        }
    }
    private fun banner(id: String, t: String, ch: String, v: String?, d: String): View {
        val f = FrameLayout(this)
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        f.addView(iv, FrameLayout.LayoutParams(-1, dp(210))); load(iv, "https://i.ytimg.com/vi/$id/hqdefault.jpg")
        f.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(40), dp(14), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0xCC000000.toInt()))
            addView(tv(t, 19f, Color.WHITE, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            addView(tv("by $ch", 14f, Color.WHITE))
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        if (d.isNotEmpty()) f.addView(badge(d), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(12), dp(12)) })
        tilt(f); f.setOnClickListener { player(id, t, ch, v) }
        return f
    }
    private fun row(id: String, t: String, ch: String, v: String?, d: String = ""): View {
        val r = LinearLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val th = FrameLayout(this); th.addView(iv, FrameLayout.LayoutParams(-1, -1)); load(iv, "https://i.ytimg.com/vi/$id/mqdefault.jpg")
        if (d.isNotEmpty()) th.addView(badge(d), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(6), dp(6)) })
        r.addView(th, LinearLayout.LayoutParams(dp(150), dp(84)))
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(4), dp(8), 0) }
        c.addView(tv(t, 15f, 0xFF222222.toInt(), true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        c.addView(tv("by $ch", 13f, 0xFF555555.toInt()).apply { maxLines = 1 })
        v?.let { c.addView(tv(it, 13f, 0xFF777777.toInt())) }
        r.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
        tilt(r); r.setOnClickListener { player(id, t, ch, v) }
        return r
    }

    private fun player(id: String, title: String, ch: String, views: String?) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val web = WebView(this).apply {
            setBackgroundColor(Color.BLACK); settings.javaScriptEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = android.webkit.WebChromeClient()
            loadDataWithBaseURL("https://www.youtube.com",
                "<body style='margin:0;background:#000'><iframe width='100%' height='100%' src='https://www.youtube.com/embed/$id?playsinline=1&autoplay=1' frameborder='0' allow='autoplay;fullscreen' allowfullscreen></iframe></body>",
                "text/html", "utf-8", null)
        }
        col.addView(web, LinearLayout.LayoutParams(-1, dp(220)))
        val bar = LinearLayout(this).apply { setBackgroundColor(0xFF4A4A4A.toInt()); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(tv("HQ", 14f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(RED); cornerRadius = dp(3).toFloat() } },
            LinearLayout.LayoutParams(0, dp(32), 1f).apply { setMargins(dp(14), 0, dp(14), 0) })
        listOf(7, 8, 9, 6).forEach { k ->
            bar.addView(Icon(this, k, 0xFFDDDDDD.toInt()).apply {
                tilt(this)
                setOnClickListener {
                    if (k == 6) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://youtu.be/$id"), null))
                    else Toast.makeText(this@MainActivity, "Sign in to use this", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        col.addView(bar, LinearLayout.LayoutParams(-1, dp(52)))
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(tv(title, 19f, 0xFF222222.toInt(), true)); addView(tv("By $ch", 15f, BLUE, true))
            views?.let { addView(tv(it, 15f, 0xFF444444.toInt())) }
        })
        val tabs = LinearLayout(this); val sv = ScrollView(this); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(body)
        fun tab(n: String) = tv(n, 17f, 0xFF777777.toInt()).apply {
            gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.WHITE); setStroke(dp(2), Color.BLACK) }
        }
        val t1 = tab("Suggested Videos"); val t2 = tab("Comments")
        tabs.addView(t1, LinearLayout.LayoutParams(0, dp(48), 1f)); tabs.addView(t2, LinearLayout.LayoutParams(0, dp(48), 1f))
        fun sel(a: TextView, b: TextView) { a.setTextColor(0xFF111111.toInt()); b.setTextColor(0xFF999999.toInt()) }
        t1.setOnClickListener {
            sel(t1, t2); body.removeAllViews()
            get("${base}search?part=snippet&type=video&maxResults=15&q=${enc(title)}&key=$key") { j ->
                val a = j?.optJSONArray("items") ?: return@get
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i); val sn = o.getJSONObject("snippet"); val vid = o.getJSONObject("id").optString("videoId")
                    if (vid.isNotEmpty() && vid != id) body.addView(row(vid, Html.fromHtml(sn.getString("title"), 0).toString(), sn.getString("channelTitle"), null),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                }
            }
        }
        t2.setOnClickListener {
            sel(t2, t1); body.removeAllViews()
            body.addView(tv("Sign In or Sign Up now to post a comment.", 14f, 0xFF555555.toInt()).apply { setPadding(dp(14), dp(10), 0, dp(6)) })
            get("${base}commentThreads?part=snippet&maxResults=30&order=relevance&videoId=$id&key=$key") { j ->
                val a = j?.optJSONArray("items")
                if (a == null) { body.addView(tv("Comments unavailable", 15f, 0xFF555555.toInt()).apply { setPadding(dp(14), dp(8), 0, 0) }); return@get }
                for (i in 0 until a.length()) {
                    val c = a.getJSONObject(i).getJSONObject("snippet").getJSONObject("topLevelComment").getJSONObject("snippet")
                    body.addView(LinearLayout(this).apply {
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                        val av = ImageView(this@MainActivity).apply { setBackgroundColor(0xFFA9BCE8.toInt()); scaleType = ImageView.ScaleType.CENTER_CROP }
                        addView(av, LinearLayout.LayoutParams(dp(46), dp(46)))
                        c.optString("authorProfileImageUrl").takeIf { it.startsWith("http") }?.let { load(av, it) }
                        addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
                            addView(LinearLayout(this@MainActivity).apply {
                                addView(tv(c.getString("authorDisplayName"), 16f, BLUE, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                                addView(tv("👍 ${c.optInt("likeCount")}", 14f, 0xFF2E9E2E.toInt()))
                            })
                            addView(tv(ago(c.optString("publishedAt")), 13f, 0xFF777777.toInt()))
                            addView(tv(Html.fromHtml(c.getString("textDisplay"), 0).toString(), 16f, 0xFF222222.toInt()))
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                    })
                }
            }
        }
        col.addView(tabs); col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        show(col); t1.performClick()
    }

    private fun askKey(done: () -> Unit) {
        val e = EditText(this).apply { hint = "AIza…"; setSingleLine(); setText(prefs.getString("key", "")) }
        android.app.AlertDialog.Builder(this).setTitle("YouTube API key")
            .setMessage("Free key from console.cloud.google.com: enable \"YouTube Data API v3\", then Credentials > API key.")
            .setView(e)
            .setPositiveButton("Save") { _, _ -> prefs.edit().putString("key", e.text.toString().trim()).apply(); done() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun settings() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(16), dp(14), 0) }
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(18), dp(14), dp(18), dp(14)) }
        card.addView(tv("Settings", 24f, 0xFF555555.toInt()))
        fun row(label: String, value: () -> String, act: (TextView) -> Unit) {
            val r = LinearLayout(this).apply { setPadding(0, dp(16), 0, dp(8)) }
            r.addView(tv(label, 17f, 0xFF777777.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
            val v = tv(value(), 17f, BLUE); r.addView(v)
            r.setOnClickListener { act(v); v.text = value() }
            card.addView(r)
        }
        row("Location", { region }) { region = regions[(regions.indexOf(region) + 1) % regions.size] }
        row("API key", { if (key.startsWith("INSERISCI")) "Not set" else "Set" }) { v -> askKey { v.text = if (key.startsWith("INSERISCI")) "Not set" else "Set" } }
        row("Safe Search", { if (safe) "On" else "Off" }) { safe = !safe }
        col.addView(card)
        show(col)
    }
}

class Icon(ctx: android.content.Context, private val k: Int, private val col: Int = Color.WHITE) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val u = minOf(width, height) / 100f; val cx = width / 2f; val cy = height / 2f
        p.color = col; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 7 * u; p.style = Paint.Style.FILL
        fun ring() { p.style = Paint.Style.STROKE; c.drawCircle(cx, cy, 44 * u, p); p.style = Paint.Style.FILL }
        when (k) {
            0 -> for (i in -1..1) c.drawRect(cx - 32 * u, cy + i * 24 * u - 5 * u, cx + 32 * u, cy + i * 24 * u + 5 * u, p)
            1 -> { p.style = Paint.Style.STROKE; c.drawCircle(cx - 6 * u, cy - 6 * u, 22 * u, p); c.drawLine(cx + 10 * u, cy + 10 * u, cx + 34 * u, cy + 34 * u, p) }
            2 -> { ring(); p.textSize = 26 * u; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD; c.drawText("You", cx, cy + 9 * u, p) }
            3 -> {
                ring(); for (i in 0..2) c.drawRect(cx - 24 * u + i * 18 * u, cy + 20 * u - (10 + i * 12) * u, cx - 14 * u + i * 18 * u, cy + 20 * u, p)
                p.style = Paint.Style.STROKE; c.drawLine(cx - 26 * u, cy - 4 * u, cx + 24 * u, cy - 26 * u, p)
            }
            4 -> {
                ring(); p.style = Paint.Style.STROKE; c.drawArc(cx - 22 * u, cy - 26 * u, cx + 22 * u, cy + 14 * u, 180f, 180f, false, p); p.style = Paint.Style.FILL
                c.drawRoundRect(cx - 28 * u, cy - 2 * u, cx - 16 * u, cy + 22 * u, 4 * u, 4 * u, p); c.drawRoundRect(cx + 16 * u, cy - 2 * u, cx + 28 * u, cy + 22 * u, 4 * u, 4 * u, p)
            }
            5 -> {
                ring(); c.drawRoundRect(cx - 28 * u, cy - 16 * u, cx + 8 * u, cy + 16 * u, 6 * u, 6 * u, p)
                val t = Path().apply { moveTo(cx + 12 * u, cy); lineTo(cx + 30 * u, cy - 14 * u); lineTo(cx + 30 * u, cy + 14 * u); close() }; c.drawPath(t, p)
            }
            6 -> {
                p.strokeWidth = 5 * u; c.drawLine(cx - 20 * u, cy, cx + 18 * u, cy - 20 * u, p); c.drawLine(cx - 20 * u, cy, cx + 18 * u, cy + 20 * u, p)
                for ((x, y) in listOf(cx - 22 * u to cy, cx + 20 * u to cy - 22 * u, cx + 20 * u to cy + 22 * u)) c.drawCircle(x, y, 9 * u, p)
            }
            7, 8 -> {
                if (k == 8) c.rotate(180f, cx, cy)
                c.drawRect(cx - 36 * u, cy - 4 * u, cx - 22 * u, cy + 34 * u, p); c.drawRoundRect(cx - 18 * u, cy - 8 * u, cx + 34 * u, cy + 34 * u, 8 * u, 8 * u, p)
                c.drawRoundRect(cx - 8 * u, cy - 36 * u, cx + 8 * u, cy - 2 * u, 8 * u, 8 * u, p)
            }
            9 -> { p.strokeWidth = 9 * u; c.drawLine(cx - 24 * u, cy, cx + 24 * u, cy, p); c.drawLine(cx, cy - 24 * u, cx, cy + 24 * u, p) }
        }
    }
}
