package it.metro.tube

import android.app.Activity
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.Html
import android.text.TextUtils
import android.text.format.DateUtils
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.webkit.WebChromeClient
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
// Client OAuth di tipo "TV e dispositivi con input limitato" (Google Cloud Console)
const val OAUTH_ID = "INSERISCI_CLIENT_ID"
const val OAUTH_SECRET = "INSERISCI_CLIENT_SECRET"

class MainActivity : Activity() {
    private val DARK = 0xFF222421.toInt(); private val BG = 0xFFEFF3EF.toInt()
    private val TOPC = 0xFF1A1C19.toInt(); private val BOTC = 0xFF202020.toInt(); private val DRAW = 0xFF212021.toInt()
    private fun grad(top: Int, bot: Int, r: Int = 0) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bot)).apply { cornerRadius = dp(r).toFloat() }
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
    private var meName = ""; private var meAvatar = ""; private var meId = ""; private var likesPl = ""
    @Volatile private var polling = false
    private var playerCol: LinearLayout? = null; private var playerWeb: WebView? = null; private val playerRest = ArrayList<View>()
    private var fsView: View? = null; private var fsCb: WebChromeClient.CustomViewCallback? = null
    private val clientId get() = prefs.getString("cid", "")!!.ifEmpty { OAUTH_ID }
    private val clientSecret get() = prefs.getString("csec", "")!!.ifEmpty { if (OAUTH_SECRET.startsWith("INSERISCI")) "" else OAUTH_SECRET }
    private val signedIn get() = prefs.getString("refresh", "")!!.isNotEmpty() || prefs.getString("access", "")!!.isNotEmpty()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private val chrome = object : WebChromeClient() {
        override fun onShowCustomView(v: View, cb: WebChromeClient.CustomViewCallback) {
            if (fsView != null) { cb.onCustomViewHidden(); return }
            fsView = v; fsCb = cb; v.setBackgroundColor(Color.BLACK)
            (window.decorView as FrameLayout).addView(v, FrameLayout.LayoutParams(-1, -1))
            immersive(true); requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        override fun onHideCustomView() {
            val v = fsView ?: return
            (window.decorView as FrameLayout).removeView(v); fsView = null; fsCb?.onCustomViewHidden(); fsCb = null
            immersive(false); requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    @Suppress("DEPRECATION")
    private fun immersive(on: Boolean) {
        window.decorView.systemUiVisibility = if (on) (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE) else 0
    }
    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); applyLayout() }
    private fun applyLayout() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val full = land && playerCol != null && stack.lastOrNull() === playerCol
        topV.visibility = if (full) View.GONE else View.VISIBLE; botV.visibility = topV.visibility
        playerRest.forEach { it.visibility = if (full) View.GONE else View.VISIBLE }
        playerWeb?.let { w -> (w.layoutParams as? LinearLayout.LayoutParams)?.let { q -> q.height = if (full) -1 else dp(220); w.layoutParams = q } }
    }
    private fun stopPlayer() {
        playerWeb?.loadUrl("about:blank"); playerCol?.let { stack.remove(it) }
        playerWeb = null; playerCol = null; playerRest.clear()
    }
    private lateinit var content: FrameLayout
    private lateinit var drawer: ScrollView
    private lateinit var drawerList: LinearLayout
    private lateinit var main: LinearLayout
    private lateinit var scrim: View
    private lateinit var topV: View
    private lateinit var botV: View
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
        window.statusBarColor = TOPC; window.navigationBarColor = BOTC
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(DRAW) }
        val stage = FrameLayout(this)
        drawerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        drawer = ScrollView(this).apply { setBackgroundColor(DRAW); visibility = View.GONE; addView(drawerList) }
        fillDrawer()
        stage.addView(drawer, FrameLayout.LayoutParams(dp(280), -1))
        main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        topV = topBar(); main.addView(topV, LinearLayout.LayoutParams(-1, dp(56)))
        content = FrameLayout(this); main.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        stage.addView(main, FrameLayout.LayoutParams(-1, -1))
        scrim = View(this).apply { visibility = View.GONE; setOnClickListener { toggleDrawer() } }
        stage.addView(scrim, FrameLayout.LayoutParams(dp(60), -1, Gravity.END))
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        botV = bottomBar(); root.addView(botV, LinearLayout.LayoutParams(-1, dp(64)))
        setContentView(root)
        if (signedIn) refreshAccount()
        home()
    }

    // ---------- Barre ----------
    private fun logo() = ImageView(this).apply {
        setImageResource(R.drawable.logo_color); scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(0, dp(13), 0, dp(13))
    }
    private fun topBar(): View {
        val b = FrameLayout(this).apply { setBackgroundColor(TOPC) }
        b.addView(Icon(this, 0).apply { tilt(this); setOnClickListener { toggleDrawer() } }, FrameLayout.LayoutParams(dp(56), -1, Gravity.START))
        logoBox = FrameLayout(this).also { it.addView(logo()) }
        b.addView(logoBox, FrameLayout.LayoutParams(-1, -1).apply { marginStart = dp(64); marginEnd = dp(64) })
        b.addView(Icon(this, 1).apply { tilt(this); setOnClickListener { openSearch() } }, FrameLayout.LayoutParams(dp(56), -1, Gravity.END))
        b.addView(View(this).apply { setBackgroundColor(0xFF323232.toInt()) }, FrameLayout.LayoutParams(-1, dp(1), Gravity.BOTTOM))
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
        val b = FrameLayout(this).apply { setBackgroundColor(BOTC) }
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

    private fun fillDrawer() {
        val d = drawerList; d.removeAllViews(); val grey = 0xFF9A9A9A.toInt()
        fun line() {
            d.addView(View(this).apply { setBackgroundColor(0xFF191819.toInt()) }, lp(-1, dp(1)))
            d.addView(View(this).apply { setBackgroundColor(0xFF313131.toInt()) }, lp(-1, dp(1)))
        }
        if (signedIn && meName.isNotEmpty()) {
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(8)) }
            val av = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.user_preview) }
            r.addView(av, lp(dp(48), dp(48))); if (meAvatar.isNotEmpty()) load(av, meAvatar)
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            c.addView(tv(meName, 17f, Color.WHITE, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            c.addView(tv("Sign out", 14f, 0xFF8FB0FF.toInt()).apply { setPadding(0, dp(4), 0, dp(4)); setOnClickListener { toggleDrawer(); signOut() } })
            r.addView(c, lp(0, -2, 1f)); d.addView(r)
        } else d.addView(tv("Sign in", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; tilt(this); setOnClickListener { toggleDrawer(); signIn() }
            background = GradientDrawable().apply { setColor(0xFF6B92E6.toInt()); setStroke(dp(1), 0xFF3F62B8.toInt()) }
        }, lp(-1, dp(44)).apply { setMargins(dp(8), dp(10), dp(8), dp(6)) })
        fun head(t: String) { d.addView(tv(t, 12f, 0xFF8A8A8A.toInt(), true).apply { setPadding(dp(14), dp(12), 0, dp(8)) }); line() }
        fun item(name: String, res: Int, kind: Int, go: () -> Unit) {
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), 0, dp(8), 0); tilt(this); setOnClickListener { toggleDrawer(); go() } }
            if (res != 0) r.addView(ImageView(this).apply { setImageResource(res); setColorFilter(grey) }, lp(dp(24), dp(24)))
            else r.addView(Icon(this, kind, grey), lp(dp(24), dp(24)))
            r.addView(tv(name, 17f, 0xFFBDBDBD.toInt(), true), lp(-2, -2).apply { marginStart = dp(16) })
            d.addView(r, lp(-1, dp(60))); line()
        }
        head("ACTIVITY")
        item("Home", 0, 10) { home() }
        if (signedIn) {
            item("My channel", R.drawable.d_channels, 0) { if (meId.isNotEmpty()) channel(meId) }
            item("Subscriptions", R.drawable.d_people, 0) { subscriptions() }
            item("Playlists", R.drawable.d_list, 0) { myPlaylists() }
            item("Liked videos", 0, 7) { liked() }
        }
        item("History", 0, 11) { localList("History", "history") }
        item("Watch later", 0, 12) { localList("Watch later", "later") }
        head("BEST OF YOUTUBE")
        item("Trending", 0, 13) { cat("Trending", null) }
        item("Music", R.drawable.d_music, 0) { cat("Music", "10") }
        item("Entertainment", R.drawable.d_entertainment, 0) { cat("Entertainment", "24") }
        item("Sports", R.drawable.d_sports, 0) { cat("Sports", "17") }
        item("Comedy", R.drawable.d_comedy, 0) { cat("Comedy", "23") }
        item("Film & Animation", R.drawable.d_film, 0) { cat("Film & Animation", "1") }
        item("Gaming", R.drawable.d_games, 0) { cat("Gaming", "20") }
        item("Settings", R.drawable.ic_manage, 0) { settings() }
    }
    private fun drawerW() = minOf((resources.displayMetrics.widthPixels * 0.75f).toInt(), dp(320))
    private fun toggleDrawer() {
        if (drawer.visibility != View.VISIBLE) {
            drawer.layoutParams = FrameLayout.LayoutParams(drawerW(), -1); drawer.visibility = View.VISIBLE
            scrim.layoutParams = FrameLayout.LayoutParams(resources.displayMetrics.widthPixels - drawerW(), -1, Gravity.END); scrim.visibility = View.VISIBLE
            main.animate().translationX(drawerW().toFloat()).setDuration(220).start()
        } else {
            scrim.visibility = View.GONE
            main.animate().translationX(0f).setDuration(220).withEndAction { drawer.visibility = View.GONE }.start()
        }
    }

    // ---------- Schermate ----------
    private fun show(v: View, push: Boolean = true) {
        if (!push) { stopPlayer(); stack.clear() }
        stack.add(v); content.removeAllViews(); content.addView(v)
        v.translationX = dp(40).toFloat(); v.alpha = 0f; v.animate().translationX(0f).alpha(1f).setDuration(200).start()
        applyLayout()
    }
    override fun onBackPressed() {
        when {
            fsView != null -> chrome.onHideCustomView()
            drawer.visibility == View.VISIBLE -> toggleDrawer()
            stack.size > 1 -> { val r = stack.removeAt(stack.size - 1); if (r === playerCol) stopPlayer(); content.removeAllViews(); content.addView(stack.last()); applyLayout() }
            else -> super.onBackPressed()
        }
    }
    private fun trendUrl(cat: String?) = "${base}videos?part=snippet,statistics,contentDetails&chart=mostPopular&maxResults=25&regionCode=$region" +
        (cat?.let { "&videoCategoryId=$it" } ?: "") + "&key=$key"
    private fun home() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; val sv = ScrollView(this); sv.addView(col)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(DARK); setPadding(dp(18), dp(14), dp(18), dp(14))
            addView(tv("Home", 22f, Color.WHITE, true)); addView(tv(if (signedIn && meName.isNotEmpty()) meName else region, 15f, 0xFFCCCCCC.toInt()))
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; val tabs = LinearLayout(this)
        val tvs = listOf("Feed", "Trending").map { n ->
            tv(n, 16f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(Color.WHITE); setStroke(dp(2), Color.BLACK) } }
        }
        fun pick(i: Int) {
            tvs.forEach { it.setTextColor(0xFF999999.toInt()) }; tvs[i].setTextColor(0xFF111111.toInt()); body.removeAllViews()
            if (i == 1) fill(body, trendUrl(null), true)
            else if (signedIn) feed(body)
            else {
                body.addView(tv("Sign in to see the latest videos from your subscriptions.", 17f, 0xFF333333.toInt()).apply { setPadding(dp(18), dp(18), dp(18), dp(10)) })
                body.addView(tv("Sign in", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setBackgroundColor(0xFF6B8FE6.toInt()); tilt(this); setOnClickListener { signIn() } },
                    lp(-1, dp(52)).apply { setMargins(dp(18), 0, dp(18), 0) })
            }
        }
        tvs.forEachIndexed { i, t -> tabs.addView(t, lp(0, dp(46), 1f)); t.setOnClickListener { pick(i) } }
        col.addView(tabs); col.addView(body)
        show(sv, false); pick(if (signedIn) 0 else 1)
    }
    private fun cat(name: String, id: String?) = list(name, "Popular videos · $region", trendUrl(id), true)

    private fun list(title: String, sub: String, url: String, featured: Boolean, push: Boolean = true, auth: Boolean = false) {
        val sv = ScrollView(this); val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(col)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(DARK); setPadding(dp(18), dp(14), dp(18), dp(14))
            addView(tv(title, 22f, Color.WHITE, true)); addView(tv(sub, 15f, 0xFFCCCCCC.toInt()))
        })
        show(sv, push)
        fill(col, url, featured, auth)
    }
    private fun fill(col: LinearLayout, url: String, featured: Boolean, auth: Boolean = false) {
        val status = tv("Loading…", 16f, 0xFF555555.toInt()).apply { setPadding(dp(18), dp(18), 0, 0) }
        col.addView(status)
        fetch(url, auth) { j ->
            val arr = j?.optJSONArray("items")
            if (arr == null) {
                status.text = when {
                    key.startsWith("INSERISCI") -> "Tap here and add your free YouTube API key to start."
                    else -> j?.optJSONObject("error")?.optString("message") ?: "Network error. Tap to open Settings."
                }
                status.setOnClickListener { settings() }
                return@fetch
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
        stopPlayer()
        addSaved("history", id, title, ch)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val web = WebView(this).apply {
            setBackgroundColor(Color.BLACK); settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = chrome
            loadDataWithBaseURL("https://it.metro.tube/",
                "<body style='margin:0;background:#000'><iframe width='100%' height='100%' src='https://www.youtube.com/embed/$id?playsinline=1&autoplay=1&rel=0&origin=https://it.metro.tube' referrerpolicy='strict-origin-when-cross-origin' frameborder='0' allow='autoplay;fullscreen;encrypted-media' allowfullscreen></iframe></body>",
                "text/html", "utf-8", null)
        }
        col.addView(web, LinearLayout.LayoutParams(-1, dp(220)))
        val bar = LinearLayout(this).apply { background = grad(0xFF515151.toInt(), 0xFF424242.toInt()); gravity = Gravity.CENTER_VERTICAL }
        bar.addView(tv("HQ", 14f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = grad(0xFFCB3A2F.toInt(), 0xFFA32A21.toInt(), 3) },
            LinearLayout.LayoutParams(0, dp(32), 1f).apply { setMargins(dp(14), 0, dp(14), 0) })
        val off = 0xFFB4B4B4.toInt(); var rating = "none"
        val likeI = Icon(this, 7, off); val dislikeI = Icon(this, 8, off)
        fun paint() { likeI.setActive(rating == "like"); dislikeI.setActive(rating == "dislike") }
        fun rate(r: String) {
            if (!signedIn) { toast("Sign in to rate videos"); signIn(); return }
            val old = rating; val nw = if (old == r) "none" else r
            rating = nw; paint()
            api("${base}videos/rate?id=$id&rating=$nw", "POST") { c, _ -> if (c !in 200..299) { rating = old; paint(); toast("Could not rate") } }
        }
        likeI.apply { tilt(this); setOnClickListener { rate("like") } }
        dislikeI.apply { tilt(this); setOnClickListener { rate("dislike") } }
        val plusI = Icon(this, 9, 0xFFDDDDDD.toInt()).apply { tilt(this); setOnClickListener { plusMenu(id, title, ch) } }
        val shareI = Icon(this, 6, 0xFFDDDDDD.toInt()).apply {
            tilt(this)
            setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://youtu.be/$id"), null)) }
        }
        for (v in listOf(likeI, dislikeI, plusI, shareI)) bar.addView(v, LinearLayout.LayoutParams(0, dp(52), 1f))
        if (signedIn) getA("${base}videos/getRating?id=$id") { j -> rating = j?.optJSONArray("items")?.optJSONObject(0)?.optString("rating") ?: "none"; paint() }
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
            if (signedIn) {
                val box = LinearLayout(this).apply { setPadding(dp(14), dp(10), dp(14), dp(6)); gravity = Gravity.CENTER_VERTICAL }
                val et = EditText(this).apply { hint = "Add a comment"; typeface = fText; textSize = 16f }
                box.addView(et, lp(0, -2, 1f))
                box.addView(tv("Post", 17f, BLUE, true).apply {
                    setPadding(dp(12), dp(8), dp(4), dp(8))
                    setOnClickListener {
                        val txt = et.text.toString().trim()
                        if (txt.isNotEmpty()) api("${base}commentThreads?part=snippet", "POST",
                            JSONObject().put("snippet", JSONObject().put("videoId", id).put("topLevelComment", JSONObject().put("snippet", JSONObject().put("textOriginal", txt)))).toString()) { c, _ ->
                            toast(if (c in 200..299) "Comment posted" else "Could not post"); if (c in 200..299) t2.performClick()
                        }
                    }
                })
                body.addView(box)
            } else body.addView(tv("Sign in to post a comment.", 14f, 0xFF555555.toInt()).apply { setPadding(dp(14), dp(10), 0, dp(6)); setOnClickListener { signIn() } })
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
        for (i in 0 until col.childCount) if (col.getChildAt(i) !== web) playerRest.add(col.getChildAt(i))
        playerWeb = web; playerCol = col
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

    // ---------- Rete e login Google (device flow) ----------
    private fun http(url: String, method: String = "GET", form: String? = null, json: String? = null, auth: Boolean = false): Pair<Int, String> {
        val c = URL(url).openConnection() as java.net.HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 20000
        if (auth) c.setRequestProperty("Authorization", "Bearer " + token())
        val body = form ?: json
        if (body != null || method == "POST") {
            c.doOutput = true
            c.setRequestProperty("Content-Type", if (form != null) "application/x-www-form-urlencoded" else "application/json")
            c.outputStream.use { it.write((body ?: "").toByteArray()) }
        }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "")
    }
    private fun httpPost(url: String, form: String): JSONObject? = try { JSONObject(http(url, "POST", form = form).second) } catch (e: Exception) { null }
    private fun token(): String {
        if (System.currentTimeMillis() < prefs.getLong("exp", 0) - 60000) return prefs.getString("access", "")!!
        val r = httpPost("https://oauth2.googleapis.com/token", "client_id=${enc(clientId)}${if (clientSecret.isEmpty()) "" else "&client_secret=" + enc(clientSecret)}&refresh_token=${enc(prefs.getString("refresh", "")!!)}&grant_type=refresh_token")
        val t = r?.optString("access_token").orEmpty()
        if (t.isNotEmpty()) prefs.edit().putString("access", t).putLong("exp", System.currentTimeMillis() + (r?.optLong("expires_in", 3600) ?: 3600) * 1000).apply()
        return t
    }
    private fun api(url: String, method: String = "GET", json: String? = null, cb: (Int, JSONObject?) -> Unit) = thread {
        val (code, txt) = try { http(url, method, json = json, auth = true) } catch (e: Exception) { -1 to "" }
        val j = try { JSONObject(txt) } catch (e: Exception) { null }
        ui.post { cb(code, j) }
    }
    private fun getA(url: String, cb: (JSONObject?) -> Unit) = api(url) { c, j -> cb(if (c in 200..299) j else null) }
    private fun fetch(url: String, auth: Boolean, cb: (JSONObject?) -> Unit) = if (auth) getA(url, cb) else get(url, cb)

    private fun signIn() {
        if (clientId.startsWith("INSERISCI")) { askOauth { }; return }
        thread {
            val r = httpPost("https://oauth2.googleapis.com/device/code", "client_id=${enc(clientId)}&scope=${enc("https://www.googleapis.com/auth/youtube")}")
            ui.post {
                if (r == null || !r.has("user_code")) { toast(r?.optString("error_description")?.ifEmpty { null } ?: "Sign-in failed. Check the OAuth client."); return@post }
                val code = r.getString("user_code"); val url = r.getString("verification_url"); val dev = r.getString("device_code")
                val until = System.currentTimeMillis() + r.optLong("expires_in", 1800) * 1000; var wait = r.optInt("interval", 5)
                val dlg = android.app.AlertDialog.Builder(this).setTitle("Sign in with Google").setCancelable(false)
                    .setMessage("1. Open $url\n2. Enter this code:\n\n$code\n\nThis window closes when you are signed in.")
                    .setPositiveButton("Open page", null).setNeutralButton("Copy code", null)
                    .setNegativeButton("Cancel") { _, _ -> polling = false }.setOnCancelListener { polling = false }.show()
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("code", code)); toast("Code copied")
                }
                polling = true
                thread {
                    while (polling && System.currentTimeMillis() < until) {
                        Thread.sleep(wait * 1000L)
                        if (!polling) break
                        val t = httpPost("https://oauth2.googleapis.com/token", "client_id=${enc(clientId)}${if (clientSecret.isEmpty()) "" else "&client_secret=" + enc(clientSecret)}&device_code=${enc(dev)}&grant_type=${enc("urn:ietf:params:oauth:grant-type:device_code")}")
                        if (t != null && t.has("access_token")) {
                            prefs.edit().putString("access", t.getString("access_token")).putString("refresh", t.optString("refresh_token"))
                                .putLong("exp", System.currentTimeMillis() + t.optLong("expires_in", 3600) * 1000).apply()
                            polling = false
                            ui.post { dlg.dismiss(); toast("Signed in"); refreshAccount(); home() }
                            return@thread
                        }
                        when (t?.optString("error")) { "authorization_pending" -> {}; "slow_down" -> wait += 5; else -> { val e = (t?.optString("error").orEmpty() + " " + t?.optString("error_description").orEmpty()).trim().ifEmpty { "network error" }; ui.post { toast("Sign-in failed: $e") }; break } }
                    }
                    ui.post { if (dlg.isShowing) dlg.dismiss() }
                }
            }
        }
    }
    private fun signOut() {
        val t = prefs.getString("access", "") ?: ""
        thread { try { http("https://oauth2.googleapis.com/revoke?token=${enc(t)}", "POST") } catch (e: Exception) { } }
        prefs.edit().remove("access").remove("refresh").remove("exp").apply()
        meName = ""; meAvatar = ""; meId = ""; likesPl = ""
        fillDrawer(); toast("Signed out"); home()
    }
    private fun refreshAccount() = getA("${base}channels?part=snippet,contentDetails&mine=true") { j ->
        val ch = j?.optJSONArray("items")?.optJSONObject(0) ?: return@getA
        meId = ch.getString("id"); val sn = ch.getJSONObject("snippet"); meName = sn.getString("title")
        meAvatar = sn.optJSONObject("thumbnails")?.optJSONObject("default")?.optString("url") ?: ""
        likesPl = ch.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("likes") ?: ""
        fillDrawer()
    }
    private fun askOauth(done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val a = EditText(this).apply { hint = "Client ID"; setSingleLine(); setText(prefs.getString("cid", "")) }
        val b = EditText(this).apply { hint = "Client secret"; setSingleLine(); setText(prefs.getString("csec", "")) }
        box.addView(a); box.addView(b)
        android.app.AlertDialog.Builder(this).setTitle("Google OAuth client")
            .setMessage("Needed to sign in. In Google Cloud Console enable YouTube Data API v3 and create an OAuth client of type \"TVs and Limited Input devices\", then paste its ID and secret.")
            .setView(box).setPositiveButton("Save") { _, _ -> prefs.edit().putString("cid", a.text.toString().trim()).putString("csec", b.text.toString().trim()).apply(); done() }
            .setNegativeButton("Cancel", null).show()
    }

    // ---------- Pagine dell'account ----------
    private fun page(title: String, sub: String): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; val sv = ScrollView(this); sv.addView(col)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(DARK); setPadding(dp(18), dp(14), dp(18), dp(14))
            addView(tv(title, 22f, Color.WHITE, true)); if (sub.isNotEmpty()) addView(tv(sub, 15f, 0xFFCCCCCC.toInt()))
        })
        show(sv); return col
    }
    private fun feed(col: LinearLayout) {
        val st = tv("Loading your feed…", 16f, 0xFF555555.toInt()).apply { setPadding(dp(18), dp(18), 0, 0) }; col.addView(st)
        getA("${base}subscriptions?part=snippet&mine=true&maxResults=25&order=relevance") { j ->
            val a = j?.optJSONArray("items")
            if (a == null) { st.text = "Could not load your subscriptions."; return@getA }
            if (a.length() == 0) { st.text = "Subscribe to channels to fill your feed."; return@getA }
            val all = ArrayList<JSONObject>(); var left = a.length()
            for (i in 0 until a.length()) {
                val cid = a.getJSONObject(i).getJSONObject("snippet").getJSONObject("resourceId").getString("channelId")
                get("${base}playlistItems?part=snippet&maxResults=3&playlistId=UU${cid.drop(2)}&key=$key") { r ->
                    r?.optJSONArray("items")?.let { for (n in 0 until it.length()) all.add(it.getJSONObject(n).getJSONObject("snippet")) }
                    if (--left == 0) {
                        col.removeView(st); all.sortByDescending { it.optString("publishedAt") }
                        for (sn in all.take(40)) {
                            val id = sn.getJSONObject("resourceId").getString("videoId"); val t = sn.getString("title")
                            if (t == "Private video" || t == "Deleted video") continue
                            val ch = sn.optString("videoOwnerChannelTitle").ifEmpty { sn.optString("channelTitle") }
                            chOf[id] = sn.optString("videoOwnerChannelId").ifEmpty { sn.optString("channelId") }
                            col.addView(row(id, t, ch, null), lp(-1, -2).apply { topMargin = dp(4) })
                        }
                    }
                }
            }
        }
    }
    private fun subscriptions() {
        val col = page("Subscriptions", "")
        getA("${base}subscriptions?part=snippet&mine=true&maxResults=50&order=alphabetical") { j ->
            val a = j?.optJSONArray("items")
            if (a == null || a.length() == 0) { col.addView(tv("No subscriptions", 16f, 0xFF555555.toInt()).apply { setPadding(dp(18), dp(18), 0, 0) }); return@getA }
            for (i in 0 until a.length()) {
                val sn = a.getJSONObject(i).getJSONObject("snippet"); val cid = sn.getJSONObject("resourceId").getString("channelId")
                val r = LinearLayout(this).apply { setBackgroundColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL; tilt(this); setOnClickListener { channel(cid) } }
                val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.user_preview) }
                r.addView(iv, lp(dp(64), dp(64))); sn.optJSONObject("thumbnails")?.optJSONObject("default")?.optString("url")?.let { load(iv, it) }
                r.addView(tv(sn.getString("title"), 18f, 0xFF222222.toInt(), true).apply { setPadding(dp(14), 0, dp(8), 0) }, lp(0, -2, 1f))
                col.addView(r, lp(-1, -2).apply { topMargin = dp(4) })
            }
        }
    }
    private fun myPlaylists() { val col = page("Playlists", ""); playlists(col, "${base}playlists?part=snippet,contentDetails&mine=true&maxResults=50", true) }
    private fun liked() {
        if (likesPl.isEmpty()) { toast("Loading your account… try again"); refreshAccount(); return }
        list("Liked videos", "", "${base}playlistItems?part=snippet&maxResults=50&playlistId=$likesPl", false, auth = true)
    }
    private fun plusMenu(id: String, t: String, ch: String) {
        val names = arrayListOf("Watch later (this device)"); val ids = arrayListOf("")
        fun showIt() {
            android.app.AlertDialog.Builder(this).setTitle("Add to").setItems(names.toTypedArray()) { _, w ->
                if (w == 0) { addSaved("later", id, t, ch); toast("Added to Watch later") }
                else api("${base}playlistItems?part=snippet", "POST", """{"snippet":{"playlistId":"${ids[w]}","resourceId":{"kind":"youtube#video","videoId":"$id"}}}""") { c, _ -> toast(if (c in 200..299) "Added" else "Could not add") }
            }.show()
        }
        if (!signedIn) { showIt(); return }
        getA("${base}playlists?part=snippet&mine=true&maxResults=50") { j ->
            j?.optJSONArray("items")?.let { for (i in 0 until it.length()) { val o = it.getJSONObject(i); ids.add(o.getString("id")); names.add(o.getJSONObject("snippet").getString("title")) } }
            showIt()
        }
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

    private fun playlists(col: LinearLayout, url: String, auth: Boolean = false) {
        fetch(url, auth) { j ->
            val a = j?.optJSONArray("items")
            if (a == null || a.length() == 0) { col.addView(tv("No playlists", 16f, 0xFF555555.toInt()).apply { setPadding(dp(16), dp(14), 0, 0) }); return@fetch }
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
                r.setOnClickListener { list(t, "$n videos", "${base}playlistItems?part=snippet&maxResults=50&playlistId=$pid&key=$key", false, auth = auth) }
                col.addView(r, lp(-1, -2).apply { topMargin = dp(4) })
            }
        }
    }

    private fun channel(cid: String) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; val sv = ScrollView(this); sv.addView(col)
        val banner = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = grad(0xFFA81F27.toInt(), 0xFF741319.toInt()) }
        col.addView(banner, lp(-1, dp(80)))
        val head = LinearLayout(this).apply { setBackgroundColor(DARK); setPadding(dp(16), dp(14), dp(16), dp(14)); gravity = Gravity.CENTER_VERTICAL }
        val av = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.user_preview) }
        head.addView(av, lp(dp(84), dp(84)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        val name = tv("…", 22f, Color.WHITE, true)
        val subs = tv("", 14f, 0xFF777777.toInt()).apply { background = grad(Color.WHITE, 0xFFF1F1F1.toInt(), 4); setPadding(dp(12), 0, dp(12), 0); gravity = Gravity.CENTER }
        val subTail = Icon(this, 15)
        val subsBox = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
        subsBox.addView(subTail, lp(dp(8), dp(16))); subsBox.addView(subs, lp(-2, dp(38)))
        var subId = ""
        val subLbl = tv("Subscribe", 16f, 0xFF222222.toInt(), true).apply { gravity = Gravity.CENTER; background = grad(0xFFF6F6F6.toInt(), 0xFFE2E2E2.toInt(), 3); setPadding(dp(16), 0, dp(16), 0) }
        val sb = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; tilt(this)
            setOnClickListener {
                if (!signedIn) signIn()
                else if (subId.isEmpty()) api("${base}subscriptions?part=snippet", "POST", """{"snippet":{"resourceId":{"kind":"youtube#channel","channelId":"$cid"}}}""") { c, j ->
                    if (c in 200..299) { subId = j?.optString("id") ?: ""; subLbl.text = "Subscribed" } else toast("Could not subscribe")
                }
                else api("${base}subscriptions?id=$subId", "DELETE") { c, _ -> if (c in 200..299) { subId = ""; subLbl.text = "Subscribe" } else toast("Could not unsubscribe") }
            } }
        val subRed = FrameLayout(this).apply { background = grad(0xFFB84731.toInt(), 0xFF8E2B1A.toInt(), 3) }
        subRed.addView(Icon(this, 14), FrameLayout.LayoutParams(-1, -1)); sb.addView(subRed, lp(dp(44), dp(38)))
        sb.addView(subLbl, lp(-2, dp(38)))
        sb.addView(subsBox, lp(-2, dp(38)).apply { marginStart = dp(8) })
        if (signedIn && cid != meId) getA("${base}subscriptions?part=id&mine=true&forChannelId=$cid") { j ->
            j?.optJSONArray("items")?.optJSONObject(0)?.optString("id")?.let { if (it.isNotEmpty()) { subId = it; subLbl.text = "Subscribed" } }
        }
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
            else if (i == 1) playlists(body, "${base}playlists?part=snippet,contentDetails&maxResults=25&channelId=$cid&key=$key")
            else body.addView(tv(about, 16f, 0xFF333333.toInt()).apply { setPadding(dp(16), dp(14), dp(16), dp(14)) })
        }
        tvs.forEachIndexed { i, t -> tabs.addView(t, lp(0, dp(46), 1f)); t.setOnClickListener { pick(i) } }
        col.addView(tabs); col.addView(body)
        show(sv)
        get("${base}channels?part=snippet,statistics,contentDetails,brandingSettings&id=$cid&key=$key") { j ->
            val ch = j?.optJSONArray("items")?.optJSONObject(0) ?: run { name.text = "Channel unavailable"; return@get }
            val sn = ch.getJSONObject("snippet"); val st = ch.optJSONObject("statistics")
            name.text = sn.getString("title")
            if (meId.isNotEmpty() && cid == meId) { subRed.visibility = View.GONE; subLbl.visibility = View.GONE; subTail.visibility = View.GONE; sb.setOnClickListener(null); sb.isClickable = false }
            sn.optJSONObject("thumbnails")?.let { th -> (th.optJSONObject("medium") ?: th.optJSONObject("default"))?.optString("url")?.let { load(av, it) } }
            ch.optJSONObject("brandingSettings")?.optJSONObject("image")?.optString("bannerExternalUrl")?.takeIf { it.startsWith("http") }?.let { load(banner, "$it=w1060") }
            if (st != null && !st.optBoolean("hiddenSubscriberCount")) st.optString("subscriberCount").toLongOrNull()?.let { subs.text = "%,d".format(it); subsBox.visibility = View.VISIBLE }
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
        row("Account", { if (signedIn) "Sign out" else "Sign in" }) { if (signedIn) signOut() else signIn() }
        row("OAuth client", { if (clientId.startsWith("INSERISCI")) "Not set" else "Set" }) { v -> askOauth { v.text = if (clientId.startsWith("INSERISCI")) "Not set" else "Set" } }
        row("Safe Search", { if (safe) "On" else "Off" }) { safe = !safe }
        col.addView(card)
        show(col)
    }
}

