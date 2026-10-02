package jadx.tests.functional

import jadx.api.JadxArgs
import jadx.core.Jadx
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 校验 jadx 各 Pass 列表的注册顺序满足 [JadxVisitor] 声明的 before/after 约束。
 */
class JadxVisitorsOrderTest {

	@Test
	fun testOrder() {
		checkPassList(Jadx.getPassesList(JadxArgs()))
		checkPassList(Jadx.getPreDecompilePassesList())
		checkPassList(Jadx.getFallbackPassesList())
	}

	private fun checkPassList(passes: List<IDexTreeVisitor>) {
		val errors = check(passes)
		for (str in errors) {
			LOG.error(str)
		}
		assertThat(errors).isEmpty()
	}

	private companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxVisitorsOrderTest::class.java)

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
				val info = passClass.getAnnotation(JadxVisitor::class.java)
				if (info == null) {
					LOG.warn("No JadxVisitor annotation for visitor: {}", passClass.name)
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
