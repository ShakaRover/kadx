package jadx.core.dex.nodes

interface IRegion : IContainer {
	var parent: IRegion?

	fun getSubBlocks(): List<IContainer>

	fun replaceSubBlock(oldBlock: IContainer, newBlock: IContainer): Boolean
}
