package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import jadx.tests.api.utils.assertj.JadxCodeAssertions
import org.junit.jupiter.api.Test

/**
 * try 位于循环体内：循环与 try 的多种等价还原形式均应被接受。
 */
class TestLoopInTryCatch : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.oneOf(
				{ c: JadxCodeAssertions ->
					c.containsLines(
						2,
						"int i;",
						"while (true) {",
						"    try {",
						"        i = getI();",
						"    } catch (RuntimeException unused) {",
						"        return;",
						"    }",
						"    if (i == 1 || i == 2) {",
						"        break;",
						"    }",
						"}",
						"if (i != 1) {",
						"    getI();",
						"}",
					)
				},
				{ c: JadxCodeAssertions ->
					c.containsLines(
						2,
						"int i;",
						"while (true) {",
						"    try {",
						"        i = getI();",
						"        if (i == 1 || i == 2) {",
						"            break;",
						"        }",
						"    } catch (RuntimeException unused) {",
						"        return;",
						"    }",
						"}",
						"if (i != 1) {",
						"    getI();",
						"}",
					)
				},
				// TODO: weird result but correct, better to not use do-while if not really needed
				{ c: JadxCodeAssertions ->
					c.containsLines(
						2,
						"int i;",
						"do {",
						"    try {",
						"        i = getI();",
						"        if (i == 1) {",
						"            break;",
						"        }",
						"    } catch (RuntimeException unused) {",
						"        return;",
						"    }",
						"} while (i != 2);",
						"if (i != 1) {",
						"    getI();",
						"}",
					)
				},
			)
	}
}
