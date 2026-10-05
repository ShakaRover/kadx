package kadx.core.dex.nodes.utils

import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.RootNode
import kadx.tests.api.utils.TestUtils
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Random

class SelectFromDuplicatesTest {

	private lateinit var root: RootNode

	@BeforeEach
	fun init() {
		val args = KadxArgs()
		args.addInputFile(TestUtils.getFileForSample("test-samples/hello.dex"))
		val decompiler = KadxDecompiler(args)
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
