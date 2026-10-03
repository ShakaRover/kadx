package jadx.tests.integration.rename

import jadx.api.data.CodeRefType
import jadx.api.data.ICodeRename
import jadx.api.data.IJavaCodeRef
import jadx.api.data.IJavaNodeRef.RefType
import jadx.api.data.impl.JadxCodeData
import jadx.api.data.impl.JadxCodeRef
import jadx.api.data.impl.JadxCodeRename
import jadx.api.data.impl.JadxNodeRef
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 用户重命名：包 / 类 / 内部类 / 字段 / 方法 / 方法参数 / 局部变量都可重命名，
 * 且代码偏移检查与动态更新（reloadCodeData）都要正确。
 */
class TestUserRenames : IntegrationTest() {

	@Test
	fun test() {
		getArgs().isDeobfuscationOn = false

		val renames = ArrayList<ICodeRename>()
		val baseClsId = TestUserRenamesFixture.TestCls::class.java.name
		renames.add(JadxCodeRename(JadxNodeRef.forPkg("jadx.tests"), "renamedPkgTests"))
		renames.add(JadxCodeRename(JadxNodeRef.forPkg("jadx.tests.integration.rename"), "renamedPkgRename"))
		renames.add(JadxCodeRename(JadxNodeRef.forCls(baseClsId), "RenamedTestCls"))
		renames.add(JadxCodeRename(JadxNodeRef.forCls(baseClsId + "\$A"), "RenamedInnerCls"))
		renames.add(JadxCodeRename(JadxNodeRef(RefType.FIELD, baseClsId, "intField:I"), "renamedField"))
		val mthRef = JadxNodeRef(RefType.METHOD, baseClsId, "test(I)I")
		renames.add(JadxCodeRename(mthRef, "renamedTestMth"))
		renames.add(JadxCodeRename(mthRef, JadxCodeRef(CodeRefType.MTH_ARG, 0), "renamedX"))
		val varDeclareRef = if (isJavaInput()) JadxCodeRef.forVar(0, 1) else JadxCodeRef.forVar(0, 0)
		renames.add(JadxCodeRename(mthRef, varDeclareRef, "renamedY"))
		val varUseRef: IJavaCodeRef = if (isJavaInput()) JadxCodeRef.forVar(0, 4) else JadxCodeRef.forVar(1, 0)
		renames.add(JadxCodeRename(mthRef, varUseRef, "renamedZ"))

		val codeData = JadxCodeData()
		codeData.setRenames(renames)
		getArgs().codeData = codeData

		val cls = getClassNode(TestUserRenamesFixture.TestCls::class.java)
		assertThat(cls)
			.decompile()
			.checkCodeOffsets()
			.code()
			.containsOne("package jadx.renamedPkgTests.integration.renamedPkgRename;")
			.containsOne("public class RenamedTestCls {")
			.containsOne("private int renamedField")
			.containsOne("public static class RenamedInnerCls {")
			.containsOne("public int renamedTestMth(int renamedX) {")
			.containsOne("int renamedY = renamedX + \"test\".length();")
			.containsOne("int renamedZ = renamedY + 1;")
			.containsOne("return renamedZ;")

		val code = cls.getCode().codeStr
		assertThat(cls)
			.reloadCode(this)
			.isEqualTo(code)

		val updVarRename: ICodeRename = JadxCodeRename(mthRef, varUseRef, "anotherZ")
		codeData.setRenames(listOf(updVarRename))
		jadxDecompiler.reloadCodeData()
		assertThat(cls)
			.reloadCode(this)
			.containsOne("int anotherZ = y + 1;")
			.doesNotContain("int z")
			.doesNotContain("int renamedZ")
	}
}
