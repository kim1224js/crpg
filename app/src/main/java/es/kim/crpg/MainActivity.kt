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
import android.widget.TextView
import android.widget.Toast
import com.google.androidgamesdk.GameActivity
import es.kim.crpg.data.GameDatabase
import es.kim.crpg.data.DeceasedCharacterEntity
import es.kim.crpg.data.LoginProfileEntity
import es.kim.crpg.data.OwnedItemEntity
import es.kim.crpg.data.InventoryRepository
import es.kim.crpg.data.MonsterDefinitionEntity
import es.kim.crpg.data.MonsterDropEntity
import es.kim.crpg.data.MonsterFloorSpawnEntity
import es.kim.crpg.data.DungeonInteractableDefinitionEntity
import es.kim.crpg.data.DungeonInteractableSpawnEntity
import es.kim.crpg.data.DungeonRunEntity
import es.kim.crpg.data.AppraisalRuleEntity
import es.kim.crpg.data.NicknameAccountEntity
import es.kim.crpg.core.audio.GameAudioSettings
import es.kim.crpg.core.audio.GameMusicPlayer
import es.kim.crpg.core.audio.VillageSoundPlayer
import es.kim.crpg.game.catalog.ItemCatalog
import es.kim.crpg.game.rules.ItemAppraisalRules
import es.kim.crpg.game.rules.DeathNarratives
import es.kim.crpg.ui.common.AntiqueGameDialog
import es.kim.crpg.ui.common.GameUiTheme
import es.kim.crpg.ui.common.gameScrollView
import es.kim.crpg.ui.dungeon.DungeonDemoView
import es.kim.crpg.ui.inventory.EquipmentOptionDialog
import es.kim.crpg.ui.inventory.HexagonSlotView
import es.kim.crpg.ui.inventory.ItemGridView
import es.kim.crpg.ui.settings.GameSettingsController
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

class MainActivity : GameActivity() {
    private lateinit var loginOverlay: FrameLayout
    private lateinit var nameInput: EditText
    private lateinit var loginButton: Button
    private lateinit var autoLoginCheckBox: CheckBox
    private lateinit var deathNoticeText: TextView
    private lateinit var villageInteractionOverlay: FrameLayout
    private lateinit var generalStoreHotspot: View
    private lateinit var blacksmithHotspot: View
    private lateinit var appraisalHotspot: View
    private lateinit var innWarehouseHotspot: View
    private lateinit var manorHotspot: View
    private lateinit var dungeonEntranceHotspot: View
    private lateinit var travelingMerchantHotspot: FrameLayout
    private var shopOverlay: FrameLayout? = null
    private var travelingMerchantList: LinearLayout? = null
    private lateinit var settingsController: GameSettingsController
    private var musicPlayer: GameMusicPlayer? = null
    private var villageSoundPlayer: VillageSoundPlayer? = null
    private var isDungeonActive = false
    private var currentDungeonFloor = 1
    private var isActivityResumed = false
    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private val gameDatabase by lazy { GameDatabase.getInstance(applicationContext) }
    private val inventoryRepository by lazy { InventoryRepository(gameDatabase) }
    private var ownedItems: List<OwnedItemEntity> = emptyList()
    private var currentPlayerId = 1L
    private var playerGold = 10
    private var survivalDay = 1
    private var highestFloor = 1
    private var introSeen = false
    private var awaitingHeirCreation = false
    private var hasEnteredVillage = false
    private var dungeonEquippedWeaponCode: String? = null
    private var monsterDefinitions: List<MonsterDefinitionEntity> = emptyList()
    private var monsterDrops: List<MonsterDropEntity> = emptyList()
    private var monsterFloorSpawns: List<MonsterFloorSpawnEntity> = emptyList()
    private var dungeonInteractableDefinitions: List<DungeonInteractableDefinitionEntity> = emptyList()
    private var dungeonInteractableSpawns: List<DungeonInteractableSpawnEntity> = emptyList()
    private var dungeonRunPayload: String? = null
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
        settingsController = GameSettingsController(this, gameDatabase, databaseExecutor, ::updateBackgroundMusic)
        settingsController.attach()
        musicPlayer = GameMusicPlayer(this)
        villageSoundPlayer = VillageSoundPlayer(this)
        settingsController.restore()
        startInitialFlow()
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

    private fun updateBackgroundMusic() {
        if (isActivityResumed && GameAudioSettings.musicEnabled) {
            if (isDungeonActive) musicPlayer?.playDungeonFloor(currentDungeonFloor)
            else musicPlayer?.playVillage()
        } else {
            musicPlayer?.pause()
        }
    }

