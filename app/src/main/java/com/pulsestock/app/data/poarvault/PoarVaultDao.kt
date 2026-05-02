package com.pulsestock.app.data.poarvault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PoarVaultDao {

    @Transaction
    @Query("SELECT * FROM institutions ORDER BY addedAt DESC")
    fun watchInstitutions(): Flow<List<InstitutionWithAccounts>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstitution(institution: InstitutionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<AccountEntity>)

    @Query("DELETE FROM institutions WHERE institutionId = :id")
    suspend fun deleteInstitution(id: String)

    @Query("SELECT institutionId FROM institutions")
    suspend fun allInstitutionIds(): List<String>
}