class Icon(ctx: android.content.Context, private val k: Int, private val base: Int = Color.WHITE) : View(ctx) {
    companion object { var font: Typeface? = null }
    private var col = base
    fun setActive(on: Boolean) { col = if (on) 0xFF2FA8F0.toInt() else base; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    private val bm: Bitmap? = when (k) {
        2 -> R.drawable.logo_white; 4 -> R.drawable.ic_music; 5 -> R.drawable.ic_upload; 6 -> R.drawable.ic_share; 7 -> R.drawable.ic_like; 8 -> R.drawable.ic_dislike; 9 -> R.drawable.ic_add; else -> 0
    }.let { if (it != 0) BitmapFactory.decodeResource(ctx.resources, it) else null }
    private fun shade(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    private fun g(c: Canvas, cx: Float, cy: Float, size: Float) {
        val b = bm ?: return
        val r = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        val sc = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        c.drawBitmap(b, null, r, bp)
        gp.shader = LinearGradient(0f, r.top, 0f, r.bottom, col, shade(col, .72f), Shader.TileMode.CLAMP)
        c.drawRect(r, gp)
        c.restoreToCount(sc)
    }
    override fun onDraw(c: Canvas) {
        val u = minOf(width, height) / 100f; val cx = width / 2f; val cy = height / 2f
        p.color = col; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 7 * u; p.style = Paint.Style.FILL
        p.shader = if (k >= 14) null else LinearGradient(0f, cy - 44 * u, 0f, cy + 44 * u, col, shade(col, .72f), Shader.TileMode.CLAMP)
        fun ring() { p.style = Paint.Style.STROKE; c.drawCircle(cx, cy, 44 * u, p); p.style = Paint.Style.FILL }
        when (k) {
            0 -> for (i in -1..1) c.drawRect(cx - 32 * u, cy + i * 24 * u - 5 * u, cx + 32 * u, cy + i * 24 * u + 5 * u, p)
            1 -> { p.style = Paint.Style.STROKE; c.drawCircle(cx - 6 * u, cy - 6 * u, 22 * u, p); c.drawLine(cx + 10 * u, cy + 10 * u, cx + 34 * u, cy + 34 * u, p) }
            2 -> { ring(); bm?.let { b -> val w = 60 * u; val h = w * b.height / b.width; c.drawBitmap(b, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), bp) } }
            3 -> {
                ring(); for (i in 0..2) c.drawRect(cx - 24 * u + i * 18 * u, cy + 20 * u - (10 + i * 12) * u, cx - 14 * u + i * 18 * u, cy + 20 * u, p)
                p.style = Paint.Style.STROKE; c.drawLine(cx - 26 * u, cy - 4 * u, cx + 24 * u, cy - 26 * u, p)
            }
            4 -> { ring(); g(c, cx, cy, 56 * u) }
            5 -> { ring(); g(c, cx, cy, 52 * u) }
            6 -> g(c, cx, cy, 72 * u)
            7, 8 -> g(c, cx, cy, 62 * u)
            9 -> g(c, cx, cy, 52 * u)
            10 -> {
                p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND
                c.drawPath(Path().apply { moveTo(cx - 36 * u, cy - 2 * u); lineTo(cx, cy - 34 * u); lineTo(cx + 36 * u, cy - 2 * u) }, p)
                c.drawRect(cx - 24 * u, cy - 6 * u, cx + 24 * u, cy + 32 * u, p)
            }
            11 -> { p.style = Paint.Style.STROKE; c.drawCircle(cx, cy, 30 * u, p); c.drawLine(cx, cy, cx, cy - 18 * u, p); c.drawLine(cx, cy, cx + 14 * u, cy + 8 * u, p) }
            12 -> {
                p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND; val st = Path()
                for (n in 0..9) {
                    val r = if (n % 2 == 0) 38 * u else 16 * u; val a = Math.toRadians(-90.0 + 36.0 * n)
                    val x = cx + (r * Math.cos(a)).toFloat(); val y = cy + (r * Math.sin(a)).toFloat()
                    if (n == 0) st.moveTo(x, y) else st.lineTo(x, y)
                }
                st.close(); c.drawPath(st, p)
            }
            13 -> {
                p.style = Paint.Style.STROKE; c.drawLine(cx - 30 * u, cy + 28 * u, cx + 28 * u, cy - 28 * u, p)
                c.drawLine(cx + 28 * u, cy - 28 * u, cx + 4 * u, cy - 28 * u, p); c.drawLine(cx + 28 * u, cy - 28 * u, cx + 28 * u, cy - 4 * u, p)
            }
            14 -> {
                p.color = Color.WHITE; c.drawRoundRect(cx - 30 * u, cy - 22 * u, cx + 30 * u, cy + 22 * u, 6 * u, 6 * u, p)
                p.color = 0xFFB84731.toInt(); c.drawPath(Path().apply { moveTo(cx - 8 * u, cy - 12 * u); lineTo(cx - 8 * u, cy + 12 * u); lineTo(cx + 14 * u, cy); close() }, p)
            }
            15 -> { p.color = Color.WHITE; c.drawPath(Path().apply { moveTo(width.toFloat(), height * .1f); lineTo(0f, height / 2f); lineTo(width.toFloat(), height * .9f); close() }, p) }
        }
    }
}
