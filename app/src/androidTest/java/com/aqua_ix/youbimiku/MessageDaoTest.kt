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
    fun testGetLatestReturnsNewestFirst() = runBlocking {
        val messages = messages(5)
        messages.forEach { messageDao.insert(it) }

        val retrievedMessages = messageDao.getLatest(5)

        assertEquals(messages.reversed(), retrievedMessages)
    }

    @Test
    fun testGetLatestLimitsToTheNewest() = runBlocking {
        val messages = messages(5)
        messages.forEach { messageDao.insert(it) }

        val retrievedMessages = messageDao.getLatest(2)

        assertEquals(listOf(messages[4], messages[3]), retrievedMessages)
    }

    @Test
    fun testGetOlderThanReturnsPreviousMessages() = runBlocking {
        val messages = messages(5)
        messages.forEach { messageDao.insert(it) }
        val oldestLoaded = messages[3]

        val retrievedMessages =
            messageDao.getOlderThan(oldestLoaded.sendTime, oldestLoaded.id, 2)

        assertEquals(listOf(messages[2], messages[1]), retrievedMessages)
    }

    /**
     * sendTime列が無かった頃の履歴はすべて0になるため、その場合もidで並びが決まること
     */
    @Test
    fun testGetOlderThanWithSameSendTime() = runBlocking {
        val messages = messages(3, sendTime = 0)
        messages.forEach { messageDao.insert(it) }
        val oldestLoaded = messages[2]

        val retrievedMessages =
            messageDao.getOlderThan(oldestLoaded.sendTime, oldestLoaded.id, 3)

        assertEquals(listOf(messages[1], messages[0]), retrievedMessages)
    }

    @Test
    fun testGetOlderThanReturnsNothingForTheOldest() = runBlocking {
        val messages = messages(3)
        messages.forEach { messageDao.insert(it) }
        val oldestLoaded = messages[0]

        val retrievedMessages =
            messageDao.getOlderThan(oldestLoaded.sendTime, oldestLoaded.id, 3)

        assertEquals(0, retrievedMessages.size)
    }

    @Test
    fun testDeleteAll() = runBlocking {
        messages(2).forEach { messageDao.insert(it) }

        messageDao.deleteAll()

        assertEquals(0, messageDao.getLatest(10).size)
    }

    /** 古い順に[count]件のメッセージを作る */
    private fun messages(count: Int, sendTime: Long? = null): List<MessageEntity> {
        val now = Calendar.getInstance().time.time
        return (1..count).map {
            MessageEntity(
                id = it,
                userId = if (it % 2 == 1) 0 else 1,
                isRightMessage = it % 2 == 1,
                text = "message $it",
                hideIcon = it % 2 == 1,
                sendTime = sendTime ?: (now + it * 1000L)
            )
        }
    }
}
