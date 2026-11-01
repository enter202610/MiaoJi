package com.inkledger.app

import com.inkledger.app.data.nl.NlParser
import com.inkledger.app.ui.quickentry.VoiceRoute
import com.inkledger.app.ui.quickentry.routeVoice
import com.inkledger.app.ui.quickentry.routeVoicePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长按＋独立语音页 · 功能与压力测试（纯 JVM，不需要设备）。
 * 覆盖：五类路由的功能用例 + 边界/超长/对抗输入的压力用例。
 */
class VoiceRouteTest {

    // ---------- 功能：记账（有金额） ----------

    @Test fun `支出金额_中文数字`() {
        val tx = routeVoice("早饭三块五") as VoiceRoute.Tx
        assertEquals(350L, tx.parsed.amountMinor)
    }

    @Test fun `支出金额_阿拉伯数字`() {
        val tx = routeVoice("打车23块") as VoiceRoute.Tx
        assertEquals(2300L, tx.parsed.amountMinor)
    }

    @Test fun `支出金额_小数点`() {
        val tx = routeVoice("午饭32.5") as VoiceRoute.Tx
        assertEquals(3250L, tx.parsed.amountMinor)
    }

    @Test fun `收入关键词`() {
        val tx = routeVoice("收红包五百块") as VoiceRoute.Tx
        assertTrue(tx.parsed.isIncome)
        assertEquals(50000L, tx.parsed.amountMinor)
    }

    @Test fun `大额金额`() {
        val tx = routeVoice("交房租三千五百块") as VoiceRoute.Tx
        assertEquals(350000L, tx.parsed.amountMinor)
    }

    // ---------- 功能：存钱目标 ----------

    @Test fun `存钱目标_存钱词`() {
        val g = routeVoice("存钱买相机") as VoiceRoute.SavingsGoal
        assertEquals("相机", g.name)   // v1 清洗规则会把「买」一并去掉
    }

    @Test fun `存钱目标_攒钱词`() {
        assertTrue(routeVoice("攒钱去旅行") is VoiceRoute.SavingsGoal)
    }

    @Test fun `存钱目标_目标词`() {
        assertTrue(routeVoice("目标买电脑") is VoiceRoute.SavingsGoal)
    }

    // ---------- 功能：打卡习惯 ----------

    @Test fun `打卡_每天跑步`() {
        val h = routeVoice("每天打卡跑步") as VoiceRoute.Habit
        assertEquals("跑步", h.name)
    }

    @Test fun `打卡_习惯词`() {
        assertTrue(routeVoice("习惯不喝奶茶") is VoiceRoute.Habit)
    }

    // ---------- 功能：倒数日 ----------

    @Test fun `倒数_纯关键词`() {
        val c = routeVoice("倒数考研") as VoiceRoute.Countdown
        assertEquals("考研", c.name)
    }

    @Test fun `倒数_还有N天_共享路由原样保持面板行为`() {
        // 共享 routeVoice 保持面板原行为：数字被金额解析抢走 → Tx（面板不可动）
        assertTrue(routeVoice("距离考试还有100天") is VoiceRoute.Tx)
    }

    @Test fun `倒数_生日`() {
        assertTrue(routeVoice("妈妈生日") is VoiceRoute.Countdown)
    }

    // ---------- 功能：记事 ----------

    @Test fun `纯文字_记事`() {
        val n = routeVoice("今天天气不错") as VoiceRoute.Note
        assertEquals("今天天气不错", n.text)
    }

    @Test fun `普通句子_记事`() {
        assertTrue(routeVoice("明天要交房租") is VoiceRoute.Note)
    }

    // ---------- 压力：边界与对抗输入（不应抛异常，路由应合理） ----------

    @Test fun `压力_空字符串`() {
        assertTrue(routeVoice("") is VoiceRoute.Unknown)
    }

    @Test fun `压力_纯空白`() {
        assertTrue(routeVoice("   \n\t  ") is VoiceRoute.Unknown)
    }

    @Test fun `压力_超长文本不崩溃`() {
        val long = "记录".repeat(5000)  // 10000 字符
        assertTrue(routeVoice(long) is VoiceRoute.Note)
    }

    @Test fun `压力_超长数字串`() {
        val r = routeVoice("花了" + "9".repeat(30))
        assertTrue(r is VoiceRoute.Tx)
    }

    @Test fun `压力_纯符号`() {
        assertNotNull(routeVoice("。，！？@@%%"))
    }

    @Test fun `压力_表情与生僻字`() {
        assertNotNull(routeVoice("😀🎉 buy 东西 𠀀𠀁"))
    }

    @Test fun `压力_只有数字`() {
        val tx = routeVoice("100") as VoiceRoute.Tx
        assertEquals(10000L, tx.parsed.amountMinor)
    }

    @Test fun `压力_负数与乱序`() {
        assertNotNull(routeVoice("-50 乱七八糟 abc123"))
    }

