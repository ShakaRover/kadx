package kadx.core.dex.nodes

import kadx.api.ICodeWriter
import kadx.core.codegen.RegionGen
import kadx.core.utils.exceptions.CodegenException

interface IBlock : IContainer {
	val instructions: List<InsnNode>

	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		regionGen.makeSimpleBlock(this, code)
	}
}
