package es.kim.crpg

import android.content.res.ColorStateList
import android.app.AlertDialog
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
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.androidgamesdk.GameActivity
import es.kim.crpg.data.GameDatabase
import es.kim.crpg.data.GameConfigEntity
import es.kim.crpg.data.LoginProfileEntity
import es.kim.crpg.data.OwnedItemEntity
import es.kim.crpg.data.InventoryRepository
import es.kim.crpg.data.MonsterDefinitionEntity
import es.kim.crpg.data.AppraisalRuleEntity
import es.kim.crpg.game.ItemCatalog
import es.kim.crpg.game.GameAudioSettings
import es.kim.crpg.game.GameMusicPlayer
import es.kim.crpg.game.VillageSoundPlayer
import es.kim.crpg.ui.EquipmentOptionDialog
import es.kim.crpg.ui.ItemGridView
import es.kim.crpg.ui.GameUiTheme
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
    private lateinit var manorHotspot: View
    private lateinit var dungeonEntranceHotspot: View
    private var shopOverlay: FrameLayout? = null
    private lateinit var globalSettingsOverlay: FrameLayout
    private var musicPlayer: GameMusicPlayer? = null
    private var villageSoundPlayer: VillageSoundPlayer? = null
    private var isDungeonActive = false
    private var isActivityResumed = false
    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private val gameDatabase by lazy { GameDatabase.getInstance(applicationContext) }
    private val inventoryRepository by lazy { InventoryRepository(gameDatabase) }
    private var ownedItems: List<OwnedItemEntity> = emptyList()
    private var currentPlayerId = 1L
    private var playerGold = 10
    private var hasEnteredVillage = false
    private var dungeonEquippedWeaponCode: String? = null
    private var monsterDefinitions: List<MonsterDefinitionEntity> = emptyList()
    private var appraisalRules: List<AppraisalRuleEntity> = emptyList()
    private var gameConfigs: Map<String, Int> = emptyMap()

    companion object {
        private const val DESIGN_WIDTH = 1280f
        private const val DESIGN_HEIGHT = 720f
        private const val COLOR_LEATHER = GameUiTheme.LEATHER
        private const val COLOR_LEATHER_DARK = GameUiTheme.LEATHER_DARK
        private const val COLOR_GOLD = GameUiTheme.GOLD
        private const val COLOR_GOLD_DARK = GameUiTheme.GOLD_DARK

        init {
            System.loadLibrary("crpg")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        createVillageInteractionOverlay()
        createLoginOverlay()
        createGlobalSettingsOverlay()
        musicPlayer = GameMusicPlayer(this)
        villageSoundPlayer = VillageSoundPlayer(this)
        restoreAudioSettings()
        restoreAutoLogin()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        updateBackgroundMusic()
    }

    override fun onPause() {
        isActivityResumed = false
        musicPlayer?.pause()
        super.onPause()
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

    private fun createGlobalSettingsOverlay() {
        globalSettingsOverlay = FrameLayout(this).apply {
            isClickable = false
            elevation = dp(100).toFloat()
        }
        val settingsButton = TextView(this).apply {
            text = "⚙"
            contentDescription = "설정 열기"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = antiquePanel(0xE618120F.toInt(), COLOR_GOLD, 24f, 2)
            setOnClickListener { showSettingsPage() }
        }
        globalSettingsOverlay.addView(
            settingsButton,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin = dp(14)
                marginEnd = dp(14)
            }
        )
        addContentView(globalSettingsOverlay, matchParentParams())
    }

    private fun restoreAudioSettings() {
        databaseExecutor.execute {
            val dao = gameDatabase.gameMasterDao()
            val percent = dao.getConfigInt("effects_volume_percent") ?: 90
            GameAudioSettings.setEffectsVolumePercent(percent)
            GameAudioSettings.setMusicEnabled((dao.getConfigInt("music_enabled") ?: 1) == 1)
            GameAudioSettings.setEffectsEnabled((dao.getConfigInt("effects_enabled") ?: 1) == 1)
            runOnUiThread { updateBackgroundMusic() }
        }
    }

    private fun updateBackgroundMusic() {
        if (isActivityResumed && GameAudioSettings.musicEnabled && !isDungeonActive) {
            musicPlayer?.play()
        } else {
            musicPlayer?.pause()
        }
    }

    private fun saveAudioSetting(key: String, value: Int) {
        databaseExecutor.execute {
            gameDatabase.gameMasterDao().saveConfig(GameConfigEntity(key, value, null, null))
        }
    }

    private fun showSettingsPage() {
        if (globalSettingsOverlay.findViewWithTag<View>("settings_page") != null) return
        val blocker = FrameLayout(this).apply {
            tag = "settings_page"
            isClickable = true
            setBackgroundColor(0xB8000000.toInt())
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(22))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD, 12f, 2)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "사운드 설정"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        header.addView(TextView(this).apply {
            text = "×"
            contentDescription = "설정 닫기"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { globalSettingsOverlay.removeView(blocker) }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        panel.addView(header)

        fun audioToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit): Switch =
            Switch(this).apply {
                text = label
                isChecked = checked
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                thumbTintList = ColorStateList.valueOf(COLOR_GOLD)
                trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(0x99725322.toInt(), 0x664D443B)
                )
                setOnCheckedChangeListener { _, enabled -> onChanged(enabled) }
            }

        panel.addView(audioToggle("배경음악", GameAudioSettings.musicEnabled) { enabled ->
            GameAudioSettings.setMusicEnabled(enabled)
            saveAudioSetting("music_enabled", if (enabled) 1 else 0)
            updateBackgroundMusic()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        panel.addView(audioToggle("효과음", GameAudioSettings.effectsEnabled) { enabled ->
            GameAudioSettings.setEffectsEnabled(enabled)
            saveAudioSetting("effects_enabled", if (enabled) 1 else 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        val volumeText = TextView(this).apply {
            setTextColor(COLOR_GOLD)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val initialPercent = (GameAudioSettings.effectsVolume * 100).toInt()
        volumeText.text = "효과음 크기  $initialPercent%"
        panel.addView(volumeText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        val volumeSlider = SeekBar(this).apply {
            max = 100
            progress = initialPercent
            progressTintList = ColorStateList.valueOf(COLOR_GOLD)
            thumbTintList = ColorStateList.valueOf(COLOR_GOLD)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    GameAudioSettings.setEffectsVolumePercent(progress)
                    volumeText.text = "효과음 크기  $progress%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val percent = seekBar?.progress ?: return
                    databaseExecutor.execute {
                        gameDatabase.gameMasterDao().saveConfig(
                            GameConfigEntity("effects_volume_percent", percent, null, null)
                        )
                    }
                }
            })
        }
        panel.addView(volumeSlider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        panel.addView(TextView(this).apply {
            text = "몬스터·무기·피격·사망 효과음에 공통 적용됩니다."
            setTextColor(0xFFCCBFA8.toInt())
            textSize = 14f
        })
        blocker.addView(
            panel,
            FrameLayout.LayoutParams(dp(480), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        globalSettingsOverlay.addView(blocker, 0, matchParentParams())
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
            loadMasterData()
            val savedGold = gameDatabase.loginProfileDao().getById(1L)?.gold ?: gameInt("starting_gold", 10)
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
                        quantity = gameInt("initial_return_stones", 5),
                        container = "INVENTORY",
                        slotIndex = 0
                    )
                )
            }
            ownedItems = gameDatabase.ownedItemDao().getForOwner(profile.id)
            ensureEquippedWeapon()
            runOnUiThread { enterVillage() }
        }
    }

    private fun restoreAutoLogin() {
        databaseExecutor.execute {
            val profile = gameDatabase.loginProfileDao().getAutoLoginProfile() ?: return@execute
            loadMasterData()
            currentPlayerId = profile.id
            playerGold = profile.gold
            ownedItems = gameDatabase.ownedItemDao().getForOwner(profile.id)
            ensureEquippedWeapon()
            runOnUiThread {
                nameInput.setText(profile.playerName)
                autoLoginCheckBox.isChecked = true
                enterVillage()
            }
        }
    }

    private fun loadMasterData() {
        val masterDao = gameDatabase.gameMasterDao()
        ItemCatalog.initialize(masterDao.getItems())
        monsterDefinitions = masterDao.getMonsters()
        appraisalRules = masterDao.getAppraisalRules()
        gameConfigs = masterDao.getGameConfigs().mapNotNull { config -> config.intValue?.let { config.key to it } }.toMap()
    }

    private fun gameInt(key: String, fallback: Int): Int = gameConfigs[key] ?: fallback

    private fun ensureEquippedWeapon() {
        val equipped = ownedItems.firstOrNull { it.isEquipped && ItemCatalog.isWeapon(it.itemCode) }
            ?: ownedItems.firstOrNull { it.container == "INVENTORY" && ItemCatalog.isWeapon(it.itemCode) }
        dungeonEquippedWeaponCode = equipped?.itemCode
        if (equipped != null && !equipped.isEquipped) {
            gameDatabase.runInTransaction {
                gameDatabase.ownedItemDao().clearEquipped(currentPlayerId)
                gameDatabase.ownedItemDao().setEquipped(equipped.id)
            }
            ownedItems = gameDatabase.ownedItemDao().getForOwner(currentPlayerId)
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
        musicPlayer?.release()
        musicPlayer = null
        villageSoundPlayer?.release()
        villageSoundPlayer = null
        databaseExecutor.shutdown()
        super.onDestroy()
    }

    private fun createVillageInteractionOverlay() {
        villageInteractionOverlay = FrameLayout(this)
        generalStoreHotspot = villageHotspotLabel("일반상점").apply {
            contentDescription = "일반 상점"
            setOnClickListener { openGeneralStore() }
        }
        blacksmithHotspot = villageHotspotLabel("대장간").apply {
            contentDescription = "대장간"
            setOnClickListener { openBlacksmith() }
        }
        appraisalHotspot = villageHotspotLabel("감정소").apply {
            contentDescription = "감정소"
            setOnClickListener {
                openFacility("ui/village/building_appraisal_house.png", 0.68f, 0f) { showAppraisalOffice() }
            }
        }
        innWarehouseHotspot = villageHotspotLabel("여관 · 창고").apply {
            contentDescription = "여관과 창고"
            setOnClickListener {
                openFacility("ui/village/building_inn_warehouse.png", 0f, 0.52f) { showInnWarehouse() }
            }
        }
        manorHotspot = villageHotspotLabel("저택").apply {
            contentDescription = "저택"
            setOnClickListener {
                openFacility("ui/village/building_manor_dungeon.png", 0.35f, 0f) { showManorEntrance() }
            }
        }
        dungeonEntranceHotspot = villageHotspotLabel("지하 입구").apply {
            contentDescription = "지하 입구"
            setOnClickListener {
                openFacility("ui/village/building_manor_dungeon.png", 0.51f, 0.08f) { showDungeonLoadout() }
            }
        }
        villageInteractionOverlay.addView(generalStoreHotspot)
        villageInteractionOverlay.addView(blacksmithHotspot)
        villageInteractionOverlay.addView(appraisalHotspot)
        villageInteractionOverlay.addView(innWarehouseHotspot)
        villageInteractionOverlay.addView(manorHotspot)
        villageInteractionOverlay.addView(dungeonEntranceHotspot)
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
            manorHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.15f).toInt(), (height * 0.22f).toInt()
            ).apply { leftMargin = (width * 0.51f).toInt(); topMargin = (height * 0.08f).toInt() }
            dungeonEntranceHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.16f).toInt(), (height * 0.22f).toInt()
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

    private fun villageHotspotLabel(label: String): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(5), 0, 0)
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun openGeneralStore() {
        openStore("ui/village/building_general_store.png", 0.68f, 0.52f, false)
    }

    private fun openBlacksmith() {
        openStore("ui/village/building_blacksmith.png", 0f, 0f, true)
    }

    private fun openStore(assetPath: String, originX: Float, originY: Float, isBlacksmith: Boolean) {
        if (shopOverlay != null) return
        villageSoundPlayer?.playDoorOpen()
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
        villageSoundPlayer?.playDoorOpen()
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
        overlay.contentDescription = if (isBlacksmith) "blacksmith_store" else "general_store"
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
        leftColumn.addView(sectionTitle("내 아이템  $inventoryCount / ${gameInt("inventory_capacity", 16)}"))
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

    private fun createInventoryGrid(): ItemGridView = createCommonItemGrid("INVENTORY", 2, 8)

    private fun createWarehouseGrid(): ItemGridView = createCommonItemGrid("STORAGE", 4, 5)

    private fun createCommonItemGrid(container: String, rows: Int, columns: Int): ItemGridView =
        ItemGridView(
            context = this,
            container = container,
            rows = rows,
            columns = columns,
            items = ownedItems,
            assetPath = ::assetPathFor,
            isConsumable = ItemCatalog::isConsumable,
            onItemClick = { item ->
                ItemCatalog.equipmentOption(item.itemCode)?.let { option -> EquipmentOptionDialog(this).show(item, option) }
            },
            onItemDrop = { payload, targetContainer, targetSlot -> moveStoredItem(payload.id, targetContainer, targetSlot) }
        )

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
                refreshShopInterface(ItemCatalog.definition(itemCode)?.storeType == "BLACKSMITH")
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
        appraisalRules.forEach { rule ->
            rates.addView(appraisalRateRow(gradeDisplayName(rule.grade), "${rule.cost} G", "${(rule.successRate * 100).toInt()}%"))
        }
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
        shopOverlay?.contentDescription = "inn_warehouse"
        content.addView(TextView(this).apply {
            text = "아이템을 길게 눌러 원하는 칸으로 옮기세요. 인벤토리와 창고 사이로도 이동할 수 있습니다."
            setTextColor(0xFFD8C7A3.toInt()); textSize = 15f; setPadding(dp(8), 0, dp(8), dp(8))
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val inventoryItems = ownedItems.count { it.container == "INVENTORY" }
        val storageItems = ownedItems.count { it.container == "STORAGE" }
        val inventoryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sectionTitle("인벤토리  $inventoryItems / ${gameInt("inventory_capacity", 16)}"))
            addView(createTransferGrid("INVENTORY", 2, 8, gameInt("inventory_capacity", 16)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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

    private fun createTransferGrid(container: String, rows: Int, columns: Int, capacity: Int): ItemGridView =
        createCommonItemGrid(container, rows, columns)

    private fun moveStoredItem(itemId: Long, targetContainer: String, targetSlot: Int) {
        databaseExecutor.execute {
            val result = inventoryRepository.moveToSlot(currentPlayerId, itemId, targetContainer, targetSlot)
            ownedItems = result.items
            runOnUiThread {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                refreshCurrentItemScreen()
            }
        }
    }

    private fun refreshCurrentItemScreen() {
        val overlay = shopOverlay ?: return
        val isWarehouseScreen = overlay.contentDescription == "inn_warehouse"
        val isBlacksmithScreen = overlay.contentDescription == "blacksmith_store"
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        shopOverlay = null
        when {
            isWarehouseScreen -> showInnWarehouse()
            isBlacksmithScreen -> showShopInterface(true)
            else -> showShopInterface(false)
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

    private fun showManorEntrance() {
        val content = createFacilityContent("저택", "ui/village/building_manor_dungeon.png")
        content.addView(facilityChoicePanel(
            "저택", "가문의 재산과 세대 계승을 관리하는 장소입니다.\n\n사망하면 다음 캐릭터가 창고의 장비와 보유 골드를 이어받습니다.",
            "가문 정보", "저택의 성장 콘텐츠는 아직 설계 전입니다."
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
        val carriedWeapons = ownedItems.filter { it.container == "INVENTORY" && ItemCatalog.isWeapon(it.itemCode) }
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
                    setOnClickListener { equipDungeonWeapon(owned) }
                }, LinearLayout.LayoutParams(dp(130), dp(38)).apply { marginEnd = dp(8) })
            }
            if (carriedWeapons.isEmpty()) addView(TextView(this@MainActivity).apply { text = "착용 가능한 무기 없음"; setTextColor(Color.LTGRAY) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        content.addView(createInventoryGrid(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(antiqueButton("돌아가기", dp(130), dp(46)).apply { setOnClickListener { closeShop() } })
        controls.addView(antiqueButton("지하 1층 입장", dp(170), dp(46)).apply {
            setOnClickListener { showDungeonEntryConfirmation() }
        }, LinearLayout.LayoutParams(dp(170), dp(46)).apply { marginStart = dp(14) })
        content.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
    }

    private fun equipDungeonWeapon(item: OwnedItemEntity) {
        databaseExecutor.execute {
            gameDatabase.runInTransaction {
                gameDatabase.ownedItemDao().clearEquipped(currentPlayerId)
                gameDatabase.ownedItemDao().setEquipped(item.id)
            }
            ownedItems = gameDatabase.ownedItemDao().getForOwner(currentPlayerId)
            dungeonEquippedWeaponCode = item.itemCode
            runOnUiThread { showDungeonLoadout() }
        }
    }

    private fun showDungeonEntryConfirmation() {
        val carriedItems = ownedItems
            .filter { it.container == "INVENTORY" }
            .sortedBy { it.slotIndex }
        val itemSummary = if (carriedItems.isEmpty()) {
            "소지품 없음"
        } else {
            carriedItems.joinToString("\n") { item ->
                val quantity = if (ItemCatalog.isConsumable(item.itemCode)) " ×${item.quantity}" else ""
                "• ${item.displayName}$quantity"
            }
        }
        val equippedName = carriedItems.firstOrNull { it.itemCode == dungeonEquippedWeaponCode }?.displayName ?: "없음"
        AlertDialog.Builder(this)
            .setTitle("지하 1층으로 출발합니까?")
            .setMessage(
                "착용 무기: $equippedName\n\n가져갈 소지품\n$itemSummary\n\n" +
                    "던전에서 사망하면 위 장비와 소지품을 모두 잃습니다."
            )
            .setNegativeButton("취소", null)
            .setPositiveButton("입장") { _, _ -> showDungeonDemo() }
            .show()
    }

    private fun showDungeonDemo() {
        val old = shopOverlay
        (old?.parent as? ViewGroup)?.removeView(old)

        isDungeonActive = true
        updateBackgroundMusic()
        val overlay = FrameLayout(this)
        shopOverlay = overlay
        overlay.addView(
            DungeonDemoView(
                this,
                ownedItems.filter { it.container == "INVENTORY" }
                    .groupBy { it.itemCode }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } },
                equippedWeaponCode = dungeonEquippedWeaponCode,
                itemDefinitions = ItemCatalog.allDefinitions,
                monsterDefinitions = monsterDefinitions,
                playerBaseHp = gameInt("base_player_hp", 10),
                inventoryCapacity = gameInt("inventory_capacity", 16),
                onUseReturnStone = { acquiredItems, acquiredGold ->
                    useReturnStoneFromDungeon(acquiredItems, acquiredGold)
                },
                onExitDungeon = { acquiredItems, acquiredGold ->
                    exitDungeonSafely(acquiredItems, acquiredGold)
                }
            ),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addContentView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun gradeDisplayName(grade: String): String = when (grade) {
        "NORMAL" -> "노말"; "HIGH" -> "고급"; "RARE" -> "레어"; "EPIC" -> "에픽"
        "UNIQUE" -> "유니크"; "LEGENDARY" -> "전설"; "MYTHIC" -> "신화"; else -> grade
    }

    private fun useReturnStoneFromDungeon(acquiredItems: Map<String, Int>, acquiredGold: Int) {
        settleDungeonRun(acquiredItems, acquiredGold, consumeReturnStone = true)
    }

    private fun exitDungeonSafely(acquiredItems: Map<String, Int>, acquiredGold: Int) {
        settleDungeonRun(acquiredItems, acquiredGold, consumeReturnStone = false)
    }

    private fun settleDungeonRun(acquiredItems: Map<String, Int>, acquiredGold: Int, consumeReturnStone: Boolean) {
        databaseExecutor.execute {
            var settlementSucceeded = !consumeReturnStone
            val itemsToSettle = acquiredItems.toMutableMap()
            gameDatabase.runInTransaction {
                val dao = gameDatabase.ownedItemDao()
                if (consumeReturnStone) {
                    val stone = dao.findItem(currentPlayerId, "INVENTORY", "return_stone")
                    if (stone != null && stone.quantity > 0) {
                        if (stone.quantity == 1) dao.deleteById(stone.id) else dao.updateQuantity(stone.id, stone.quantity - 1)
                    } else {
                        val acquiredStones = itemsToSettle["return_stone"] ?: 0
                        if (acquiredStones <= 0) return@runInTransaction
                        if (acquiredStones == 1) itemsToSettle.remove("return_stone") else itemsToSettle["return_stone"] = acquiredStones - 1
                    }
                    settlementSucceeded = true
                }
                itemsToSettle.forEach { (code, quantity) ->
                    val existing = dao.findItem(currentPlayerId, "INVENTORY", code)
                    if (existing != null) {
                        dao.updateQuantity(existing.id, existing.quantity + quantity)
                    } else {
                        val usedSlots = dao.getForOwner(currentPlayerId).filter { it.container == "INVENTORY" }.map { it.slotIndex }.toSet()
                        val emptySlot = (0 until gameInt("inventory_capacity", 16)).firstOrNull { it !in usedSlots } ?: return@forEach
                        val catalogItem = ItemCatalog.get(code) ?: return@forEach
                        dao.insert(OwnedItemEntity(
                            ownerId = currentPlayerId,
                            itemCode = code,
                            displayName = catalogItem.name.removeSuffix(" 5개"),
                            quantity = quantity,
                            container = "INVENTORY",
                            slotIndex = emptySlot
                        ))
                    }
                }
                val profile = gameDatabase.loginProfileDao().getById(currentPlayerId)
                if (profile != null && acquiredGold > 0) {
                    playerGold = profile.gold + acquiredGold
                    gameDatabase.loginProfileDao().updateGold(currentPlayerId, playerGold)
                }
                ownedItems = dao.getForOwner(currentPlayerId)
            }
            runOnUiThread {
                if (!settlementSucceeded) return@runOnUiThread
                closeShop()
                val lootMessage = if (acquiredGold > 0 || itemsToSettle.isNotEmpty()) " · 전리품 정산 완료" else ""
                val returnMessage = if (consumeReturnStone) "귀환석을 사용해 마을로 돌아왔습니다" else "탐험을 마치고 마을로 돌아왔습니다"
                Toast.makeText(this, "$returnMessage$lootMessage", Toast.LENGTH_SHORT).show()
            }
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
        if (isDungeonActive) {
            isDungeonActive = false
            updateBackgroundMusic()
        }
        setVillageHotspotsEnabled(true)
    }

    private fun setVillageHotspotsEnabled(enabled: Boolean) {
        generalStoreHotspot.isEnabled = enabled
        blacksmithHotspot.isEnabled = enabled
        appraisalHotspot.isEnabled = enabled
        innWarehouseHotspot.isEnabled = enabled
        manorHotspot.isEnabled = enabled
        dungeonEntranceHotspot.isEnabled = enabled
    }
}
