package kadx.tests.integration.rename

import kadx.api.data.CodeRefType
import kadx.api.data.ICodeRename
import kadx.api.data.IJavaCodeRef
import kadx.api.data.IJavaNodeRef.RefType
import kadx.api.data.impl.KadxCodeData
import kadx.api.data.impl.KadxCodeRef
import kadx.api.data.impl.KadxCodeRename
import kadx.api.data.impl.KadxNodeRef
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
		renames.add(KadxCodeRename(KadxNodeRef.forPkg("kadx.tests"), "renamedPkgTests"))
		renames.add(KadxCodeRename(KadxNodeRef.forPkg("kadx.tests.integration.rename"), "renamedPkgRename"))
		renames.add(KadxCodeRename(KadxNodeRef.forCls(baseClsId), "RenamedTestCls"))
		renames.add(KadxCodeRename(KadxNodeRef.forCls(baseClsId + "\$A"), "RenamedInnerCls"))
		renames.add(KadxCodeRename(KadxNodeRef(RefType.FIELD, baseClsId, "intField:I"), "renamedField"))
		val mthRef = KadxNodeRef(RefType.METHOD, baseClsId, "test(I)I")
		renames.add(KadxCodeRename(mthRef, "renamedTestMth"))
		renames.add(KadxCodeRename(mthRef, KadxCodeRef(CodeRefType.MTH_ARG, 0), "renamedX"))
		val varDeclareRef = if (isJavaInput()) KadxCodeRef.forVar(0, 1) else KadxCodeRef.forVar(0, 0)
		renames.add(KadxCodeRename(mthRef, varDeclareRef, "renamedY"))
		val varUseRef: IJavaCodeRef = if (isJavaInput()) KadxCodeRef.forVar(0, 4) else KadxCodeRef.forVar(1, 0)
		renames.add(KadxCodeRename(mthRef, varUseRef, "renamedZ"))

		val codeData = KadxCodeData()
		codeData.setRenames(renames)
		getArgs().codeData = codeData

		val cls = getClassNode(TestUserRenamesFixture.TestCls::class.java)
		assertThat(cls)
			.decompile()
			.checkCodeOffsets()
			.code()
			.containsOne("package kadx.renamedPkgTests.integration.renamedPkgRename;")
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

		val updVarRename: ICodeRename = KadxCodeRename(mthRef, varUseRef, "anotherZ")
		codeData.setRenames(listOf(updVarRename))
		kadxDecompiler.reloadCodeData()
		assertThat(cls)
			.reloadCode(this)
			.containsOne("int anotherZ = y + 1;")
			.doesNotContain("int z")
			.doesNotContain("int renamedZ")
	}
}
