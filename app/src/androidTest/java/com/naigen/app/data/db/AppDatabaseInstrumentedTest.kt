package com.naigen.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.naigen.app.data.db.entities.FavoriteEntity
import com.naigen.app.data.db.entities.HistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 数据库 instrumented 测试 —— 在真机/模拟器上跑真实 SQLite。
 *
 * 覆盖：
 *   - HistoryDao / FavoritesDao 的 CRUD
 *   - 索引（createdAt / tag / isNegative）正常工作
 *   - ByteArray（thumbBytes）的写入读取一致性
 *
 * CI 通过 .github/workflows/build.yml 的 instrumented-tests job 用
 * reactivecircus/android-emulator-runner 在 API 30 模拟器上运行。
 *
 * 注意：用 in-memory 数据库，测试间不共享状态。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()  // 测试用，避免额外切线程
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── HistoryDao ──────────────────────────────────────────────────────────

    @Test
    fun history_insertAndRead_roundTripsCorrectly() = runTest {
        val dao = db.historyDao()
        val entity = HistoryEntity(
            prompt = "test prompt",
            negativePrompt = "test negative",
            styleKey = "2.5d",
            styleName = "2.5d",
            sizeKey = "竖图",
            sizeCost = 1,
            imageUrl = "https://example.com/img.png",
            imagePath = "images/test.png",
            thumbBytes = byteArrayOf(1, 2, 3, 4, 5),
            generationTimeMs = 1234L,
            success = true,
            errorMessage = null
        )

        val id = dao.insert(entity)
        assertTrue("插入应返回正数 id", id > 0)

        val read = dao.get(id)
        assertNotNull(read)
        assertEquals("test prompt", read!!.prompt)
        assertEquals("2.5d", read.styleKey)
        assertEquals(1234L, read.generationTimeMs)
        assertTrue(read.success)
        // ByteArray 内容应一致
        assertTrue(read.thumbBytes.contentEquals(byteArrayOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun history_observeAll_returnsInsertedList() = runTest {
        val dao = db.historyDao()
        dao.insert(fakeHistory(prompt = "p1"))
        dao.insert(fakeHistory(prompt = "p2"))

        val list = dao.observeAll().first()
        assertEquals(2, list.size)
        // 按 createdAt DESC，后插入的在前
        assertEquals("p2", list[0].prompt)
        assertEquals("p1", list[1].prompt)
    }

    @Test
    fun history_delete_removesEntity() = runTest {
        val dao = db.historyDao()
        val id = dao.insert(fakeHistory(prompt = "to delete"))
        assertNotNull(dao.get(id))

        dao.delete(id)
        assertNull(dao.get(id))
    }

    @Test
    fun history_clearAll_emptiesTable() = runTest {
        val dao = db.historyDao()
        dao.insert(fakeHistory())
        dao.insert(fakeHistory())
        assertEquals(2, dao.observeAll().first().size)

        dao.clearAll()
        assertEquals(0, dao.observeAll().first().size)
    }

    // ── FavoritesDao ────────────────────────────────────────────────────────

    @Test
    fun favorites_insertAndRead_roundTripsCorrectly() = runTest {
        val dao = db.favoritesDao()
        val entity = FavoriteEntity(
            title = "角色模板",
            content = "1girl, silver hair",
            tag = "角色",
            isNegative = false
        )

        val id = dao.insert(entity)
        assertTrue(id > 0)

        val read = dao.get(id)
        assertNotNull(read)
        assertEquals("角色模板", read!!.title)
        assertEquals("1girl, silver hair", read.content)
        assertEquals("角色", read.tag)
        assertEquals(false, read.isNegative)
    }

    @Test
    fun favorites_observeByTag_filtersCorrectly() = runTest {
        val dao = db.favoritesDao()
        dao.insert(FavoriteEntity(title = "t1", content = "c1", tag = "角色"))
        dao.insert(FavoriteEntity(title = "t2", content = "c2", tag = "场景"))
        dao.insert(FavoriteEntity(title = "t3", content = "c3", tag = "角色"))

        val characterTag = dao.observeByTag("角色").first()
        assertEquals(2, characterTag.size)
        assertTrue(characterTag.all { it.tag == "角色" })
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private fun fakeHistory(prompt: String = "p"): HistoryEntity = HistoryEntity(
        prompt = prompt,
        negativePrompt = "",
        styleKey = "2.5d",
        styleName = "2.5d",
        sizeKey = "竖图",
        sizeCost = 1,
        imageUrl = "",
        imagePath = "",
        thumbBytes = ByteArray(0),
        generationTimeMs = 1000L,
        success = true,
        errorMessage = null
    )
}
