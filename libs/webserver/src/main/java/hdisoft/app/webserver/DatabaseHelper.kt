package hdisoft.app.webserver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "webserver_db.db"
        private const val DATABASE_VERSION = 2
        
        const val TABLE_NAME = "records"
        const val COLUMN_ID = "id"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_CREATE_AT = "createAt"
        const val COLUMN_UPDATE_AT = "updateAt"
        const val COLUMN_LAST_UPDATE_BY = "lastUpdateBy"

        const val TABLE_SCRIPTS = "scripts"
        const val COLUMN_SCRIPT_ID = "id"
        const val COLUMN_SCRIPT_NAME = "name"
        const val COLUMN_SCRIPT_CONTENT = "content"
        const val COLUMN_SCRIPT_RUN_COUNT = "runCount"
        const val COLUMN_SCRIPT_LAST_RUN = "lastRun"
        const val COLUMN_SCRIPT_LAST_UPDATE = "lastUpdate"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = ("CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_CONTENT TEXT, " +
                "$COLUMN_CREATE_AT TEXT, " +
                "$COLUMN_UPDATE_AT TEXT, " +
                "$COLUMN_LAST_UPDATE_BY TEXT)")
        db.execSQL(createTableQuery)

        val createScriptsTable = ("CREATE TABLE $TABLE_SCRIPTS (" +
                "$COLUMN_SCRIPT_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_SCRIPT_NAME TEXT, " +
                "$COLUMN_SCRIPT_CONTENT TEXT, " +
                "$COLUMN_SCRIPT_RUN_COUNT INTEGER DEFAULT 0, " +
                "$COLUMN_SCRIPT_LAST_RUN TEXT, " +
                "$COLUMN_SCRIPT_LAST_UPDATE TEXT)")
        db.execSQL(createScriptsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val createScriptsTable = ("CREATE TABLE $TABLE_SCRIPTS (" +
                    "$COLUMN_SCRIPT_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_SCRIPT_NAME TEXT, " +
                    "$COLUMN_SCRIPT_CONTENT TEXT, " +
                    "$COLUMN_SCRIPT_RUN_COUNT INTEGER DEFAULT 0, " +
                    "$COLUMN_SCRIPT_LAST_RUN TEXT, " +
                    "$COLUMN_SCRIPT_LAST_UPDATE TEXT)")
            db.execSQL(createScriptsTable)
        }
    }

    private fun getCurrentIsoTimestamp(): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date())
    }

    // --- CRUD Operations ---
    
    fun insertRecord(content: String, lastUpdateBy: String): JSONObject? {
        val db = this.writableDatabase
        val timestamp = getCurrentIsoTimestamp()
        val contentValues = ContentValues().apply {
            put(COLUMN_CONTENT, content)
            put(COLUMN_CREATE_AT, timestamp)
            put(COLUMN_UPDATE_AT, timestamp)
            put(COLUMN_LAST_UPDATE_BY, lastUpdateBy)
        }
        val id = db.insert(TABLE_NAME, null, contentValues)
        return if (id != -1L) getRecordById(id) else null
    }

    fun getAllRecords(): JSONArray {
        val list = JSONArray()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_ID DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val record = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)))
                    put("content", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)))
                    put("createAt", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATE_AT)))
                    put("updateAt", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UPDATE_AT)))
                    put("lastUpdateBy", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_UPDATE_BY)))
                }
                list.put(record)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getRecordById(id: Long): JSONObject? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_ID = ?", arrayOf(id.toString()))
        var record: JSONObject? = null
        if (cursor.moveToFirst()) {
            record = JSONObject().apply {
                put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)))
                put("content", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)))
                put("createAt", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATE_AT)))
                put("updateAt", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UPDATE_AT)))
                put("lastUpdateBy", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_UPDATE_BY)))
            }
        }
        cursor.close()
        return record
    }

    fun updateRecord(id: Long, content: String, lastUpdateBy: String): JSONObject? {
        val db = this.writableDatabase
        val timestamp = getCurrentIsoTimestamp()
        val contentValues = ContentValues().apply {
            put(COLUMN_CONTENT, content)
            put(COLUMN_UPDATE_AT, timestamp)
            put(COLUMN_LAST_UPDATE_BY, lastUpdateBy)
        }
        val affectedRows = db.update(TABLE_NAME, contentValues, "$COLUMN_ID = ?", arrayOf(id.toString()))
        return if (affectedRows > 0) getRecordById(id) else null
    }

    fun deleteRecord(id: Long): Boolean {
        val db = this.writableDatabase
        val affectedRows = db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
        return affectedRows > 0
    }

    // --- Scripts CRUD Operations ---

    fun insertScript(name: String, content: String): JSONObject? {
        val db = this.writableDatabase
        val timestamp = getCurrentIsoTimestamp()
        val contentValues = ContentValues().apply {
            put(COLUMN_SCRIPT_NAME, name)
            put(COLUMN_SCRIPT_CONTENT, content)
            put(COLUMN_SCRIPT_RUN_COUNT, 0)
            put(COLUMN_SCRIPT_LAST_RUN, "")
            put(COLUMN_SCRIPT_LAST_UPDATE, timestamp)
        }
        val id = db.insert(TABLE_SCRIPTS, null, contentValues)
        return if (id != -1L) getScriptById(id) else null
    }

    fun getAllScripts(): JSONArray {
        val list = JSONArray()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_SCRIPTS ORDER BY $COLUMN_SCRIPT_ID DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val record = JSONObject().apply {
                    put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_ID)))
                    put("name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_NAME)))
                    put("content", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_CONTENT)))
                    put("runCount", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_RUN_COUNT)))
                    put("lastRun", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_LAST_RUN)))
                    put("lastUpdate", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_LAST_UPDATE)))
                }
                list.put(record)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getScriptById(id: Long): JSONObject? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_SCRIPTS WHERE $COLUMN_SCRIPT_ID = ?", arrayOf(id.toString()))
        var record: JSONObject? = null
        if (cursor.moveToFirst()) {
            record = JSONObject().apply {
                put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_ID)))
                put("name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_NAME)))
                put("content", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_CONTENT)))
                put("runCount", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_RUN_COUNT)))
                put("lastRun", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_LAST_RUN)))
                put("lastUpdate", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCRIPT_LAST_UPDATE)))
            }
        }
        cursor.close()
        return record
    }

    fun updateScript(id: Long, name: String, content: String): JSONObject? {
        val db = this.writableDatabase
        val timestamp = getCurrentIsoTimestamp()
        val contentValues = ContentValues().apply {
            put(COLUMN_SCRIPT_NAME, name)
            put(COLUMN_SCRIPT_CONTENT, content)
            put(COLUMN_SCRIPT_LAST_UPDATE, timestamp)
        }
        val affectedRows = db.update(TABLE_SCRIPTS, contentValues, "$COLUMN_SCRIPT_ID = ?", arrayOf(id.toString()))
        return if (affectedRows > 0) getScriptById(id) else null
    }

    fun incrementScriptRunCount(id: Long): Boolean {
        val db = this.writableDatabase
        val timestamp = getCurrentIsoTimestamp()
        db.execSQL(
            "UPDATE $TABLE_SCRIPTS SET $COLUMN_SCRIPT_RUN_COUNT = $COLUMN_SCRIPT_RUN_COUNT + 1, $COLUMN_SCRIPT_LAST_RUN = ? WHERE $COLUMN_SCRIPT_ID = ?",
            arrayOf(timestamp, id.toString())
        )
        return true
    }

    fun deleteScript(id: Long): Boolean {
        val db = this.writableDatabase
        val affectedRows = db.delete(TABLE_SCRIPTS, "$COLUMN_SCRIPT_ID = ?", arrayOf(id.toString()))
        return affectedRows > 0
    }
}
