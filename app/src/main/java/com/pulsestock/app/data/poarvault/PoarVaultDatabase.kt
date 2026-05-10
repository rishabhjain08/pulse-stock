package com.pulsestock.app.data.poarvault

import android.content.Context
import androidx.annotation.VisibleForTesting
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
        CategoryRule::class,
        CustomCategory::class,
        BalanceSnapshot::class,
    ],
    version = 9,
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

        // v4→v5: add Plaid personal_finance_category columns + user override
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plaid_transactions ADD COLUMN pfcPrimary TEXT")
                db.execSQL("ALTER TABLE plaid_transactions ADD COLUMN pfcDetailed TEXT")
                db.execSQL("ALTER TABLE plaid_transactions ADD COLUMN categoryOverride TEXT")
            }
        }

        // v5→v6: store Plaid last_statement_issue_date on accounts
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN lastStatementDate TEXT")
            }
        }

        // v6→v7: add merchantName to transactions; add category_rules table
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plaid_transactions ADD COLUMN merchantName TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_rules (
                        merchantName TEXT NOT NULL PRIMARY KEY,
                        category TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        // v7→v8: add custom_categories table for user-created category names
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_categories (
                        name TEXT NOT NULL PRIMARY KEY
                    )
                """.trimIndent())
            }
        }

        // v8→v9: add balance_snapshots table for spending/balance history feature
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS balance_snapshots (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        accountId TEXT NOT NULL,
                        capturedAt INTEGER NOT NULL,
                        statementBalance REAL,
                        currentBalance REAL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_balance_snapshots_accountId ON balance_snapshots (accountId)"
                )
            }
        }

        @VisibleForTesting
        fun getInMemory(context: Context): PoarVaultDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, PoarVaultDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        fun get(context: Context, passphrase: ByteArray): PoarVaultDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        PoarVaultDatabase::class.java,
                        "poarvault.db",
                    )
                    .openHelperFactory(SupportFactory(passphrase))
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
