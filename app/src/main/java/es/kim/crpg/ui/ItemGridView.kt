package es.kim.crpg.ui

import android.content.ClipData
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import es.kim.crpg.HexagonSlotView
import es.kim.crpg.data.OwnedItemEntity

class ItemGridView(
    context: Context,
    private val container: String,
    private val rows: Int,
    private val columns: Int,
    private val items: List<OwnedItemEntity>,
    private val assetPath: (String) -> String,
    private val isConsumable: (String) -> Boolean,
    private val onItemClick: (OwnedItemEntity) -> Unit,
    private val onItemDrop: (DraggedItem, String, Int) -> Unit
) : GridLayout(context) {

    data class DraggedItem(val id: Long, val container: String, val slotIndex: Int)

    init {
        rowCount = rows
        columnCount = columns
        alignmentMode = ALIGN_BOUNDS
        useDefaultMargins = false
        val bySlot = items.filter { it.container == container }.associateBy { it.slotIndex }
        repeat(rows * columns) { slotIndex -> addView(createSlot(slotIndex, bySlot[slotIndex]), slotParams(slotIndex)) }
        addOnLayoutChangeListener { grid, _, _, _, _, _, _, _, _ -> resizeSlots(grid.width) }
    }

    private fun createSlot(slotIndex: Int, item: OwnedItemEntity?): HexagonSlotView =
        HexagonSlotView(context, GameUiTheme.LEATHER_DARK, GameUiTheme.GOLD_DARK).apply {
            contentDescription = item?.let { "${it.displayName}, 길게 눌러 이동" } ?: "빈 아이템 칸"
            setOnDragListener { view, event -> handleDrag(view, event, slotIndex) }
            if (item != null) {
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(context.assets.open(assetPath(item.itemCode)).use(BitmapFactory::decodeStream))
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
                if (isConsumable(item.itemCode)) addView(quantity(item.quantity), FrameLayout.LayoutParams(dp(28), dp(24), Gravity.END or Gravity.BOTTOM).apply {
                    marginEnd = dp(7); bottomMargin = dp(2)
                })
                isClickable = true
                isFocusable = true
                setOnClickListener { onItemClick(item) }
                setOnLongClickListener {
                    val payload = DraggedItem(item.id, item.container, item.slotIndex)
                    startDragAndDrop(ClipData.newPlainText("item", item.id.toString()), DragShadowBuilder(this), payload, 0)
                    alpha = .35f
                    true
                }
            }
        }

    private fun handleDrag(view: View, event: DragEvent, targetSlot: Int): Boolean {
        val payload = event.localState as? DraggedItem ?: return false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DRAG_ENTERED -> { view.scaleX = 1.08f; view.scaleY = 1.08f; view.alpha = .78f }
            DragEvent.ACTION_DRAG_EXITED -> { view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f }
            DragEvent.ACTION_DROP -> {
                view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f
                if (payload.container != container || payload.slotIndex != targetSlot) onItemDrop(payload, container, targetSlot)
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f
                findViewById<View>(payload.id.toInt())?.alpha = 1f
            }
        }
        return true
    }

    private fun quantity(value: Int) = TextView(context).apply {
        text = value.toString(); setTextColor(Color.WHITE); textSize = 14f
        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
    }

    private fun slotParams(index: Int) = LayoutParams().apply {
        width = dp(56); height = (dp(56) * HexagonSlotView.HEX_HEIGHT_RATIO).toInt()
        rowSpec = spec(index / columns); columnSpec = spec(index % columns)
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun resizeSlots(gridWidth: Int) {
        if (gridWidth <= 0) return
        val slotWidth = ((gridWidth - dp(8) * columns) / columns).coerceAtLeast(dp(36))
        val slotHeight = (slotWidth * HexagonSlotView.HEX_HEIGHT_RATIO).toInt()
        repeat(childCount) { index ->
            val child = getChildAt(index); val params = child.layoutParams as LayoutParams
            if (params.width != slotWidth || params.height != slotHeight) { params.width = slotWidth; params.height = slotHeight; child.layoutParams = params }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

}
