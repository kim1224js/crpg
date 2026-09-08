package es.kim.crpg.ui.settings

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import es.kim.crpg.core.audio.GameAudioSettings
import es.kim.crpg.data.GameConfigEntity
import es.kim.crpg.data.GameDatabase
import es.kim.crpg.ui.common.GameUiTheme
import es.kim.crpg.ui.common.gameScrollView
import es.kim.crpg.ui.settings.guide.GameGuideController
import java.util.concurrent.Executor

class GameSettingsController(
    private val activity: Activity,
    private val database: GameDatabase,
    private val executor: Executor,
    private val onMusicSettingChanged: () -> Unit
) {
    private val guideController by lazy { GameGuideController(activity, overlay, database, executor) }
    val overlay = FrameLayout(activity).apply {
        isClickable = false
        elevation = dp(100).toFloat()
    }

    fun attach() {
        val settingsButton = TextView(activity).apply {
            text = "⚙"; contentDescription = "설정 열기"; setTextColor(Color.WHITE); textSize = 28f
            gravity = Gravity.CENTER; isClickable = true; isFocusable = true
            background = panel(0xE618120F.toInt(), GameUiTheme.GOLD, 24f, 2)
            setOnClickListener { show() }
        }
        overlay.addView(settingsButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin = dp(14); marginEnd = dp(14)
        })
        activity.addContentView(overlay, matchParentParams())
    }

    fun setLauncherVisible(visible: Boolean) {
        overlay.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun restore() {
        executor.execute {
            val dao = database.gameMasterDao()
            GameAudioSettings.setEffectsVolumePercent(dao.getConfigInt("effects_volume_percent") ?: 90)
            GameAudioSettings.setMusicEnabled((dao.getConfigInt("music_enabled") ?: 1) == 1)
            GameAudioSettings.setEffectsEnabled((dao.getConfigInt("effects_enabled") ?: 1) == 1)
            activity.runOnUiThread(onMusicSettingChanged)
        }
    }

    private fun save(key: String, value: Int) {
        executor.execute { database.gameMasterDao().saveConfig(GameConfigEntity(key, value, null, null)) }
    }

    private fun show() {
        if (overlay.findViewWithTag<View>(SETTINGS_TAG) != null) return
        val blocker = FrameLayout(activity).apply {
            tag = SETTINGS_TAG; isClickable = true; setBackgroundColor(0xB8000000.toInt())
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(22))
            background = panel(GameUiTheme.LEATHER_DARK, GameUiTheme.GOLD, 12f, 2)
        }
        content.addView(header(blocker))
        content.addView(toggle("배경음악", GameAudioSettings.musicEnabled) { enabled ->
            GameAudioSettings.setMusicEnabled(enabled); save("music_enabled", if (enabled) 1 else 0); onMusicSettingChanged()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        content.addView(toggle("효과음", GameAudioSettings.effectsEnabled) { enabled ->
            GameAudioSettings.setEffectsEnabled(enabled); save("effects_enabled", if (enabled) 1 else 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        val volumeText = TextView(activity).apply {
            setTextColor(GameUiTheme.GOLD); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        }
        val initialPercent = (GameAudioSettings.effectsVolume * 100).toInt()
        volumeText.text = "효과음 크기  $initialPercent%"
        content.addView(volumeText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        content.addView(SeekBar(activity).apply {
            max = 100; progress = initialPercent
            progressTintList = ColorStateList.valueOf(GameUiTheme.GOLD); thumbTintList = ColorStateList.valueOf(GameUiTheme.GOLD)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    GameAudioSettings.setEffectsVolumePercent(progress); volumeText.text = "효과음 크기  $progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { save("effects_volume_percent", seekBar?.progress ?: return) }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        content.addView(TextView(activity).apply {
            text = "문·구매·몬스터·무기·피격·사망 효과음에 공통 적용됩니다."; setTextColor(0xFFCCBFA8.toInt()); textSize = 14f
        })
        content.addView(TextView(activity).apply {
            text = "게임 가이드"; setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; isClickable = true; background = panel(0xFF5B2418.toInt(), GameUiTheme.GOLD, 7f, 2)
            setOnClickListener { guideController.show() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(14) })

        blocker.addView(activity.gameScrollView(content), FrameLayout.LayoutParams(
            minOf(dp(480), activity.resources.displayMetrics.widthPixels - dp(28)),
            minOf(dp(450), activity.resources.displayMetrics.heightPixels - dp(28)), Gravity.CENTER
        ))
        overlay.addView(blocker, 0, matchParentParams())
    }

    private fun header(blocker: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            text = "설정"; setTextColor(Color.WHITE); textSize = 24f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        addView(TextView(activity).apply {
            text = "×"; contentDescription = "설정 닫기"; setTextColor(Color.WHITE); textSize = 28f
            gravity = Gravity.CENTER; isClickable = true
            setOnClickListener { this@GameSettingsController.overlay.removeView(blocker) }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
    }

    private fun toggle(label: String, checked: Boolean, changed: (Boolean) -> Unit) = Switch(activity).apply {
        text = label; isChecked = checked; setTextColor(Color.WHITE); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL; thumbTintList = ColorStateList.valueOf(GameUiTheme.GOLD)
        trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0x99725322.toInt(), 0x664D443B))
        setOnCheckedChangeListener { _, enabled -> changed(enabled) }
    }

    private fun panel(fill: Int, stroke: Int, radius: Float, width: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(width), stroke)
    }
    private fun matchParentParams() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * activity.resources.displayMetrics.density).toInt()

    companion object { private const val SETTINGS_TAG = "settings_page" }
}
