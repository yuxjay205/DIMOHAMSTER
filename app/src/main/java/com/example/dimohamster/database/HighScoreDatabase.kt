package com.example.dimohamster.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HighScoreEntry(
    val id: Int,
    val score: Int,
    val level: Int,
    val timestamp: Long,
    val playerName: String
)

class HighScoreDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "breakout.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_HIGH_SCORES = "high_scores"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SCORE = "score"
        private const val COLUMN_LEVEL = "level"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_PLAYER_NAME = "player_name"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_HIGH_SCORES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SCORE INTEGER NOT NULL,
                $COLUMN_LEVEL INTEGER NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_PLAYER_NAME TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HIGH_SCORES")
        onCreate(db)
    }

    /**
     * Insert or update high score for a player
     * If player exists, only update if new score is higher
     */
    fun insertHighScore(score: Int, level: Int, playerName: String = "Player"): Long {
        val db = writableDatabase

        // Check if player already exists
        val cursor = db.query(
            TABLE_HIGH_SCORES,
            arrayOf(COLUMN_ID, COLUMN_SCORE),
            "$COLUMN_PLAYER_NAME = ?",
            arrayOf(playerName),
            null,
            null,
            null
        )

        var result: Long = -1
        cursor.use {
            if (it.moveToFirst()) {
                // Player exists - check if new score is higher
                val existingId = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID))
                val existingScore = it.getInt(it.getColumnIndexOrThrow(COLUMN_SCORE))

                if (score > existingScore) {
                    // Update with higher score
                    val values = ContentValues().apply {
                        put(COLUMN_SCORE, score)
                        put(COLUMN_LEVEL, level)
                        put(COLUMN_TIMESTAMP, System.currentTimeMillis())
                    }
                    db.update(
                        TABLE_HIGH_SCORES,
                        values,
                        "$COLUMN_ID = ?",
                        arrayOf(existingId.toString())
                    )
                    result = existingId.toLong()
                } else {
                    // Keep existing higher score
                    result = existingId.toLong()
                }
            } else {
                // New player - insert new entry
                val values = ContentValues().apply {
                    put(COLUMN_SCORE, score)
                    put(COLUMN_LEVEL, level)
                    put(COLUMN_TIMESTAMP, System.currentTimeMillis())
                    put(COLUMN_PLAYER_NAME, playerName)
                }
                result = db.insert(TABLE_HIGH_SCORES, null, values)
            }
        }

        return result
    }

    /**
     * Get top N high scores
     */
    fun getTopScores(limit: Int = 10): List<HighScoreEntry> {
        val db = readableDatabase
        val scores = mutableListOf<HighScoreEntry>()

        val cursor = db.query(
            TABLE_HIGH_SCORES,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_SCORE DESC",
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                val entry = HighScoreEntry(
                    id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                    score = it.getInt(it.getColumnIndexOrThrow(COLUMN_SCORE)),
                    level = it.getInt(it.getColumnIndexOrThrow(COLUMN_LEVEL)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    playerName = it.getString(it.getColumnIndexOrThrow(COLUMN_PLAYER_NAME))
                )
                scores.add(entry)
            }
        }

        return scores
    }

    /**
     * Get the highest score
     */
    fun getHighestScore(): Int {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HIGH_SCORES,
            arrayOf("MAX($COLUMN_SCORE) as max_score"),
            null,
            null,
            null,
            null,
            null
        )

        var highScore = 0
        cursor.use {
            if (it.moveToFirst()) {
                highScore = it.getInt(0)
            }
        }

        return highScore
    }

    /**
     * Clear all scores
     */
    fun clearAllScores() {
        val db = writableDatabase
        db.delete(TABLE_HIGH_SCORES, null, null)
    }
}