    private fun createLoginOverlay() {
        loginOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
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

        deathNoticeText = TextView(this).apply {
            visibility = View.GONE
            setTextColor(0xFFFFB7A8.toInt())
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        loginOverlay.addView(deathNoticeText)

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
        setVillageHotspotsEnabled(false)
    }

    private fun positionLoginControls() {
        val scale = min(loginOverlay.width / DESIGN_WIDTH, loginOverlay.height / DESIGN_HEIGHT)
        val imageWidth = DESIGN_WIDTH * scale
        val imageHeight = DESIGN_HEIGHT * scale
        val offsetX = (loginOverlay.width - imageWidth) / 2f
        val offsetY = (loginOverlay.height - imageHeight) / 2f

        placeView(autoLoginCheckBox, offsetX, offsetY, scale, 505f, 474f, 270f, 36f)
        placeView(deathNoticeText, offsetX, offsetY, scale, 300f, 392f, 680f, 78f)
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
            val accountDao = gameDatabase.nicknameAccountDao()
            var account = if (awaitingHeirCreation) {
                val duplicate = accountDao.getByNickname(playerName)
                if (duplicate != null && duplicate.id != currentPlayerId) {
                    runOnUiThread { Toast.makeText(this, "이미 사용 중인 이름입니다.", Toast.LENGTH_SHORT).show() }
                    return@execute
                }
                accountDao.rename(currentPlayerId, playerName)
                NicknameAccountEntity(currentPlayerId, playerName, System.currentTimeMillis())
            } else accountDao.getByNickname(playerName)
            if (account == null) {
                val createdAt = System.currentTimeMillis()
                val insertedId = accountDao.insert(
                    NicknameAccountEntity(nickname = playerName, createdAt = createdAt)
                )
                account = if (insertedId > 0L) {
                    NicknameAccountEntity(id = insertedId, nickname = playerName, createdAt = createdAt)
                } else {
                    accountDao.getByNickname(playerName)
                }
            }
            val accountId = account?.id ?: return@execute
            val savedProfile = gameDatabase.loginProfileDao().getById(accountId)
            val isNewProfile = savedProfile == null
            val savedGold = savedProfile?.gold ?: gameInt("starting_gold", 10)
            val profile = LoginProfileEntity(
                id = accountId,
                playerName = playerName,
                autoLogin = autoLogin,
                lastLoginAt = System.currentTimeMillis(),
                gold = savedGold,
                introSeen = savedProfile?.introSeen ?: false,
                survivalDay = savedProfile?.survivalDay ?: 1,
                lastManorSearchDay = savedProfile?.lastManorSearchDay ?: 0,
                highestFloor = savedProfile?.highestFloor ?: 1,
                pendingEstateLossCount = savedProfile?.pendingEstateLossCount ?: 0,
                pendingEstateKeptNames = savedProfile?.pendingEstateKeptNames,
                lastMerchantFreeDay = savedProfile?.lastMerchantFreeDay ?: 0,
                merchantFreeClaimMask = savedProfile?.merchantFreeClaimMask ?: 0
            )
            gameDatabase.loginProfileDao().clearAutoLogin()
            gameDatabase.loginProfileDao().save(profile)
            currentPlayerId = profile.id
            playerGold = profile.gold
            survivalDay = profile.survivalDay
            highestFloor = profile.highestFloor
            introSeen = profile.introSeen

            if (isNewProfile) {
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
            dungeonRunPayload = gameDatabase.dungeonRunDao().get(profile.id)?.payloadJson
            awaitingHeirCreation = false
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
            survivalDay = profile.survivalDay
            highestFloor = profile.highestFloor
            introSeen = profile.introSeen
            ownedItems = gameDatabase.ownedItemDao().getForOwner(profile.id)
            dungeonRunPayload = gameDatabase.dungeonRunDao().get(profile.id)?.payloadJson
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
        monsterDrops = masterDao.getMonsterDrops()
        monsterFloorSpawns = masterDao.getMonsterFloorSpawns()
        dungeonInteractableDefinitions = masterDao.getDungeonInteractableDefinitions()
        dungeonInteractableSpawns = masterDao.getDungeonInteractableSpawns()
        appraisalRules = masterDao.getAppraisalRules()
        gameConfigs = masterDao.getGameConfigs().mapNotNull { config -> config.intValue?.let { config.key to it } }.toMap()
    }

    private fun gameInt(key: String, fallback: Int): Int = gameConfigs[key] ?: fallback

    private fun ensureEquippedWeapon() {
        val equipped = ownedItems.firstOrNull { it.isIdentified && it.isEquipped && ItemCatalog.isWeapon(it.itemCode) }
            ?: ownedItems.firstOrNull { it.isIdentified && it.container == "INVENTORY" && ItemCatalog.isWeapon(it.itemCode) }
        dungeonEquippedWeaponCode = equipped?.itemCode
        if (equipped != null && !equipped.isEquipped) {
            gameDatabase.runInTransaction {
                gameDatabase.ownedItemDao().clearEquippedCategories(currentPlayerId, listOf("WEAPON"))
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
        deathNoticeText.visibility = View.GONE
        setVillageHotspotsEnabled(true)
        updateTravelingMerchantVisibility()
        hideSystemUi()
        if (!dungeonRunPayload.isNullOrBlank()) {
            villageInteractionOverlay.postDelayed({ showSavedDungeonRunDialog() }, 350L)
        } else {
            villageInteractionOverlay.postDelayed({ showPendingEstateNotice() }, 350L)
        }
    }

    private fun showPendingEstateNotice() {
        val ownerId = currentPlayerId
        databaseExecutor.execute {
            val profile = gameDatabase.loginProfileDao().getById(ownerId) ?: return@execute
            if (profile.pendingEstateLossCount <= 0 && profile.pendingEstateKeptNames.isNullOrBlank()) return@execute
            val keptNames = profile.pendingEstateKeptNames?.takeIf { it.isNotBlank() } ?: "남겨진 물품 없음"
            runOnUiThread {
                AntiqueGameDialog.show(
                    this,
                    AntiqueGameDialog.Config(
                        title = "다음 세대의 상속",
                        subtitle = "죽음은 창고까지 온전히 비켜가지 않았다",
                        body = "창고에서 무작위로 살아남은 물품\n\n$keptNames",
                        warning = "창고 물품 ${profile.pendingEstateLossCount}개가 소멸했습니다. 최대 5개 슬롯만 다음 세대에 계승됩니다.",
                        actions = listOf(AntiqueGameDialog.Action("상속 확인", primary = true) {
                            databaseExecutor.execute { gameDatabase.loginProfileDao().clearEstateNotice(ownerId) }
                        }),
                        cancelable = false,
                        scrollHint = "↕ 상속 목록을 위아래로 움직여 확인",
                        bodyHeightDp = 260
                    )
                )
            }
        }
    }

    private fun showSavedDungeonRunDialog() {
        val payload = dungeonRunPayload ?: return
        val summary = runCatching {
            val state = JSONObject(payload)
            "마지막 위치  지하 ${state.optInt("floor", 1)}층\n" +
                "마지막 행동  ${state.optInt("turn", 1)}턴\n" +
                "현재 체력  ${state.optInt("hp", gameInt("base_player_hp", 10))}"
        }.getOrElse { "이전에 끝내지 못한 지하 원정 기록이 남아 있습니다." }
        AntiqueGameDialog.show(
            this,
            AntiqueGameDialog.Config(
                title = "원정 기록 발견",
                subtitle = "어둠 속에 이전 모험가의 발자국이 남아 있습니다",
                body = "$summary\n\n마지막으로 완료된 행동 직후부터 탐사를 계속할 수 있습니다.",
                actions = listOf(
                    AntiqueGameDialog.Action("마을에 머물기"),
                    AntiqueGameDialog.Action("탐사 이어가기", primary = true) { showDungeonDemo() }
                ),
                bodyHeightDp = 160
            )
        )
    }

    private fun startInitialFlow() {
        val preferences = getSharedPreferences("crpg_app_state", MODE_PRIVATE)
        if (preferences.getBoolean("opening_prologue_completed", false)) {
            restoreAutoLogin()
            return
        }
        loginOverlay.visibility = View.INVISIBLE
        showOpeningPrologue {
            preferences.edit().putBoolean("opening_prologue_completed", true).apply()
            loginOverlay.animate().cancel()
            loginOverlay.alpha = 1f
            loginOverlay.visibility = View.VISIBLE
            nameInput.requestFocus()
        }
    }

    private fun showOpeningPrologue(onFinished: () -> Unit) {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }
        overlay.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = .24f
            setImageBitmap(assets.open("ui/login/login_screen.png").use(BitmapFactory::decodeStream))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val storyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(42), dp(28), dp(42), dp(28))
            background = antiquePanel(0xE8120D0A.toInt(), COLOR_GOLD_DARK, 12f, 1)
        }
        storyPanel.addView(TextView(this).apply {
            text = "핏빛 문 아래"
            setTextColor(COLOR_GOLD)
            textSize = 29f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            bottomMargin = dp(14)
        })

        val lines = listOf(
            "모든 것은 부모님과 함께 살던 낡은 집에서 시작되었다.",
            "어머니는 원인을 알 수 없는 병에 걸려 날이 갈수록 쇠약해졌다.",
            "병의 원인을 찾겠다며 집을 나선 아버지는 끝내 돌아오지 않았다.",
            "두 사람의 흔적을 쫓던 당신은 집 깊숙한 곳에서 봉인된 지하 입구를 발견했다.",
            "문을 열자 썩은 냄새와 함께 기괴할 만큼 거대한 쥐가 어둠 속에서 기어 나왔다.",
            "간신히 문을 닫은 당신은 부모님에게 일어난 일의 답이 저 아래에 있음을 깨달았다.",
            "이제 장비를 마련하고 지하로 내려가 진실을 마주하자."
        )
        val lineViews = lines.mapIndexed { index, line ->
            TextView(this).apply {
                text = line
                setTextColor(if (index == lines.lastIndex) 0xFFFFD58A.toInt() else Color.WHITE)
                textSize = if (index == lines.lastIndex) 17f else 16f
                typeface = if (index == lines.lastIndex) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                alpha = 0f
                setLineSpacing(dp(3).toFloat(), 1f)
                storyPanel.addView(this, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(if (index == lines.lastIndex) 0 else 12) })
            }
        }
        val touchPrompt = TextView(this).apply {
            text = "화면을 터치하여 시작"
            setTextColor(COLOR_GOLD)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            alpha = 0f
            letterSpacing = .08f
        }
        storyPanel.addView(touchPrompt, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(38)
        ).apply { topMargin = dp(18) })
        val storyScroll = gameScrollView(storyPanel, showScrollbar = false)
        overlay.addView(storyScroll, FrameLayout.LayoutParams(
            minOf(dp(900), resources.displayMetrics.widthPixels - dp(70)),
            resources.displayMetrics.heightPixels - dp(40),
            Gravity.CENTER
        ))
        addContentView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        overlay.bringToFront()

