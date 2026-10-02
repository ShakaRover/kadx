package jadx.core.dex.nodes

import jadx.api.ICodeWriter
import jadx.core.codegen.RegionGen
import jadx.core.utils.exceptions.CodegenException

interface IBlock : IContainer {
	fun getInstructions(): List<InsnNode>

	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		regionGen.makeSimpleBlock(this, code)
	}
}
