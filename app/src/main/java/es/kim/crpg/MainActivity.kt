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
import android.widget.Toast
import com.google.androidgamesdk.GameActivity
import es.kim.crpg.data.GameDatabase
import es.kim.crpg.data.LoginProfileEntity
import es.kim.crpg.data.OwnedItemEntity
import es.kim.crpg.data.InventoryRepository
import es.kim.crpg.game.ItemCatalog
import es.kim.crpg.ui.EquipmentOptionDialog
import es.kim.crpg.ui.dungeon.DungeonDemoView
import java.util.concurrent.Executors
import kotlin.math.min

class MainActivity : GameActivity() {
    private lateinit var loginOverlay: FrameLayout
    private lateinit var nameInput: EditText
    private lateinit var loginButton: Button
    private lateinit var autoLoginCheckBox: CheckBox
    private lateinit var villageInteractionOverlay: FrameLayout
    private lateinit var generalStoreHotspot: View
    private lateinit var blacksmithHotspot: View
    private lateinit var appraisalHotspot: View
    private lateinit var innWarehouseHotspot: View
    private lateinit var manorDungeonHotspot: View
    private var shopOverlay: FrameLayout? = null
    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private val gameDatabase by lazy { GameDatabase.getInstance(applicationContext) }
    private val inventoryRepository by lazy { InventoryRepository(gameDatabase) }
    private var ownedItems: List<OwnedItemEntity> = emptyList()
    private var currentPlayerId = 1L
    private var playerGold = 10
    private var hasEnteredVillage = false
    private var dungeonEquippedWeaponCode: String? = null

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
            val savedGold = gameDatabase.loginProfileDao().getById(1L)?.gold ?: 10
            val profile = LoginProfileEntity(
                playerName = playerName,
                autoLogin = autoLogin,
                lastLoginAt = System.currentTimeMillis(),
                gold = savedGold
            )
            gameDatabase.loginProfileDao().save(profile)
            currentPlayerId = profile.id
            playerGold = profile.gold

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
            currentPlayerId = profile.id
            playerGold = profile.gold
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
        blacksmithHotspot = View(this).apply {
            contentDescription = "대장간"
            setOnClickListener { openBlacksmith() }
        }
        appraisalHotspot = View(this).apply {
            contentDescription = "감정소"
            setOnClickListener {
                openFacility("ui/village/building_appraisal_house.png", 0.68f, 0f) { showAppraisalOffice() }
            }
        }
        innWarehouseHotspot = View(this).apply {
            contentDescription = "여관과 창고"
            setOnClickListener {
                openFacility("ui/village/building_inn_warehouse.png", 0f, 0.52f) { showInnWarehouse() }
            }
        }
        manorDungeonHotspot = View(this).apply {
            contentDescription = "저택과 지하 입구"
            setOnClickListener {
                openFacility("ui/village/building_manor_dungeon.png", 0.35f, 0f) { showManorDungeonEntrance() }
            }
        }
        villageInteractionOverlay.addView(generalStoreHotspot)
        villageInteractionOverlay.addView(blacksmithHotspot)
        villageInteractionOverlay.addView(appraisalHotspot)
        villageInteractionOverlay.addView(innWarehouseHotspot)
        villageInteractionOverlay.addView(manorDungeonHotspot)
        villageInteractionOverlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val width = villageInteractionOverlay.width
            val height = villageInteractionOverlay.height
            generalStoreHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.32f).toInt(),
                (height * 0.48f).toInt()
            ).apply {
                leftMargin = (width * 0.68f).toInt()
                topMargin = (height * 0.52f).toInt()
            }
            blacksmithHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.32f).toInt(),
                (height * 0.48f).toInt()
            ).apply {
                leftMargin = 0
                topMargin = 0
            }
            appraisalHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.32f).toInt(), (height * 0.48f).toInt()
            ).apply { leftMargin = (width * 0.68f).toInt(); topMargin = 0 }
            innWarehouseHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.32f).toInt(), (height * 0.48f).toInt()
            ).apply { leftMargin = 0; topMargin = (height * 0.52f).toInt() }
            manorDungeonHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.30f).toInt(), (height * 0.34f).toInt()
            ).apply { leftMargin = (width * 0.35f).toInt(); topMargin = 0 }
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
        openStore("ui/village/building_general_store.png", 0.68f, 0.52f, false)
    }

    private fun openBlacksmith() {
        openStore("ui/village/building_blacksmith.png", 0f, 0f, true)
    }

    private fun openStore(assetPath: String, originX: Float, originY: Float, isBlacksmith: Boolean) {
        if (shopOverlay != null) return
        setVillageHotspotsEnabled(false)

        val zoomImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(
                assets.open(assetPath).use(BitmapFactory::decodeStream)
            )
            pivotX = 0f
            pivotY = 0f
            scaleX = 0.28f
            scaleY = 0.48f
            x = villageInteractionOverlay.width * originX
            y = villageInteractionOverlay.height * originY
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
                showShopInterface(isBlacksmith)
            }
            .start()
    }

    private fun openFacility(assetPath: String, originX: Float, originY: Float, onOpened: () -> Unit) {
        if (shopOverlay != null) return
        setVillageHotspotsEnabled(false)
        val zoomImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(assets.open(assetPath).use(BitmapFactory::decodeStream))
            pivotX = 0f
            pivotY = 0f
            scaleX = 0.30f
            scaleY = 0.40f
            x = villageInteractionOverlay.width * originX
            y = villageInteractionOverlay.height * originY
            alpha = 0.35f
        }
        villageInteractionOverlay.addView(zoomImage, matchParentParams())
        zoomImage.animate().x(0f).y(0f).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(650L)
            .withEndAction {
                villageInteractionOverlay.removeView(zoomImage)
                onOpened()
            }.start()
    }

    private fun showShopInterface(isBlacksmith: Boolean = false) {
        val overlay = FrameLayout(this)
        shopOverlay = overlay

        val background = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(
                assets.open(
                    if (isBlacksmith) "ui/village/building_blacksmith.png"
                    else "ui/village/building_general_store.png"
                ).use(BitmapFactory::decodeStream)
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
            text = if (isBlacksmith) "대장간" else "일반 상점"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        titleBar.addView(TextView(this).apply {
            text = "보유 골드  $playerGold G"
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
        val leftScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(leftColumn, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        content.addView(leftScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.12f).apply {
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
        rightColumn.addView(sectionTitle(if (isBlacksmith) "구매 가능 장비" else "구매 가능 아이템"))

        val storeList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollView = ScrollView(this).apply { addView(storeList) }
        rightColumn.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        if (isBlacksmith) {
            ItemCatalog.blacksmith.forEach { item ->
                storeList.addView(storeItemRow(item.code, item.name, item.price, item.unitsPerPurchase, item.assetPath, item.detail))
            }
        } else {
            ItemCatalog.generalStore.forEach { item ->
                storeList.addView(storeItemRow(item.code, item.name, item.price, item.unitsPerPurchase, item.assetPath, item.detail))
            }
        }

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
                if (ItemCatalog.isConsumable(item.itemCode)) {
                    slot.addView(
                        quantityBadge(item.quantity.toString()),
                        itemQuantityLayoutParams()
                    )
                }
                ItemCatalog.equipmentOption(item.itemCode)?.let { option ->
                    slot.isClickable = true
                    slot.isFocusable = true
                    slot.contentDescription = "${item.displayName} 옵션 보기"
                    slot.setOnClickListener { EquipmentOptionDialog(this).show(item, option) }
                }
            }
    }

    private fun assetPathFor(itemCode: String): String = ItemCatalog.assetPath(itemCode)

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

    private fun storeItemRow(
        itemCode: String,
        name: String,
        unitPrice: Int,
        unitsPerPurchase: Int,
        assetPath: String,
        detail: String? = null
    ): View {
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
            if (ItemCatalog.isConsumable(itemCode)) {
                addView(imageQuantity, itemQuantityLayoutParams())
            }
        }
        row.addView(itemPreview, LinearLayout.LayoutParams(dp(64), dp(64)))

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
            if (detail != null) addView(TextView(this@MainActivity).apply {
                text = detail
                setTextColor(0xFFD8C7A3.toInt())
                textSize = 12f
                maxLines = 1
            })
            addView(TextView(this@MainActivity).apply {
                text = "$unitPrice G"
                setTextColor(COLOR_GOLD)
                textSize = 16f
            })
        }
        row.addView(info, LinearLayout.LayoutParams(0, dp(64), 1f))

        val quantityText = TextView(this).apply {
            text = quantity.toString()
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 5f, 1)
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(antiqueButton("−", dp(28), dp(32)).apply {
            setOnClickListener {
                if (quantity > 1) quantity--
                quantityText.text = quantity.toString()
                imageQuantity.text = quantity.toString()
            }
        })
        controls.addView(quantityText, LinearLayout.LayoutParams(dp(30), dp(32)).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        })
        controls.addView(antiqueButton("+", dp(28), dp(32)).apply {
            setOnClickListener {
                if (quantity < 99) quantity++
                quantityText.text = quantity.toString()
                imageQuantity.text = quantity.toString()
            }
        })
        row.addView(controls)
        row.addView(antiqueButton("구매", dp(60), dp(36)).apply {
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                isEnabled = false
                purchaseItem(itemCode, name.removeSuffix(" 5개"), unitPrice, unitsPerPurchase, quantity)
            }
        }, LinearLayout.LayoutParams(dp(60), dp(36)).apply { marginStart = dp(5) })

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(80)
            ).apply { bottomMargin = dp(6) })
        }
    }

    private fun purchaseItem(
        itemCode: String,
        displayName: String,
        unitPrice: Int,
        unitsPerPurchase: Int,
        purchaseQuantity: Int
    ) {
        databaseExecutor.execute {
            val result = inventoryRepository.purchase(
                currentPlayerId, itemCode, displayName, unitPrice, unitsPerPurchase, purchaseQuantity
            )
            ownedItems = result.items
            result.gold?.let { playerGold = it }
            runOnUiThread {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                refreshShopInterface(itemCode.startsWith("crude_"))
            }
        }
    }

    private fun refreshShopInterface(isBlacksmith: Boolean) {
        val overlay = shopOverlay ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        shopOverlay = null
        showShopInterface(isBlacksmith)
    }

    private fun createFacilityContent(title: String, backgroundAsset: String): LinearLayout {
        val overlay = FrameLayout(this)
        shopOverlay = overlay
        overlay.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(assets.open(backgroundAsset).use(BitmapFactory::decodeStream))
        }, matchParentParams())
        overlay.addView(View(this).apply { setBackgroundColor(0xC5000000.toInt()) }, matchParentParams())

        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(18), 0)
            background = antiquePanel(0xE6150E0B.toInt(), COLOR_GOLD_DARK, 0f, 1)
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
            addView(TextView(this@MainActivity).apply {
                text = "보유 골드  $playerGold G"
                setTextColor(COLOR_GOLD)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(180), dp(56)))
            addView(antiqueButton("닫기", dp(78), dp(40)).apply {
                setOnClickListener { closeShop() }
            })
        }
        overlay.addView(titleBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(20), dp(28), dp(24))
            background = antiquePanel(COLOR_LEATHER, COLOR_GOLD_DARK, 12f, 2)
        }
        val contentScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        overlay.addView(contentScroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(72); bottomMargin = dp(16); leftMargin = dp(24); rightMargin = dp(24) })
        addContentView(overlay, matchParentParams())
        return content
    }

    private fun showAppraisalOffice() {
        val content = createFacilityContent("감정소", "ui/village/building_appraisal_house.png")
        content.addView(TextView(this).apply {
            text = "미확인 아이템을 감정해 등급과 옵션을 확인합니다. 실패하면 아이템은 유지되며 감정 비용만 소모됩니다."
            setTextColor(0xFFD8C7A3.toInt())
            textSize = 15f
            setPadding(dp(8), 0, dp(8), dp(12))
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val rates = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sectionTitle("등급별 감정 비용과 성공 확률"))
        }
        listOf(
            Triple("레어", "10 G", "80%"), Triple("에픽", "20 G", "70%"),
            Triple("유니크", "50 G", "60%"), Triple("전설", "100 G", "50%"),
            Triple("신화", "200 G", "40%")
        ).forEach { (grade, price, chance) -> rates.addView(appraisalRateRow(grade, price, chance)) }
        body.addView(rates, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.46f).apply { marginEnd = dp(14) })
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(TextView(this@MainActivity).apply {
                text = "미확인 아이템 없음"
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "던전에서 미확인 아이템을 획득하면\n이곳에서 선택하여 감정할 수 있습니다."
                setTextColor(0xFFBFAF95.toInt())
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.54f))
    }

    private fun appraisalRateRow(grade: String, price: String, chance: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        addView(TextView(this@MainActivity).apply {
            text = grade; setTextColor(Color.WHITE); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(TextView(this@MainActivity).apply {
            text = price; setTextColor(COLOR_GOLD); textSize = 15f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(80), dp(48)))
        addView(TextView(this@MainActivity).apply {
            text = chance; setTextColor(0xFF9DD6A5.toInt()); textSize = 15f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(64), dp(48)))
    }

    private fun showInnWarehouse() {
        val content = createFacilityContent("여관 · 창고", "ui/village/building_inn_warehouse.png")
        content.addView(TextView(this).apply {
            text = "아이템을 눌러 인벤토리와 창고 사이로 이동하세요. 창고의 아이템과 골드는 사망해도 유지됩니다."
            setTextColor(0xFFD8C7A3.toInt()); textSize = 15f; setPadding(dp(8), 0, dp(8), dp(8))
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val inventoryItems = ownedItems.count { it.container == "INVENTORY" }
        val storageItems = ownedItems.count { it.container == "STORAGE" }
        val inventoryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sectionTitle("인벤토리  $inventoryItems / 10"))
            addView(createTransferGrid("INVENTORY", 2, 5, 10), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val storagePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sectionTitle("보호 창고  $storageItems / 20"))
            addView(createTransferGrid("STORAGE", 4, 5, 20), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        body.addView(inventoryPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(12) })
        body.addView(storagePanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun createTransferGrid(container: String, rows: Int, columns: Int, capacity: Int): GridLayout =
        createSlotGrid(rows, columns).also { grid ->
            ownedItems.filter { it.container == container && it.slotIndex in 0 until capacity }.forEach { item ->
                val slot = grid.getChildAt(item.slotIndex) as FrameLayout
                slot.addView(itemImage(assetPathFor(item.itemCode)), matchParentParams(dp(6)))
                if (ItemCatalog.isConsumable(item.itemCode)) {
                    slot.addView(quantityBadge(item.quantity.toString()), itemQuantityLayoutParams())
                }
                slot.isClickable = true
                slot.contentDescription = "${item.displayName} 이동"
                slot.setOnClickListener { transferStoredItem(item) }
            }
        }

    private fun transferStoredItem(item: OwnedItemEntity) {
        databaseExecutor.execute {
            val result = inventoryRepository.transfer(currentPlayerId, item)
            ownedItems = result.items
            runOnUiThread {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                refreshInnWarehouse()
            }
        }
    }

    private fun refreshInnWarehouse() {
        val overlay = shopOverlay ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        shopOverlay = null
        showInnWarehouse()
    }

    private fun showManorDungeonEntrance() {
        val content = createFacilityContent("저택 · 지하 입구", "ui/village/building_manor_dungeon.png")
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(facilityChoicePanel(
            "저택", "가문의 재산과 세대 계승을 관리하는 장소입니다.\n\n사망하면 다음 캐릭터가 창고의 장비와 보유 골드를 이어받습니다.",
            "가문 정보", "저택의 성장 콘텐츠는 아직 설계 전입니다."
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(14) })
        body.addView(facilityChoicePanel(
            "지하 입구", "던전에 가져갈 장비와 소지품을 마지막으로 확인합니다.\n\n모든 탐험은 지하 1층에서 시작합니다.",
            "출발 준비", null
        ) { showDungeonLoadout() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun facilityChoicePanel(
        title: String, description: String, buttonText: String, message: String?, action: (() -> Unit)? = null
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(24), dp(28), dp(24))
        background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 12f, 1)
        addView(TextView(this@MainActivity).apply {
            text = title; setTextColor(COLOR_GOLD); textSize = 25f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        addView(TextView(this@MainActivity).apply {
            text = description; setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(20) })
        addView(antiqueButton(buttonText, dp(150), dp(46)).apply {
            setOnClickListener {
                if (action != null) action() else Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDungeonLoadout() {
        val old = shopOverlay
        (old?.parent as? ViewGroup)?.removeView(old)
        shopOverlay = null
        val content = createFacilityContent("지하 출발 준비", "ui/village/building_manor_dungeon.png")
        content.addView(TextView(this).apply {
            text = "지하 1층 출발 · 사망 시 아래 인벤토리의 장비와 소지품을 모두 잃습니다."
            setTextColor(0xFFFFC6A3.toInt()); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        val carriedWeapons = ownedItems.filter {
            it.container == "INVENTORY" && it.itemCode in setOf("crude_sword", "crude_spear", "crude_bow", "crude_gun")
        }
        if (dungeonEquippedWeaponCode !in carriedWeapons.map { it.itemCode }) {
            dungeonEquippedWeaponCode = carriedWeapons.firstOrNull()?.itemCode
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(TextView(this@MainActivity).apply {
                text = "착용 무기  "; setTextColor(COLOR_GOLD); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            })
            carriedWeapons.forEach { owned ->
                val selected = owned.itemCode == dungeonEquippedWeaponCode
                addView(antiqueButton(if (selected) "✓ ${owned.displayName}" else owned.displayName, dp(130), dp(38)).apply {
                    setOnClickListener { dungeonEquippedWeaponCode = owned.itemCode; showDungeonLoadout() }
                }, LinearLayout.LayoutParams(dp(130), dp(38)).apply { marginEnd = dp(8) })
            }
            if (carriedWeapons.isEmpty()) addView(TextView(this@MainActivity).apply { text = "착용 가능한 무기 없음"; setTextColor(Color.LTGRAY) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        content.addView(createInventoryGrid(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(antiqueButton("돌아가기", dp(130), dp(46)).apply { setOnClickListener { replaceWithManorEntrance() } })
        controls.addView(antiqueButton("지하 1층 입장", dp(170), dp(46)).apply {
            setOnClickListener { showDungeonDemo() }
        }, LinearLayout.LayoutParams(dp(170), dp(46)).apply { marginStart = dp(14) })
        content.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
    }

    private fun showDungeonDemo() {
        val old = shopOverlay
        (old?.parent as? ViewGroup)?.removeView(old)

        val overlay = FrameLayout(this)
        shopOverlay = overlay
        overlay.addView(
            DungeonDemoView(
                this,
                ownedItems.filter { it.container == "INVENTORY" }
                    .groupBy { it.itemCode }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } },
                equippedWeaponCode = dungeonEquippedWeaponCode,
                onUseReturnStone = { useReturnStoneFromDungeon() }
            ),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addContentView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun useReturnStoneFromDungeon() {
        databaseExecutor.execute {
            val dao = gameDatabase.ownedItemDao()
            val stone = dao.findItem(currentPlayerId, "INVENTORY", "return_stone")
            if (stone == null || stone.quantity <= 0) return@execute
            if (stone.quantity == 1) dao.deleteById(stone.id) else dao.updateQuantity(stone.id, stone.quantity - 1)
            ownedItems = dao.getForOwner(currentPlayerId)
            runOnUiThread {
                closeShop()
                Toast.makeText(this, "귀환석을 사용해 마을로 돌아왔습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun replaceWithManorEntrance() {
        val old = shopOverlay
        (old?.parent as? ViewGroup)?.removeView(old)
        shopOverlay = null
        showManorDungeonEntrance()
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
        setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
    }

    private fun itemQuantityLayoutParams(): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
        dp(38), dp(24), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    ).apply { bottomMargin = dp(3) }

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
        setVillageHotspotsEnabled(true)
    }

    private fun setVillageHotspotsEnabled(enabled: Boolean) {
        generalStoreHotspot.isEnabled = enabled
        blacksmithHotspot.isEnabled = enabled
        appraisalHotspot.isEnabled = enabled
        innWarehouseHotspot.isEnabled = enabled
        manorDungeonHotspot.isEnabled = enabled
    }
}
