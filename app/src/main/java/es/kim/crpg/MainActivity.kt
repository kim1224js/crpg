package es.kim.crpg

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
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
import es.kim.crpg.ui.common.antiqueButton
import es.kim.crpg.ui.common.antiquePanel
import es.kim.crpg.ui.common.dp
import es.kim.crpg.ui.common.itemGridHeight
import es.kim.crpg.ui.common.itemImage
import es.kim.crpg.ui.common.itemQuantityLayoutParams
import es.kim.crpg.ui.common.matchParentParams
import es.kim.crpg.ui.common.quantityBadge
import es.kim.crpg.ui.common.sectionTitle
import es.kim.crpg.ui.dungeon.DungeonDemoView
import es.kim.crpg.ui.inventory.EquipmentOptionDialog
import es.kim.crpg.ui.inventory.HexagonSlotView
import es.kim.crpg.ui.inventory.ItemGridView
import es.kim.crpg.ui.settings.GameSettingsController
import es.kim.crpg.ui.login.LoginScreen
import es.kim.crpg.ui.village.VillageMapOverlay
import es.kim.crpg.ui.facility.FacilityUiController
import es.kim.crpg.ui.story.TimedStoryOverlay
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.min
import kotlin.random.Random

class MainActivity : GameActivity() {
    private data class PendingDeath(
        val playerId: Long,
        val reachedFloor: Int,
        val survivedTurns: Int,
        val killerCode: String?,
        val killerName: String?,
        val diedAt: Long
    )

    private data class DeathSettlement(
        val deceasedName: String,
        val generation: Int,
        val deathCauseLabel: String,
        val deathMessage: String
    )

    private lateinit var loginScreen: LoginScreen
    private val loginOverlay get() = loginScreen
    private val nameInput get() = loginScreen.nameInput
    private val autoLoginCheckBox get() = loginScreen.autoLoginCheckBox
    private val deathNoticeText get() = loginScreen.deathNoticeText
    private lateinit var villageMapOverlay: VillageMapOverlay
    private lateinit var facilityUiController: FacilityUiController
    private val villageInteractionOverlay get() = villageMapOverlay
    private val travelingMerchantHotspot get() = villageMapOverlay.travelingMerchant
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
    private var currentCharacterId = 1L
    private var playerGold = 20
    private var survivalDay = 1
    private var highestFloor = 1
    private var unlockedDungeonStartFloor = 1
    private var storageCapacity = 20
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
    private var pendingStoredItemMoveId: Long? = null

