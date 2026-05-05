package com.pulsestock.app.data.poarvault

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        InstitutionEntity::class,
        AccountEntity::class,
        PlaidTransaction::class,
        SplitwiseExpense::class,
        SplitwisePlaidLink::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class PoarVaultDatabase : RoomDatabase() {

    abstract fun dao(): PoarVaultDao
    abstract fun splitwiseDao(): SplitwiseDao

    companion object {
        @Volatile private var INSTANCE: PoarVaultDatabase? = null

        // ── Migrations ────────────────────────────────────────────────────────
        // Rule: Plaid tables (institutions, accounts, plaid_transactions) must
        // always be preserved. Splitwise tables can be dropped+recreated freely
        // since their data is re-fetched from the API after a sync.

        // v2→v3: replace linkedPlaidId column with splitwise_plaid_links table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS splitwise_plaid_links (
                        expenseId INTEGER NOT NULL,
                        plaidTransactionId TEXT NOT NULL,
                        PRIMARY KEY(expenseId, plaidTransactionId)
                    )
                """.trimIndent())
                // Recreate splitwise_expenses without linkedPlaidId.
                // Wiping Splitwise data is fine — it re-syncs from the API.
                db.execSQL("DROP TABLE IF EXISTS splitwise_expenses")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS splitwise_expenses (
                        id INTEGER NOT NULL PRIMARY KEY,
                        description TEXT NOT NULL,
                        date TEXT NOT NULL,
                        totalAmount REAL NOT NULL,
                        currencyCode TEXT NOT NULL,
                        pageOffset INTEGER NOT NULL DEFAULT 0,
                        cachedAt INTEGER NOT NULL DEFAULT 0,
                        isDismissed INTEGER NOT NULL DEFAULT 0,
                        isAutoMatched INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        // v3→v4: add paidShare and ownedShare columns to splitwise_expenses
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE splitwise_expenses ADD COLUMN paidShare REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE splitwise_expenses ADD COLUMN ownedShare REAL NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context, passphrase: ByteArray): PoarVaultDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        PoarVaultDatabase::class.java,
                        "poarvault.db",
                    )
                    .openHelperFactory(SupportFactory(passphrase))
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
