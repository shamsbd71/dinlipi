package com.dinlipi.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

/** One routine block, e.g. "Exercise, 07:00, 30 min, Sun-Thu, alarm". */
data class Block(
    val id: Long,
    val name: String,
    val startMin: Int,
    val lengthMin: Int,
    val days: Int,
    val alarm: Boolean,
)

/** A block as it falls today: start after any "move later" shifts, plus today's status. */
data class DayItem(val block: Block, val start: Int, val status: String?) {
    val end: Int get() = start + block.lengthMin
    val isClosed: Boolean get() = status == DONE || status == SKIPPED
}

const val STARTED = "started"
const val DONE = "done"
const val SKIPPED = "skipped"

/** Everything lives on the phone in one small SQLite file. */
class Db private constructor(c: Context) : SQLiteOpenHelper(c, "dinlipi.db", null, 1) {

    companion object {
        @Volatile private var inst: Db? = null
        fun get(c: Context): Db =
            inst ?: synchronized(this) { inst ?: Db(c.applicationContext).also { inst = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE blocks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "start_min INTEGER NOT NULL, length_min INTEGER NOT NULL, days INTEGER NOT NULL, alarm INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE log (day INTEGER NOT NULL, block_id INTEGER NOT NULL, status TEXT NOT NULL, " +
                "PRIMARY KEY (day, block_id))"
        )
        db.execSQL("CREATE TABLE shifts (day INTEGER NOT NULL, after_min INTEGER NOT NULL, delta INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun blocks(): List<Block> {
        val out = ArrayList<Block>()
        readableDatabase.rawQuery(
            "SELECT id, name, start_min, length_min, days, alarm FROM blocks ORDER BY start_min", null
        ).use { c ->
            while (c.moveToNext()) {
                out += Block(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5) == 1)
            }
        }
        return out
    }

    fun block(id: Long): Block? = blocks().firstOrNull { it.id == id }

    fun save(b: Block): Long {
        val v = ContentValues().apply {
            put("name", b.name)
            put("start_min", b.startMin)
            put("length_min", b.lengthMin)
            put("days", b.days)
            put("alarm", if (b.alarm) 1 else 0)
        }
        return if (b.id > 0) {
            writableDatabase.update("blocks", v, "id = ?", arrayOf(b.id.toString()))
            b.id
        } else {
            writableDatabase.insert("blocks", null, v)
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("blocks", "id = ?", arrayOf(id.toString()))
        writableDatabase.delete("log", "block_id = ?", arrayOf(id.toString()))
    }

    fun status(day: Int, id: Long): String? =
        readableDatabase.rawQuery(
            "SELECT status FROM log WHERE day = ? AND block_id = ?", arrayOf(day.toString(), id.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun statuses(day: Int): Map<Long, String> {
        val out = HashMap<Long, String>()
        readableDatabase.rawQuery("SELECT block_id, status FROM log WHERE day = ?", arrayOf(day.toString()))
            .use { c -> while (c.moveToNext()) out[c.getLong(0)] = c.getString(1) }
        return out
    }

    fun setStatus(day: Int, id: Long, status: String) {
        val v = ContentValues().apply {
            put("day", day)
            put("block_id", id)
            put("status", status)
        }
        writableDatabase.insertWithOnConflict("log", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearStatus(day: Int, id: Long) {
        writableDatabase.delete("log", "day = ? AND block_id = ?", arrayOf(day.toString(), id.toString()))
    }

    /** Shifts for a day: blocks starting at or after [first] move later by [second] minutes. */
    fun shifts(day: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        readableDatabase.rawQuery("SELECT after_min, delta FROM shifts WHERE day = ?", arrayOf(day.toString()))
            .use { c -> while (c.moveToNext()) out += c.getInt(0) to c.getInt(1) }
        return out
    }

    fun addShift(day: Int, afterMin: Int, delta: Int) {
        val v = ContentValues().apply {
            put("day", day)
            put("after_min", afterMin)
            put("delta", delta)
        }
        writableDatabase.insert("shifts", null, v)
    }

    fun startOn(b: Block, shifts: List<Pair<Int, Int>>): Int =
        b.startMin + shifts.filter { b.startMin >= it.first }.sumOf { it.second }

    /** Today's blocks in order, with shifts and statuses applied. */
    fun today(cal: Calendar = Time.now()): List<DayItem> {
        val day = Time.dayKey(cal)
        val bit = Time.dayBit(cal)
        val sh = shifts(day)
        val st = statuses(day)
        return blocks()
            .filter { it.days and bit != 0 }
            .map { DayItem(it, startOn(it, sh), st[it.id]) }
            .sortedBy { it.start }
    }
}
