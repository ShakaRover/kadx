package kadx.core.dex.nodes

interface IRegion : IContainer {
	var parent: IRegion?

	val subBlocks: List<IContainer>

	fun replaceSubBlock(oldBlock: IContainer, newBlock: IContainer): Boolean
}
