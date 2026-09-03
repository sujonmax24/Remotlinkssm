package com.sujon.remotlinkssm.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedDeviceDao {
    @Query("SELECT * FROM trusted_devices ORDER BY lastConnectedAt DESC, pairedAt DESC")
    fun observeAll(): Flow<List<TrustedDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: TrustedDeviceEntity)

    @Delete
    suspend fun delete(device: TrustedDeviceEntity)

    @Query("DELETE FROM trusted_devices WHERE deviceId = :deviceId")
    suspend fun revoke(deviceId: String)

    @Query("UPDATE trusted_devices SET lastConnectedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun markConnected(deviceId: String, timestamp: Long)
}
