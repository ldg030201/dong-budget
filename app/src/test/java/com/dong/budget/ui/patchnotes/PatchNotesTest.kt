package com.dong.budget.ui.patchnotes

import com.dong.budget.BuildConfig
import com.dong.budget.data.update.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchNotesTest {
    private val current = requireNotNull(AppVersion.parse(BuildConfig.VERSION_NAME))

    @Test
    fun `지금 버전의 패치노트가 있다`() {
        // 버전을 올리면서 패치노트를 빠뜨리면 여기서 걸린다
        assertTrue(
            "${BuildConfig.VERSION_NAME} 패치노트가 없다",
            PATCH_NOTES.any { AppVersion.parse(it.version) == current },
        )
    }

    @Test
    fun `버전은 최신이 위에 오고 겹치지 않는다`() {
        val versions = PATCH_NOTES.map { requireNotNull(AppVersion.parse(it.version)) { "버전 형식이 이상하다: ${it.version}" } }
        assertEquals(versions.sortedDescending(), versions)
        assertEquals(versions.size, versions.toSet().size)
    }

    @Test
    fun `이미 낸 버전에는 날짜가 있다`() {
        PATCH_NOTES
            .filter { requireNotNull(AppVersion.parse(it.version)) <= current }
            .forEach { assertNotNull("${it.version} 날짜가 없다", it.date) }
    }

    @Test
    fun `신규 메뉴는 그 메뉴가 처음 나오는 버전에만 붙는다`() {
        // 목록은 최신이 위라서 뒤쪽이 더 오래된 버전이다
        PATCH_NOTES.forEachIndexed { index, release ->
            release.menus.filter { it.isNew }.forEach { menu ->
                val older = PATCH_NOTES.drop(index + 1).filter { old -> old.menus.any { it.menu == menu.menu } }
                assertTrue("${release.version} ${menu.menu} 는 ${older.map { it.version }} 에 이미 있다", older.isEmpty())
            }
        }
    }

    @Test
    fun `빈 버전이나 빈 메뉴가 없다`() {
        PATCH_NOTES.forEach { release ->
            assertTrue("${release.version} 에 메뉴가 없다", release.menus.isNotEmpty())
            release.menus.forEach { menu ->
                assertTrue("${release.version} ${menu.menu} 가 비었다", menu.changes.isNotEmpty())
                menu.changes.forEach { assertTrue(it.text.isNotBlank()) }
            }
        }
    }
}
