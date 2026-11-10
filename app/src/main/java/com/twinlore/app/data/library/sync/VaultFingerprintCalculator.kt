package com.twinlore.app.data.library.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic per-vault content hash: SHA-256 over the vault row plus every
 * row of every SyncSchema.VAULT_SCOPED table for that vault, in PK order,
 * with volatile columns excluded. Same data => same hex string, on any device
 * running the same schema version. Merge-time comparisons always recompute on
 * both (already migrated) sides, so cross-version drift is irrelevant.
 */
@Singleton
class VaultFingerprintCalculator @Inject constructor() {

    suspend fun computeAll(database: VaultDatabase): Map<String, String> =
        computeAll(database.openHelper.writableDatabase)

    suspend fun computeAll(db: SupportSQLiteDatabase): Map<String, String> =
        withContext(Dispatchers.IO) {
            val vaultIds = mutableListOf<String>()
            db.query("SELECT id FROM vaults ORDER BY id").use { c ->
                while (c.moveToNext()) vaultIds.add(c.getString(0))
            }
            vaultIds.associateWith { computeOne(db, it) }
        }

    private fun computeOne(db: SupportSQLiteDatabase, vaultId: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        hashQuery(md, db, "vaults", "SELECT * FROM vaults WHERE id = ? ORDER BY id", vaultId)
        for (t in SyncSchema.VAULT_SCOPED) {
            val where = SyncSchema.whereFor(t, prefix = "")
            hashQuery(md, db, t.name, "SELECT * FROM ${t.name} WHERE $where ORDER BY id", vaultId)
        }
        return md.digest().joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    }

    private fun hashQuery(
        md: MessageDigest,
        db: SupportSQLiteDatabase,
        table: String,
        sql: String,
        vaultId: String,
    ) {
        val volatileCols = SyncSchema.VOLATILE_COLUMNS[table].orEmpty()
        md.update(table.toByteArray())
        db.query(sql, arrayOf(vaultId)).use { c ->
            // Sort column indices by name so declared column order (which can
            // shift across migrations) never affects the hash.
            val cols = (0 until c.columnCount)
                .map { it to c.getColumnName(it) }
                .filter { it.second !in volatileCols }
                .sortedBy { it.second }
            while (c.moveToNext()) {
                for ((i, name) in cols) {
                    md.update(name.toByteArray())
                    md.update(0)
                    when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> md.update(TAG_NULL)
                        android.database.Cursor.FIELD_TYPE_BLOB -> {
                            md.update(TAG_BLOB)
                            md.update(c.getBlob(i))
                        }
                        else -> {
                            md.update(TAG_TEXT)
                            md.update((c.getString(i) ?: "").toByteArray())
                        }
                    }
                    md.update(1)
                }
                md.update(2)
            }
        }
        md.update(3)
    }

    companion object {
        private const val TAG_NULL: Byte = 10
        private const val TAG_BLOB: Byte = 11
        private const val TAG_TEXT: Byte = 12
    }
}
