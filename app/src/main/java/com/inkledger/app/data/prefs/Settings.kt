package com.inkledger.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemePreset {
    NIGHT_MINT, NIGHT_AMBER, NIGHT_BLUE, NIGHT_SAKURA,   // 已下架：读到自动转 LIGHT_SKY
    LIGHT_LAVENDER, LIGHT_CREAM, LIGHT_SKY, LIGHT_MATCHA,
    CITY_SHENZHEN, CITY_SHANGHAI, CITY_BEIJING, CITY_GUANGZHOU, CITY_HK, CITY_MACAU,
    CHINESE,         // 中国传统色主题：强调色由 chineseSeed 索引决定，小组件同步
    THEME_DYNAMIC,   // 已移除：历史选择回退 LIGHT_SKY
    SYSTEM
}

/** 深色与跟随系统一律回退到默认浅色（晴空蓝）；动态取色已移除，历史选择回退晴空蓝 */
fun ThemePreset.effective(): ThemePreset = when (this) {
    ThemePreset.NIGHT_MINT, ThemePreset.NIGHT_AMBER, ThemePreset.NIGHT_BLUE,
    ThemePreset.NIGHT_SAKURA, ThemePreset.SYSTEM, ThemePreset.THEME_DYNAMIC -> ThemePreset.LIGHT_SKY
    else -> this
}

data class AppSettings(
    val theme: ThemePreset = ThemePreset.LIGHT_SKY,
    val currency: String = "¥",
    val monthStartDay: Int = 1,
    val budgetMinor: Long = 0L,          // 0 = 未设置预算
    val autoRecordEnabled: Boolean = false,
    val autoConfirmMode: Boolean = true, // true = 自动入账后发确认通知（可一键忽略）
    val onboarded: Boolean = false,
    // AI：默认全部关闭，零联网；开启问答后按用户配置调用大模型
    val aiEnabled: Boolean = false,
    val aiProvider: String = "glm",      // glm / deepseek / custom
    val aiKey: String = "",
    val aiModel: String = "",
    val aiBase: String = "",
    val bgEnabled: Boolean = false,     // 自定义背景
    val bgVersion: Int = 0,             // 换背景图时 +1，触发重载
    val bgBlur: Int = 60,               // 背景高斯模糊程度 0-100（0=原图清晰），无级调节
    val bgGradient: Int = -1,           // -1=不使用渐变背景，0-11=预置渐变索引
    val celebratedGoals: Set<String> = emptySet(),  // 已庆祝过的存钱目标 id，防止重复弹
    // 灵活预算项：可配置多个专项预算（如周末聚餐、奶茶），每个独立周期+申报记录
    val flexItems: List<FlexItem> = emptyList(),
    val flexSpends: List<FlexSpend> = emptyList(),  // 申报记录 (itemId, 金额minor, 时间millis)
    val pureMode: Boolean = false,      // 纯净模式：首页只留核心信息
    val lastTipAt: Long = 0L,           // 上次展示标题文案的时间（隔三差五用）
    val chineseSeedIndex: Int = 0,      // 中国传统色主题选中索引（小组件背景同步派生）
    // 小组件背景独立设置：0=跟随主题 1=自定义图片 2=纯色
    val widgetBgMode: Int = 0,
    val widgetBgColor: Long = 0L,       // 纯色模式的 ARGB（0=未设置）
    val widgetBgBlur: Int = 60,         // 自定义图片的模糊度 0-100（毛玻璃，自己把握显示效果）
    val weeklyReportText: String = "",  // 周报文本（AI 面板展示）
    val weeklyReportEnd: Long = 0L      // 周报覆盖的周期结束日 epochDay
)

