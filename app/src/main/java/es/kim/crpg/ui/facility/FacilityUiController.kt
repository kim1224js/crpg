package es.kim.crpg.ui.facility

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.androidgamesdk.GameActivity
import es.kim.crpg.ui.common.GameUiTheme
import es.kim.crpg.ui.common.antiqueButton
import es.kim.crpg.ui.common.antiquePanel
import es.kim.crpg.ui.common.dp
import es.kim.crpg.ui.common.gameScrollView
import es.kim.crpg.ui.common.matchParentParams

class FacilityUiController(
    private val activity: GameActivity,
    private val villageOverlay: FrameLayout
) {
    data class Screen(val overlay: FrameLayout, val content: LinearLayout)

    fun playEntrance(
        assetPath: String,
        originX: Float,
        originY: Float,
        initialScaleX: Float = .30f,
        initialScaleY: Float = .40f,
        onOpened: () -> Unit
    ) {
        val zoomImage = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(activity.assets.open(assetPath).use(BitmapFactory::decodeStream))
            pivotX = 0f
            pivotY = 0f
            scaleX = initialScaleX
            scaleY = initialScaleY
            x = villageOverlay.width * originX
            y = villageOverlay.height * originY
            alpha = .35f
        }
        villageOverlay.addView(zoomImage, activity.matchParentParams())
        zoomImage.animate().x(0f).y(0f).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(650L)
            .withEndAction {
                villageOverlay.removeView(zoomImage)
                onOpened()
            }.start()
    }

    fun createScreen(
        title: String,
        backgroundAsset: String,
        gold: Int,
        scrollContent: Boolean,
        onClose: (FrameLayout) -> Unit
    ): Screen {
        val overlay = FrameLayout(activity)
        overlay.addView(ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(activity.assets.open(backgroundAsset).use(BitmapFactory::decodeStream))
        }, activity.matchParentParams())
        overlay.addView(View(activity).apply { setBackgroundColor(0xC5000000.toInt()) }, activity.matchParentParams())

        val titleBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(24), 0, activity.dp(18), 0)
            background = activity.antiquePanel(0xE6150E0B.toInt(), GameUiTheme.GOLD_DARK, 0f, 1)
            addView(TextView(activity).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, activity.dp(56), 1f))
            addView(TextView(activity).apply {
                text = "보유 골드  $gold G"
                setTextColor(GameUiTheme.GOLD)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(activity.dp(180), activity.dp(56)))
            addView(activity.antiqueButton("닫기", activity.dp(78), activity.dp(40)).apply {
                setOnClickListener { onClose(overlay) }
            })
        }
        overlay.addView(titleBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(56), Gravity.TOP))

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(28), activity.dp(20), activity.dp(28), activity.dp(24))
            background = activity.antiquePanel(GameUiTheme.LEATHER, GameUiTheme.GOLD_DARK, 12f, 2)
        }
        val contentView: View = if (scrollContent) activity.gameScrollView(content) else content
        overlay.addView(contentView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = activity.dp(72)
            bottomMargin = activity.dp(16)
            leftMargin = activity.dp(24)
            rightMargin = activity.dp(24)
        })
        activity.addContentView(overlay, activity.matchParentParams())
        return Screen(overlay, content)
    }
}