    @Test fun `压力_目标词带金额仍记账`() {
        val tx = routeVoice("存钱二百块") as VoiceRoute.Tx
        assertEquals(20000L, tx.parsed.amountMinor)
    }

    @Test fun `压力_千次随机混合不崩溃`() {
        val pieces = listOf("早饭", "打车", "存钱", "打卡", "倒数", "生日", "记一下", "35", "三块五", " ", "，", "😀")
        val rnd = java.util.Random(42)
        repeat(1000) {
            val sb = StringBuilder()
            repeat(rnd.nextInt(12)) { sb.append(pieces[rnd.nextInt(pieces.size)]) }
            assertNotNull("routeVoice 不应返回 null：$sb", routeVoice(sb.toString()))
        }
    }

    // ---------- 语音页专用预路由（routeVoicePage，仅新页面使用，不影响面板） ----------

    @Test fun `页面路由_带数字天数不被当成金额`() {
        val c = routeVoicePage("距离考试还有100天") as VoiceRoute.Countdown
        assertEquals("考试", c.name)
        assertEquals(100L, c.daysHint)
    }

    @Test fun `页面路由_中文天数`() {
        val c = routeVoicePage("还有一百天") as VoiceRoute.Countdown
        assertEquals(100L, c.daysHint)
    }

    @Test fun `页面路由_倒数词`() {
        val c = routeVoicePage("倒数考研") as VoiceRoute.Countdown
        assertEquals("考研", c.name)
    }

    // ---------- 全场景：裸中文数字归一化（晚饭二十五等无单位说法） ----------

    @Test fun `全场景_晚饭二十五`() {
        val tx = routeVoicePage("晚饭二十五") as VoiceRoute.Tx
        assertEquals(2500L, tx.parsed.amountMinor)
    }

    @Test fun `全场景_晚饭二十五块`() {
        val tx = routeVoicePage("晚饭二十五块") as VoiceRoute.Tx
        assertEquals(2500L, tx.parsed.amountMinor)
    }

    @Test fun `全场景_花了五百`() {
        val tx = routeVoicePage("花了五百") as VoiceRoute.Tx
        assertEquals(50000L, tx.parsed.amountMinor)
    }

    @Test fun `全场景_一百五`() {
        val tx = routeVoicePage("外卖一百五") as VoiceRoute.Tx
        assertEquals(15000L, tx.parsed.amountMinor)
    }

    @Test fun `全场景_一千五`() {
        val tx = routeVoicePage("手机一千五") as VoiceRoute.Tx
        assertEquals(150000L, tx.parsed.amountMinor)
    }

    @Test fun `全场景_两百`() {
        val tx = routeVoicePage("打车两百") as VoiceRoute.Tx
        assertEquals(20000L, tx.parsed.amountMinor)
    }

    @Test fun `全场景_二五连读`() {
        val tx = routeVoicePage("奶茶二五") as VoiceRoute.Tx
        assertEquals(2500L, tx.parsed.amountMinor)
    }

    @Test fun `全场景_周五聚餐不误伤`() {
        // 单字「五」不转换，周五聚餐仍是记事
        assertTrue(routeVoicePage("周五聚餐") is VoiceRoute.Note)
    }

    @Test fun `全场景_五十个人不误伤`() {
        // 后跟量词「个」的不转换
        assertTrue(routeVoicePage("记一下来了五十个人") is VoiceRoute.Note)
    }

    @Test fun `全场景_存钱二百保持目标意图`() {
        // 无单位裸数字 + 存钱关键词：保持面板原语义 → 存钱目标
        assertTrue(routeVoicePage("存钱二百") is VoiceRoute.SavingsGoal)
    }

    @Test fun `页面路由_其余说法透传通用路由`() {
        assertTrue(routeVoicePage("早饭三块五") is VoiceRoute.Tx)
        assertTrue(routeVoicePage("存钱买相机") is VoiceRoute.SavingsGoal)
        assertTrue(routeVoicePage("每天打卡跑步") is VoiceRoute.Habit)
    }

    // ---------- NlParser 直接验证（金额解析核心） ----------

    @Test fun `解析_三块五`() {
        assertEquals(350L, NlParser.parse("三块五")?.amountMinor)
    }

    @Test fun `解析_一百二十块一`() {
        assertEquals(12010L, NlParser.parse("一百二十块一")?.amountMinor)
    }

    @Test fun `解析_一千五`() {
        // 设计升级：句尾裸中文数字现在解析（晚饭二十五等场景），一千五=1500
        assertEquals(150000L, NlParser.parse("一千五")?.amountMinor)
    }

    @Test fun `解析_无金额返回null`() {
        assertNull(NlParser.parse("今天天气不错"))
    }

    @Test fun `解析_空串安全`() {
        assertNull(NlParser.parse(""))
    }
}
