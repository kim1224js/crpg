package es.kim.crpg

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.androidgamesdk.GameActivity
import es.kim.crpg.data.GameDatabase
import es.kim.crpg.data.LoginProfileEntity
import es.kim.crpg.data.OwnedItemEntity
import java.util.concurrent.Executors
import kotlin.math.min

class MainActivity : GameActivity() {
    private lateinit var loginOverlay: FrameLayout
    private lateinit var nameInput: EditText
    private lateinit var loginButton: Button
    private lateinit var autoLoginCheckBox: CheckBox
    private lateinit var villageInteractionOverlay: FrameLayout
    private lateinit var generalStoreHotspot: View
    private var shopOverlay: FrameLayout? = null
    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private val gameDatabase by lazy { GameDatabase.getInstance(applicationContext) }
    private var ownedItems: List<OwnedItemEntity> = emptyList()
    private var hasEnteredVillage = false

    companion object {
        private const val DESIGN_WIDTH = 1280f
        private const val DESIGN_HEIGHT = 720f
        private const val COLOR_LEATHER = 0xE619120F.toInt()
        private const val COLOR_LEATHER_DARK = 0xF20D0907.toInt()
        private const val COLOR_GOLD = 0xFFD0A653.toInt()
        private const val COLOR_GOLD_DARK = 0xFF725322.toInt()

        init {
            System.loadLibrary("crpg")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        createVillageInteractionOverlay()
        createLoginOverlay()
        restoreAutoLogin()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
        }
    }

    private fun hideSystemUi() {
        val decorView = window.decorView
        decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun createLoginOverlay() {
        loginOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val background = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            val bitmap = assets.open("ui/login/login_screen.png").use(BitmapFactory::decodeStream)
            setImageBitmap(bitmap)
        }
        loginOverlay.addView(
            background,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        nameInput = EditText(this).apply {
            hint = "이름"
            setTextColor(Color.WHITE)
            setHintTextColor(0xCCFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setSingleLine(true)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
        }
        loginOverlay.addView(nameInput)

        loginButton = Button(this).apply {
            text = "로그인"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            stateListAnimator = null
            setPadding(0, 0, 0, 0)
            setOnClickListener { saveLoginAndEnterVillage() }
        }
        loginOverlay.addView(loginButton)

        autoLoginCheckBox = CheckBox(this).apply {
            text = "자동 로그인"
            setTextColor(Color.WHITE)
            textSize = 15f
            buttonTintList = ColorStateList.valueOf(COLOR_GOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        loginOverlay.addView(autoLoginCheckBox)

        loginOverlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionLoginControls()
        }

        addContentView(
            loginOverlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun positionLoginControls() {
        val scale = min(loginOverlay.width / DESIGN_WIDTH, loginOverlay.height / DESIGN_HEIGHT)
        val imageWidth = DESIGN_WIDTH * scale
        val imageHeight = DESIGN_HEIGHT * scale
        val offsetX = (loginOverlay.width - imageWidth) / 2f
        val offsetY = (loginOverlay.height - imageHeight) / 2f

        placeView(autoLoginCheckBox, offsetX, offsetY, scale, 505f, 474f, 270f, 36f)
        placeView(nameInput, offsetX, offsetY, scale, 400f, 515f, 480f, 75f)
        placeView(loginButton, offsetX, offsetY, scale, 510f, 625f, 260f, 60f)
    }

    private fun placeView(
        view: View,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        view.layoutParams = FrameLayout.LayoutParams(
            (width * scale).toInt(),
            (height * scale).toInt()
        ).apply {
            leftMargin = (offsetX + x * scale).toInt()
            topMargin = (offsetY + y * scale).toInt()
        }
    }

    private fun saveLoginAndEnterVillage() {
        val playerName = nameInput.text.toString().trim()
        if (playerName.isEmpty()) {
            nameInput.requestFocus()
            return
        }

        val autoLogin = autoLoginCheckBox.isChecked
        databaseExecutor.execute {
            val profile = LoginProfileEntity(
                playerName = playerName,
                autoLogin = autoLogin,
                lastLoginAt = System.currentTimeMillis()
            )
            gameDatabase.loginProfileDao().save(profile)

            if (gameDatabase.ownedItemDao().countForOwner(profile.id) == 0) {
                gameDatabase.ownedItemDao().insert(
                    OwnedItemEntity(
                        ownerId = profile.id,
                        itemCode = "return_stone",
                        displayName = "귀환석",
                        quantity = 5,
                        container = "INVENTORY",
                        slotIndex = 0
                    )
                )
            }
            ownedItems = gameDatabase.ownedItemDao().getForOwner(profile.id)
            runOnUiThread { enterVillage() }
        }
    }

    private fun restoreAutoLogin() {
        databaseExecutor.execute {
            val profile = gameDatabase.loginProfileDao().getAutoLoginProfile() ?: return@execute
            ownedItems = gameDatabase.ownedItemDao().getForOwner(profile.id)
            runOnUiThread {
                nameInput.setText(profile.playerName)
                autoLoginCheckBox.isChecked = true
                enterVillage()
            }
        }
    }

    private fun enterVillage() {
        if (hasEnteredVillage) return
        hasEnteredVillage = true
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(nameInput.windowToken, 0)
        nameInput.clearFocus()
        loginOverlay.visibility = View.GONE
        hideSystemUi()
    }

    override fun onDestroy() {
        databaseExecutor.shutdown()
        super.onDestroy()
    }

    private fun createVillageInteractionOverlay() {
        villageInteractionOverlay = FrameLayout(this)
        generalStoreHotspot = View(this).apply {
            contentDescription = "일반 상점"
            setOnClickListener { openGeneralStore() }
        }
        villageInteractionOverlay.addView(generalStoreHotspot)
        villageInteractionOverlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val width = villageInteractionOverlay.width
            val height = villageInteractionOverlay.height
            generalStoreHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.32f).toInt(),
                (height * 0.48f).toInt()
            ).apply {
                leftMargin = 0
                topMargin = (height * 0.52f).toInt()
            }
        }
        addContentView(
            villageInteractionOverlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun openGeneralStore() {
        if (shopOverlay != null) return
        generalStoreHotspot.isEnabled = false

        val zoomImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(
                assets.open("ui/village/building_general_store.png").use(BitmapFactory::decodeStream)
            )
            pivotX = 0f
            pivotY = 0f
            scaleX = 0.28f
            scaleY = 0.42f
            x = 0f
            y = villageInteractionOverlay.height * 0.5f
            alpha = 0.35f
        }
        villageInteractionOverlay.addView(
            zoomImage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        zoomImage.animate()
            .x(0f)
            .y(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(650L)
            .withEndAction {
                villageInteractionOverlay.removeView(zoomImage)
                showShopInterface()
            }
            .start()
    }

    private fun showShopInterface() {
        val overlay = FrameLayout(this)
        shopOverlay = overlay

        val background = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(
                assets.open("ui/village/building_general_store.png").use(BitmapFactory::decodeStream)
            )
        }
        overlay.addView(background, matchParentParams())
        overlay.addView(View(this).apply { setBackgroundColor(0xB8000000.toInt()) }, matchParentParams())

        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(18), 0)
            this.background = antiquePanel(0xE6150E0B.toInt(), COLOR_GOLD_DARK, 0f, 1)
        }
        titleBar.addView(TextView(this).apply {
            text = "일반 상점"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        titleBar.addView(TextView(this).apply {
            text = "보유 골드  10 G"
            setTextColor(COLOR_GOLD)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(180), dp(56)))
        titleBar.addView(antiqueButton("닫기", dp(78), dp(40)).apply {
            setOnClickListener { closeShop() }
        })
        overlay.addView(titleBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(56),
            Gravity.TOP
        ))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(14), dp(18), dp(18))
        }
        overlay.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(56) })

