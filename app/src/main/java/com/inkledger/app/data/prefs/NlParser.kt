package com.inkledger.app.data.nl

import kotlin.math.roundToLong

/**
 * 一句话记账解析器 v3.0——全面覆盖中文口语表达
 *
 * 金额格式支持：
 * - 数字：32 / 32.5 / ¥32 / 32元 / 32块 / 120块1 / 3.5元
 * - 中文整数：三 / 十五 / 三十五 / 一百二十 / 一千五 / 一万二 / 两块
 * - 中文小数（X块Y）：三块五=3.5 / 十块五=10.5 / 一百二十块一=120.1
 * - 中文小数（X点Y）：三十二点五=32.5 / 一百二十点一=120.1
 * - 中文毛/角：三块五毛=3.5 / 十二块三毛=12.3 / 五毛=0.5 / 三块五毛五=3.55
 * - 混合：120块1 / 32点5元
 */
data class ParsedEntry(
    val amountMinor: Long,
    val isIncome: Boolean,
    val categoryId: Long?,
    val note: String,
    val dayOffset: Int   // 0 今天 1 昨天 2 前天
)

object NlParser {

    private data class Rule(val keywords: List<String>, val categoryId: Long)

    // ══════════ 品类关键词（按日常频率排序，高频在前提升匹配准确度） ══════════
    private val expenseRules = listOf(
        // 餐饮（最高频）
        Rule(listOf(
            "早饭", "早餐", "午饭", "午餐", "晚饭", "晚餐", "宵夜", "夜宵", "下午茶",
            "吃饭", "饭", "餐", "外卖", "美团", "饿了么",
            "面", "粉", "米粉", "米线", "包子", "饺子", "馄饨", "火锅", "烧烤", "串",
            "麻辣烫", "便当", "盒饭", "食堂", "大排档", "小吃", "快餐", "汉堡", "披萨",
            "寿司", "沙拉", "轻食", "日料", "韩餐", "西餐",
            "奶茶", "咖啡", "果汁", "茶", "可乐", "雪碧", "水", "奶", "饮料", "冰",
            "甜品", "蛋糕", "冰淇淋", "雪糕", "甜甜圈", "面包", "甜甜圈",
            "零食", "薯片", "饼干", "糖果", "巧克力", "坚果", "辣条", "瓜子", "花生",
            "水果", "苹果", "香蕉", "橙子", "西瓜", "葡萄", "草莓", "芒果", "桃",
            "菜", "肉", "鱼", "虾", "蟹", "鸡", "鸭", "牛", "羊", "猪", "蛋", "豆腐"
        ), 1L),
        // 交通
        Rule(listOf(
            "打车", "出租", "的士", "滴滴", "顺风车", "代驾",
            "地铁", "公交", "巴士", "大巴", "长途", "火车", "高铁", "动车", "轻轨",
            "机票", "飞机", "船", "轮渡", "摩的",
            "加油", "油费", "充电桩", "停车", "车费", "交通",
            "共享单车", "骑行", "哈啰", "青桔", "美团单车"
        ), 2L),
        // 购物
        Rule(listOf(
            "淘宝", "京东", "拼多多", "天猫", "抖音买", "网购", "购物", "买东西",
            "超市", "便利店", "711", "全家", "罗森", "菜市场", "沃尔玛", "永辉",
            "衣服", "鞋", "裤", "外套", "T恤", "衬衫", "袜子", "帽子", "围巾", "手套",
            "包", "钱包", "手表", "首饰", "项链", "戒指", "耳环",
            "化妆品", "护肤品", "香水", "口红", "粉底", "面膜",
            "洗发水", "沐浴露", "牙膏", "牙刷", "毛巾", "纸巾", "日用",
            "洗衣液", "洗洁精", "垃圾袋", "保鲜膜", "电池", "灯泡",
            "家具", "家电", "床上用品", "枕头", "被子", "床垫"
        ), 3L),
        // 娱乐
        Rule(listOf(
            "电影", "游戏", "KTV", "唱K", "ktv", "旅游", "门票", "会员", "演出",
            "演唱会", "音乐节", "展览", "博物馆", "游乐场", "水上乐园", "滑雪", "溜冰",
            "桌游", "密室", "剧本杀", "酒吧", "清吧", "网咖", "电竞",
            "直播打赏", "追星", "视频会员", "爱奇艺", "腾讯视频", "优酷", "B站", "网易云"
        ), 4L),
        // 医疗
        Rule(listOf(
            "药", "医院", "看病", "挂号", "医疗", "体检", "牙医", "眼镜", "诊所",
            "打针", "疫苗", "保健品", "维生素", "创可贴", "感冒药", "发烧", "咳嗽",
            "体检", "体检报告"
        ), 5L),
        // 居住
        Rule(listOf(
            "房租", "水电", "水费", "电费", "物业", "燃气", "燃气费", "暖气费",
            "空调费", "宽带", "居住", "维修", "搬家"
        ), 6L),
        // 学习
        Rule(listOf(
            "书", "课程", "学费", "文具", "学习", "考试", "打印", "培训", "网课",
            "知识付费", "电子书", "教材", "参考书", "笔记本", "笔", "纸",
            "考试报名", "证书", "资格证"
        ), 7L),
        // 通讯
        Rule(listOf(
            "话费", "流量", "充话费", "月租", "套餐", "漫游", "国际长途", "短信", "通讯"
        ), 8L),
        // 运动
        Rule(listOf(
            "健身", "球", "跑步", "运动", "游泳", "瑜伽", "健身房", "私教",
            "运动装备", "跑鞋", "运动服", "瑜伽垫", "跳绳", "哑铃", "蛋白粉"
        ), 11L),
        // 宠物
        Rule(listOf(
            "猫", "狗", "宠物", "猫粮", "狗粮", "猫砂", "宠物医院",
            "驱虫", "牵引绳", "猫窝", "狗窝", "猫罐头"
        ), 12L)
    )

