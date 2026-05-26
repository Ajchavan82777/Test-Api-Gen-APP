package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "generations")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val enhancedPrompt: String = "",
    val negativePrompt: String = "",
    val imageUrl: String,
    val style: String,
    val aspectRatio: String,
    val resolution: String,
    val quality: String,
    val numImages: Int,
    val seed: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "avatars")
data class AvatarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val faceImagesJson: String, // comma-separated strings of URIs/paths
    val characterSheet: String,  // single URI/path
    val lockStrength: String = "Strict" // "Medium", "High", "Strict"
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM generations ORDER BY timestamp DESC")
    fun getAllGenerations(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM generations WHERE prompt LIKE :query OR enhancedPrompt LIKE :query ORDER BY timestamp DESC")
    fun searchGenerations(query: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneration(generation: HistoryEntity): Long

    @Query("DELETE FROM generations WHERE id = :id")
    suspend fun deleteGeneration(id: Long)

    @Query("DELETE FROM generations")
    suspend fun clearAll()
}

@Dao
interface AvatarDao {
    @Query("SELECT * FROM avatars ORDER BY id DESC")
    fun getAllAvatars(): Flow<List<AvatarEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAvatar(avatar: AvatarEntity): Long

    @Query("DELETE FROM avatars WHERE id = :id")
    suspend fun deleteAvatar(id: Long)

    @Query("DELETE FROM avatars")
    suspend fun clearAll()
}

@Database(entities = [HistoryEntity::class, AvatarEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun avatarDao(): AvatarDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_vision_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class HistoryRepository(private val dao: HistoryDao, private val avatarDao: AvatarDao) {
    val allGenerations: Flow<List<HistoryEntity>> = dao.getAllGenerations()
    val allAvatars: Flow<List<AvatarEntity>> = avatarDao.getAllAvatars()

    fun search(query: String): Flow<List<HistoryEntity>> {
        return dao.searchGenerations("%$query%")
    }

    suspend fun insert(generation: HistoryEntity): Long {
        return dao.insertGeneration(generation)
    }

    suspend fun delete(id: Long) {
        dao.deleteGeneration(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun insertAvatar(avatar: AvatarEntity): Long {
        return avatarDao.insertAvatar(avatar)
    }

    suspend fun deleteAvatar(id: Long) {
        avatarDao.deleteAvatar(id)
    }

    suspend fun clearAllAvatars() {
        avatarDao.clearAll()
    }
}