        val leftColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            this.background = antiquePanel(COLOR_LEATHER, COLOR_GOLD_DARK, 12f, 2)
        }
        content.addView(leftColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.12f).apply {
            marginEnd = dp(12)
        })

        val inventoryCount = ownedItems.count { it.container == "INVENTORY" }
        val storageCount = ownedItems.count { it.container == "STORAGE" }
        leftColumn.addView(sectionTitle("내 아이템  $inventoryCount / 10"))
        leftColumn.addView(createInventoryGrid(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            0.42f
        ))
        leftColumn.addView(sectionTitle("창고  $storageCount / 20"))
        leftColumn.addView(createWarehouseGrid(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            0.58f
        ))

        val rightColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            this.background = antiquePanel(COLOR_LEATHER, COLOR_GOLD_DARK, 12f, 2)
        }
        content.addView(rightColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.88f))
        rightColumn.addView(sectionTitle("구매 가능 아이템"))

        val storeList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollView = ScrollView(this).apply { addView(storeList) }
        rightColumn.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        storeList.addView(storeItemRow("횃불 5개", "1 G", "ui/items/item_torch.png"))
        storeList.addView(storeItemRow("귀환석", "2 G", "ui/items/item_return_stone.png"))
        storeList.addView(storeItemRow("야영 세트", "3 G", "ui/items/item_camping_kit.png"))
        storeList.addView(storeItemRow("화염병", "2 G", "ui/items/item_fire_bomb.png"))

        addContentView(overlay, matchParentParams())
    }

    private fun createInventoryGrid(): GridLayout {
        return createSlotGrid(2, 5).also { grid ->
            populateStoredItems(grid, "INVENTORY", 10)
        }
    }

    private fun createWarehouseGrid(): GridLayout = createSlotGrid(4, 5).also { grid ->
        populateStoredItems(grid, "STORAGE", 20)
    }

    private fun populateStoredItems(grid: GridLayout, container: String, capacity: Int) {
        ownedItems
            .filter { it.container == container && it.slotIndex in 0 until capacity }
            .forEach { item ->
                val slot = grid.getChildAt(item.slotIndex) as FrameLayout
                slot.addView(itemImage(assetPathFor(item.itemCode)), matchParentParams(dp(6)))
                slot.addView(
                    quantityBadge(item.quantity.toString()),
                    FrameLayout.LayoutParams(dp(38), dp(30), Gravity.CENTER)
                )
            }
    }

    private fun assetPathFor(itemCode: String): String = when (itemCode) {
        "torch" -> "ui/items/item_torch.png"
        "return_stone" -> "ui/items/item_return_stone.png"
        "camping_kit" -> "ui/items/item_camping_kit.png"
        "fire_bomb" -> "ui/items/item_fire_bomb.png"
        else -> "ui/items/item_return_stone.png"
    }

    private fun createSlotGrid(rows: Int, columns: Int): GridLayout {
        return GridLayout(this).apply {
            rowCount = rows
            columnCount = columns
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            repeat(rows * columns) {
                addView(HexagonSlotView(
                    this@MainActivity,
                    COLOR_LEATHER_DARK,
                    COLOR_GOLD_DARK
                ), GridLayout.LayoutParams().apply {
                    width = dp(56)
                    height = (dp(56) * HexagonSlotView.HEX_HEIGHT_RATIO).toInt()
                    rowSpec = GridLayout.spec(it / columns)
                    columnSpec = GridLayout.spec(it % columns)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            addOnLayoutChangeListener { grid, _, _, _, _, _, _, _, _ ->
                val horizontalMargins = dp(8) * columns
                val slotWidth = ((grid.width - horizontalMargins) / columns).coerceAtLeast(dp(36))
                val slotHeight = (slotWidth * HexagonSlotView.HEX_HEIGHT_RATIO).toInt()
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    val params = child.layoutParams as GridLayout.LayoutParams
                    if (params.width != slotWidth || params.height != slotHeight) {
                        params.width = slotWidth
                        params.height = slotHeight
                        child.layoutParams = params
                    }
                }
            }
        }
    }

    private fun storeItemRow(name: String, price: String, assetPath: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = antiquePanel(0xE6120C09.toInt(), COLOR_GOLD_DARK, 9f, 1)
        }
        var quantity = 1
        val imageQuantity = quantityBadge(quantity.toString())
        val itemPreview = FrameLayout(this).apply {
            addView(itemImage(assetPath), matchParentParams())
            addView(imageQuantity, FrameLayout.LayoutParams(dp(38), dp(30), Gravity.CENTER))
        }
        row.addView(itemPreview, LinearLayout.LayoutParams(dp(72), dp(72)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            addView(TextView(this@MainActivity).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@MainActivity).apply {
                text = price
                setTextColor(COLOR_GOLD)
                textSize = 16f
            })
        }
        row.addView(info, LinearLayout.LayoutParams(0, dp(72), 1f))

        val quantityText = TextView(this).apply {
            text = quantity.toString()
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 5f, 1)
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(antiqueButton("−", dp(34), dp(38)).apply {
            setOnClickListener {
                if (quantity > 1) quantity--
                quantityText.text = quantity.toString()
                imageQuantity.text = quantity.toString()
            }
        })
        controls.addView(quantityText, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        controls.addView(antiqueButton("+", dp(34), dp(38)).apply {
            setOnClickListener {
                if (quantity < 99) quantity++
                quantityText.text = quantity.toString()
                imageQuantity.text = quantity.toString()
            }
        })
        row.addView(controls)
        row.addView(antiqueButton("구매", dp(72), dp(42)).apply {
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(dp(72), dp(42)).apply { marginStart = dp(8) })

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(90)
            ).apply { bottomMargin = dp(8) })
        }
    }

    private fun sectionTitle(title: String): TextView = TextView(this).apply {
        text = title
        setTextColor(COLOR_GOLD)
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34))
    }

    private fun itemImage(assetPath: String): ImageView = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setImageBitmap(assets.open(assetPath).use(BitmapFactory::decodeStream))
    }

    private fun quantityBadge(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(Color.WHITE)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = antiquePanel(0xE6000000.toInt(), COLOR_GOLD, 12f, 1)
    }

    private fun antiqueButton(label: String, width: Int, height: Int): Button = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 15f
        gravity = Gravity.CENTER
        minWidth = 0
        minHeight = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        background = antiquePanel(0xFF5B2418.toInt(), COLOR_GOLD, 7f, 2)
        layoutParams = LinearLayout.LayoutParams(width, height)
    }

    private fun antiquePanel(fillColor: Int, strokeColor: Int, radiusDp: Float, strokeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(strokeDp), strokeColor)
        }
    }

    private fun matchParentParams(marginDp: Int = 0): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { setMargins(marginDp, marginDp, marginDp, marginDp) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun closeShop() {
        val overlay = shopOverlay ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        shopOverlay = null
        generalStoreHotspot.isEnabled = true
    }
}
