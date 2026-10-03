package jadx.core.deobf

import jadx.api.JadxArgs
import jadx.api.deobf.IAliasProvider
import jadx.api.deobf.IRenameCondition
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.utils.exceptions.JadxException

/**
 * 反混淆主 pass：遍历整棵 dex 树，按条件把包 / 类 / 字段 / 方法重命名为别名。
 *
 * **执行流程**（[init]）：
 * 1. 若未开启反混淆（`args.isDeobfuscationOn == false`）则直接返回；
 * 2. 构建 [DeobfPresets]（映射文件）；
 * 3. 若映射文件模式要求读取，则加载已有映射并应用（保证多次运行得到相同名字）；
 * 4. 用参数里的别名提供者（[IAliasProvider]）与重命名条件（[IRenameCondition]）执行
 *    [process]。
 *
 * [process] 本身是静态入口：先处理包（包有更新时需要触发一次包结构刷新），
 * 再逐个类处理其字段与方法。
 */
class DeobfuscatorVisitor : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun init(root: RootNode) {
		val args: JadxArgs = root.args
		if (!args.isDeobfuscationOn) {
			return
		}
		val mapping = DeobfPresets.build(root)
		if (args.generatedRenamesMappingFileMode.shouldRead()) {
			if (mapping.load()) {
				mapping.apply(root)
			}
		}
		val aliasProvider = args.aliasProvider
		val renameCondition = args.renameCondition
		mapping.initIndexes(aliasProvider)
		process(root, renameCondition, aliasProvider)
	}

	companion object {
		/**
		 * 按 [renameCondition] 判断是否需要重命名，并用 [aliasProvider] 生成别名。
		 *
		 * 注意：包的别名会改变包的层级结构，因此只要有任意一个包被重命名，就必须调用
		 * [RootNode.runPackagesUpdate] 刷新包节点。
		 */
		fun process(root: RootNode, renameCondition: IRenameCondition, aliasProvider: IAliasProvider) {
			var pkgUpdated = false
			for (pkg in root.getPackages()) {
				if (renameCondition.shouldRename(pkg)) {
					val alias = aliasProvider.forPackage(pkg)
					if (alias != null) {
						pkg.rename(alias, false)
						pkgUpdated = true
					}
				}
			}
			if (pkgUpdated) {
				root.runPackagesUpdate()
			}

			for (cls in root.getClasses()) {
				if (renameCondition.shouldRename(cls)) {
					val clsAlias = aliasProvider.forClass(cls)
					if (clsAlias != null) {
						cls.rename(clsAlias)
					}
				}
				for (fld in cls.fields) {
					if (renameCondition.shouldRename(fld)) {
						val fldAlias = aliasProvider.forField(fld)
						if (fldAlias != null) {
							fld.rename(fldAlias)
						}
					}
				}
				for (mth in cls.methods) {
					if (renameCondition.shouldRename(mth)) {
						val mthAlias = aliasProvider.forMethod(mth)
						if (mthAlias != null) {
							mth.rename(mthAlias)
						}
					}
				}
			}
		}
	}

	override fun getName(): String = "DeobfuscatorVisitor"
}
