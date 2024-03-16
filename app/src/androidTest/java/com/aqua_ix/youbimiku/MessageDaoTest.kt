package com.aqua_ix.youbimiku

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua_ix.youbimiku.database.AppDatabase
import com.aqua_ix.youbimiku.database.MessageDao
import com.aqua_ix.youbimiku.database.MessageEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        messageDao = db.messageDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testGetAll() = runBlocking {
        val messages = arrayListOf(
            MessageEntity(1, 0, true, "Hello Assistant", true, Calendar.getInstance().time.time),
            MessageEntity(2, 1, false, "Hello User", false, Calendar.getInstance().time.time)
        )

        messages.forEach { messageDao.insert(it) }
        val retrievedMessages = messageDao.getAll()
        assertEquals(messages, retrievedMessages)
    }

    @Test
    fun testDeleteAll() = runBlocking {
        val messages = arrayListOf(
            MessageEntity(1, 0, true, "Hello Assistant", true, Calendar.getInstance().time.time),
            MessageEntity(2, 1, false, "Hello User", false, Calendar.getInstance().time.time)
        )

        messages.forEach { messageDao.insert(it) }
        messageDao.deleteAll()
        val retrievedMessages = messageDao.getAll()
        assertEquals(0, retrievedMessages.size)
    }
}