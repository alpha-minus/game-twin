package com.twinlore.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EnumerateGuideCacheDirsTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun empty_cache_returns_empty_list() {
        assertEquals(emptyList<File>(), enumerateGuideCacheDirs(tmp.newFolder("cache")))
    }

    @Test
    fun walks_three_roots_at_correct_depth() {
        val cache = tmp.newFolder("cache")
        // guide_cache/<id>/
        val gcA = File(cache, "guide_cache/aaa").apply { mkdirs() }
        val gcB = File(cache, "guide_cache/bbb").apply { mkdirs() }
        // single_guide_render/<id>/
        val sgrC = File(cache, "single_guide_render/ccc").apply { mkdirs() }
        // group_render/<group>/<resource>/  — two groups, three resources total
        val grD = File(cache, "group_render/g1/d1").apply { mkdirs() }
        val grE = File(cache, "group_render/g1/d2").apply { mkdirs() }
        val grF = File(cache, "group_render/g2/d3").apply { mkdirs() }

        val result = enumerateGuideCacheDirs(cache).toSet()
        assertEquals(setOf(gcA, gcB, sgrC, grD, grE, grF), result)
    }

    @Test
    fun missing_root_dirs_are_skipped() {
        val cache = tmp.newFolder("cache")
        File(cache, "guide_cache/only_this_one").apply { mkdirs() }
        val result = enumerateGuideCacheDirs(cache)
        assertEquals(1, result.size)
        assertTrue(result.first().name == "only_this_one")
    }

    @Test
    fun files_at_root_are_ignored() {
        val cache = tmp.newFolder("cache")
        File(cache, "guide_cache").mkdirs()
        File(cache, "guide_cache/loose_file.txt").writeText("nope")
        File(cache, "guide_cache/real_dir").mkdirs()
        val result = enumerateGuideCacheDirs(cache)
        assertEquals(1, result.size)
        assertTrue(result.first().name == "real_dir")
    }

    @Test
    fun group_render_with_no_resource_children_returns_no_leaves() {
        val cache = tmp.newFolder("cache")
        File(cache, "group_render/empty_group").mkdirs()  // no <resource> child
        val result = enumerateGuideCacheDirs(cache)
        assertEquals(emptyList<File>(), result)
    }
}
