package es.kim.crpg.ui.village

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import es.kim.crpg.ui.common.dp
import es.kim.crpg.ui.common.matchParentParams
import es.kim.crpg.ui.common.antiquePanel
import es.kim.crpg.ui.event.RedMoonView

class VillageMapOverlay(
    context: Context,
    onGeneralStore: () -> Unit,
    onBlacksmith: () -> Unit,
    onAppraisal: () -> Unit,
    onInnWarehouse: () -> Unit,
    onManor: () -> Unit,
    onDungeonEntrance: () -> Unit,
    onTravelingMerchant: () -> Unit
) : FrameLayout(context) {
    val travelingMerchant = FrameLayout(context)
    private val redMoon = RedMoonView(context).apply { visibility = View.GONE; contentDescription = "붉은 달" }
    private val redMoonNotice = TextView(context).apply {
        text = "붉은 달이 떴습니다."
        setTextColor(0xFFFFD7D7.toInt()); textSize = 17f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setShadowLayer(context.dp(5).toFloat(), 0f, context.dp(2).toFloat(), 0xFF650000.toInt())
        visibility = View.GONE
    }
    private val territoryTitle = TextView(context).apply {
        setTextColor(0xFFFFE1A6.toInt())
        textSize = 17f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        gravity = Gravity.CENTER
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        setPadding(context.dp(10), 0, context.dp(10), 0)
        setShadowLayer(context.dp(3).toFloat(), 0f, context.dp(2).toFloat(), Color.BLACK)
        background = context.antiquePanel(0xD919120E.toInt(), 0xFF8E6A31.toInt(), 9f, 1)
        visibility = View.GONE
        isClickable = false
        isFocusable = false
    }
    private val facilityHotspots = listOf(
        hotspot("일반상점", "일반 상점", onGeneralStore),
        hotspot("대장간", "대장간", onBlacksmith),
        hotspot("감정소", "감정소", onAppraisal),
        hotspot("여관 · 창고", "여관과 창고", onInnWarehouse),
        hotspot("저택", "저택", onManor),
        hotspot("지하 입구", "지하 입구", onDungeonEntrance)
    )

    init {
        addView(redMoon)
        addView(redMoonNotice)
        facilityHotspots.forEach(::addView)
        travelingMerchant.apply {
            visibility = View.GONE
            contentDescription = "떠돌이 뽑기상자 상인"
            isClickable = true
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(context.assets.open("ui/village/merchant/traveling_gacha_merchant.png").use(BitmapFactory::decodeStream))
            }, context.matchParentParams())
            addView(TextView(context).apply {
                text = "떠돌이 상인"
                setTextColor(0xFFFFE586.toInt())
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setShadowLayer(context.dp(3).toFloat(), 0f, context.dp(2).toFloat(), Color.BLACK)
            }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(30), Gravity.TOP))
            setOnClickListener { onTravelingMerchant() }
        }
        addView(travelingMerchant)
        addView(territoryTitle)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        facilityHotspots[0].place(width, height, 0.68f, 0.52f, 0.32f, 0.48f)
        facilityHotspots[1].place(width, height, 0f, 0f, 0.32f, 0.48f)
        facilityHotspots[1].setPadding(0, (height * 0.10f).toInt(), 0, 0)
        facilityHotspots[2].place(width, height, 0.68f, 0f, 0.32f, 0.48f)
        facilityHotspots[3].place(width, height, 0f, 0.52f, 0.32f, 0.48f)
        facilityHotspots[4].place(width, height, 0.51f, 0.08f, 0.15f, 0.22f)
        facilityHotspots[5].place(width, height, 0.35f, 0f, 0.16f, 0.22f)
        travelingMerchant.place(width, height, 0.47f, 0.474f, 0.06f, 0.153f)
        redMoon.place(width, height, 0.76f, 0.015f, 0.18f, 0.22f)
        redMoonNotice.place(width, height, 0.34f, 0.015f, 0.38f, 0.08f)
        territoryTitle.place(width, height, 0.018f, 0.018f, 0.30f, 0.075f)
    }

    fun setTerritoryOwner(nickname: String, generation: Int) {
        territoryTitle.text = "[$nickname] ${generation.coerceAtLeast(1)}세의 영지"
        territoryTitle.visibility = View.VISIBLE
    }

    fun setRedMoonActive(active: Boolean) {
        redMoon.visibility = if (active) View.VISIBLE else View.GONE
        redMoonNotice.visibility = if (active) View.VISIBLE else View.GONE
    }

    fun setFacilityInteractionEnabled(enabled: Boolean) {
        facilityHotspots.forEach { it.isEnabled = enabled }
        travelingMerchant.isEnabled = enabled && travelingMerchant.visibility == View.VISIBLE
    }

    private fun hotspot(label: String, description: String, action: () -> Unit) = TextView(context).apply {
        text = label
        contentDescription = description
        setTextColor(Color.WHITE)
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        setPadding(0, context.dp(5), 0, 0)
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { action() }
    }

    private fun View.place(parentWidth: Int, parentHeight: Int, x: Float, y: Float, width: Float, height: Float) {
        layoutParams = LayoutParams((parentWidth * width).toInt(), (parentHeight * height).toInt()).apply {
            leftMargin = (parentWidth * x).toInt()
            topMargin = (parentHeight * y).toInt()
        }
    }
}