    private val incomeRules = listOf(
        Rule(listOf("工资", "薪水", "薪资", "工资到账"), 101L),
        Rule(listOf("红包", "压岁钱", "转账收到", "微信红包", "支付宝红包"), 102L),
        Rule(listOf("理财", "利息", "基金", "股票", "收益", "分红", "余额宝", "零钱通"), 103L),
        Rule(listOf("退款", "报销", "副业", "闲鱼", "卖出", "奖金", "绩效", "外快", "兼职"), 104L)
    )

    private val incomeHints = listOf(
        "工资", "红包", "退款", "报销", "利息", "收益", "奖金", "到账",
        "压岁钱", "闲鱼", "副业", "薪水", "薪资", "收到", "卖出", "绩效", "外快", "兼职"
    )

    // ══════════ 金额解析（分层，优先级从高到低） ══════════

    // 中文数字映射
    private val cnD = mapOf(
        '一' to 1L, '二' to 2L, '三' to 3L, '四' to 4L, '五' to 5L,
        '六' to 6L, '七' to 7L, '八' to 8L, '九' to 9L, '零' to 0L, '两' to 2L
    )
    private val cnU = mapOf('十' to 10L, '百' to 100L, '千' to 1000L, '万' to 10000L)
    private val cnCharClass = "[一二三四五六七八九十百千万零两]"

    /** 中文数字→Long。支持：三=3, 十五=15, 三十五=35, 一百二十=120, 一千五=1500, 一万二=12000, 一百五=150 */
    private fun cnToNum(s: String): Long? {
        var result = 0L; var cur = 0L; var has = false; var lastUnit = 0L; var lastWasDigit = false
        for (c in s) {
            when {
                cnD.containsKey(c) -> {
                    // 连续数字拼接：二五→25
                    cur = if (lastWasDigit) cur * 10 + cnD[c]!! else cnD[c]!!
                    has = true; lastWasDigit = true
                }
                cnU.containsKey(c) -> {
                    val u = cnU[c]!!
                    if (cur == 0L) cur = 1L
                    result += cur * u; cur = 0L; has = true; lastUnit = u; lastWasDigit = false
                }
            }
        }
        // 口语尾缀：一千五=1500 / 一百二=120（百/千后的裸数字 ×单位的十分之一）
        if (cur > 0 && (lastUnit == 100L || lastUnit == 1000L)) cur *= lastUnit / 10
        return if (has) result + cur else null
    }

