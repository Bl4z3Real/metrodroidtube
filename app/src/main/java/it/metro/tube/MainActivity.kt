package it.metro.tube

import android.app.Activity
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.Html
import android.text.TextUtils
import android.text.format.DateUtils
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
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

    private val chOf = HashMap<String, String>()
    private lateinit var content: FrameLayout
    private lateinit var drawer: LinearLayout
    private lateinit var logoBox: FrameLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val fText by lazy { resources.getFont(R.font.segoe_wp_light) }
    private val fSym by lazy { resources.getFont(R.font.seguisym) }
    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.create(fText, Typeface.BOLD) else fText
    }
    private fun sym(t: String, size: Float, color: Int) = TextView(this).apply { text = t; textSize = size; setTextColor(color); typeface = fSym }
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
        Icon.font = fText
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
            hint = "Search"; typeface = fText; setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt()); setSingleLine()
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
        val b = FrameLayout(this).apply { setBackgroundColor(DARK) }
        val mid = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        listOf(2 to { home() }, 3 to { cat("Trending", null) }, 4 to { cat("Music", "10") }, 5 to { uploadMenu() }).forEach { (k, a) ->
            mid.addView(Icon(this, k).apply { tilt(this); setOnClickListener { a() } },
                LinearLayout.LayoutParams(dp(50), dp(50)).apply { setMargins(dp(10), 0, dp(10), 0) })
        }
        b.addView(mid, FrameLayout.LayoutParams(-2, -1, Gravity.CENTER))
        b.addView(sym("•••", 18f, Color.WHITE).apply { setPadding(dp(8), dp(4), dp(14), dp(8)); setOnClickListener { settings() } },
            FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP))
        return b
    }
    private fun uploadMenu() {
        android.app.AlertDialog.Builder(this).setTitle("Upload a video")
            .setItems(arrayOf("Record a video", "Choose from gallery")) { _, w ->
                if (w == 0) startActivityForResult(Intent(MediaStore.ACTION_VIDEO_CAPTURE), 11)
                else startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("video/*"), 12)
            }.show()
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        val uri = d?.data
        if ((rc == 11 || rc == 12) && res == RESULT_OK && uri != null) {
            val send = Intent(Intent.ACTION_SEND).setType("video/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { startActivity(Intent(send).setPackage("com.google.android.youtube")) }
            catch (e: Exception) { startActivity(Intent.createChooser(send, "Upload with…")) }
        }
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
        val grey = 0xFF999999.toInt()
        d.addView(tv("Sign in", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setBackgroundColor(0xFF6B8FE6.toInt()); setOnClickListener { Toast.makeText(this@MainActivity, "Sign-in not available yet", Toast.LENGTH_SHORT).show() } },
            LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(14), dp(14), dp(14), 0) })
        fun head(t: String) = d.addView(tv(t, 12f, 0xFF888888.toInt(), true).apply { setPadding(dp(16), dp(18), 0, dp(6)) })
        fun item(name: String, res: Int, glyph: String, go: () -> Unit) {
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(12), dp(8), dp(12)); tilt(this); setOnClickListener { toggleDrawer(); go() } }
            if (res != 0) r.addView(ImageView(this).apply { setImageResource(res); setColorFilter(grey) }, LinearLayout.LayoutParams(dp(26), dp(26)))
            else r.addView(sym(glyph, 20f, grey).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(26), dp(26)))
            r.addView(tv(name, 19f, 0xFFCCCCCC.toInt(), true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) })
            d.addView(r)
        }
        head("ACTIVITY"); item("Home", 0, "\u2302") { home() }
        item("History", 0, "\u21BA") { localList("History", "history") }
        item("Watch later", 0, "\u2606") { localList("Watch later", "later") }
        head("BEST OF YOUTUBE")
        item("Trending", 0, "\u2197") { cat("Trending", null) }
        item("Music", R.drawable.d_music, "") { cat("Music", "10") }
        item("Entertainment", R.drawable.d_entertainment, "") { cat("Entertainment", "24") }
        item("Sports", R.drawable.d_sports, "") { cat("Sports", "17") }
        item("Comedy", R.drawable.d_comedy, "") { cat("Comedy", "23") }
        item("Film & Animation", R.drawable.d_film, "") { cat("Film & Animation", "1") }
        item("Gaming", R.drawable.d_games, "") { cat("Gaming", "20") }
        item("Settings", R.drawable.ic_manage, "") { settings() }
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
        show(sv, push)
        fill(col, url, featured)
    }
    private fun fill(col: LinearLayout, url: String, featured: Boolean) {
        val status = tv("Loading…", 16f, 0xFF555555.toInt()).apply { setPadding(dp(18), dp(18), 0, 0) }
        col.addView(status)
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
                val idv = o.get("id"); val id = sn.optJSONObject("resourceId")?.optString("videoId") ?: if (idv is JSONObject) idv.optString("videoId") else idv.toString()
                if (id.isEmpty()) continue
                val views = o.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull()?.let { "%,d views".format(it) }
                val t = Html.fromHtml(sn.getString("title"), 0).toString(); val ch = sn.optString("videoOwnerChannelTitle").ifEmpty { sn.optString("channelTitle") }
                if (t == "Private video" || t == "Deleted video") continue
                chOf[id] = sn.optString("videoOwnerChannelId").ifEmpty { sn.optString("channelId") }
                val dur = o.optJSONObject("contentDetails")?.optString("duration")?.let { fmt(it) } ?: ""
                col.addView(if (i == 0 && featured) banner(id, t, ch, views, dur) else row(id, t, ch, views, dur),
                    LinearLayout.LayoutParams(-1, -2).apply { if (!(i == 0 && featured)) topMargin = dp(4) })
            }
        }
    }
    private fun banner(id: String, t: String, ch: String, v: String?, d: String): View {
        val f = FrameLayout(this)
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.video_preview) }
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
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.video_preview) }
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
        addSaved("history", id, title, ch)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val web = WebView(this).apply {
            setBackgroundColor(Color.BLACK); settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = android.webkit.WebChromeClient()
            loadDataWithBaseURL("https://it.metro.tube/",
                "<body style='margin:0;background:#000'><iframe width='100%' height='100%' src='https://www.youtube.com/embed/$id?playsinline=1&autoplay=1&rel=0&origin=https://it.metro.tube' referrerpolicy='strict-origin-when-cross-origin' frameborder='0' allow='autoplay;fullscreen;encrypted-media' allowfullscreen></iframe></body>",
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
                    else if (k == 9) { addSaved("later", id, title, ch); Toast.makeText(this@MainActivity, "Added to Watch later", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(this@MainActivity, "Sign in to use this", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        col.addView(bar, LinearLayout.LayoutParams(-1, dp(52)))
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(tv(title, 19f, 0xFF222222.toInt(), true)); addView(tv("By $ch", 15f, BLUE, true).apply { setOnClickListener { chOf[id]?.takeIf { it.isNotEmpty() }?.let { channel(it) } } })
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
                    chOf[vid] = sn.optString("channelId"); if (vid.isNotEmpty() && vid != id) body.addView(row(vid, Html.fromHtml(sn.getString("title"), 0).toString(), sn.getString("channelTitle"), null),
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
                        val av = ImageView(this@MainActivity).apply { setImageResource(R.drawable.user_preview); scaleType = ImageView.ScaleType.CENTER_CROP }
                        addView(av, LinearLayout.LayoutParams(dp(46), dp(46)))
                        c.optString("authorProfileImageUrl").takeIf { it.startsWith("http") }?.let { load(av, it) }
                        addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
                            addView(LinearLayout(this@MainActivity).apply {
                                addView(tv(c.getString("authorDisplayName"), 16f, BLUE, true).apply {
                                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                                    setOnClickListener { c.optJSONObject("authorChannelId")?.optString("value")?.takeIf { it.isNotEmpty() }?.let { channel(it) } }
                                }, LinearLayout.LayoutParams(0, -2, 1f))
                                addView(sym("👍 ${c.optInt("likeCount")}", 14f, 0xFF2E9E2E.toInt()))
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

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun saved(name: String) = try { org.json.JSONArray(prefs.getString(name, "[]")) } catch (e: Exception) { org.json.JSONArray() }
    private fun addSaved(name: String, id: String, t: String, ch: String) {
        val old = saved(name); val n = org.json.JSONArray()
        n.put(JSONObject().put("id", id).put("t", t).put("c", ch).put("h", chOf[id] ?: ""))
        for (i in 0 until old.length()) { val o = old.getJSONObject(i); if (o.getString("id") != id && n.length() < 100) n.put(o) }
        prefs.edit().putString(name, n.toString()).apply()
    }
    private fun localList(title: String, name: String) {
        val sv = ScrollView(this); val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; sv.addView(col)
        val head = LinearLayout(this).apply { setBackgroundColor(DARK); setPadding(dp(18), dp(14), dp(18), dp(14)); gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(title, 22f, Color.WHITE, true), lp(0, -2, 1f))
        head.addView(tv("Clear", 16f, 0xFFCCCCCC.toInt()).apply { setOnClickListener { prefs.edit().remove(name).apply(); stack.removeAt(stack.size - 1); localList(title, name) } })
        col.addView(head)
        val a = saved(name)
        if (a.length() == 0) {
            col.addView(ImageView(this).apply { setImageResource(R.drawable.no_video) }, lp(dp(100), dp(100)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(40) })
            col.addView(tv("Nothing here yet", 18f, 0xFF555555.toInt()).apply { gravity = Gravity.CENTER }, lp(-1, -2).apply { topMargin = dp(10) })
        }
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); chOf[o.getString("id")] = o.optString("h")
            col.addView(row(o.getString("id"), o.getString("t"), o.getString("c"), null), lp(-1, -2).apply { topMargin = dp(4) })
        }
        show(sv)
    }

    private fun playlists(col: LinearLayout, cid: String) {
        get("${base}playlists?part=snippet,contentDetails&maxResults=25&channelId=$cid&key=$key") { j ->
            val a = j?.optJSONArray("items")
            if (a == null || a.length() == 0) { col.addView(tv("No playlists", 16f, 0xFF555555.toInt()).apply { setPadding(dp(16), dp(14), 0, 0) }); return@get }
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i); val sn = o.getJSONObject("snippet"); val pid = o.getString("id"); val t = sn.getString("title")
                val n = o.getJSONObject("contentDetails").optInt("itemCount")
                val r = LinearLayout(this).apply { setBackgroundColor(Color.WHITE) }
                val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.video_preview) }
                r.addView(iv, lp(dp(150), dp(84)))
                sn.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url")?.let { load(iv, it) }
                val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(4), dp(8), 0) }
                c.addView(tv(t, 15f, 0xFF222222.toInt(), true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
                c.addView(tv("$n videos", 13f, 0xFF777777.toInt()))
                r.addView(c, lp(0, -2, 1f)); tilt(r)
                r.setOnClickListener { list(t, "$n videos", "${base}playlistItems?part=snippet&maxResults=50&playlistId=$pid&key=$key", false) }
                col.addView(r, lp(-1, -2).apply { topMargin = dp(4) })
            }
        }
    }

    private fun channel(cid: String) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; val sv = ScrollView(this); sv.addView(col)
        val banner = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF9E1F1B.toInt()) }
        col.addView(banner, lp(-1, dp(80)))
        val head = LinearLayout(this).apply { setBackgroundColor(DARK); setPadding(dp(16), dp(14), dp(16), dp(14)); gravity = Gravity.CENTER_VERTICAL }
        val av = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.user_preview) }
        head.addView(av, lp(dp(84), dp(84)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        val name = tv("…", 22f, Color.WHITE, true)
        val subs = tv("", 14f, 0xFF555555.toInt()).apply { setBackgroundColor(0xFFECF0EC.toInt()); setPadding(dp(10), dp(8), dp(10), dp(8)); visibility = View.GONE }
        val sb = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; tilt(this)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/channel/$cid"))) } }
        sb.addView(sym("▶", 14f, Color.WHITE).apply { gravity = Gravity.CENTER; setBackgroundColor(RED) }, lp(dp(40), dp(38)))
        sb.addView(tv("Subscribe", 16f, 0xFF222222.toInt(), true).apply { gravity = Gravity.CENTER; setBackgroundColor(0xFFECECEC.toInt()); setPadding(dp(16), 0, dp(16), 0) }, lp(-2, dp(38)))
        sb.addView(subs, lp(-2, dp(38)).apply { marginStart = dp(8) })
        info.addView(name); info.addView(sb, lp(-2, -2).apply { topMargin = dp(8) })
        head.addView(info, lp(0, -2, 1f)); col.addView(head)
        var uploads = ""; var about = ""
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabs = LinearLayout(this)
        val tvs = listOf("Videos", "Playlists", "About").map { n ->
            tv(n, 16f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(Color.WHITE); setStroke(dp(2), Color.BLACK) } }
        }
        fun pick(i: Int) {
            tvs.forEach { it.setTextColor(0xFF999999.toInt()) }; tvs[i].setTextColor(0xFF111111.toInt()); body.removeAllViews()
            if (i == 0) { if (uploads.isNotEmpty()) fill(body, "${base}playlistItems?part=snippet&maxResults=30&playlistId=$uploads&key=$key", false) }
            else if (i == 1) playlists(body, cid)
            else body.addView(tv(about, 16f, 0xFF333333.toInt()).apply { setPadding(dp(16), dp(14), dp(16), dp(14)) })
        }
        tvs.forEachIndexed { i, t -> tabs.addView(t, lp(0, dp(46), 1f)); t.setOnClickListener { pick(i) } }
        col.addView(tabs); col.addView(body)
        show(sv)
        get("${base}channels?part=snippet,statistics,contentDetails,brandingSettings&id=$cid&key=$key") { j ->
            val ch = j?.optJSONArray("items")?.optJSONObject(0) ?: run { name.text = "Channel unavailable"; return@get }
            val sn = ch.getJSONObject("snippet"); val st = ch.optJSONObject("statistics")
            name.text = sn.getString("title")
            sn.optJSONObject("thumbnails")?.let { th -> (th.optJSONObject("medium") ?: th.optJSONObject("default"))?.optString("url")?.let { load(av, it) } }
            ch.optJSONObject("brandingSettings")?.optJSONObject("image")?.optString("bannerExternalUrl")?.takeIf { it.startsWith("http") }?.let { load(banner, "$it=w1060") }
            if (st != null && !st.optBoolean("hiddenSubscriberCount")) st.optString("subscriberCount").toLongOrNull()?.let { subs.text = "%,d".format(it); subs.visibility = View.VISIBLE }
            uploads = ch.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("uploads") ?: ""
            about = sn.optString("description").ifEmpty { "No description." } + "\n\n" +
                "%,d views".format(st?.optString("viewCount")?.toLongOrNull() ?: 0L) + "\n" +
                "%,d videos".format(st?.optString("videoCount")?.toLongOrNull() ?: 0L) + "\nJoined " + sn.optString("publishedAt").take(10)
            pick(0)
        }
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
    companion object { var font: Typeface? = null }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val bm: Bitmap? = when (k) {
        4 -> R.drawable.ic_music; 5 -> R.drawable.ic_upload; 6 -> R.drawable.ic_share; 9 -> R.drawable.ic_add; else -> 0
    }.let { if (it != 0) BitmapFactory.decodeResource(ctx.resources, it) else null }
    private fun g(c: Canvas, cx: Float, cy: Float, size: Float) {
        val b = bm ?: return
        p.colorFilter = PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(b, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), p)
        p.colorFilter = null
    }
    override fun onDraw(c: Canvas) {
        val u = minOf(width, height) / 100f; val cx = width / 2f; val cy = height / 2f
        p.color = col; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 7 * u; p.style = Paint.Style.FILL
        fun ring() { p.style = Paint.Style.STROKE; c.drawCircle(cx, cy, 44 * u, p); p.style = Paint.Style.FILL }
        when (k) {
            0 -> for (i in -1..1) c.drawRect(cx - 32 * u, cy + i * 24 * u - 5 * u, cx + 32 * u, cy + i * 24 * u + 5 * u, p)
            1 -> { p.style = Paint.Style.STROKE; c.drawCircle(cx - 6 * u, cy - 6 * u, 22 * u, p); c.drawLine(cx + 10 * u, cy + 10 * u, cx + 34 * u, cy + 34 * u, p) }
            2 -> { ring(); p.textSize = 26 * u; p.textAlign = Paint.Align.CENTER; p.typeface = font ?: Typeface.DEFAULT_BOLD; p.isFakeBoldText = true; c.drawText("You", cx, cy + 9 * u, p) }
            3 -> {
                ring(); for (i in 0..2) c.drawRect(cx - 24 * u + i * 18 * u, cy + 20 * u - (10 + i * 12) * u, cx - 14 * u + i * 18 * u, cy + 20 * u, p)
                p.style = Paint.Style.STROKE; c.drawLine(cx - 26 * u, cy - 4 * u, cx + 24 * u, cy - 26 * u, p)
            }
            4 -> { ring(); g(c, cx, cy, 56 * u) }
            5 -> { ring(); g(c, cx, cy, 52 * u) }
            6 -> g(c, cx, cy, 72 * u)
            7, 8 -> {
                if (k == 8) c.rotate(180f, cx, cy)
                c.drawRect(cx - 36 * u, cy - 4 * u, cx - 22 * u, cy + 34 * u, p); c.drawRoundRect(cx - 18 * u, cy - 8 * u, cx + 34 * u, cy + 34 * u, 8 * u, 8 * u, p)
                c.drawRoundRect(cx - 8 * u, cy - 36 * u, cx + 8 * u, cy - 2 * u, 8 * u, 8 * u, p)
            }
            9 -> g(c, cx, cy, 52 * u)
        }
    }
}
