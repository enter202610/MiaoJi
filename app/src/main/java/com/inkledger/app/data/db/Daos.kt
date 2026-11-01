package com.inkledger.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TxDao {
    @Insert
    suspend fun insert(t: TransactionEntity): Long

    @Update
    suspend fun update(t: TransactionEntity)

    @Delete
    suspend fun delete(t: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE source = 4 AND amountMinor = :amount AND type = :type AND timestamp >= :since")
    suspend fun countAutoSimilar(amount: Long, type: Int, since: Long): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<CategoryEntity>)

    @Query("SELECT * FROM categories ORDER BY type, sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type, sortOrder")
    suspend fun listAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?
}

@Dao
interface TemplateDao {
    @Insert
    suspend fun insert(t: TemplateEntity): Long

    @Update
    suspend fun update(t: TemplateEntity)

    @Delete
    suspend fun delete(t: TemplateEntity)

    @Query("SELECT * FROM templates ORDER BY useCount DESC, createdAt DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates")
    suspend fun listAll(): List<TemplateEntity>
}

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(g: GoalEntity): Long

    @Update
    suspend fun update(g: GoalEntity)

    @Delete
    suspend fun delete(g: GoalEntity)

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert
    suspend fun insertDeposit(d: GoalDepositEntity): Long

    @Query("SELECT * FROM goal_deposits WHERE goalId = :goalId ORDER BY timestamp DESC")
    fun observeDeposits(goalId: Long): Flow<List<GoalDepositEntity>>

    @Query("SELECT * FROM goal_deposits WHERE goalId = :goalId")
    suspend fun listDeposits(goalId: Long): List<GoalDepositEntity>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM goal_deposits WHERE goalId = :goalId")
    suspend fun sumDeposits(goalId: Long): Long

    @Query("DELETE FROM goal_deposits WHERE goalId = :goalId")
    suspend fun deleteDeposits(goalId: Long)

    @Insert
    suspend fun insertCheck(c: HabitCheckEntity)

    @Query("DELETE FROM habit_checks WHERE goalId = :goalId AND epochDay = :day")
    suspend fun deleteCheck(goalId: Long, day: Long)

    @Query("SELECT * FROM habit_checks WHERE goalId = :goalId ORDER BY epochDay")
    fun observeChecks(goalId: Long): Flow<List<HabitCheckEntity>>

    @Query("DELETE FROM habit_checks WHERE goalId = :goalId")
    suspend fun deleteChecks(goalId: Long)

    @Query("SELECT COUNT(*) FROM habit_checks WHERE goalId = :goalId AND epochDay = :day")
    suspend fun countCheck(goalId: Long, day: Long): Int

    @Query("SELECT * FROM goal_deposits")
    fun observeAllDeposits(): Flow<List<GoalDepositEntity>>

    @Query("SELECT * FROM habit_checks")
    fun observeAllChecks(): Flow<List<HabitCheckEntity>>

    @Query("SELECT * FROM goals")
    suspend fun listAll(): List<GoalEntity>

    @Query("SELECT * FROM goal_deposits")
    suspend fun listAllDeposits(): List<GoalDepositEntity>

    @Query("SELECT * FROM habit_checks")
    suspend fun listAllChecks(): List<HabitCheckEntity>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertGoalKeepingId(g: GoalEntity)
}

@Dao
interface CountDownDao {
    @Insert
    suspend fun insert(c: CountDownEntity): Long

    @Update
    suspend fun update(c: CountDownEntity)

    @Delete
    suspend fun delete(c: CountDownEntity)

    @Query("SELECT * FROM countdowns ORDER BY targetEpochDay")
    fun observeAll(): Flow<List<CountDownEntity>>

    @Query("SELECT * FROM countdowns")
    suspend fun listAll(): List<CountDownEntity>
}

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(n: NoteEntity): Long

    @Update
    suspend fun update(n: NoteEntity)

    @Delete
    suspend fun delete(n: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun listAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?
}

@androidx.room.Dao
interface RecycleBinDao {
    @Insert
    suspend fun insert(e: RecycleBinEntity): Long

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<RecycleBinEntity>>

    @Query("SELECT * FROM recycle_bin WHERE binId = :binId")
    suspend fun getById(binId: Long): RecycleBinEntity?

    @Delete
    suspend fun delete(e: RecycleBinEntity)

    @Query("DELETE FROM recycle_bin WHERE deletedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("DELETE FROM recycle_bin")
    suspend fun clearAll()
}