    /**
     * 综合中文金额解析，返回「分」。按优先级依次尝试：
     * 1. ¥/￥ + 数字
     * 2. 数字 + 元/块（含小数）
     * 3. 数字 + 块 + 个位数（120块1 = 120.1）
     * 4. 中文 + 块 + 中文个位（一百二十块一 = 120.1）
     * 5. 中文 + 点 + 中文个位（一百二十点一 = 120.1）
     * 6. 中文 + 块 + 中文 + 毛/角 + [中文]（三块五毛五 = 3.55）
     * 7. 中文 + 元/块（十五块 = 15, 一千五 = 1500）
     * 8. 纯中文 + 毛（五毛 = 0.5）
     * 9. 纯数字（含小数）
     */
    /**
     * 综合金额解析，返回「分」。
     * 核心原则：**更精确的模式先检查**（X块Y 必须在 X块 之前），防止截断小数。
     * 同时处理语音识别把中文转为数字的情况（"三块五"→"3块5"）。
     */
    private fun parseAmount(text: String): Long? {
        // ══════ 第一梯队：带小数分隔符的（块/点/毛），必须最先检查 ══════

        // 1a. ¥ + 数字（含小数）
        Regex("""[¥￥]\s*(\d{1,9}(?:,\d{3})*(?:\.\d{1,2})?)""").find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()?.let { v -> (v * 100).roundToLong() }
        }

        // 1b. 数字+块+数字：「3块5」=3.5 /「120块1」=120.1 /「12块50」=12.50
        Regex("""(\d{1,7})\s*块\s*(\d{1,2})""").find(text)?.let {
            val int = it.groupValues[1].toLongOrNull() ?: return@let null
            val dec = it.groupValues[2].toLongOrNull() ?: return@let null
            return if (dec < 10) int * 100 + dec * 10 else int * 100 + dec
        }

        // 1c. 数字+块+中文个位：「3块五」=3.5（语音识别混合输出）
        Regex("""(\d{1,7})\s*块\s*([一二三四五六七八九])""").find(text)?.let {
            val int = it.groupValues[1].toLongOrNull() ?: return@let null
            val dec = cnD[it.groupValues[2][0]] ?: return@let null
            return int * 100 + dec * 10
        }

        // 1d. 中文+块+数字：「三块5」=3.5（不太常见但可能）
        Regex("""($cnCharClass{1,10})\s*块\s*(\d{1,2})""").find(text)?.let {
            val int = cnToNum(it.groupValues[1]) ?: return@let null
            val dec = it.groupValues[2].toLongOrNull() ?: return@let null
            return if (dec < 10) int * 100 + dec * 10 else int * 100 + dec
        }

        // 1e. 中文+块+中文个位：「三块五」=3.5 /「一百二十块一」=120.1
        Regex("""($cnCharClass{1,10})\s*块\s*([一二三四五六七八九])""").find(text)?.let {
            val intPart = cnToNum(it.groupValues[1]) ?: return@let null
            val decDigit = cnD[it.groupValues[2][0]] ?: return@let null
            return intPart * 100 + decDigit * 10
        }

        // 1f. 数字+点+数字：「32点5」=32.5（语音可能用点代替小数点）
        Regex("""(\d{1,7})\s*点\s*(\d{1,2})""").find(text)?.let {
            val int = it.groupValues[1].toLongOrNull() ?: return@let null
            val dec = it.groupValues[2].toLongOrNull() ?: return@let null
            return if (dec < 10) int * 100 + dec * 10 else int * 100 + dec
        }

        // 1g. 中文+点+中文个位：「三十二点五」=32.5 /「一百二十点一」=120.1 /「一百零一点五」=101.5
        Regex("""($cnCharClass{1,10})点([一二三四五六七八九十])""").find(text)?.let {
            val intPart = cnToNum(it.groupValues[1]) ?: return@let null
            val decStr = it.groupValues[2]
            val decVal = if (decStr == "十") 0L else cnD[decStr[0]] ?: return@let null
            return if (decStr == "十") intPart * 100 + 100 else intPart * 100 + decVal * 10
        }

        // 1h. 中文+块+中文+毛+可选中文分：「三块五毛」=3.5 /「三块五毛五」=3.55 /「十二块三毛」=12.3
        Regex("""($cnCharClass{1,10})\s*块\s*([一二三四五六七八九]?)\s*毛\s*([一二三四五六七八九]?)""").find(text)?.let {
            val intPart = cnToNum(it.groupValues[1]) ?: return@let null
            val mao = if (it.groupValues[2].isNotEmpty()) cnD[it.groupValues[2][0]] ?: 0L else 0L
            val fen = if (it.groupValues[3].isNotEmpty()) cnD[it.groupValues[3][0]] ?: 0L else 0L
            return intPart * 100 + mao * 10 + fen
        }