        lineViews.forEachIndexed { index, lineView ->
            lineView.postDelayed({
                if (overlay.parent != null) {
                    lineView.animate().alpha(1f).setDuration(1550L).start()
                    storyScroll.post { storyScroll.smoothScrollTo(0, lineView.bottom) }
                }
            }, index * 1_000L)
        }
        var canStart = false
        val promptDelay = (lines.lastIndex * 1_000L) + 1550L
        overlay.postDelayed({
            canStart = true
            touchPrompt.animate().alpha(1f).setDuration(700L).start()
        }, promptDelay)
        overlay.setOnClickListener {
            if (!canStart) return@setOnClickListener
            canStart = false
            overlay.isClickable = false
            onFinished()
            overlay.animate().alpha(0f).setDuration(800L).withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }.start()
        }
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
        travelingMerchantHotspot = FrameLayout(this).apply {
            visibility = View.GONE
            contentDescription = "떠돌이 뽑기상자 상인"
            isClickable = true
            addView(ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(assets.open("ui/village/merchant/traveling_gacha_merchant.png").use(BitmapFactory::decodeStream))
            }, matchParentParams())
            addView(TextView(this@MainActivity).apply {
                text = "떠돌이 상인"
                setTextColor(0xFFFFE586.toInt()); textSize = 10f; typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setShadowLayer(dp(3).toFloat(), 0f, dp(2).toFloat(), Color.BLACK)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30), Gravity.TOP))
            setOnClickListener { showTravelingMerchant() }
        }
        villageInteractionOverlay.addView(generalStoreHotspot)
        villageInteractionOverlay.addView(blacksmithHotspot)
        villageInteractionOverlay.addView(appraisalHotspot)
        villageInteractionOverlay.addView(innWarehouseHotspot)
        villageInteractionOverlay.addView(manorHotspot)
        villageInteractionOverlay.addView(dungeonEntranceHotspot)
        villageInteractionOverlay.addView(travelingMerchantHotspot)
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
            travelingMerchantHotspot.layoutParams = FrameLayout.LayoutParams(
                (width * 0.06f).toInt(), (height * 0.153f).toInt()
            ).apply { leftMargin = (width * 0.47f).toInt(); topMargin = (height * 0.474f).toInt() }
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

    private fun updateTravelingMerchantVisibility() {
        if (!::travelingMerchantHotspot.isInitialized) return
        val interval = gameInt("traveling_merchant_interval_days", 5).coerceAtLeast(1)
        travelingMerchantHotspot.visibility = if (hasEnteredVillage && survivalDay > 0 && survivalDay % interval == 0) View.VISIBLE else View.GONE
        travelingMerchantHotspot.isEnabled = travelingMerchantHotspot.visibility == View.VISIBLE && shopOverlay == null
    }

    private fun showTravelingMerchant() {
        val interval = gameInt("traveling_merchant_interval_days", 5).coerceAtLeast(1)
        if (survivalDay % interval != 0) return
        databaseExecutor.execute {
            val claimMask = gameDatabase.loginProfileDao().getById(currentPlayerId)?.merchantFreeClaimMask ?: 0
            runOnUiThread { showTravelingMerchantContent(claimMask) }
        }
    }

    private fun showTravelingMerchantContent(claimMask: Int) {
        val content = createFacilityContent("떠돌이 상자 상인", "ui/village/village_map.png")
        shopOverlay?.contentDescription = "traveling_merchant"
        content.addView(TextView(this).apply {
            text = "생존 ${survivalDay}일차 · 5일마다 마을 중앙을 찾는 수상한 행상인입니다."
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)))
        body.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(assets.open("ui/village/merchant/traveling_gacha_merchant.png").use(BitmapFactory::decodeStream))
        }, LinearLayout.LayoutParams(dp(110), dp(110)).apply { marginEnd = dp(10) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        travelingMerchantList = list
        renderTravelingMerchantItems(claimMask)
        body.addView(gameScrollView(list), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun renderTravelingMerchantItems(claimMask: Int) {
        val list = travelingMerchantList ?: return
        list.removeAllViews()
        ItemCatalog.travelingMerchant.forEachIndexed { index, item ->
            val firstPurchase = claimMask and (1 shl index) == 0
            val displayedPrice = if (firstPurchase) 0 else item.price
            list.addView(storeItemRow(
                item.code, item.name, displayedPrice, item.unitsPerPurchase, item.assetPath, item.detail,
                purchaseOverride = { quantity, restoreButton ->
                    if (firstPurchase) claimMerchantSample(item.code, index, restoreButton)
                    else purchaseItem(item.code, item.name, item.price, item.unitsPerPurchase, quantity, restoreButton)
                }
            ))
        }
    }

    private fun claimMerchantSample(itemCode: String, claimIndex: Int, onFinished: () -> Unit) {
        databaseExecutor.execute {
            val profileDao = gameDatabase.loginProfileDao()
            val profile = profileDao.getById(currentPlayerId) ?: return@execute
            val claimBit = 1 shl claimIndex
            if (profile.merchantFreeClaimMask and claimBit != 0) {
                runOnUiThread { onFinished() }
                return@execute
            }
            val box = ItemCatalog.definition(itemCode) ?: return@execute
            val result = inventoryRepository.grantFreeMerchantBox(currentPlayerId, box.code, box.name)
            ownedItems = result.items
            if (result.gold != null) {
                profileDao.updateMerchantFreeClaimMask(currentPlayerId, profile.merchantFreeClaimMask or claimBit)
            }
            runOnUiThread {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                if (result.gold != null) refreshTravelingMerchant() else onFinished()
            }
        }
    }

    private fun refreshTravelingMerchant() {
        databaseExecutor.execute {
            val claimMask = gameDatabase.loginProfileDao().getById(currentPlayerId)?.merchantFreeClaimMask ?: 0
            runOnUiThread { renderTravelingMerchantItems(claimMask) }
        }
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
        val leftScroll = gameScrollView(leftColumn)
        content.addView(leftScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.12f).apply {
            marginEnd = dp(12)
        })

        val inventoryCount = ownedItems.count { it.container == "INVENTORY" }
        val storageCount = ownedItems.count { it.container == "STORAGE" }
        leftColumn.addView(sectionTitle("내 아이템  $inventoryCount / ${gameInt("inventory_capacity", 25)}"))
        leftColumn.addView(createInventoryGrid(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemGridHeight(5)
        ))
        val storageCapacity = gameInt("storage_capacity", 20)
        val storageRows = (storageCapacity + 4) / 5
        leftColumn.addView(sectionTitle("창고  $storageCount / $storageCapacity"))
        leftColumn.addView(createWarehouseGrid(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemGridHeight(storageRows)
        ))

        val rightColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            this.background = antiquePanel(COLOR_LEATHER, COLOR_GOLD_DARK, 12f, 2)
        }
        content.addView(rightColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.88f))
        rightColumn.addView(sectionTitle(if (isBlacksmith) "구매 가능 장비" else "구매 가능 아이템"))

        val storeList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollView = gameScrollView(storeList)
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

    private fun createInventoryGrid(): ItemGridView = createCommonItemGrid("INVENTORY", 5, 5)

    private fun createWarehouseGrid(): ItemGridView {
        val capacity = gameInt("storage_capacity", 20)
        return createCommonItemGrid("STORAGE", (capacity + 4) / 5, 5)
    }

    private fun createCommonItemGrid(
        container: String,
        rows: Int,
        columns: Int,
        capacity: Int = rows * columns
    ): ItemGridView =
        ItemGridView(
            context = this,
            container = container,
            rows = rows,
            columns = columns,
            capacity = capacity,
            items = ownedItems,
            assetPath = { item ->
                if (item.isIdentified) assetPathFor(item.itemCode) else "ui/dungeon/loot/chest_normal.png"
            },
            gradeColor = ItemCatalog::gradeColor,
            isConsumable = ItemCatalog::isConsumable,
            onItemClick = { item ->
                if (!item.isIdentified) {
                    Toast.makeText(this, "감정소에서 감정해야 정보를 확인할 수 있습니다.", Toast.LENGTH_SHORT).show()
                } else if (ItemCatalog.definition(item.itemCode)?.specialEffect?.startsWith("GACHA_BOX_") == true) {
                    showGachaOpeningScene(item)
                } else ItemCatalog.equipmentOption(item.itemCode)?.let { option ->
                    EquipmentOptionDialog(this).show(
                        item = item,
                        option = option,
                        grade = ItemCatalog.grade(item.itemCode),
                        baseAttackPower = ItemCatalog.definition(item.itemCode)?.attackPower ?: 0,
                        baseDurability = ItemAppraisalRules.baseDurability(ItemCatalog.grade(item.itemCode)),
                        appraisedAttackPower = item.appraisedAttackPower,
                        salePrice = ItemCatalog.salePrice(item.itemCode),
                        onSell = { confirmSellEquipment(item) }
                    )
                }
            },
            onItemDrop = { payload, targetContainer, targetSlot -> moveStoredItem(payload.id, targetContainer, targetSlot) }
        )

    private fun showGachaOpeningScene(box: OwnedItemEntity) {
        val definition = ItemCatalog.definition(box.itemCode) ?: return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xEE070504.toInt()); isClickable = true; isFocusable = true
        }
        val chest = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(assets.open(definition.assetPath).use(BitmapFactory::decodeStream))
            scaleX = .82f; scaleY = .82f
        }
        val status = TextView(this).apply {
            text = "${definition.name}의 봉인을 해제합니다"
            setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            isClickable = false
        }
        overlay.addView(chest, FrameLayout.LayoutParams(dp(260), dp(260), Gravity.CENTER))
        overlay.addView(status, FrameLayout.LayoutParams(dp(420), dp(84), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(42) })
        addContentView(overlay, matchParentParams())
        chest.animate().scaleX(1.08f).scaleY(1.08f).rotation(-3f).setDuration(380L).withEndAction {
            chest.animate().rotation(3f).setDuration(260L).withEndAction {
                chest.animate().rotation(0f).alpha(.15f).scaleX(1.35f).scaleY(1.35f).setDuration(420L).withEndAction {
                    openOwnedGachaBox(box, overlay, status)
                }.start()
            }.start()
        }.start()
    }

    private fun openOwnedGachaBox(box: OwnedItemEntity, overlay: FrameLayout, status: TextView) {
        databaseExecutor.execute {
            var resultMessage = "상자는 텅 비어 있었습니다"
            gameDatabase.runInTransaction {
                val dao = gameDatabase.ownedItemDao()
                val current = dao.getForOwner(currentPlayerId).firstOrNull { it.id == box.id } ?: return@runInTransaction
                val boxDefinition = ItemCatalog.definition(current.itemCode) ?: return@runInTransaction
                val won = kotlin.random.Random.nextInt(100) < 30
                val wantsEquipment = won && kotlin.random.Random.nextBoolean()
                val rewardDefinition = if (wantsEquipment) {
                    ItemCatalog.allDefinitions.filter { !it.isConsumable && it.grade == boxDefinition.grade }.randomOrNull()
                } else null
                val usedSlots = dao.getForOwner(currentPlayerId).filter { it.container == current.container }.map { it.slotIndex }.toSet()
                val capacity = gameInt(if (current.container == "STORAGE") "storage_capacity" else "inventory_capacity", if (current.container == "STORAGE") 20 else 25)
                val rewardSlot = if (current.quantity == 1) current.slotIndex else (0 until capacity).firstOrNull { it !in usedSlots }
                val canGrantEquipment = rewardDefinition != null && rewardSlot != null
                if (current.quantity == 1) dao.deleteById(current.id) else dao.updateQuantity(current.id, current.quantity - 1)
                when {
                    !won -> Unit
                    canGrantEquipment -> {
                        val reward = rewardDefinition!!
                        val unidentified = reward.grade != "NORMAL"
                        dao.insert(OwnedItemEntity(
                            ownerId = currentPlayerId, itemCode = reward.code,
                            displayName = if (unidentified) "미확인 ${ItemAppraisalRules.gradeName(reward.grade)} 장비" else reward.name,
                            quantity = 1, container = current.container, slotIndex = rewardSlot!!, isIdentified = !unidentified,
                            durability = ItemAppraisalRules.baseDurability(reward.grade)
                        ))
                        resultMessage = "${reward.name}을 획득했습니다"
                    }
                    else -> {
                        val gold = (boxDefinition.basePrice / 2).coerceAtLeast(10)
                        val profile = gameDatabase.loginProfileDao().getById(currentPlayerId) ?: return@runInTransaction
                        playerGold = profile.gold + gold
                        gameDatabase.loginProfileDao().updateGold(currentPlayerId, playerGold)
                        resultMessage = "${gold}G를 획득했습니다"
                    }
                }
                ownedItems = dao.getForOwner(currentPlayerId)
            }
            runOnUiThread {
                status.text = "$resultMessage\n결과를 눌러 돌아가기"
                status.setTextColor(if (resultMessage.contains("텅 비어")) 0xFFAAA29A.toInt() else COLOR_GOLD)
                status.background = antiquePanel(0xE63A2113.toInt(), COLOR_GOLD_DARK, 9f, 1)
                status.isClickable = true
                status.setOnClickListener {
                    status.isClickable = false
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                    refreshCurrentItemScreen()
                }
            }
        }
    }

    private fun assetPathFor(itemCode: String): String = ItemCatalog.assetPath(itemCode)

    private fun confirmSellEquipment(item: OwnedItemEntity) {
        val salePrice = ItemCatalog.salePrice(item.itemCode)
        AntiqueGameDialog.show(
            this,
            AntiqueGameDialog.Config(
                title = "장비 판매",
                body = "${item.displayName}\n\n판매 금액  ${salePrice}G",
                warning = "판매한 장비는 되돌릴 수 없습니다.",
                actions = listOf(
                    AntiqueGameDialog.Action("취소"),
                    AntiqueGameDialog.Action("판매", primary = true) {
                        databaseExecutor.execute {
                            val result = inventoryRepository.sell(currentPlayerId, item.id)
                            ownedItems = result.items
                            result.gold?.let { playerGold = it }
                            ensureEquippedWeapon()
                            runOnUiThread {
                                closeShop()
                                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ),
                bodyHeightDp = 100
            )
        )
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

    private fun storeItemRow(
        itemCode: String,
        name: String,
        unitPrice: Int,
        unitsPerPurchase: Int,
        assetPath: String,
        detail: String? = null,
        purchaseOverride: ((Int, () -> Unit) -> Unit)? = null
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
                val restoreButton = { isEnabled = true }
                purchaseOverride?.invoke(quantity, restoreButton)
                    ?: purchaseItem(itemCode, name.removeSuffix(" 5개"), unitPrice, unitsPerPurchase, quantity)
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
        purchaseQuantity: Int,
        onFinished: (() -> Unit)? = null
    ) {
        databaseExecutor.execute {
            val result = inventoryRepository.purchase(
                currentPlayerId, itemCode, displayName, unitPrice, unitsPerPurchase, purchaseQuantity
            )
            ownedItems = result.items
            result.gold?.let { playerGold = it }
            runOnUiThread {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                when (ItemCatalog.definition(itemCode)?.storeType) {
                    "MERCHANT" -> onFinished?.invoke()
                    else -> refreshShopInterface(ItemCatalog.definition(itemCode)?.storeType == "BLACKSMITH")
                }
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
        shopOverlay?.let { existing -> (existing.parent as? ViewGroup)?.removeView(existing) }
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
                setOnClickListener { closeShopOverlay(overlay) }
            })
        }
        overlay.addView(titleBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(20), dp(28), dp(24))
            background = antiquePanel(COLOR_LEATHER, COLOR_GOLD_DARK, 12f, 2)
        }
        val contentScroll = gameScrollView(content)
        overlay.addView(contentScroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(72); bottomMargin = dp(16); leftMargin = dp(24); rightMargin = dp(24) })
        addContentView(overlay, matchParentParams())
        return content
    }

    private fun showAppraisalOffice() {
        val content = createFacilityContent("감정소", "ui/village/building_appraisal_house.png")
        shopOverlay?.contentDescription = "appraisal_office"
        content.addView(TextView(this).apply {
            text = "고급 이상 장비는 미확인 상태로 입수됩니다. 감정 비용은 장비 가치와 같으며, 실패해도 장비는 유지됩니다."
            setTextColor(0xFFD8C7A3.toInt())
            textSize = 15f
            setPadding(dp(8), 0, dp(8), dp(12))
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310)))
        val rates = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sectionTitle("등급별 기본 성공 확률"))
        }
        appraisalRules.forEach { rule ->
            rates.addView(appraisalRateRow(ItemAppraisalRules.gradeName(rule.grade), "장비 가치", "${(rule.successRate * 100).toInt()}%"))
        }
        body.addView(rates, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.46f).apply { marginEnd = dp(14) })
        val unidentifiedItems = ownedItems.filter { !it.isIdentified }
        val appraisalList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (unidentifiedItems.isEmpty()) Gravity.CENTER else Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            if (unidentifiedItems.isEmpty()) {
                addView(TextView(this@MainActivity).apply {
                    text = "미확인 아이템 없음\n\n던전에서 고급 이상 장비를 획득하면\n이곳에서 감정할 수 있습니다."
                    setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
                })
            } else unidentifiedItems.forEach { item ->
                val definition = ItemCatalog.definition(item.itemCode) ?: return@forEach
                val rule = appraisalRules.firstOrNull { it.grade == definition.grade } ?: return@forEach
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    addView(TextView(this@MainActivity).apply {
                        text = "미확인 ${ItemAppraisalRules.gradeName(definition.grade)} 장비\n가치·감정료 ${definition.basePrice}G · 성공 ${(rule.successRate * 100).toInt()}%"
                        setTextColor(Color.WHITE); textSize = 14f
                    }, LinearLayout.LayoutParams(0, dp(58), 1f))
                    addView(antiqueButton("감정", dp(64), dp(38)).apply { setOnClickListener { appraiseItem(item) } })
                })
            }
        }
        body.addView(gameScrollView(appraisalList), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.54f))
    }

    private fun appraiseItem(item: OwnedItemEntity) {
        val definition = ItemCatalog.definition(item.itemCode) ?: return
        val rule = appraisalRules.firstOrNull { it.grade == definition.grade } ?: return
        databaseExecutor.execute {
            var message = ""
            var revealBody: String? = null
            var revealChanges: String? = null
            gameDatabase.runInTransaction {
                val profileDao = gameDatabase.loginProfileDao()
                val profile = profileDao.getById(currentPlayerId) ?: return@runInTransaction
                if (profile.gold < definition.basePrice) {
                    message = "골드가 부족합니다. 감정에는 ${definition.basePrice}G가 필요합니다."
                    return@runInTransaction
                }
                playerGold = profile.gold - definition.basePrice
                profileDao.updateGold(currentPlayerId, playerGold)
                if (Random.nextDouble() < rule.successRate) {
                    val rolledAttack = definition.attackPower.takeIf { it > 0 }?.let { base ->
                        Random.nextInt(((base + 1) / 2).coerceAtLeast(1), (base * 3 / 2) + 1)
                    }
                    val baseDurability = ItemAppraisalRules.baseDurability(definition.grade)
                    val rolledDurability = baseDurability + Random.nextInt(0, 11)
                    gameDatabase.ownedItemDao().markIdentified(item.id, definition.grade, definition.name, rolledAttack, rolledDurability)
                    val option = ItemCatalog.equipmentOption(definition.code)
                    val attackDelta = rolledAttack?.minus(definition.attackPower)?.takeIf { definition.attackPower > 0 }
                    val durabilityDelta = rolledDurability - baseDurability
                    revealBody = buildString {
                        append("${ItemAppraisalRules.gradeName(definition.grade)} · ${option?.category ?: definition.category}\n\n")
                        if (rolledAttack != null) {
                            append("• 공격력  $rolledAttack")
                            append(when {
                                attackDelta == null || attackDelta == 0 -> "  (변화 없음)"
                                attackDelta > 0 -> "  ▲ +$attackDelta"
                                else -> "  ▼ $attackDelta"
                            })
                            append("\n")
                        }
                        option?.lines?.filterNot { it.startsWith("공격력") }?.forEach { append("• $it\n") }
                        append("• 원정 수명  ${rolledDurability}회")
                        if (durabilityDelta > 0) append("  ▲ +$durabilityDelta") else append("  (추가 없음)")
                        append("\n")
                        option?.specialEffect?.let { append("\n◆ $it") }
                    }
                    revealChanges = listOfNotNull(
                        attackDelta?.takeIf { it != 0 }?.let { "공격력 ${if (it > 0) "+" else ""}$it" },
                        durabilityDelta.takeIf { it > 0 }?.let { "수명 +${it}회" }
                    ).ifEmpty { listOf("추가 변화 없음") }.joinToString(" · ")
                    message = "감정 성공"
                } else {
                    message = "감정 실패 · 장비는 유지됩니다."
                }
                ownedItems = gameDatabase.ownedItemDao().getForOwner(currentPlayerId)
            }
            runOnUiThread {
                if (message.startsWith("골드가")) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                shopOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                shopOverlay = null
                showAppraisalOffice()
                if (revealBody != null) {
                    AntiqueGameDialog.show(
                        this,
                        AntiqueGameDialog.Config(
                            title = definition.name,
                            subtitle = "감정 성공 · 전체 장비 효과",
                            body = revealBody!!,
                            warning = "감정 추가 변화 · ${revealChanges ?: "추가 변화 없음"}",
                            actions = listOf(AntiqueGameDialog.Action("확인", primary = true)),
                            scrollHint = "↕ 장비 효과를 위아래로 움직여 확인",
                            bodyHeightDp = 260
                        )
                    )
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))
        val inventoryItems = ownedItems.count { it.container == "INVENTORY" }
        val storageItems = ownedItems.count { it.container == "STORAGE" }
        val storageCapacity = gameInt("storage_capacity", 20)
        val storageRows = (storageCapacity + 4) / 5
        val inventoryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sectionTitle("인벤토리  $inventoryItems / ${gameInt("inventory_capacity", 25)}"))
            addView(createTransferGrid("INVENTORY", 5, 5, gameInt("inventory_capacity", 25)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val storagePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sectionTitle("보호 창고  $storageItems / $storageCapacity"))
            addView(createTransferGrid("STORAGE", storageRows, 5, storageCapacity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        body.addView(inventoryPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(12) })
        body.addView(storagePanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun createTransferGrid(container: String, rows: Int, columns: Int, capacity: Int): ItemGridView =
        createCommonItemGrid(container, rows, columns, capacity)

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
        val isDungeonLoadout = overlay.contentDescription == "dungeon_loadout"
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        shopOverlay = null
        when {
            isWarehouseScreen -> showInnWarehouse()
            isBlacksmithScreen -> showShopInterface(true)
            isDungeonLoadout -> showDungeonLoadout()
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
        databaseExecutor.execute {
            val deceased = gameDatabase.deceasedCharacterDao().getAll()
            val profile = gameDatabase.loginProfileDao().getById(currentPlayerId)
            runOnUiThread { showManorWithDeceasedList(deceased, profile) }
        }
    }

    private fun showManorWithDeceasedList(
        deceased: List<DeceasedCharacterEntity>,
        profile: LoginProfileEntity?
    ) {
        val content = createFacilityContent("저택", "ui/village/building_manor_dungeon.png")
        content.addView(TextView(this).apply {
            text = "생존 ${profile?.survivalDay ?: survivalDay}일 · 가문의 기록과 재산은 다음 세대로 계승됩니다."
            setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        val currentDay = profile?.survivalDay ?: survivalDay
        val lastSearchDay = profile?.lastManorSearchDay ?: 0
        val availableSearchDay = ((lastSearchDay / 5) + 1) * 5
        val canSearch = currentDay >= availableSearchDay
        content.addView(facilityChoicePanel(
            title = if (canSearch) "저택 수색 가능" else "다음 수색까지 ${availableSearchDay - currentDay}일",
            description = if (canSearch) {
                "$availableSearchDay 일차 수색이 해금되었습니다. 저택에 남은 흔적을 조사합니다."
            } else {
                "저택은 5일마다 다시 수색할 수 있습니다."
            },
            buttonText = if (canSearch) "저택 수색" else "아직 수색할 수 없음",
            message = if (canSearch) null else "${availableSearchDay}일차에 다시 수색할 수 있습니다.",
            action = if (canSearch) ({ searchManor(availableSearchDay) }) else null
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)).apply {
            setMargins(dp(18), 0, dp(18), dp(12))
        })
        val records = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            if (deceased.isEmpty()) {
                addView(TextView(this@MainActivity).apply {
                    text = "아직 기록된 사망자가 없습니다."
                    setTextColor(0xFFCCBFA8.toInt()); textSize = 17f; gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)))
            } else {
                val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
                deceased.forEach { character ->
                    addView(TextView(this@MainActivity).apply {
                        text = "† ${character.generation}세 · ${character.playerName} †\n묘비 기록 · 최고 도달 지하 ${character.reachedFloor}층\n생존 ${character.survivedTurns}턴 · ${dateFormat.format(Date(character.diedAt))}"
                        setTextColor(Color.WHITE); textSize = 16f
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        background = antiquePanel(0xC51B1410.toInt(), COLOR_GOLD_DARK, 7f, 1)
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(10)
                    })
                }
            }
        }
        content.addView(gameScrollView(records), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(270)
        ))
    }

    private fun searchManor(searchDay: Int) {
        val stories = listOf(
            "어머니의 방에서 약초 냄새가 밴 낡은 주머니를 찾았다. 안쪽에는 비상금 10G가 숨겨져 있었다.",
            "아버지의 서재에서 지하 통로를 표시한 찢어진 지도와 함께 봉인된 동전 10G를 발견했다.",
            "무너진 벽난로의 재를 걷어내자 검게 그을린 철제 상자가 드러났다. 안에는 10G가 남아 있었다.",
            "다락방의 뒤틀린 마룻장을 들어 올리자 누군가 급히 감춘 가죽 주머니와 10G가 나타났다.",
            "정원의 말라 죽은 나무 아래에서 아버지의 표식이 새겨진 작은 함을 팠다. 함에는 10G가 들어 있었다.",
            "지하 입구로 이어지는 복도에서 쥐가 갉아낸 벽 틈을 조사해 피 묻은 편지와 10G를 찾아냈다."
        )
        databaseExecutor.execute {
            var story: String? = null
            gameDatabase.runInTransaction {
                val dao = gameDatabase.loginProfileDao()
                val profile = dao.getById(currentPlayerId) ?: return@runInTransaction
                val expectedDay = ((profile.lastManorSearchDay / 5) + 1) * 5
                if (searchDay != expectedDay || profile.survivalDay < searchDay) return@runInTransaction
                val reward = 10
                playerGold = profile.gold + reward
                survivalDay = profile.survivalDay
                dao.save(profile.copy(gold = playerGold, lastManorSearchDay = searchDay))
                story = stories[((searchDay / 5) - 1) % stories.size]
            }
            val resultStory = story ?: return@execute
            runOnUiThread {
                AntiqueGameDialog.show(
                    this,
                    AntiqueGameDialog.Config(
                        title = "$searchDay 일차 · 저택 수색",
                        subtitle = "어둠 속에 묻혀 있던 흔적",
                        body = resultStory,
                        warning = "10G를 발견했습니다. 현재 보유 골드: ${playerGold}G",
                        actions = listOf(
                            AntiqueGameDialog.Action("확인", primary = true) { showManorEntrance() }
                        )
                    )
                )
            }
        }
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
        val content = createFacilityContent("", "ui/village/building_manor_dungeon.png")
        shopOverlay?.contentDescription = "dungeon_loadout"
        val carriedWeapons = ownedItems
            .filter { it.container == "INVENTORY" && ItemCatalog.isWeapon(it.itemCode) }
            .sortedBy { it.slotIndex }
        dungeonEquippedWeaponCode = carriedWeapons.firstOrNull()?.itemCode
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(antiqueButton("지하 1층 입장", dp(170), dp(46)).apply {
            setOnClickListener { showDungeonEntryConfirmation() }
        })
        content.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemGridHeight(5)))
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("가져갈 아이템"))
            addView(createInventoryGrid(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f).apply { marginEnd = dp(14) })

        val equippedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sectionTitle("착용 정보"))
        }
        val equippedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dungeonLoadoutEquipment().forEach { (slotName, item) ->
            equippedList.addView(loadoutEquipmentRow(slotName, item))
        }
        equippedPanel.addView(gameScrollView(equippedList), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(equippedPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .85f))
    }

    private fun dungeonLoadoutEquipment(): List<Pair<String, OwnedItemEntity?>> {
        val inventoryEquipment = ownedItems.filter { item ->
            item.container == "INVENTORY" && item.isIdentified && !ItemCatalog.isConsumable(item.itemCode)
        }
        fun selected(category: String): OwnedItemEntity? = inventoryEquipment.firstOrNull {
            it.isEquipped && ItemCatalog.category(it.itemCode) == category
        } ?: inventoryEquipment.firstOrNull { ItemCatalog.category(it.itemCode) == category }
        return listOf(
            "무기" to inventoryEquipment.firstOrNull { it.itemCode == dungeonEquippedWeaponCode },
            "투구" to selected("HELMET"),
            "갑옷" to selected("ARMOR"),
            "신발" to selected("BOOTS"),
            "보조" to selected("AUXILIARY"),
            "장신구" to selected("ACCESSORY")
        )
    }

    private fun loadoutEquipmentRow(slotName: String, item: OwnedItemEntity?): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(5), dp(10), dp(5))
        val definition = item?.let { ItemCatalog.definition(it.itemCode) }
        background = antiquePanel(0xD9292018.toInt(), item?.let { ItemCatalog.gradeColor(it.itemCode) } ?: 0xFF514634.toInt(), 7f, if (item == null) 1 else 2)
        addView(TextView(this@MainActivity).apply {
            text = slotName; setTextColor(COLOR_GOLD); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.CENTER_VERTICAL })
        addView(TextView(this@MainActivity).apply {
            text = if (item == null || definition == null) {
                "비어 있음"
            } else {
                val remaining = (item.durability - item.dungeonUseCount).coerceAtLeast(0)
                "${item.displayName} · ${ItemAppraisalRules.gradeName(definition.grade)}\n${definition.detail ?: "추가 옵션 없음"} · 수명 ${remaining}회"
            }
            setTextColor(if (item == null) 0xFF817563.toInt() else Color.WHITE)
            textSize = 12f
            maxLines = 2
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        if (item != null) {
            isClickable = true
            setOnClickListener {
                ItemCatalog.equipmentOption(item.itemCode)?.let { option ->
                    EquipmentOptionDialog(this@MainActivity).show(
                        item = item,
                        option = option,
                        grade = ItemCatalog.grade(item.itemCode),
                        baseAttackPower = definition?.attackPower ?: 0,
                        baseDurability = ItemAppraisalRules.baseDurability(ItemCatalog.grade(item.itemCode)),
                        appraisedAttackPower = item.appraisedAttackPower
                    )
                }
            }
        }
    }.apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply { bottomMargin = dp(6) }
    }

    private fun showDungeonEntryConfirmation() {
        if (!dungeonRunPayload.isNullOrBlank()) {
            showSavedDungeonRunDialog()
            return
        }
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
        AntiqueGameDialog.show(
            this,
            AntiqueGameDialog.Config(
                title = "지하 원정 서약",
                subtitle = "모든 탐험은 지하 1층에서 시작됩니다",
                body = "착용 무기\n  $equippedName\n\n가져갈 소지품\n$itemSummary",
                warning = "사망하면 가져간 장비와 소지품을 모두 잃습니다. 창고와 골드만 계승됩니다.",
                actions = listOf(
                    AntiqueGameDialog.Action("마을에 남기"),
                    AntiqueGameDialog.Action("지하 1층 입장", primary = true) { showDungeonDemo() }
                ),
                actionsAboveBody = true,
                scrollHint = "↕ 소지품 목록을 위아래로 움직여 확인"
            )
        )
    }

    private fun showDungeonDemo() {
        val old = shopOverlay
        (old?.parent as? ViewGroup)?.removeView(old)

        isDungeonActive = true
        currentDungeonFloor = 1
        updateBackgroundMusic()
        val identifiedInventory = ownedItems.filter { it.container == "INVENTORY" && it.isIdentified }
        val appraisedAttackByCode = identifiedInventory.mapNotNull { item -> item.appraisedAttackPower?.let { item.itemCode to it } }.toMap()
        val overlay = FrameLayout(this)
        shopOverlay = overlay
        overlay.addView(
            DungeonDemoView(
                this,
                ownedItems.filter { it.container == "INVENTORY" }
                    .filter { it.isIdentified || ItemCatalog.isConsumable(it.itemCode) }
                    .groupBy { it.itemCode }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } },
                equippedWeaponCode = dungeonEquippedWeaponCode,
                equippedItemCodes = ownedItems.filter { it.container == "INVENTORY" && it.isEquipped && it.isIdentified }.map { it.itemCode }.toSet(),
                appraisedAttackPowerByCode = appraisedAttackByCode,
                itemDefinitions = ItemCatalog.allDefinitions,
                monsterDefinitions = monsterDefinitions,
                monsterDrops = monsterDrops,
                monsterFloorSpawns = monsterFloorSpawns,
                interactableDefinitions = dungeonInteractableDefinitions,
                interactableSpawns = dungeonInteractableSpawns,
                healingObjectChancePercent = gameInt("dungeon_healing_object_chance_percent", 30),
                monsterCountMin = gameInt("dungeon_monster_count_min", 5),
                monsterCountMax = gameInt("dungeon_monster_count_max", 10),
                returnStoneCombatLockFloor = gameInt("return_stone_combat_lock_floor", 11),
                dungeonChestSpawnPercent = gameInt("dungeon_chest_spawn_percent", 25),
                dungeonChestMimicPercent = gameInt("dungeon_chest_mimic_percent", 25),
                savedRunPayload = dungeonRunPayload,
                villageGold = playerGold,
                playerBaseHp = gameInt("base_player_hp", 10),
                survivalDay = survivalDay,
                inventoryCapacity = gameInt("inventory_capacity", 25),
                onUseReturnStone = { acquiredItems, acquiredGold, consumedItems, equippedCodes ->
                    useReturnStoneFromDungeon(acquiredItems, acquiredGold, consumedItems, equippedCodes)
                },
                onExitDungeon = { acquiredItems, acquiredGold, consumedItems, equippedCodes ->
                    exitDungeonSafely(acquiredItems, acquiredGold, consumedItems, equippedCodes)
                },
                onPlayerDeath = { floor, survivedTurns, killerCode, killerName ->
                    handlePlayerDeath(floor, survivedTurns, killerCode, killerName)
                },
                onSpendReviveGold = { amount -> spendGoldForDungeonRevive(amount) },
                onEquipItem = { code, category -> persistDungeonEquipment(code, category) },
                onFloorChanged = { floor ->
                    currentDungeonFloor = floor
                    if (floor > highestFloor) {
                        highestFloor = floor
                        val ownerId = currentPlayerId
                        databaseExecutor.execute { gameDatabase.loginProfileDao().updateHighestFloor(ownerId, floor) }
                    }
                    updateBackgroundMusic()
                },
                onPersistRun = { payload ->
                    dungeonRunPayload = payload
                    val ownerId = currentPlayerId
                    databaseExecutor.execute {
                        gameDatabase.dungeonRunDao().save(DungeonRunEntity(ownerId, payload, System.currentTimeMillis()))
                    }
                }
            ),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addContentView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun useReturnStoneFromDungeon(
        acquiredItems: Map<String, Int>,
        acquiredGold: Int,
        consumedItems: Map<String, Int>,
        equippedCodes: Set<String>
    ) {
        settleDungeonRun(acquiredItems, acquiredGold, consumedItems, equippedCodes, consumeReturnStone = true)
    }

    private fun exitDungeonSafely(
        acquiredItems: Map<String, Int>,
        acquiredGold: Int,
        consumedItems: Map<String, Int>,
        equippedCodes: Set<String>
    ) {
        settleDungeonRun(acquiredItems, acquiredGold, consumedItems, equippedCodes, consumeReturnStone = false)
    }

    private fun spendGoldForDungeonRevive(amount: Int) {
        playerGold = (playerGold - amount).coerceAtLeast(0)
        databaseExecutor.execute { gameDatabase.loginProfileDao().updateGold(currentPlayerId, playerGold) }
    }

    private fun persistDungeonEquipment(code: String, category: String) {
        databaseExecutor.execute {
            val dao = gameDatabase.ownedItemDao()
            gameDatabase.runInTransaction {
                dao.clearEquippedCategories(currentPlayerId, listOf(category))
                dao.findItem(currentPlayerId, "INVENTORY", code)?.let { dao.setEquipped(it.id) }
            }
            ownedItems = dao.getForOwner(currentPlayerId)
            if (category == "WEAPON") dungeonEquippedWeaponCode = code
        }
    }

    private fun handlePlayerDeath(
        reachedFloor: Int,
        survivedTurns: Int,
        killerCode: String?,
        killerName: String?
    ) {
        databaseExecutor.execute {
            gameDatabase.dungeonRunDao().delete(currentPlayerId)
            dungeonRunPayload = null
            val profile = gameDatabase.loginProfileDao().getById(currentPlayerId)
            val deceasedName = profile?.playerName ?: "이름 없는 모험가"
            val recordedHighestFloor = maxOf(reachedFloor, profile?.highestFloor ?: highestFloor)
            val generation = gameDatabase.deceasedCharacterDao().count() + 1
            gameDatabase.runInTransaction {
                val itemDao = gameDatabase.ownedItemDao()
                val storedItems = itemDao.getForOwner(currentPlayerId).filter { it.container == "STORAGE" }
                val keptStorage = storedItems.shuffled().take(5)
                val keptIds = keptStorage.map { it.id }.toSet()
                val lostStorage = storedItems.filter { it.id !in keptIds }
                lostStorage.forEach { itemDao.deleteById(it.id) }
                val keptSummary = keptStorage.joinToString("\n") { item ->
                    "• ${item.displayName}${if (item.quantity > 1) " ×${item.quantity}" else ""}"
                }
                gameDatabase.deceasedCharacterDao().insert(
                    DeceasedCharacterEntity(
                        playerName = deceasedName,
                        generation = generation,
                        reachedFloor = recordedHighestFloor,
                        survivedTurns = survivedTurns,
                        diedAt = System.currentTimeMillis()
                    )
                )
                itemDao.deleteContainer(currentPlayerId, "INVENTORY")
                profile?.let {
                    gameDatabase.loginProfileDao().save(
                        it.copy(
                            autoLogin = false,
                            lastLoginAt = System.currentTimeMillis(),
                            survivalDay = 1,
                            lastManorSearchDay = 0,
                            highestFloor = 1,
                            pendingEstateLossCount = lostStorage.size,
                            pendingEstateKeptNames = keptSummary
                        )
                    )
                }
                ownedItems = gameDatabase.ownedItemDao().getForOwner(currentPlayerId)
            }
            survivalDay = 1
            highestFloor = 1
            dungeonEquippedWeaponCode = null
            val deathMessage = DeathNarratives.forMonster(killerCode, killerName)
            runOnUiThread { showGameOver(deceasedName, generation, deathMessage) }
        }
    }

    private fun showGameOver(deceasedName: String, generation: Int, deathMessage: String) {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true
            isFocusable = true
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(42), dp(30), dp(42), dp(28))
            background = antiquePanel(0xF5170C0B.toInt(), 0xFF7D2925.toInt(), 14f, 2)
            elevation = dp(18).toFloat()
        }
        panel.addView(TextView(this).apply {
            text = "GAME OVER"
            setTextColor(0xFFCF3F38.toInt())
            textSize = 34f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = .12f
            setShadowLayer(dp(5).toFloat(), 0f, dp(3).toFloat(), Color.BLACK)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            bottomMargin = dp(14)
        })

        val messages = listOf(
            deathMessage,
            "${generation}세 · $deceasedName",
            "차가운 지하에서 생을 마감했다.",
            "던전에 가져간 장비와 소지품은 어둠 속에 남겨졌다.",
            "그러나 창고의 재산과 가문의 기록은 다음 세대로 이어진다."
        )
        val messageViews = messages.mapIndexed { index, message ->
            TextView(this).apply {
                text = message
                setTextColor(
                    when (index) {
                        0 -> 0xFFFFA099.toInt()
                        messages.lastIndex -> COLOR_GOLD
                        else -> Color.WHITE
                    }
                )
                textSize = if (index == 0) 18f else 16f
                typeface = if (index == 0 || index == messages.lastIndex) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                alpha = 0f
                translationY = -dp(10).toFloat()
                setLineSpacing(dp(4).toFloat(), 1f)
                panel.addView(this, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) })
            }
        }
        val touchPrompt = TextView(this).apply {
            text = "화면을 터치하여 새로운 캐릭터 만들기"
            setTextColor(0xFFD0A653.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            alpha = 0f
            letterSpacing = .05f
        }
        panel.addView(touchPrompt, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ).apply { topMargin = dp(8) })

        val gameOverScroll = gameScrollView(panel, showScrollbar = false)
        overlay.addView(gameOverScroll, FrameLayout.LayoutParams(
            minOf(dp(780), resources.displayMetrics.widthPixels - dp(70)),
            resources.displayMetrics.heightPixels - dp(36),
            Gravity.CENTER
        ))
        addContentView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        overlay.bringToFront()

        messageViews.forEachIndexed { index, messageView ->
            messageView.postDelayed({
                if (overlay.parent != null) {
                    messageView.animate().alpha(1f).translationY(0f).setDuration(1_350L).start()
                    gameOverScroll.post { gameOverScroll.smoothScrollTo(0, messageView.bottom) }
                }
            }, index * 1_000L)
        }
        var canContinue = false
        overlay.postDelayed({
            canContinue = true
            touchPrompt.animate().alpha(1f).setDuration(700L).start()
        }, (messages.lastIndex * 1_000L) + 1_350L)
        overlay.setOnClickListener {
            if (!canContinue) return@setOnClickListener
            canContinue = false
            overlay.isClickable = false
            overlay.animate().alpha(0f).setDuration(800L).withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                returnToLoginAfterDeath(deceasedName, generation, deathMessage)
            }.start()
        }
    }

    private fun returnToLoginAfterDeath(deceasedName: String, generation: Int, deathMessage: String) {
        shopOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        shopOverlay = null
        isDungeonActive = false
        hasEnteredVillage = false
        nameInput.setText("")
        autoLoginCheckBox.isChecked = false
        deathNoticeText.text = "$deathMessage\n${generation}세 $deceasedName 사망 · 새 캐릭터 이름을 입력하세요."
        deathNoticeText.visibility = View.VISIBLE
        loginOverlay.visibility = View.VISIBLE
        loginOverlay.bringToFront()
        settingsController.overlay.bringToFront()
        setVillageHotspotsEnabled(false)
        awaitingHeirCreation = true
        updateBackgroundMusic()
    }

    private fun settleDungeonRun(
        acquiredItems: Map<String, Int>,
        acquiredGold: Int,
        consumedItems: Map<String, Int>,
        equippedCodes: Set<String>,
        consumeReturnStone: Boolean
    ) {
        databaseExecutor.execute {
            var settlementSucceeded = !consumeReturnStone
            val brokenEquipmentNames = mutableListOf<String>()
            val itemsToSettle = acquiredItems.toMutableMap()
            gameDatabase.runInTransaction {
                gameDatabase.dungeonRunDao().delete(currentPlayerId)
                dungeonRunPayload = null
                val dao = gameDatabase.ownedItemDao()
                consumedItems.forEach { (code, quantity) ->
                    if (quantity <= 0) return@forEach
                    val existing = dao.findItem(currentPlayerId, "INVENTORY", code) ?: return@forEach
                    val remaining = (existing.quantity - quantity).coerceAtLeast(0)
                    if (remaining == 0) dao.deleteById(existing.id) else dao.updateQuantity(existing.id, remaining)
                }
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
                dao.getForOwner(currentPlayerId)
                    .filter { it.container == "INVENTORY" && it.isIdentified }
                    .filter { ItemCatalog.category(it.itemCode) in setOf("WEAPON", "ARMOR", "HELMET", "BOOTS", "AUXILIARY", "ACCESSORY", "RELIC") }
                    .forEach { carriedItem ->
                        val nextUseCount = carriedItem.dungeonUseCount + 1
                        if (nextUseCount >= carriedItem.durability) {
                            brokenEquipmentNames += carriedItem.displayName
                            dao.deleteById(carriedItem.id)
                        } else {
                            dao.updateDungeonUseCount(carriedItem.id, nextUseCount)
                        }
                    }
                itemsToSettle.forEach { (code, quantity) ->
                    val definition = ItemCatalog.definition(code) ?: return@forEach
                    val existing = if (definition.isConsumable) dao.findItem(currentPlayerId, "INVENTORY", code) else null
                    if (existing != null) {
                        val newQuantity = existing.quantity + quantity
                        if (newQuantity > 0) dao.updateQuantity(existing.id, newQuantity) else dao.deleteById(existing.id)
                    } else if (quantity > 0) {
                        val insertCount = if (definition.isConsumable) 1 else quantity
                        repeat(insertCount) {
                            val usedSlots = dao.getForOwner(currentPlayerId).filter { it.container == "INVENTORY" }.map { it.slotIndex }.toSet()
                            val emptySlot = (0 until gameInt("inventory_capacity", 25)).firstOrNull { it !in usedSlots } ?: return@repeat
                            val unidentified = definition.grade != "NORMAL" &&
                                definition.category in setOf("WEAPON", "ARMOR", "HELMET", "BOOTS", "AUXILIARY", "ACCESSORY", "RELIC")
                            dao.insert(OwnedItemEntity(
                                ownerId = currentPlayerId,
                                itemCode = code,
                                displayName = if (unidentified) "미확인 ${ItemAppraisalRules.gradeName(definition.grade)} 장비" else definition.name.removeSuffix(" 5개"),
                                quantity = if (definition.isConsumable) quantity else 1,
                                container = "INVENTORY",
                                slotIndex = emptySlot,
                                isIdentified = !unidentified,
                                durability = ItemAppraisalRules.baseDurability(definition.grade)
                            ))
                        }
                    }
                }
                equippedCodes.groupBy { ItemCatalog.category(it) }.forEach { (category, codes) ->
                    if (category == null) return@forEach
                    dao.clearEquippedCategories(currentPlayerId, listOf(category))
                    codes.firstNotNullOfOrNull { dao.findItem(currentPlayerId, "INVENTORY", it)?.takeIf(OwnedItemEntity::isIdentified) }?.let { dao.setEquipped(it.id) }
                }
                val profileDao = gameDatabase.loginProfileDao()
                val profile = profileDao.getById(currentPlayerId)
                if (profile != null && settlementSucceeded) {
                    playerGold = profile.gold + acquiredGold
                    survivalDay = profile.survivalDay + 1
                    profileDao.save(
                        profile.copy(
                            gold = playerGold,
                            survivalDay = survivalDay
                        )
                    )
                }
                ownedItems = dao.getForOwner(currentPlayerId)
            }
            runOnUiThread {
                if (!settlementSucceeded) return@runOnUiThread
                closeShop()
                val lootMessage = if (acquiredGold > 0 || itemsToSettle.isNotEmpty()) " · 전리품 정산 완료" else ""
                val brokenMessage = if (brokenEquipmentNames.isEmpty()) "" else " · ${brokenEquipmentNames.joinToString()} 파괴"
                val returnMessage = if (consumeReturnStone) "귀환석을 사용해 마을로 돌아왔습니다" else "탐험을 마치고 마을로 돌아왔습니다"
                Toast.makeText(this, "$returnMessage · 생존 ${survivalDay}일$lootMessage$brokenMessage", Toast.LENGTH_LONG).show()
                updateTravelingMerchantVisibility()
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

    private fun itemGridHeight(rows: Int): Int {
        val slotHeight = (dp(64) * HexagonSlotView.HEX_HEIGHT_RATIO).toInt()
        return rows * (slotHeight + dp(8)) + dp(4)
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
        closeShopOverlay(overlay)
    }

    private fun closeShopOverlay(overlay: FrameLayout) {
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        if (shopOverlay === overlay) shopOverlay = null
        if (isDungeonActive) {
            isDungeonActive = false
            updateBackgroundMusic()
        }
        if (shopOverlay == null) setVillageHotspotsEnabled(true)
    }

    private fun setVillageHotspotsEnabled(enabled: Boolean) {
        generalStoreHotspot.isEnabled = enabled
        blacksmithHotspot.isEnabled = enabled
        appraisalHotspot.isEnabled = enabled
        innWarehouseHotspot.isEnabled = enabled
        manorHotspot.isEnabled = enabled
        dungeonEntranceHotspot.isEnabled = enabled
        if (::travelingMerchantHotspot.isInitialized) {
            travelingMerchantHotspot.isEnabled = enabled && travelingMerchantHotspot.visibility == View.VISIBLE
        }
    }
}
