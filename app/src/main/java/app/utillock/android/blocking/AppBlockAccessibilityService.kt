package app.utillock.android.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.utillock.android.R
import app.utillock.android.UtilLockApplication
import app.utillock.android.challenge.ChallengeActivity
import app.utillock.android.model.DomainMatcher
import app.utillock.android.model.ScheduleEvaluator

class AppBlockAccessibilityService : AccessibilityService() {
    private val repository by lazy { (application as UtilLockApplication).container.protectionRepository }
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var lastBlockKey = ""
    private var lastBlockAt = 0L
    private var overlay: View? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        val active = ScheduleEvaluator.activeProtection(repository.snapshot())
        if (!active.active) {
            hideOverlay()
            return
        }
        if (packageName == applicationContext.packageName) return

        if (packageName in active.packages) {
            block("app:$packageName", packageName, null)
            return
        }

        val url = findBrowserUrl(packageName, rootInActiveWindow) ?: return
        if (DomainMatcher.matches(url, active.domains, active.keywords)) {
            block("web:${DomainMatcher.normalize(url)}", packageName, url)
        }
    }

    override fun onInterrupt() = hideOverlay()

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun block(key: String, packageName: String, url: String?) {
        val now = SystemClock.elapsedRealtime()
        if (lastBlockKey == key && now - lastBlockAt < 1_200) return
        lastBlockKey = key
        lastBlockAt = now
        if (!showOverlay(packageName, url)) {
            // Fallback for devices that do not allow accessibility overlay windows.
            startActivity(
                Intent(this, BlockedActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(BlockedActivity.EXTRA_PACKAGE, packageName)
                    .putExtra(BlockedActivity.EXTRA_URL, url),
            )
        }
    }

    private fun showOverlay(packageName: String, url: String?): Boolean {
        if (overlay != null) return true
        val target = url ?: runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val density = resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(26), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.rgb(30, 30, 34))
                cornerRadius = 42f
            }
        }
        card.addView(TextView(this).apply {
            text = getString(R.string.blocking_active)
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(TextView(this).apply {
            text = target
            textSize = 18f
            setTextColor(Color.rgb(120, 190, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(7))
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(TextView(this).apply {
            text = getString(R.string.blocking_challenge_message)
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(Button(this).apply {
            text = getString(R.string.solve_exercise)
            setOnClickListener {
                hideOverlay()
                startActivity(Intent(this@AppBlockAccessibilityService, ChallengeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(16) })
        card.addView(Button(this).apply {
            text = getString(R.string.go_home)
            setOnClickListener {
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(6) })

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(245, 5, 8, 18))
            addView(card, FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.86f).toInt(),
                -2,
                Gravity.CENTER,
            ))
        }
        val params = WindowManager.LayoutParams(
            -1,
            -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        return runCatching {
            windowManager.addView(root, params)
            overlay = root
            true
        }.getOrDefault(false)
    }

    private fun hideOverlay() {
        val current = overlay ?: return
        overlay = null
        runCatching { windowManager.removeView(current) }
    }

    private fun findBrowserUrl(packageName: String, root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val ids = browserUrlIds[packageName].orEmpty()
        ids.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id)
                .firstOrNull { !it.text.isNullOrBlank() }
                ?.text
                ?.toString()
                ?.let { return it }
        }

        // Browser updates sometimes rename the address field. This bounded fallback
        // only considers editable nodes near the top of the accessibility tree.
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            val value = node.text?.toString().orEmpty()
            if (node.isEditable && depth <= 8 && looksLikeUrl(value)) return value
            if (depth < 8) for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it to depth + 1) }
            }
        }
        return null
    }

    private fun looksLikeUrl(value: String): Boolean = value.contains('.') && !value.contains(' ')

    private companion object {
        val browserUrlIds = mapOf(
            "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
            "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
            "org.mozilla.firefox" to listOf(
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox:id/url_bar_title",
            ),
            "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
        )
    }
}