        // ══════ 第二梯队：纯整数（无小数部分），排除已被上面匹配的情况 ══════

        // 2a. 数字+元/块（后面不能跟数字或中文数字，防止截断）：「32元」=32 /「120块」=120
        Regex("""(\d{1,9}(?:\.\d{1,2})?)\s*[元块](?!\s*[\d一二三四五六七八九])""").find(text)?.let {
            return it.groupValues[1].toDoubleOrNull()?.let { v -> (v * 100).roundToLong() }
        }

        // 2b. 中文+元/块（后面不能跟中文数字）：「十五块」=15 /「三元」=3 /「一千五」=1500 /「两块」=2
        Regex("""($cnCharClass{1,10})\s*[元块](?!\s*[一二三四五六七八九])""").find(text)?.let {
            val cn = it.groupValues[1]
            // 「一千五」= 1500 /「一万二」= 12000：尾数字跟在大单位后→乘以单位的十分之一
            val lastDigit = cn.lastOrNull()?.let { c -> cnD[c] }
            if (lastDigit != null && lastDigit > 0 && cn.length > 1) {
                val prefix = cn.dropLast(1)
                val lastUnit = prefix.lastOrNull()?.let { c -> cnU[c] }
                if (lastUnit != null) {
                    val base = cnToNum(prefix) ?: return@let null
                    return base * 100 + lastDigit * (lastUnit / 10).coerceAtLeast(1) * 100
                }
            }
            val num = cnToNum(cn) ?: return@let null
            return num * 100
        }

        // 2c. 纯毛（无整数部分）：「五毛」=0.5 /「三毛」=0.3
        Regex("""([一二三四五六七八九])毛""").find(text)?.let {
            val v = cnD[it.groupValues[1][0]] ?: return@let null
            return v * 10
        }

        // 2d. 句尾裸中文数字：「晚饭二十五」=25 /「工资三十五」=35 /「外卖一百五」=150
        // 保护：前一个字是 月/周/第/号/每年/每年 之类的计数语境 → 不是金额；「一」「两」单字不认
        val cleanedTail = text.trimEnd('了', '哦', '呀', '啊', '哈', '的')
        Regex("""($cnCharClass{1,10})$""").find(cleanedTail)?.let {
            val run = it.groupValues[1]
            val before = cleanedTail.getOrNull(it.range.first - 1) ?: ' '
            if (before !in "月周第号每上下午" && run != "一" && run != "两") {
                cnToNum(run)?.let { v -> return v * 100 }
            }
        }

        // ══════ 第三梯队：纯数字（兜底） ══════
        // 「32.5」=32.5 /「120」=120
        Regex("""\d{1,9}(?:\.\d{1,2})?""").findAll(text).maxByOrNull { it.value.length }?.value?.let {
            return it.toDoubleOrNull()?.let { v -> (v * 100).roundToLong() }
        }

        return null
    }

    // ══════════ 主解析入口 ══════════

    fun parse(raw: String): ParsedEntry? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val amountMinor = parseAmount(text) ?: return null
        if (amountMinor <= 0) return null

        val isIncome = incomeHints.any { text.contains(it) }
        val rule = if (isIncome) {
            incomeRules.find { r -> r.keywords.any { text.contains(it) } }
        } else {
            expenseRules.find { r -> r.keywords.any { text.contains(it) } }
        }

        val dayOffset = when {
            text.contains("前天") -> 2
            text.contains("昨天") -> 1
            else -> 0
        }

        // 备注 = 去掉金额和日期词后的原文
        val note = Regex("""[¥￥]?\s*(?:\d{1,9}(?:[,.]\d{1,2})?|$cnCharClass{1,10})\s*(?:块|元|毛|角|点[一二三四五六七八九十]?)?""")
            .replace(text, "")
            .replace("昨天", "").replace("前天", "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return ParsedEntry(
            amountMinor = amountMinor,
            isIncome = isIncome,
            categoryId = rule?.categoryId,
            note = note.ifBlank { text },
            dayOffset = dayOffset
        )
    }
}
