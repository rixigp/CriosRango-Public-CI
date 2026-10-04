package es.criosrango.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CategoryProductCacheDatabaseSchemaTest {
    private lateinit var context: Context
    private lateinit var database: CategoryProductCacheDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("criosrango_catalog.db")
        database = CategoryProductCacheDatabase.create(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("criosrango_catalog.db")
    }

    @Test
    fun freshDatabaseV4HasExpectedTablesAndIndices() {
        val sqlite = database.openHelper.writableDatabase
        assertEquals(4, sqlite.version)

        val tables = mutableSetOf<String>()
        sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
        ).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        assertTrue(tables.contains("catalog_products"))
        assertTrue(tables.contains("catalog_product_categories"))
        assertTrue(tables.contains("catalog_categories"))
        assertTrue(tables.contains("catalog_sync_metadata"))
        assertTrue(tables.contains("catalog_priority_fallback"))

        assertIndex(sqlite, "catalog_products", "index_catalog_products_generation_productId")
        assertIndex(sqlite, "catalog_product_categories", "index_catalog_product_categories_generation_categoryId")
        assertIndex(sqlite, "catalog_product_categories", "index_catalog_product_categories_generation_productId")
        assertIndex(sqlite, "catalog_categories", "index_catalog_categories_generation_parentId")
        assertIndex(sqlite, "catalog_priority_fallback", "index_catalog_priority_fallback_categoryId")

        val foreignKeyCount = sqlite.query("PRAGMA foreign_key_list(catalog_products)").use { cursor -> cursor.count }
        assertEquals(0, foreignKeyCount)
    }

    @Test
    fun currentSchemaKeepsExpectedPrimaryKeysAndColumns() {
        val sqlite = database.openHelper.writableDatabase
        assertColumns(sqlite, "catalog_products", listOf("generation", "productId", "payloadJson", "catalogOrder"), listOf(true, true, true, true))
        assertColumns(sqlite, "catalog_product_categories", listOf("generation", "productId", "categoryId"), listOf(true, true, true))
        assertColumns(sqlite, "catalog_categories", listOf("key", "generation", "categoryId", "parentId", "name", "slug", "count"), listOf(true, true, true, true, true, true, true))
        assertColumns(sqlite, "catalog_sync_metadata", listOf("id", "activeGeneration", "lastCompleteSyncAt", "hasValidSnapshot"), listOf(true, false, false, true))
        assertColumns(sqlite, "catalog_priority_fallback", listOf("categoryId", "productId", "payloadJson", "catalogOrder", "fetchedAt"), listOf(true, true, true, true, true))
    }

    private fun assertIndex(sqlite: androidx.sqlite.db.SupportSQLiteDatabase, table: String, index: String) {
        var found = false
        sqlite.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == index) {
                    found = true
                    break
                }
            }
        }
        assertTrue("$table missing index $index", found)
    }

    private fun assertColumns(
        sqlite: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        expected: List<String>,
        expectedNotNull: List<Boolean>
    ) {
        val actual = mutableListOf<String>()
        val notNull = mutableListOf<Boolean>()
        sqlite.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndex("name")
            val notNullColumn = cursor.getColumnIndex("notnull")
            while (cursor.moveToNext()) {
                actual += cursor.getString(nameColumn)
                notNull += cursor.getInt(notNullColumn) != 0
            }
        }
        assertEquals(expected, actual)
        assertEquals(expectedNotNull, notNull)
    }
}
