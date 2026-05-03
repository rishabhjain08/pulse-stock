package com.pulsestock.app.data.poarvault

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        InstitutionEntity::class,
        AccountEntity::class,
        PlaidTransaction::class,
        SplitwiseExpense::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PoarVaultDatabase : RoomDatabase() {

    abstract fun dao(): PoarVaultDao
    abstract fun splitwiseDao(): SplitwiseDao

    companion object {
        @Volatile private var INSTANCE: PoarVaultDatabase? = null

        fun get(context: Context, passphrase: ByteArray): PoarVaultDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        PoarVaultDatabase::class.java,
                        "poarvault.db",
                    )
                    .openHelperFactory(SupportFactory(passphrase))
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
