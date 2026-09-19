package jadx.core.dex.nodes

import jadx.api.ICodeWriter
import jadx.core.codegen.RegionGen
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.utils.exceptions.CodegenException

interface IContainer : IAttributeNode {
	/**
	 * Unique id for use in 'toString()' method
	 */
	fun baseString(): String

	/**
	 * Dispatch to needed generate method in RegionGen
	 */
	fun generate(regionGen: RegionGen, code: ICodeWriter): Unit = throw CodegenException("Code generate not implemented for container: ${javaClass.simpleName}")
}
