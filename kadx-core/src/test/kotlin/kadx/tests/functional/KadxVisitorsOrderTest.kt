package kadx.tests.functional

import kadx.api.KadxArgs
import kadx.core.Kadx
import kadx.core.dex.visitors.IDexTreeVisitor
import kadx.core.dex.visitors.KadxVisitor
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 校验 kadx 各 Pass 列表的注册顺序满足 [KadxVisitor] 声明的 before/after 约束。
 */
class KadxVisitorsOrderTest {

	@Test
	fun testOrder() {
		checkPassList(Kadx.getPassesList(KadxArgs()))
		checkPassList(Kadx.preDecompilePassesList)
		checkPassList(Kadx.fallbackPassesList)
	}

	private fun checkPassList(passes: List<IDexTreeVisitor>) {
		val errors = check(passes)
		for (str in errors) {
			LOG.error(str)
		}
		assertThat(errors).isEmpty()
	}

	private companion object {
		private val LOG: Logger = LoggerFactory.getLogger(KadxVisitorsOrderTest::class.java)

		private fun check(passes: List<IDexTreeVisitor>): List<String> {
			val classList = ArrayList<Class<*>>(passes.size)
			for (pass in passes) {
				classList.add(pass.javaClass)
			}
			val errors = ArrayList<String>()

			val names = HashSet<String>()
			val passClsSet = HashSet<Class<*>>()
			for (i in passes.indices) {
				val pass = passes[i]
				val passClass = pass.javaClass
				val info = passClass.getAnnotation(KadxVisitor::class.java)
				if (info == null) {
					LOG.warn("No KadxVisitor annotation for visitor: {}", passClass.name)
					continue
				}
				val firstOccurrence = passClsSet.add(passClass)
				val passName = passClass.simpleName
				if (firstOccurrence && !names.add(passName)) {
					errors.add("Visitor name conflict: " + passName + ", class: " + passClass.name)
				}
				for (cls in info.runBefore) {
					val beforeIndex = classList.indexOf(cls.java)
					if (beforeIndex != -1 && beforeIndex < i) {
						errors.add("Pass " + passName + " must be before " + cls.java.simpleName)
					}
				}
				for (cls in info.runAfter) {
					if (classList.indexOf(cls.java) > i) {
						errors.add("Pass " + passName + " must be after " + cls.java.simpleName)
					}
				}
			}
			return errors
		}
	}
}
