package com.inkledger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,      // 恒为正数，方向由 type 决定
    val type: Int,              // 0 支出 1 收入
    val categoryId: Long,
    val note: String = "",
    val timestamp: Long,        // 记账归属时间
    val createdAt: Long,
    val source: Int = 0         // 0手动 1一句话 2模板 3再来一笔 4自动记账
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val emoji: String,
    val type: Int,              // 0 支出 1 收入
    val sortOrder: Int
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val categoryId: Long,
    val type: Int = 0,
    val amountMinor: Long,
    val note: String = "",
    val useCount: Int = 0,
    val createdAt: Long = 0
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int,                      // 0 存钱目标 1 习惯打卡
    val name: String,
    val emoji: String = "🎯",
    val targetAmountMinor: Long? = null, // 存钱
    val deadlineEpochDay: Long? = null,  // 存钱期限（epochDay）
    val archived: Boolean = false,
    val createdAt: Long = 0
)

@Entity(tableName = "goal_deposits")
data class GoalDepositEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountMinor: Long,      // 负数 = 取出
    val timestamp: Long
)

@Entity(tableName = "habit_checks")
data class HabitCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val epochDay: Long          // LocalDate.toEpochDay()
)

@Entity(tableName = "countdowns")
data class CountDownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "⏳",
    val targetEpochDay: Long,
    val repeatYearly: Boolean = false,
    val pinned: Boolean = false,
    val bgId: Int = 0,           // 展示页预置背景索引，-1 = 自定义图片
    val bgBlur: Int = 60,        // 自定义背景模糊度 0-100
    val createdAt: Long = 0
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0,          // 创建时间（排序用，不随编辑变）
    val updatedAt: Long,              // 最后编辑时间
    val media: String = ""    // 逗号分隔的应用内图片文件名
)

/** 回收站：删除的实体快照，7 天内可恢复 */
@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true) val binId: Long = 0,
    val type: String,        // tx / goal / countdown / note
    val payload: String,     // 原实体 JSON 快照
    val label: String,       // 列表展示：分类名或条目名
    val detail: String,      // 次要信息：金额或日期
    val deletedAt: Long
)
