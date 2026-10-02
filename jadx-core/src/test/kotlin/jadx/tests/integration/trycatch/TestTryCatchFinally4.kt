package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * finally 内嵌套 try/catch 关闭资源并删除文件：外层 finally 与内层 catch 都应保留。
 */
class TestTryCatchFinally4 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestTryCatchFinally4Fixture.TestCls::class.java))
			.code()
			.containsOne("File file = File.createTempFile(\"test\", \"txt\");")
			.containsOne("OutputStream outputStream = new FileOutputStream(file);")
			.containsOne("outputStream.write(1);")
			.containsOne("} finally {")
			.containsOne("outputStream.close();")
			.containsOne("} catch (IOException e) {")
	}
}