data class FlexItem(val id: Long, val name: String, val minor: Long, val period: Int)   // period: 0=每周 1=每周期
data class FlexSpend(val itemId: Long, val amountMinor: Long, val at: Long)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val currency = stringPreferencesKey("currency")
        val monthStartDay = intPreferencesKey("month_start_day")
        val budgetMinor = longPreferencesKey("budget_minor")
        val autoRecordEnabled = booleanPreferencesKey("auto_record_enabled")
        val autoConfirmMode = booleanPreferencesKey("auto_confirm_mode")
        val onboarded = booleanPreferencesKey("onboarded")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiProvider = stringPreferencesKey("ai_provider")
        val aiKey = stringPreferencesKey("ai_key")
        val aiModel = stringPreferencesKey("ai_model")
        val aiBase = stringPreferencesKey("ai_base")
        val bgEnabled = booleanPreferencesKey("bg_enabled")
        val bgVersion = intPreferencesKey("bg_version")
        val bgBlur = intPreferencesKey("bg_blur")
        val bgGradient = intPreferencesKey("bg_gradient")
        val celebratedGoals = androidx.datastore.preferences.core.stringSetPreferencesKey("celebrated_goals")
        val flexName = stringPreferencesKey("flex_name")
        val flexMinor = longPreferencesKey("flex_minor")
        val flexPeriod = intPreferencesKey("flex_period")
        val flexItems = stringPreferencesKey("flex_items")     // JSON: [{"id":1,"n":"xx","m":minor,"p":0}]
        val flexSpends = stringPreferencesKey("flex_spends")   // JSON: [{"i":itemId,"a":minor,"t":millis}]
        val pureMode = booleanPreferencesKey("pure_mode")
        val lastTipAt = longPreferencesKey("last_tip_at")
        val chineseSeed = intPreferencesKey("chinese_seed")
        val widgetBgMode = intPreferencesKey("widget_bg_mode")
        val widgetBgColor = longPreferencesKey("widget_bg_color")
        val widgetBgBlur = intPreferencesKey("widget_bg_blur")
        val weeklyReportText = stringPreferencesKey("weekly_report_text")
        val weeklyReportEnd = longPreferencesKey("weekly_report_end")
    }

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                theme = runCatching { ThemePreset.valueOf(p[Keys.theme] ?: ThemePreset.LIGHT_SKY.name) }
                    .getOrDefault(ThemePreset.LIGHT_SKY).effective(),
                currency = (p[Keys.currency] ?: "¥").ifBlank { "¥" },
                monthStartDay = (p[Keys.monthStartDay] ?: 1).coerceIn(1, 28),
                budgetMinor = p[Keys.budgetMinor] ?: 0L,
                autoRecordEnabled = p[Keys.autoRecordEnabled] ?: false,
                autoConfirmMode = p[Keys.autoConfirmMode] ?: true,
                onboarded = p[Keys.onboarded] ?: false,
                aiEnabled = p[Keys.aiEnabled] ?: false,
                aiProvider = p[Keys.aiProvider] ?: "glm",
                aiKey = p[Keys.aiKey] ?: "",
                aiModel = p[Keys.aiModel] ?: "",
                aiBase = p[Keys.aiBase] ?: "",
                bgEnabled = p[Keys.bgEnabled] ?: false,
                bgVersion = p[Keys.bgVersion] ?: 0,
                bgBlur = (p[Keys.bgBlur] ?: 60).coerceIn(0, 100),
                bgGradient = p[Keys.bgGradient] ?: -1,
                celebratedGoals = p[Keys.celebratedGoals] ?: emptySet(),
                flexItems = parseFlexItems(
                    p[Keys.flexItems] ?: "[]",
                    // 旧版单项目数据迁移：老 flexName 还有值且新列表为空 → 转成一个项目
                    legacyName = p[Keys.flexName] ?: "",
                    legacyMinor = p[Keys.flexMinor] ?: 0L,
                    legacyPeriod = p[Keys.flexPeriod] ?: 0
                ),
                flexSpends = parseFlexSpends(p[Keys.flexSpends] ?: "[]"),
                pureMode = p[Keys.pureMode] ?: false,
                lastTipAt = p[Keys.lastTipAt] ?: 0L,
                chineseSeedIndex = p[Keys.chineseSeed] ?: 0,
                widgetBgMode = p[Keys.widgetBgMode] ?: 0,
                widgetBgColor = p[Keys.widgetBgColor] ?: 0L,
                widgetBgBlur = (p[Keys.widgetBgBlur] ?: 60).coerceIn(0, 100),
                weeklyReportText = p[Keys.weeklyReportText] ?: "",
                weeklyReportEnd = p[Keys.weeklyReportEnd] ?: 0L
            )
        }

    suspend fun setTheme(v: ThemePreset) = context.dataStore.edit { it[Keys.theme] = v.name }
    suspend fun setCurrency(v: String) = context.dataStore.edit { it[Keys.currency] = v.ifBlank { "¥" } }
    suspend fun setMonthStartDay(v: Int) = context.dataStore.edit { it[Keys.monthStartDay] = v.coerceIn(1, 28) }
    suspend fun setBudgetMinor(v: Long) = context.dataStore.edit { it[Keys.budgetMinor] = v }
    suspend fun setAutoRecordEnabled(v: Boolean) = context.dataStore.edit { it[Keys.autoRecordEnabled] = v }
    suspend fun setAutoConfirmMode(v: Boolean) = context.dataStore.edit { it[Keys.autoConfirmMode] = v }
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[Keys.onboarded] = v }
    suspend fun setAiEnabled(v: Boolean) = context.dataStore.edit { it[Keys.aiEnabled] = v }
    suspend fun setAiProvider(v: String) = context.dataStore.edit { it[Keys.aiProvider] = v }
    suspend fun setAiKey(v: String) = context.dataStore.edit { it[Keys.aiKey] = v }
    suspend fun setAiModel(v: String) = context.dataStore.edit { it[Keys.aiModel] = v }
    suspend fun setAiBase(v: String) = context.dataStore.edit { it[Keys.aiBase] = v }
    suspend fun setBgEnabled(v: Boolean) = context.dataStore.edit { it[Keys.bgEnabled] = v }
    suspend fun setBgVersion(v: Int) = context.dataStore.edit { it[Keys.bgVersion] = v }
    suspend fun setBgBlur(v: Int) = context.dataStore.edit { it[Keys.bgBlur] = v.coerceIn(0, 100) }
    suspend fun setBgGradient(v: Int) = context.dataStore.edit { it[Keys.bgGradient] = v }
    suspend fun addCelebratedGoal(id: Long) = context.dataStore.edit {
        it[Keys.celebratedGoals] = (it[Keys.celebratedGoals] ?: emptySet()) + id.toString()
    }

    suspend fun setFlexItems(items: List<FlexItem>) = context.dataStore.edit {
        it[Keys.flexItems] = org.json.JSONArray().apply {
            items.forEach { f ->
                put(org.json.JSONObject().put("id", f.id).put("n", f.name)
                    .put("m", f.minor).put("p", f.period))
            }
        }.toString()
        // 兼容旧字段清空
        it[Keys.flexName] = ""
    }

    /** 申报一笔花销；顺带清理 60 天前的旧申报，防止无限膨胀 */
    suspend fun addFlexSpend(itemId: Long, minor: Long) = context.dataStore.edit {
        val cur = parseFlexSpends(it[Keys.flexSpends] ?: "[]").toMutableList()
        val cutoff = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
        cur.removeAll { it.at < cutoff }
        cur.add(FlexSpend(itemId, minor, System.currentTimeMillis()))
        it[Keys.flexSpends] = org.json.JSONArray().apply {
            cur.forEach { s ->
                put(org.json.JSONObject().put("i", s.itemId).put("a", s.amountMinor).put("t", s.at))
            }
        }.toString()
    }

    /** 删除一笔申报（填错修正用） */
    suspend fun removeFlexSpend(itemId: Long, at: Long) = context.dataStore.edit {
        val cur = parseFlexSpends(it[Keys.flexSpends] ?: "[]")
            .filterNot { s -> s.itemId == itemId && s.at == at }
        it[Keys.flexSpends] = org.json.JSONArray().apply {
            cur.forEach { s ->
                put(org.json.JSONObject().put("i", s.itemId).put("a", s.amountMinor).put("t", s.at))
            }
        }.toString()
    }

    suspend fun setPureMode(v: Boolean) = context.dataStore.edit { it[Keys.pureMode] = v }
    suspend fun setChineseSeed(v: Int) = context.dataStore.edit {
        it[Keys.chineseSeed] = v.coerceIn(0, com.inkledger.app.ui.theme.CHINESE_COLORS.lastIndex)
    }
    suspend fun setWidgetBgMode(v: Int) = context.dataStore.edit { it[Keys.widgetBgMode] = v.coerceIn(0, 2) }
    suspend fun setWidgetBgColor(v: Long) = context.dataStore.edit { it[Keys.widgetBgColor] = v }
    suspend fun setWidgetBgBlur(v: Int) = context.dataStore.edit { it[Keys.widgetBgBlur] = v.coerceIn(0, 100) }

    // 小组件选中的具体条目（按 appWidgetId 独立存储；null=自动）
    suspend fun widgetCountdownPick(appWidgetId: Int): Long? =
        context.dataStore.data.first()[stringPreferencesKey("widget_cd_$appWidgetId")]?.toLongOrNull()

    suspend fun setWidgetCountdownPick(appWidgetId: Int, cdId: Long?) = context.dataStore.edit {
        val k = stringPreferencesKey("widget_cd_$appWidgetId")
        if (cdId == null) it.remove(k) else it[k] = cdId.toString()
    }

    suspend fun widgetHabitPick(appWidgetId: Int): Long? =
        context.dataStore.data.first()[stringPreferencesKey("widget_hb_$appWidgetId")]?.toLongOrNull()

    suspend fun setWidgetHabitPick(appWidgetId: Int, hbId: Long?) = context.dataStore.edit {
        val k = stringPreferencesKey("widget_hb_$appWidgetId")
        if (hbId == null) it.remove(k) else it[k] = hbId.toString()
    }

    suspend fun markTipShown() = context.dataStore.edit {
        it[Keys.lastTipAt] = System.currentTimeMillis()
    }

    suspend fun setWeeklyReport(text: String, endEpochDay: Long) = context.dataStore.edit {
        it[Keys.weeklyReportText] = text
        it[Keys.weeklyReportEnd] = endEpochDay
    }
}

/** 解析项目列表 JSON；旧单项目数据自动升级 */
private fun parseFlexItems(json: String, legacyName: String, legacyMinor: Long, legacyPeriod: Int): List<FlexItem> {
    val parsed = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id")
            val n = o.optString("n")
            val m = o.optLong("m")
            if (id > 0 && n.isNotBlank() && m > 0) FlexItem(id, n, m, o.optInt("p", 0)) else null
        }
    } catch (e: Exception) {
        emptyList()
    }
    if (parsed.isNotEmpty()) return parsed
    // 迁移：旧版单项目
    return if (legacyName.isNotBlank() && legacyMinor > 0) {
        listOf(FlexItem(1L, legacyName, legacyMinor, legacyPeriod))
    } else emptyList()
}

/** 解析申报记录 JSON（兼容旧格式无 itemId 的记录，归到项目 1） */
private fun parseFlexSpends(json: String): List<FlexSpend> = try {
    val arr = org.json.JSONArray(json)
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val a = o.optLong("a")
        val t = o.optLong("t")
        if (a > 0 && t > 0) FlexSpend(o.optLong("i", 1L), a, t) else null
    }
} catch (e: Exception) {
    emptyList()
}