    companion object {
        private const val APP_STATE_PREFERENCES = "crpg_app_state"
        private const val PENDING_DEATH_KEY = "pending_death"
        private const val COLOR_LEATHER = GameUiTheme.LEATHER
        private const val COLOR_LEATHER_DARK = GameUiTheme.LEATHER_DARK
        private const val COLOR_GOLD = GameUiTheme.GOLD
        private const val COLOR_GOLD_DARK = GameUiTheme.GOLD_DARK
        private val EQUIPMENT_CATEGORIES = setOf("WEAPON", "HELMET", "ARMOR", "BOOTS", "CLOAK", "AUXILIARY", "ACCESSORY", "RELIC")

        init {
            System.loadLibrary("crpg")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.BLACK)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        createVillageInteractionOverlay()
        createLoginOverlay()
        settingsController = GameSettingsController(this, gameDatabase, databaseExecutor, ::updateBackgroundMusic)
        settingsController.attach()
        settingsController.setLauncherVisible(false)
        musicPlayer = GameMusicPlayer(this)
        villageSoundPlayer = VillageSoundPlayer(this)
        settingsController.restore()
        startInitialFlow()
        hideSystemUi()
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
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
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
        loginScreen = LoginScreen(this, ::saveLoginAndEnterVillage)
        addContentView(
            loginScreen,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setVillageHotspotsEnabled(false)
    }

    private fun saveLoginAndEnterVillage() {
        val playerName = nameInput.text.toString().trim()
        if (playerName.isEmpty()) {
            loginScreen.focusNameInput()
            return
        }

        val autoLogin = true
        databaseExecutor.execute {
            val creatingHeir = awaitingHeirCreation
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
            val savedGold = savedProfile?.gold ?: gameInt("starting_gold", 20)
            val activeCharacterId = if (creatingHeir) {
                maxOf(System.currentTimeMillis(), (savedProfile?.activeCharacterId ?: accountId) + 1L)
            } else savedProfile?.activeCharacterId?.takeIf { it > 0L } ?: accountId
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
                unlockedDungeonStartFloor = savedProfile?.unlockedDungeonStartFloor ?: 1,
                pendingEstateLossCount = savedProfile?.pendingEstateLossCount ?: 0,
                pendingEstateKeptNames = savedProfile?.pendingEstateKeptNames,
                lastMerchantFreeDay = savedProfile?.lastMerchantFreeDay ?: 0,
                merchantFreeClaimMask = savedProfile?.merchantFreeClaimMask ?: 0,
                storageCapacity = savedProfile?.storageCapacity ?: gameInt("storage_capacity", 20),
                activeCharacterId = activeCharacterId
            )
            gameDatabase.loginProfileDao().clearAutoLogin()
            gameDatabase.loginProfileDao().save(profile)
            if (creatingHeir) {
                gameDatabase.ownedItemDao().inheritAllStorage(profile.id, profile.activeCharacterId)
            }
            currentPlayerId = profile.id
            currentCharacterId = profile.activeCharacterId
            playerGold = profile.gold
            survivalDay = profile.survivalDay
            highestFloor = profile.highestFloor
            unlockedDungeonStartFloor = profile.unlockedDungeonStartFloor
            storageCapacity = profile.storageCapacity
            introSeen = profile.introSeen

            if (isNewProfile) {
                gameDatabase.ownedItemDao().insert(
                    OwnedItemEntity(
                        ownerId = profile.id,
                        characterId = profile.activeCharacterId,
                        itemCode = "return_stone",
                        displayName = "귀환석",
                        quantity = gameInt("initial_return_stones", 5),
                        container = "INVENTORY",
                        slotIndex = 0
                    )
                )
            }
            if (isNewProfile || creatingHeir) {
                val itemDao = gameDatabase.ownedItemDao()
                val usedStorageSlots = itemDao.getForOwner(profile.id)
                    .filter { it.container == "STORAGE" }
                    .map { it.slotIndex }
                    .toSet()
                val storageCapacity = gameInt("storage_capacity", 20)
                val welcomeSlot = (0 until storageCapacity).firstOrNull { it !in usedStorageSlots }
                val usedInventorySlots = itemDao.getForOwner(profile.id)
                    .filter { it.container == "INVENTORY" }
                    .map { it.slotIndex }
                    .toSet()
                val inventoryCapacity = gameInt("inventory_capacity", 25)
                val inventoryWelcomeSlot = (0 until inventoryCapacity).firstOrNull { it !in usedInventorySlots }
                val welcomeContainer = if (welcomeSlot != null) "STORAGE" else "INVENTORY"
                val targetWelcomeSlot = welcomeSlot ?: inventoryWelcomeSlot
                if (targetWelcomeSlot != null) {
                    itemDao.insert(
                        OwnedItemEntity(
                            ownerId = profile.id,
                            characterId = profile.activeCharacterId,
                            itemCode = "welcome_weapon_box",
                            displayName = "웰컴팩 무기 상자",
                            quantity = 1,
                            container = welcomeContainer,
                            slotIndex = targetWelcomeSlot,
                            isSellable = false
                        )
                    )
                }
            }
            ownedItems = currentCharacterItems(gameDatabase.ownedItemDao().getForOwner(profile.id))
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
            currentCharacterId = profile.activeCharacterId
            playerGold = profile.gold
            survivalDay = profile.survivalDay
            highestFloor = profile.highestFloor
            unlockedDungeonStartFloor = profile.unlockedDungeonStartFloor
            storageCapacity = profile.storageCapacity
            introSeen = profile.introSeen
            ownedItems = currentCharacterItems(gameDatabase.ownedItemDao().getForOwner(profile.id))
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

    private fun currentStorageCapacity(): Int = storageCapacity.coerceAtLeast(gameInt("storage_capacity", 20))

    private fun currentCharacterItems(items: List<OwnedItemEntity>): List<OwnedItemEntity> =
        items.filter { it.characterId == currentCharacterId && it.container != "ESTATE" }

    private fun ensureEquippedWeapon() {
        val equipped = ownedItems.firstOrNull { it.isEquipped && ItemCatalog.isWeapon(it.itemCode) }
            ?: ownedItems.firstOrNull { it.container == "INVENTORY" && ItemCatalog.isWeapon(it.itemCode) }
        dungeonEquippedWeaponCode = equipped?.itemCode
        if (equipped != null && !equipped.isEquipped) {
            gameDatabase.runInTransaction {
                gameDatabase.ownedItemDao().clearEquippedCategories(currentPlayerId, listOf("WEAPON"))
                gameDatabase.ownedItemDao().setEquipped(equipped.id)
            }
            ownedItems = currentCharacterItems(gameDatabase.ownedItemDao().getForOwner(currentPlayerId))
        }
    }

    private fun enterVillage() {
        if (hasEnteredVillage) return
        hasEnteredVillage = true
        updateVillageTerritoryTitle()
        hideLoginKeyboard()
        loginOverlay.visibility = View.GONE
        deathNoticeText.visibility = View.GONE
        settingsController.setLauncherVisible(true)
        setVillageHotspotsEnabled(true)
        updateTravelingMerchantVisibility()
        updateRedMoonVisibility()
        hideSystemUi()
        if (!dungeonRunPayload.isNullOrBlank()) {
            villageInteractionOverlay.postDelayed({ showSavedDungeonRunDialog() }, 350L)
        } else {
            villageInteractionOverlay.postDelayed({ showPendingEstateNotice() }, 350L)
        }
    }

    private fun hideLoginKeyboard() {
        nameInput.clearFocus()
        loginOverlay.requestFocus()
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        window.decorView.postDelayed({
            inputMethodManager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }, 120L)
    }

    private fun updateVillageTerritoryTitle() {
        val savedName = nameInput.text.toString().trim().ifBlank { "이름 없는 모험가" }
        val inheritedName = Regex("^(.+?)\\s+(\\d+)세$").matchEntire(savedName)
        val nickname = inheritedName?.groupValues?.get(1)?.trim().orEmpty().ifBlank { savedName }
        val generation = inheritedName?.groupValues?.get(2)?.toIntOrNull() ?: 1
        villageMapOverlay.setTerritoryOwner(nickname, generation)
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
                        title = "선대의 유산이 도착했습니다",
                        subtitle = "죽음은 창고까지 온전히 비켜가지 않았다",
                        body = "선대가 남긴 물품이 저택에 보관되어 있습니다.\n저택에서 수령하면 현재 캐릭터의 창고에 귀속됩니다.\n\n$keptNames",
                        warning = "창고 물품 ${profile.pendingEstateLossCount}개가 소멸했습니다. 살아남은 최대 5개 슬롯만 유산으로 남았습니다.",
                        actions = listOf(AntiqueGameDialog.Action("저택으로", primary = true) { showManorEntrance() }),
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
        val preferences = getSharedPreferences(APP_STATE_PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean("opening_prologue_completed", false)) {
            recoverPendingDeath(thenRestoreAutoLogin = true)
            return
        }
        loginOverlay.visibility = View.INVISIBLE
        showOpeningPrologue {
            preferences.edit().putBoolean("opening_prologue_completed", true).apply()
            loginOverlay.animate().cancel()
            loginOverlay.alpha = 1f
            loginOverlay.visibility = View.VISIBLE
            loginOverlay.requestFocus()
        }
    }

    private fun showOpeningPrologue(onFinished: () -> Unit) {
        val lines = listOf(
            "모든 것은 부모님과 함께 살던 낡은 집에서 시작되었다.",
            "어머니는 원인을 알 수 없는 병에 걸려 날이 갈수록 쇠약해졌다.",
            "병의 원인을 찾겠다며 집을 나선 아버지는 끝내 돌아오지 않았다.",
            "두 사람의 흔적을 쫓던 당신은 집 깊숙한 곳에서 봉인된 지하 입구를 발견했다.",
            "문을 열자 썩은 냄새와 함께 기괴할 만큼 거대한 쥐가 어둠 속에서 기어 나왔다.",
            "간신히 문을 닫은 당신은 부모님에게 일어난 일의 답이 저 아래에 있음을 깨달았다.",
            "이제 장비를 마련하고 지하로 내려가 진실을 마주하자."
        )
        TimedStoryOverlay.show(this, TimedStoryOverlay.Config(
            title = "핏빛 문 아래",
            lines = lines,
            prompt = "화면을 터치하여 시작",
            style = TimedStoryOverlay.Style.PROLOGUE,
            onFinished = onFinished
        ))
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
        villageMapOverlay = VillageMapOverlay(
            context = this,
            onGeneralStore = ::openGeneralStore,
            onBlacksmith = ::openBlacksmith,
            onAppraisal = {
                openFacility("ui/village/building_appraisal_house.png", 0.68f, 0f) { showAppraisalOffice() }
            },
            onInnWarehouse = {
                openFacility("ui/village/building_inn_warehouse.png", 0f, 0.52f) { showInnWarehouse() }
            },
            onManor = {
                openFacility("ui/village/building_manor_dungeon.png", 0.35f, 0f) { showManorEntrance() }
            },
            onDungeonEntrance = {
                openFacility("ui/village/building_manor_dungeon.png", 0.51f, 0.08f) { showDungeonLoadout() }
            },
            onTravelingMerchant = ::showTravelingMerchant
        )
        addContentView(
            villageMapOverlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        facilityUiController = FacilityUiController(this, villageMapOverlay)
    }

    private fun openGeneralStore() {
        openStore("ui/village/building_general_store.png", 0.68f, 0.52f, false)
    }

    private fun openBlacksmith() {
        openStore("ui/village/building_blacksmith.png", 0f, 0f, true)
    }

    private fun updateTravelingMerchantVisibility() {
        if (!::villageMapOverlay.isInitialized) return
        val interval = gameInt("traveling_merchant_interval_days", 5).coerceAtLeast(1)
        travelingMerchantHotspot.visibility = if (hasEnteredVillage && survivalDay > 0 && survivalDay % interval == 0) View.VISIBLE else View.GONE
        travelingMerchantHotspot.isEnabled = travelingMerchantHotspot.visibility == View.VISIBLE && shopOverlay == null
    }

    private fun isRedMoonActive(): Boolean {
        val interval = gameInt("red_moon_interval_days", 10).coerceAtLeast(1)
        return survivalDay > 0 && survivalDay % interval == 0
    }

    private fun updateRedMoonVisibility() {
        if (::villageMapOverlay.isInitialized) villageMapOverlay.setRedMoonActive(hasEnteredVillage && isRedMoonActive())
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
        val content = createFacilityContent("떠돌이 상자 상인", "ui/village/village_map.png", scrollContent = false)
        shopOverlay?.contentDescription = "traveling_merchant"
        content.addView(TextView(this).apply {
            text = "생존 ${survivalDay}일차 · 5일마다 마을 중앙을 찾는 수상한 행상인입니다."
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
            ownedItems = currentCharacterItems(result.items)
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
        facilityUiController.playEntrance(assetPath, originX, originY, .28f, .48f) {
            showShopInterface(isBlacksmith)
        }
    }

    private fun openFacility(assetPath: String, originX: Float, originY: Float, onOpened: () -> Unit) {
        if (shopOverlay != null) return
        villageSoundPlayer?.playDoorOpen()
        setVillageHotspotsEnabled(false)
        facilityUiController.playEntrance(assetPath, originX, originY, onOpened = onOpened)
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
        leftColumn.addView(sortableItemHeader("내 아이템  $inventoryCount / ${gameInt("inventory_capacity", 25)}", "INVENTORY"))
        leftColumn.addView(createInventoryGrid(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            itemGridHeight(5)
        ))
        val storageCapacity = currentStorageCapacity()
        val storageRows = (storageCapacity + 4) / 5
        leftColumn.addView(sortableItemHeader("창고  $storageCount / $storageCapacity", "STORAGE"))
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
                val generalPrice = if (item.code in setOf("gacha_normal", "gacha_high")) {
                    item.price * gameInt("general_store_gacha_price_multiplier", 2)
                } else item.price
                storeList.addView(storeItemRow(item.code, item.name, generalPrice, item.unitsPerPurchase, item.assetPath, item.detail))
            }
        }

        addContentView(overlay, matchParentParams())
    }

    private fun createInventoryGrid(): ItemGridView = createCommonItemGrid("INVENTORY", 5, 5)

    private fun createWarehouseGrid(): ItemGridView {
        val capacity = currentStorageCapacity()
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
            assetPath = { item -> assetPathFor(item.itemCode) },
            gradeColor = ItemCatalog::gradeColor,
            isConsumable = ItemCatalog::isConsumable,
            movingItemId = pendingStoredItemMoveId,
            onSlotClick = { item, targetContainer, targetSlot ->
                val movingId = pendingStoredItemMoveId
                if (movingId != null) {
                    pendingStoredItemMoveId = null
                    moveStoredItem(movingId, targetContainer, targetSlot)
                } else if (item != null) {
                    val definition = ItemCatalog.definition(item.itemCode)
                    if (definition?.specialEffect?.startsWith("GACHA_BOX_") == true) {
                        showGachaOpeningScene(item)
                    } else if (definition?.isConsumable == true) {
                        showOwnedConsumableInfo(item)
                    } else showOwnedEquipmentInfo(item)
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
            var rewardItem: OwnedItemEntity? = null
            gameDatabase.runInTransaction {
                val dao = gameDatabase.ownedItemDao()
                val current = dao.getForOwner(currentPlayerId).firstOrNull { it.id == box.id } ?: return@runInTransaction
                val boxDefinition = ItemCatalog.definition(current.itemCode) ?: return@runInTransaction
                val isWelcomeWeaponBox = boxDefinition.specialEffect == "GACHA_BOX_WELCOME_WEAPON"
                val won = isWelcomeWeaponBox || kotlin.random.Random.nextInt(100) < 30
                val wantsEquipment = isWelcomeWeaponBox || (won && kotlin.random.Random.nextBoolean())
                val rewardDefinition = if (wantsEquipment) {
                    if (isWelcomeWeaponBox) {
                        val rareOrHigher = setOf("RARE", "EPIC", "UNIQUE", "LEGENDARY", "MYTHIC")
                        ItemCatalog.allDefinitions
                            .filter { it.category == "WEAPON" && it.grade in rareOrHigher }
                            .randomOrNull()
                    } else {
                        ItemCatalog.allDefinitions.filter { !it.isConsumable && it.grade == boxDefinition.grade }.randomOrNull()
                    }
                } else null
                val usedSlots = dao.getForOwner(currentPlayerId).filter { it.container == current.container }.map { it.slotIndex }.toSet()
                val capacity = if (current.container == "STORAGE") currentStorageCapacity() else gameInt("inventory_capacity", 25)
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
                            characterId = currentCharacterId,
                            displayName = reward.name,
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
                ownedItems = currentCharacterItems(dao.getForOwner(currentPlayerId))
                if (canGrantEquipment) {
                    rewardItem = ownedItems.firstOrNull {
                        it.container == current.container && it.slotIndex == rewardSlot && it.itemCode == rewardDefinition?.code
                    }
                }
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
                    rewardItem?.let(::showOwnedEquipmentInfo)
                }
            }
        }
    }

    private fun showOwnedEquipmentInfo(item: OwnedItemEntity) {
        val definition = ItemCatalog.definition(item.itemCode) ?: return
        val option = ItemCatalog.equipmentOption(item.itemCode) ?: return
        EquipmentOptionDialog(this).show(
            item = item.copy(displayName = definition.name),
            option = option,
            grade = definition.grade,
            baseAttackPower = definition.attackPower,
            baseDurability = ItemAppraisalRules.baseDurability(definition.grade),
            appraisedAttackPower = item.appraisedAttackPower,
            salePrice = ItemCatalog.salePrice(item.itemCode),
            onMove = { beginStoredItemMove(item) },
            onSell = { confirmSellEquipment(item) }
        )
    }

    private fun sortableItemHeader(title: String, container: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(sectionTitle(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(antiqueButton("정렬", dp(70), dp(36)).apply {
            contentDescription = "$title 정렬"
            setOnClickListener { sortStoredItems(container) }
        })
    }

    private fun sortStoredItems(container: String) {
        pendingStoredItemMoveId = null
        databaseExecutor.execute {
            val result = inventoryRepository.sortContainer(currentPlayerId, container)
            ownedItems = currentCharacterItems(result.items)
            runOnUiThread {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                refreshCurrentItemScreen()
            }
        }
    }

    private fun beginStoredItemMove(item: OwnedItemEntity) {
        pendingStoredItemMoveId = item.id
        Toast.makeText(this, "이동할 칸을 눌러주세요.", Toast.LENGTH_SHORT).show()
        refreshCurrentItemScreen()
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
                            ownedItems = currentCharacterItems(result.items)
                            result.gold?.let { playerGold = it }
                            ensureEquippedWeapon()
                            runOnUiThread {
                                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                                refreshCurrentItemScreen()
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
            ownedItems = currentCharacterItems(result.items)
            result.gold?.let { playerGold = it }
            runOnUiThread {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                if (result.gold != null) villageSoundPlayer?.playPurchase()
                val storeType = ItemCatalog.definition(itemCode)?.storeType.orEmpty()
                if ("MERCHANT" in storeType && onFinished != null) onFinished()
                else refreshShopInterface(storeType == "BLACKSMITH")
            }
        }
    }

    private fun refreshShopInterface(isBlacksmith: Boolean) {
        val overlay = shopOverlay ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        shopOverlay = null
        showShopInterface(isBlacksmith)
    }

    private fun createFacilityContent(title: String, backgroundAsset: String, scrollContent: Boolean = true): LinearLayout {
        shopOverlay?.let { existing -> (existing.parent as? ViewGroup)?.removeView(existing) }
        val screen = facilityUiController.createScreen(title, backgroundAsset, playerGold, scrollContent, ::closeShopOverlay)
        shopOverlay = screen.overlay
        return screen.content
    }

    private fun showAppraisalOffice() {
        val content = createFacilityContent("감정소", "ui/village/building_appraisal_house.png", scrollContent = false)
        shopOverlay?.contentDescription = "appraisal_office"
        content.addView(TextView(this).apply {
            text = "고급 이상 장비도 이름과 기본 옵션을 확인하고 즉시 사용할 수 있습니다. 감정에 성공하면 능력치와 수명이 변동되며, 실패해도 장비는 유지됩니다."
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
            addView(sectionTitle("등급별 기본 성공 확률"))
        }
        appraisalRules.forEach { rule ->
            rates.addView(appraisalRateRow(ItemAppraisalRules.gradeName(rule.grade), "장비 가치", "${(rule.successRate * 100).toInt()}%"))
        }
        body.addView(gameScrollView(rates), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.46f).apply { marginEnd = dp(14) })
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
                    addView(ImageView(this@MainActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        contentDescription = "${definition.name} 이미지"
                        setImageBitmap(assets.open(definition.assetPath).use(BitmapFactory::decodeStream))
                        background = antiquePanel(0xCC211711.toInt(), ItemCatalog.gradeColor(definition.code), 6f, 1)
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                    }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(8) })
                    addView(TextView(this@MainActivity).apply {
                        text = "${definition.name} · 미감정 ${ItemAppraisalRules.gradeName(definition.grade)}\n가치·감정료 ${definition.basePrice}G · 성공 ${(rule.successRate * 100).toInt()}%"
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
                ownedItems = currentCharacterItems(gameDatabase.ownedItemDao().getForOwner(currentPlayerId))
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
        val content = createFacilityContent("여관 · 창고", "ui/village/building_inn_warehouse.png", scrollContent = false)
        shopOverlay?.contentDescription = "inn_warehouse"
        content.addView(TextView(this).apply {
            text = "아이템을 길게 눌러 원하는 칸으로 옮기세요. 인벤토리와 창고 사이로도 이동할 수 있습니다."
            setTextColor(0xFFD8C7A3.toInt()); textSize = 15f; setPadding(dp(8), 0, dp(8), dp(8))
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val inventoryItems = ownedItems.count { it.container == "INVENTORY" }
        val storageItems = ownedItems.count { it.container == "STORAGE" }
        val storageCapacity = currentStorageCapacity()
        val storageRows = (storageCapacity + 4) / 5
        val inventoryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sortableItemHeader("인벤토리  $inventoryItems / ${gameInt("inventory_capacity", 25)}", "INVENTORY"))
            addView(gameScrollView(createTransferGrid("INVENTORY", 5, 5, gameInt("inventory_capacity", 25)), fillViewport = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val storagePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            background = antiquePanel(COLOR_LEATHER_DARK, COLOR_GOLD_DARK, 10f, 1)
            addView(sortableItemHeader("보호 창고  $storageItems / $storageCapacity", "STORAGE"))
            addView(gameScrollView(createTransferGrid("STORAGE", storageRows, 5, storageCapacity), fillViewport = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        body.addView(inventoryPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(12) })
        body.addView(storagePanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun createTransferGrid(container: String, rows: Int, columns: Int, capacity: Int): ItemGridView =
        createCommonItemGrid(container, rows, columns, capacity)

    private fun moveStoredItem(itemId: Long, targetContainer: String, targetSlot: Int) {
        val changingDungeonLoadout = shopOverlay?.contentDescription == "dungeon_loadout"
        databaseExecutor.execute {
            val result = inventoryRepository.moveToSlot(currentPlayerId, itemId, targetContainer, targetSlot)
            ownedItems = currentCharacterItems(result.items)
            var equippedMessage: String? = null
            if (changingDungeonLoadout && targetContainer == "INVENTORY") {
                val movedItem = ownedItems.firstOrNull { it.id == itemId }
                val category = movedItem?.takeIf { !ItemCatalog.isConsumable(it.itemCode) }
                    ?.let { ItemCatalog.category(it.itemCode) }
                if (movedItem != null && category in EQUIPMENT_CATEGORIES) {
                    val dao = gameDatabase.ownedItemDao()
                    gameDatabase.runInTransaction {
                        dao.clearEquippedCategories(currentPlayerId, listOf(category!!))
                        dao.setEquipped(movedItem.id)
                    }
                    ownedItems = currentCharacterItems(dao.getForOwner(currentPlayerId))
                    if (category == "WEAPON") dungeonEquippedWeaponCode = movedItem.itemCode
                    equippedMessage = "${movedItem.displayName}으로 장착 장비를 변경했습니다."
                }
            }
            runOnUiThread {
                Toast.makeText(this, equippedMessage ?: result.message, Toast.LENGTH_SHORT).show()
                refreshCurrentItemScreen()
            }
        }
    }

    private fun refreshCurrentItemScreen() {
        val overlay = shopOverlay ?: return
        val screen = overlay.contentDescription?.toString()
        val reopenScreen: (() -> Unit) = when (screen) {
            "inn_warehouse" -> ::showInnWarehouse
            "blacksmith_store" -> { { showShopInterface(true) } }
            "general_store" -> { { showShopInterface(false) } }
            "dungeon_loadout" -> ::showDungeonLoadout
            "appraisal_office" -> ::showAppraisalOffice
            "traveling_merchant" -> ::showTravelingMerchant
            else -> return
        }
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        shopOverlay = null
        reopenScreen()
    }

    private fun transferStoredItem(item: OwnedItemEntity) {
        databaseExecutor.execute {
            val result = inventoryRepository.transfer(currentPlayerId, item)
            ownedItems = currentCharacterItems(result.items)
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
            val estateItems = gameDatabase.ownedItemDao().getForOwner(currentPlayerId).filter { it.container == "ESTATE" }
            runOnUiThread { showManorWithDeceasedList(deceased, profile, estateItems) }
        }
    }

    private fun showManorWithDeceasedList(
        deceased: List<DeceasedCharacterEntity>,
        profile: LoginProfileEntity?,
        estateItems: List<OwnedItemEntity>
    ) {
        val content = createFacilityContent("저택", "ui/village/building_manor_dungeon.png", scrollContent = false)
        content.addView(TextView(this).apply {
            text = "생존 ${profile?.survivalDay ?: survivalDay}일 · 가문의 기록과 재산은 다음 세대로 계승됩니다."
            setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        if (estateItems.isNotEmpty()) {
            content.addView(facilityChoicePanel(
                title = "선대의 유산 도착",
                description = estateItems.joinToString("\n") { "${it.displayName}${if (it.quantity > 1) " ×${it.quantity}" else ""}" },
                buttonText = "유산을 창고로",
                message = null,
                action = { claimEstateItems() }
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)).apply {
                setMargins(dp(18), 0, dp(18), dp(12))
            })
        }
        val currentDay = profile?.survivalDay ?: survivalDay
        val lastSearchDay = profile?.lastManorSearchDay ?: 0
        val manorInterval = gameInt("manor_search_interval_days", 5).coerceAtLeast(1)
        val availableSearchDay = ((lastSearchDay / manorInterval) + 1) * manorInterval
        val canSearch = currentDay >= availableSearchDay
        content.addView(facilityChoicePanel(
            title = if (canSearch) "저택 수색 가능" else "다음 수색까지 ${availableSearchDay - currentDay}일",
            description = if (canSearch) {
                "$availableSearchDay 일차 수색이 해금되었습니다. 저택에 남은 흔적을 조사합니다."
            } else {
                "저택은 ${manorInterval}일마다 다시 수색할 수 있습니다."
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
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
    }

    private fun claimEstateItems() {
        val ownerId = currentPlayerId
        val characterId = currentCharacterId
        databaseExecutor.execute {
            val dao = gameDatabase.ownedItemDao()
            gameDatabase.runInTransaction {
                val allItems = dao.getForOwner(ownerId)
                val usedSlots = allItems.filter { it.container == "STORAGE" }.map { it.slotIndex }.toMutableSet()
                allItems.filter { it.container == "ESTATE" }.forEach { item ->
                    val slot = (0 until currentStorageCapacity()).firstOrNull { it !in usedSlots } ?: return@forEach
                    dao.claimEstateItem(item.id, characterId, slot)
                    usedSlots += slot
                }
                gameDatabase.loginProfileDao().clearEstateNotice(ownerId)
            }
            ownedItems = currentCharacterItems(dao.getForOwner(ownerId))
            runOnUiThread { showManorEntrance() }
        }
    }

    private fun searchManor(searchDay: Int) {
        val stories = listOf(
            "어머니의 방에서 약초 냄새가 밴 낡은 비상금 주머니를 찾았다.",
            "아버지의 서재에서 지하 통로를 표시한 찢어진 지도와 봉인된 동전을 발견했다.",
            "무너진 벽난로의 재를 걷어내자 검게 그을린 철제 상자가 드러났다.",
            "다락방의 뒤틀린 마룻장을 들어 올리자 누군가 급히 감춘 가죽 주머니가 나타났다.",
            "정원의 말라 죽은 나무 아래에서 아버지의 표식이 새겨진 작은 함을 팠다.",
            "지하 입구로 이어지는 복도에서 쥐가 갉아낸 벽 틈과 피 묻은 편지를 찾아냈다."
        )
        databaseExecutor.execute {
            var story: String? = null
            var grantedGold = 0
            gameDatabase.runInTransaction {
                val dao = gameDatabase.loginProfileDao()
                val profile = dao.getById(currentPlayerId) ?: return@runInTransaction
                val interval = gameInt("manor_search_interval_days", 5).coerceAtLeast(1)
                val expectedDay = ((profile.lastManorSearchDay / interval) + 1) * interval
                if (searchDay != expectedDay || profile.survivalDay < searchDay) return@runInTransaction
                val searchCount = searchDay / interval
                val groupSize = gameInt("manor_reward_group_size", 4).coerceAtLeast(1)
                val rewardTier = ((searchCount - 1) / groupSize).coerceAtLeast(0) + 1
                val minimumStep = gameInt("manor_reward_min_step", 10).coerceAtLeast(0)
                val maximumStep = gameInt("manor_reward_max_step", 50).coerceAtLeast(minimumStep)
                val minimum = minimumStep * rewardTier
                val maximum = maximumStep * rewardTier
                val reward = Random.nextInt(minimum, maximum + 1)
                grantedGold = reward
                playerGold = profile.gold + reward
                survivalDay = profile.survivalDay
                dao.save(profile.copy(gold = playerGold, lastManorSearchDay = searchDay))
                story = stories[(searchCount - 1) % stories.size]
            }
            val resultStory = story ?: return@execute
            runOnUiThread {
                AntiqueGameDialog.show(
                    this,
                    AntiqueGameDialog.Config(
                        title = "$searchDay 일차 · 저택 수색",
                        subtitle = "어둠 속에 묻혀 있던 흔적",
                        body = "$resultStory\n\n${grantedGold}G를 발견했습니다.",
                        warning = "현재 보유 골드: ${playerGold}G",
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
        val content = createFacilityContent("", "ui/village/building_manor_dungeon.png", scrollContent = false)
        shopOverlay?.contentDescription = "dungeon_loadout"
        val carriedWeapons = ownedItems
            .filter { it.container == "INVENTORY" && ItemCatalog.isWeapon(it.itemCode) }
            .sortedBy { it.slotIndex }
        dungeonEquippedWeaponCode = carriedWeapons.firstOrNull { it.isEquipped }?.itemCode
            ?: dungeonEquippedWeaponCode?.takeIf { code -> carriedWeapons.any { it.itemCode == code } }
            ?: carriedWeapons.firstOrNull()?.itemCode
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(antiqueButton(if (unlockedDungeonStartFloor >= 10) "던전 입장" else "지하 1층 입장", dp(170), dp(46)).apply {
            setOnClickListener { showDungeonEntryConfirmation() }
        })
        content.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sortableItemHeader("가져갈 아이템", "INVENTORY"))
            addView(gameScrollView(createInventoryGrid(), fillViewport = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
            item.container == "INVENTORY" && !ItemCatalog.isConsumable(item.itemCode)
        }
        fun selected(category: String): OwnedItemEntity? = inventoryEquipment.firstOrNull {
            it.isEquipped && ItemCatalog.category(it.itemCode) == category
        } ?: inventoryEquipment.firstOrNull { ItemCatalog.category(it.itemCode) == category }
        return listOf(
            "무기" to inventoryEquipment.firstOrNull { it.itemCode == dungeonEquippedWeaponCode },
            "투구" to selected("HELMET"),
            "갑옷" to selected("ARMOR"),
            "신발" to selected("BOOTS"),
            "망토" to selected("CLOAK"),
            "보조" to selected("AUXILIARY"),
            "장신구" to selected("ACCESSORY"),
            "유물" to selected("RELIC")
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
        addView(FrameLayout(this@MainActivity).apply {
            background = antiquePanel(
                0xD91B1510.toInt(),
                item?.let { ItemCatalog.gradeColor(it.itemCode) } ?: 0xFF514634.toInt(),
                6f,
                if (item == null) 1 else 2
            )
            if (item != null) {
                addView(ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(assets.open(assetPathFor(item.itemCode)).use(BitmapFactory::decodeStream))
                    contentDescription = item.displayName
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        addView(TextView(this@MainActivity).apply {
            text = if (item == null || definition == null) {
                "비어 있음"
            } else {
                val remaining = (item.durability - item.dungeonUseCount).coerceAtLeast(0)
                "${definition.name}${if (!item.isIdentified) " · 미감정" else ""} · ${ItemAppraisalRules.gradeName(definition.grade)}\n${definition.detail ?: "추가 옵션 없음"} · 수명 ${remaining}회"
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
                        item = item.copy(displayName = definition?.name ?: item.displayName),
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
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply { bottomMargin = dp(6) }
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
        val actions = mutableListOf(AntiqueGameDialog.Action("마을에 남기"))
        actions += AntiqueGameDialog.Action("지하 1층 입장", primary = unlockedDungeonStartFloor < 10) { showDungeonDemo(1) }
        if (unlockedDungeonStartFloor >= 10) {
            actions += AntiqueGameDialog.Action("지하 10층 입장", primary = true) { showDungeonDemo(10) }
        }
        AntiqueGameDialog.show(
            this,
            AntiqueGameDialog.Config(
                title = "지하 원정 서약",
                subtitle = if (unlockedDungeonStartFloor >= 10) "해금된 시작 지점을 선택할 수 있습니다" else "모든 탐험은 지하 1층에서 시작됩니다",
                body = "착용 무기\n  $equippedName\n\n가져갈 소지품\n$itemSummary",
                warning = "사망하면 가져간 장비와 소지품을 모두 잃고 골드는 시작 자금으로 초기화됩니다. 창고 물품만 상속 대상입니다.",
                actions = actions,
                actionsAboveBody = true,
                scrollHint = "↕ 소지품 목록을 위아래로 움직여 확인"
            )
        )
    }

    private fun showDungeonDemo(startFloor: Int = 1) {
        val old = shopOverlay
        (old?.parent as? ViewGroup)?.removeView(old)

        isDungeonActive = true
        currentDungeonFloor = startFloor
        updateBackgroundMusic()
        val carriedInventory = ownedItems.filter { it.container == "INVENTORY" }
        val equippedHealthBonus = EQUIPMENT_CATEGORIES.sumOf { category ->
            val categoryItems = carriedInventory.filter { ItemCatalog.category(it.itemCode) == category }
            val selected = categoryItems.firstOrNull { it.isEquipped } ?: categoryItems.firstOrNull()
            selected?.let { ItemCatalog.definition(it.itemCode)?.healthBonus } ?: 0
        }
        val appraisedAttackByCode = carriedInventory.mapNotNull { item -> item.appraisedAttackPower?.let { item.itemCode to it } }.toMap()
        val overlay = FrameLayout(this)
        shopOverlay = overlay
        overlay.addView(
            DungeonDemoView(
                this,
                ownedItems.filter { it.container == "INVENTORY" }
                    .groupBy { it.itemCode }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } },
                equippedWeaponCode = dungeonEquippedWeaponCode,
                equippedItemCodes = ownedItems.filter { it.container == "INVENTORY" && it.isEquipped }.map { it.itemCode }.toSet(),
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
                redMoonActive = isRedMoonActive(),
                redMoonMonsterAttackPercent = gameInt("red_moon_monster_attack_percent", 150),
                redMoonDropRatePercent = gameInt("red_moon_drop_rate_percent", 200),
                redMoonReturnFloorInterval = gameInt("red_moon_return_floor_interval", 5),
                equipmentDropPercent = gameInt("expanded_weapon_drop_percent", 8),
                initialFloor = startFloor,
                dungeonChestSpawnPercent = gameInt("dungeon_chest_spawn_percent", 25),
                dungeonChestMimicPercent = gameInt("dungeon_chest_mimic_percent", 25),
                fireBombDurationTurns = gameInt("fire_bomb_duration_turns", 3),
                fireBombRelicBonusTurns = gameInt("fire_bomb_relic_bonus_turns", 1),
                acidDurationTurns = gameInt("acid_duration_turns", 3),
                acidDamagePerTurn = gameInt("acid_damage_per_turn", 3),
                springBottleFillCount = gameInt("spring_bottle_fill_count", 1),
                statueBottleFillCount = gameInt("statue_bottle_fill_count", 2),
                uniqueArmorDamageThreshold = gameInt("unique_armor_damage_threshold", 10),
                uniqueArmorDamageReductionPercent = gameInt("unique_armor_damage_reduction_percent", 50),
                baseVisionRange = gameInt("base_vision", 5),
                torchVisionRange = gameInt("torch_vision", 7),
                savedRunPayload = dungeonRunPayload,
                villageGold = playerGold,
                playerBaseHp = gameInt("base_player_hp", 10) + equippedHealthBonus,
                survivalDay = survivalDay,
                inventoryCapacity = gameInt("inventory_capacity", 25),
                onUseReturnStone = { acquiredItems, acquiredGold, consumedItems, equippedCodes, wornEquipmentCodes ->
                    useReturnStoneFromDungeon(acquiredItems, acquiredGold, consumedItems, equippedCodes, wornEquipmentCodes)
                },
                onExitDungeon = { acquiredItems, acquiredGold, consumedItems, equippedCodes, wornEquipmentCodes ->
                    exitDungeonSafely(acquiredItems, acquiredGold, consumedItems, equippedCodes, wornEquipmentCodes)
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
                onFloorCleared = { floor ->
                    if (floor >= 10 && unlockedDungeonStartFloor < 10) {
                        unlockedDungeonStartFloor = 10
                        val ownerId = currentPlayerId
                        databaseExecutor.execute { gameDatabase.loginProfileDao().updateUnlockedDungeonStartFloor(ownerId, 10) }
                    }
                },
                onGameCleared = { acquiredItems, acquiredGold, consumedItems, equippedCodes, wornEquipmentCodes ->
                    settleDungeonRun(
                        acquiredItems, acquiredGold, consumedItems, equippedCodes,
                        wornEquipmentCodes,
                        consumeReturnStone = false,
                        completion = ::showGameClearEnding
                    )
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
        equippedCodes: Set<String>,
        wornEquipmentCodes: Set<String>
    ) {
        settleDungeonRun(acquiredItems, acquiredGold, consumedItems, equippedCodes, wornEquipmentCodes, consumeReturnStone = true)
    }

    private fun exitDungeonSafely(
        acquiredItems: Map<String, Int>,
        acquiredGold: Int,
        consumedItems: Map<String, Int>,
        equippedCodes: Set<String>,
        wornEquipmentCodes: Set<String>
    ) {
        settleDungeonRun(acquiredItems, acquiredGold, consumedItems, equippedCodes, wornEquipmentCodes, consumeReturnStone = false)
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
            ownedItems = currentCharacterItems(dao.getForOwner(currentPlayerId))
            if (category == "WEAPON") dungeonEquippedWeaponCode = code
        }
    }

    private fun handlePlayerDeath(
        reachedFloor: Int,
        survivedTurns: Int,
        killerCode: String?,
        killerName: String?
    ) {
        val pendingDeath = PendingDeath(
            playerId = currentPlayerId,
            reachedFloor = reachedFloor,
            survivedTurns = survivedTurns,
            killerCode = killerCode,
            killerName = killerName,
            diedAt = System.currentTimeMillis()
        )
        persistPendingDeath(pendingDeath)
        databaseExecutor.execute {
            val settlement = settlePlayerDeath(pendingDeath)
            clearPendingDeath(pendingDeath.diedAt)
            currentPlayerId = pendingDeath.playerId
            dungeonRunPayload = null
            survivalDay = 1
            highestFloor = 1
            dungeonEquippedWeaponCode = null
            runOnUiThread {
                showGameOver(
                    settlement.deceasedName,
                    settlement.generation,
                    settlement.deathCauseLabel,
                    settlement.deathMessage
                )
            }
        }
    }

    private fun settlePlayerDeath(pending: PendingDeath): DeathSettlement {
        val deceasedDao = gameDatabase.deceasedCharacterDao()
        deceasedDao.getByDiedAt(pending.diedAt)?.let { existing ->
            return DeathSettlement(
                existing.playerName,
                existing.generation,
                DeathNarratives.causeLabel(pending.killerCode, pending.killerName),
                DeathNarratives.forMonster(pending.killerCode, pending.killerName)
            )
        }
        val profile = gameDatabase.loginProfileDao().getById(pending.playerId)
        val deceasedName = profile?.playerName ?: "이름 없는 모험가"
        val recordedHighestFloor = maxOf(pending.reachedFloor, profile?.highestFloor ?: 1)
        val generation = deceasedDao.count() + 1
        val resetGold = gameDatabase.gameMasterDao().getConfigInt("starting_gold") ?: 20
        gameDatabase.runInTransaction {
            val itemDao = gameDatabase.ownedItemDao()
            gameDatabase.dungeonRunDao().delete(pending.playerId)
            deceasedDao.insert(
                DeceasedCharacterEntity(
                    playerName = deceasedName,
                    generation = generation,
                    reachedFloor = recordedHighestFloor,
                    survivedTurns = pending.survivedTurns,
                    diedAt = pending.diedAt
                )
            )
            itemDao.deleteContainer(pending.playerId, "INVENTORY")
            profile?.let {
                gameDatabase.loginProfileDao().save(
                    it.copy(
                        autoLogin = false,
                        lastLoginAt = pending.diedAt,
                        gold = resetGold,
                        survivalDay = 1,
                        lastManorSearchDay = 0,
                        highestFloor = 1,
                        pendingEstateLossCount = 0,
                        pendingEstateKeptNames = null
                    )
                )
            }
        }
        playerGold = resetGold
        ownedItems = currentCharacterItems(gameDatabase.ownedItemDao().getForOwner(pending.playerId))
        return DeathSettlement(
            deceasedName,
            generation,
            DeathNarratives.causeLabel(pending.killerCode, pending.killerName),
            DeathNarratives.forMonster(pending.killerCode, pending.killerName)
        )
    }

    private fun persistPendingDeath(pending: PendingDeath) {
        val payload = JSONObject()
            .put("playerId", pending.playerId)
            .put("reachedFloor", pending.reachedFloor)
            .put("survivedTurns", pending.survivedTurns)
            .put("killerCode", pending.killerCode ?: JSONObject.NULL)
            .put("killerName", pending.killerName ?: JSONObject.NULL)
            .put("diedAt", pending.diedAt)
            .toString()
        getSharedPreferences(APP_STATE_PREFERENCES, MODE_PRIVATE)
            .edit().putString(PENDING_DEATH_KEY, payload).commit()
    }

    private fun clearPendingDeath(diedAt: Long) {
        val preferences = getSharedPreferences(APP_STATE_PREFERENCES, MODE_PRIVATE)
        val stored = parsePendingDeath(preferences.getString(PENDING_DEATH_KEY, null))
        if (stored?.diedAt == diedAt) preferences.edit().remove(PENDING_DEATH_KEY).commit()
    }

    private fun parsePendingDeath(payload: String?): PendingDeath? = runCatching {
        if (payload.isNullOrBlank()) return null
        val json = JSONObject(payload)
        PendingDeath(
            playerId = json.getLong("playerId"),
            reachedFloor = json.getInt("reachedFloor"),
            survivedTurns = json.getInt("survivedTurns"),
            killerCode = json.optString("killerCode").takeIf { it.isNotBlank() && it != "null" },
            killerName = json.optString("killerName").takeIf { it.isNotBlank() && it != "null" },
            diedAt = json.getLong("diedAt")
        )
    }.getOrNull()

    private fun recoverPendingDeath(thenRestoreAutoLogin: Boolean) {
        val pending = parsePendingDeath(
            getSharedPreferences(APP_STATE_PREFERENCES, MODE_PRIVATE).getString(PENDING_DEATH_KEY, null)
        )
        if (pending == null) {
            if (thenRestoreAutoLogin) restoreAutoLogin()
            return
        }
        databaseExecutor.execute {
            val settlement = settlePlayerDeath(pending)
            clearPendingDeath(pending.diedAt)
            currentPlayerId = pending.playerId
            dungeonRunPayload = null
            survivalDay = 1
            highestFloor = 1
            awaitingHeirCreation = true
            runOnUiThread {
                autoLoginCheckBox.isChecked = false
                prefillHeirName(settlement.deceasedName, settlement.generation)
                deathNoticeText.text = "사망 원인 · ${settlement.deathCauseLabel}\n${settlement.deathMessage}\n${settlement.generation}세 ${settlement.deceasedName} 사망 · 새 캐릭터 이름을 입력하세요."
                deathNoticeText.visibility = View.VISIBLE
                loginOverlay.visibility = View.VISIBLE
                loginOverlay.bringToFront()
                settingsController.setLauncherVisible(false)
                setVillageHotspotsEnabled(false)
            }
        }
    }

    private fun showGameOver(
        deceasedName: String,
        generation: Int,
        deathCauseLabel: String,
        deathMessage: String
    ) {
        val messages = listOf(
            "사망 원인 · $deathCauseLabel",
            deathMessage,
            "${generation}세 · $deceasedName",
            "차가운 지하에서 생을 마감했다.",
            "던전에 가져간 장비와 소지품은 어둠 속에 남겨졌다.",
            "골드는 시작 자금으로 돌아가며, 창고의 모든 재산과 가문의 기록은 다음 세대로 이어진다."
        )
        TimedStoryOverlay.show(this, TimedStoryOverlay.Config(
            title = "GAME OVER",
            lines = messages,
            prompt = "화면을 터치하여 새로운 캐릭터 만들기",
            style = TimedStoryOverlay.Style.GAME_OVER,
            onFinished = { returnToLoginAfterDeath(deceasedName, generation, deathCauseLabel, deathMessage) }
        ))
    }

    private fun returnToLoginAfterDeath(
        deceasedName: String,
        generation: Int,
        deathCauseLabel: String,
        deathMessage: String
    ) {
        shopOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        shopOverlay = null
        isDungeonActive = false
        hasEnteredVillage = false
        prefillHeirName(deceasedName, generation)
        autoLoginCheckBox.isChecked = false
        deathNoticeText.text = "사망 원인 · $deathCauseLabel\n$deathMessage\n${generation}세 $deceasedName 사망 · 새 캐릭터 이름을 입력하세요."
        deathNoticeText.visibility = View.VISIBLE
        loginOverlay.visibility = View.VISIBLE
        loginOverlay.bringToFront()
        settingsController.setLauncherVisible(false)
        settingsController.overlay.bringToFront()
        setVillageHotspotsEnabled(false)
        awaitingHeirCreation = true
        updateBackgroundMusic()
    }

    private fun showOwnedConsumableInfo(item: OwnedItemEntity) {
        val definition = ItemCatalog.definition(item.itemCode) ?: return
        AntiqueGameDialog.show(
            this,
            AntiqueGameDialog.Config(
                title = definition.name,
                subtitle = "소모품 · ${ItemAppraisalRules.gradeName(definition.grade)}",
                body = "보유 수량  ${item.quantity}개\n\n${definition.detail ?: definition.specialEffect ?: "추가 효과 없음"}",
                actions = listOf(
                    AntiqueGameDialog.Action("닫기"),
                    AntiqueGameDialog.Action("이동", primary = true) { beginStoredItemMove(item) }
                ),
                bodyHeightDp = 150
            )
        )
    }

    private fun prefillHeirName(deceasedName: String, deceasedGeneration: Int) {
        val familyName = deceasedName.replace(Regex("\\s+\\d+세$"), "").trim()
            .ifBlank { "이름 없는 모험가" }
        val heirName = "$familyName ${deceasedGeneration + 1}세"
        nameInput.setText(heirName)
        nameInput.setSelection(heirName.length)
    }

    private fun settleDungeonRun(
        acquiredItems: Map<String, Int>,
        acquiredGold: Int,
        consumedItems: Map<String, Int>,
        equippedCodes: Set<String>,
        wornEquipmentCodes: Set<String>,
        consumeReturnStone: Boolean,
        completion: (() -> Unit)? = null
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
                    .filter { it.container == "INVENTORY" }
                    .filter { it.itemCode in wornEquipmentCodes }
                    .forEach { carriedItem ->
                        val nextUseCount = carriedItem.dungeonUseCount + 1
                        if (nextUseCount >= carriedItem.durability) {
                            brokenEquipmentNames += carriedItem.displayName
                            dao.deleteById(carriedItem.id)
                        } else {
                            dao.updateDungeonUseCount(carriedItem.id, nextUseCount)
                        }
                    }
                itemsToSettle.entries.sortedByDescending { entry ->
                    when (ItemCatalog.grade(entry.key)) {
                        "MYTHIC" -> 6; "LEGENDARY" -> 5; "UNIQUE" -> 4; "EPIC" -> 3
                        "RARE" -> 2; "HIGH" -> 1; else -> 0
                    }
                }.forEach { (code, quantity) ->
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
                                definition.category in setOf("WEAPON", "ARMOR", "HELMET", "BOOTS", "CLOAK", "AUXILIARY", "ACCESSORY", "RELIC")
                            dao.insert(OwnedItemEntity(
                                ownerId = currentPlayerId,
                                characterId = currentCharacterId,
                                itemCode = code,
                                displayName = definition.name.removeSuffix(" 5개"),
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
                    codes.firstNotNullOfOrNull { dao.findItem(currentPlayerId, "INVENTORY", it) }?.let { dao.setEquipped(it.id) }
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
                ownedItems = currentCharacterItems(dao.getForOwner(currentPlayerId))
            }
            runOnUiThread {
                if (!settlementSucceeded) return@runOnUiThread
                closeShop()
                val lootMessage = if (acquiredGold > 0 || itemsToSettle.isNotEmpty()) " · 전리품 정산 완료" else ""
                val brokenMessage = if (brokenEquipmentNames.isEmpty()) "" else " · ${brokenEquipmentNames.joinToString()} 파괴"
                val returnMessage = if (consumeReturnStone) "귀환석을 사용해 마을로 돌아왔습니다" else "탐험을 마치고 마을로 돌아왔습니다"
                if (completion == null) {
                    Toast.makeText(this, "$returnMessage · 생존 ${survivalDay}일$lootMessage$brokenMessage", Toast.LENGTH_LONG).show()
                }
                updateTravelingMerchantVisibility()
                updateRedMoonVisibility()
                completion?.invoke()
            }
        }
    }

    private fun showGameClearEnding() {
        databaseExecutor.execute {
            gameDatabase.loginProfileDao().expandStorageCapacity(currentPlayerId, 100)
            storageCapacity = 100
            runOnUiThread { showGameClearStory() }
        }
    }

    private fun showGameClearStory() {
        TimedStoryOverlay.show(this, TimedStoryOverlay.Config(
            title = "잊혀진 혈통",
            lines = listOf(
                "잊혀진 영주의 신화 갑주가 갈라지고 오래된 왕관이 바닥에 떨어졌다.",
                "그 얼굴은 낯선 군주가 아니라, 당신의 피 속에 남아 있던 최초의 조상이었다.",
                "아버지는 저주받은 창을 놓았고 어머니를 잠식하던 병도 검은 안개와 함께 걷혔다.",
                "가문은 힘을 얻기 위해 지하의 왕좌와 계약했고, 대대로 그 값을 치르고 있었다.",
                "당신은 왕좌를 부수고 끝없이 이어지던 탐사의 굴레를 마침내 끊었다.",
                "왕릉에서 회수한 공간의 성흔이 가문의 창고를 100칸으로 확장했다.",
                "GAME CLEAR"
            ),
            prompt = "화면을 터치하여 마을로 돌아가기",
            style = TimedStoryOverlay.Style.PROLOGUE,
            onFinished = ::returnToVillageAfterClear
        ))
    }

    private fun returnToVillageAfterClear() {
        isDungeonActive = false
        hasEnteredVillage = true
        deathNoticeText.visibility = View.GONE
        loginOverlay.visibility = View.GONE
        setVillageHotspotsEnabled(true)
        updateTravelingMerchantVisibility()
        updateRedMoonVisibility()
        settingsController.overlay.bringToFront()
        updateBackgroundMusic()
    }

    private fun closeShop() {
        val overlay = shopOverlay ?: return
        closeShopOverlay(overlay)
    }

    private fun closeShopOverlay(overlay: FrameLayout) {
        pendingStoredItemMoveId = null
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        if (shopOverlay === overlay) shopOverlay = null
        if (isDungeonActive) {
            isDungeonActive = false
            updateBackgroundMusic()
        }
        if (shopOverlay == null) setVillageHotspotsEnabled(true)
    }

    private fun setVillageHotspotsEnabled(enabled: Boolean) {
        villageMapOverlay.setFacilityInteractionEnabled(enabled)
    }
}
