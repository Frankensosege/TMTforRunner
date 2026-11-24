package com.frankensosege.tmtforrunner.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*

// ─── 운동 세션 요약 기록 ──────────────────────────────────
@Entity(tableName = "workout_records")
data class WorkoutRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,           // "2025-03-27"
    val steps: Int,
    val distanceKm: Double,
    val durationMin: Int,
    val calories: Int
)

// ─── 분 단위 그래프용 포인트 ──────────────────────────────
@Entity(tableName = "workout_minutes")
data class WorkoutMinute(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,           // "2025-03-27" — 날짜 단위로 관리
    val minuteIndex: Int,       // 0, 1, 2, ... (하루 누적 분)
    val steps: Int,             // 해당 분의 걸음 수
    val distanceKm: Double,     // 누적 거리
    val calories: Int           // 누적 칼로리
)

// ─── DAOs ─────────────────────────────────────────────────
@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_records ORDER BY date DESC LIMIT 7")
    fun getLastSevenDays(): LiveData<List<WorkoutRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WorkoutRecord)

    // 특정 날짜 레코드 조회
    @Query("SELECT * FROM workout_records WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): WorkoutRecord?

    // 날짜 기준 합산 업데이트
    @Query("""
        UPDATE workout_records
        SET steps       = steps       + :steps,
            distanceKm  = distanceKm  + :distanceKm,
            durationMin = durationMin + :durationMin,
            calories    = calories    + :calories
        WHERE date = :date
    """)
    suspend fun addToDate(date: String, steps: Int, distanceKm: Double, durationMin: Int, calories: Int)

    // 월별 조회: yearMonth = "2026-10"
    @Query("SELECT * FROM workout_records WHERE date LIKE :yearMonth || '%' ORDER BY date ASC")
    suspend fun getByMonth(yearMonth: String): List<WorkoutRecord>
}

@Dao
interface WorkoutMinuteDao {
    // 가장 최근 날짜의 분 단위 기록
    @Query("""
        SELECT * FROM workout_minutes
        WHERE date = (SELECT MAX(date) FROM workout_minutes)
        ORDER BY minuteIndex ASC
    """)
    fun getLatestSessionMinutes(): LiveData<List<WorkoutMinute>>

    // 특정 날짜의 분 단위 기록 (LiveData)
    @Query("SELECT * FROM workout_minutes WHERE date = :date ORDER BY minuteIndex ASC")
    fun getMinutesForDate(date: String): LiveData<List<WorkoutMinute>>

    // 특정 날짜의 분 단위 기록 (suspend)
    @Query("SELECT * FROM workout_minutes WHERE date = :date ORDER BY minuteIndex ASC")
    suspend fun getMinutesForDateOnce(date: String): List<WorkoutMinute>

    // 해당 날짜의 마지막 minuteIndex (이어서 저장할 때 사용)
    @Query("SELECT COALESCE(MAX(minuteIndex), -1) FROM workout_minutes WHERE date = :date")
    suspend fun getLastMinuteIndex(date: String): Int

    // 존재하는 모든 날짜 목록 (최신순)
    @Query("SELECT DISTINCT date FROM workout_minutes ORDER BY date DESC")
    suspend fun getAllDates(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(minute: WorkoutMinute)

    @Query("SELECT COUNT(*) FROM workout_minutes")
    suspend fun totalCount(): Int
}

// ─── Database ─────────────────────────────────────────────
@Database(
    entities = [WorkoutRecord::class, WorkoutMinute::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutMinuteDao(): WorkoutMinuteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tmt_workout_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}