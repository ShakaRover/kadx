package jadx.core.dex.nodes.utils

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.RootNode
import jadx.tests.api.utils.TestUtils
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Random

class SelectFromDuplicatesTest {

	private lateinit var root: RootNode

	@BeforeEach
	fun init() {
		val args = JadxArgs()
		args.addInputFile(TestUtils.getFileForSample("test-samples/hello.dex"))
		val decompiler = JadxDecompiler(args)
		decompiler.load()
		root = checkNotNull(decompiler.getRoot())
	}

	@Test
	fun testSelectBySource() {
		selectBySources(0, false, "classes.dex", "classes2.dex")
		selectBySources(2, false, "classes10.dex", "classes20.dex", "classes2.dex")
	}

	@RepeatedTest(10)
	fun testSelectBySourceShuffled() {
		selectFirstByShuffleSources("classes.dex", "classes2.dex", "classes4.dex")
		selectFirstByShuffleSources("classes2.dex", "classes10.dex", "classes20.dex")
		selectFirstByShuffleSources("classes10.dex", "classes1.dex", "classes01.dex", "classes000.dex", "classes02.dex")
	}

	private fun selectFirstByShuffleSources(vararg sources: String) {
		selectBySources(0, true, *sources)
	}

	private fun selectBySources(selectedPos: Int, shuffle: Boolean, vararg sources: String) {
		val clsList = sources.map { buildClassNodeBySource(it) }.toMutableList()
		val expected = clsList[selectedPos]
		if (shuffle) {
			clsList.shuffle(Random(System.currentTimeMillis() + System.nanoTime()))
		}
		val selectedCls = SelectFromDuplicates.process(clsList)
		assertThat(selectedCls)
			.describedAs("Expect %s, but got %s from list: %s", expected, selectedCls, clsList)
			.isSameAs(expected)
	}

	private fun buildClassNodeBySource(clsSource: String): ClassNode {
		val clsInfo = ClassInfo.fromName(root, "ClassFromSource:" + clsSource)
		val cls = ClassNode.addSyntheticClass(root, clsInfo, 0)
		cls.inputFileNameValue = clsSource
		return cls
	}
}
