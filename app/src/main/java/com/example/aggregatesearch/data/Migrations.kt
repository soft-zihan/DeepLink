package com.example.aggregatesearch.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create url_groups table
        database.execSQL("CREATE TABLE IF NOT EXISTS `url_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isExpanded` INTEGER NOT NULL DEFAULT 1, `orderIndex` INTEGER NOT NULL DEFAULT 0)")

        // Add groupId column to search_urls table
        database.execSQL("ALTER TABLE `search_urls` ADD COLUMN `groupId` INTEGER")

        // Create a default group
        database.execSQL("INSERT INTO `url_groups` (name, orderIndex) VALUES ('默认分组', 0)")

        // Get the id of the default group (assuming it's the first one, so id = 1)
        // Update existing search_urls to belong to this default group
        // This is a simplification; in a real scenario, you might need to query the id
        database.execSQL("UPDATE `search_urls` SET `groupId` = (SELECT id FROM `url_groups` WHERE name = '默认分组' LIMIT 1)")

        // Add foreign key constraint (if not already defined in @Entity, though it's better there)
        // This step might be complex if data integrity issues arise.
        // For Room, it's generally handled by defining ForeignKey in the @Entity annotation.
        // However, if you need to add it manually for existing tables:
        // 1. Create a new table with the foreign key constraint
        // 2. Copy data from the old table to the new table
        // 3. Drop the old table
        // 4. Rename the new table to the original table name
        // For simplicity, we'll assume the ForeignKey in @Entity handles this correctly upon schema validation.
    }
}

