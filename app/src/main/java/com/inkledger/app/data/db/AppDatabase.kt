package com.inkledger.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SeedData {
    val categories = listOf(
        CategoryEntity(1, "餐饮", "🍜", 0, 1),
        CategoryEntity(2, "交通", "🚌", 0, 2),
        CategoryEntity(3, "购物", "🛍️", 0, 3),
        CategoryEntity(4, "娱乐", "🎮", 0, 4),
        CategoryEntity(5, "医疗", "💊", 0, 5),
        CategoryEntity(6, "居住", "🏠", 0, 6),
        CategoryEntity(7, "学习", "📚", 0, 7),
        CategoryEntity(8, "通讯", "📱", 0, 8),
        CategoryEntity(9, "服饰", "👔", 0, 9),
        CategoryEntity(10, "日用", "🧻", 0, 10),
        CategoryEntity(11, "运动", "⚽", 0, 11),
        CategoryEntity(12, "宠物", "🐱", 0, 12),
        CategoryEntity(13, "其他", "📦", 0, 13),
        CategoryEntity(101, "工资", "💰", 1, 1),
        CategoryEntity(102, "红包", "🧧", 1, 2),
        CategoryEntity(103, "理财", "📈", 1, 3),
        CategoryEntity(104, "其他收入", "✨", 1, 4)
    )
}

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        TemplateEntity::class,
        GoalEntity::class,
        GoalDepositEntity::class,
        HabitCheckEntity::class,
        CountDownEntity::class,
        NoteEntity::class,
        RecycleBinEntity::class
    ],
        version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txDao(): TxDao
    abstract fun categoryDao(): CategoryDao
    abstract fun templateDao(): TemplateDao
    abstract fun goalDao(): GoalDao
    abstract fun countDownDao(): CountDownDao
    abstract fun noteDao(): NoteDao
    abstract fun recycleBinDao(): RecycleBinDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN media TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE countdowns ADD COLUMN bgId INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE countdowns ADD COLUMN bgBlur INTEGER NOT NULL DEFAULT 60")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE notes SET createdAt = updatedAt WHERE createdAt = 0")
            }
        }

        // v6: 修复 v5 迁移中 createdAt 用 id*1000 导致的 1970 年日期
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE notes SET createdAt = updatedAt WHERE createdAt < 1000000000000")
            }
        }

        // v7: 回收站
        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recycle_bin (" +
                        "binId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "payload TEXT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "detail TEXT NOT NULL, " +
                        "deletedAt INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "inkledger.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // 种子分类用固定 SQL 写入，避免在建库回调里二次获取实例
                        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                            SeedData.categories.forEach { c ->
                                db.execSQL(
                                    "INSERT OR IGNORE INTO categories(id, name, emoji, type, sortOrder) " +
                                        "VALUES(${c.id}, '${c.name}', '${c.emoji}', ${c.type}, ${c.sortOrder})"
                                )
                            }
                        }
                    }
                })
                .build()
    }
}
