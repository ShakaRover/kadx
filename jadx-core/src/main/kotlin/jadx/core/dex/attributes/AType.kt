package jadx.core.dex.attributes

import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.codegen.utils.CodeComment
import jadx.core.dex.attributes.nodes.AnonymousClassAttr
import jadx.core.dex.attributes.nodes.ClassTypeVarsAttr
import jadx.core.dex.attributes.nodes.CodeFeaturesAttr
import jadx.core.dex.attributes.nodes.DeclareVariablesAttr
import jadx.core.dex.attributes.nodes.DecompileModeOverrideAttr
import jadx.core.dex.attributes.nodes.EdgeInsnAttr
import jadx.core.dex.attributes.nodes.EnumClassAttr
import jadx.core.dex.attributes.nodes.EnumMapAttr
import jadx.core.dex.attributes.nodes.ExcSplitCrossAttr
import jadx.core.dex.attributes.nodes.FieldReplaceAttr
import jadx.core.dex.attributes.nodes.ForceReturnAttr
import jadx.core.dex.attributes.nodes.GenericInfoAttr
import jadx.core.dex.attributes.nodes.InlinedAttr
import jadx.core.dex.attributes.nodes.JadxCommentsAttr
import jadx.core.dex.attributes.nodes.JadxError
import jadx.core.dex.attributes.nodes.JumpInfo
import jadx.core.dex.attributes.nodes.LocalVarsDebugInfoAttr
import jadx.core.dex.attributes.nodes.LoopInfo
import jadx.core.dex.attributes.nodes.LoopLabelAttr
import jadx.core.dex.attributes.nodes.MethodBridgeAttr
import jadx.core.dex.attributes.nodes.MethodInlineAttr
import jadx.core.dex.attributes.nodes.MethodOverrideAttr
import jadx.core.dex.attributes.nodes.MethodReplaceAttr
import jadx.core.dex.attributes.nodes.MethodThrowsAttr
import jadx.core.dex.attributes.nodes.MethodTypeVarsAttr
import jadx.core.dex.attributes.nodes.PhiListAttr
import jadx.core.dex.attributes.nodes.RegDebugInfoAttr
import jadx.core.dex.attributes.nodes.RegionRefAttr
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.attributes.nodes.SkipMethodArgsAttr
import jadx.core.dex.attributes.nodes.SpecialEdgeAttr
import jadx.core.dex.attributes.nodes.TmpEdgeAttr
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.trycatch.CatchAttr
import jadx.core.dex.trycatch.ExcHandlerAttr
import jadx.core.dex.trycatch.TryCatchBlockAttr

/**
 * 属性类型枚举表（这里用“类型对象”而非 Java enum 实现）。
 *
 * **设计目的**：为每种属性提供一个**类型安全的唯一标识**，配合 [AttributeStorage]
 * 的 `Map<IJadxAttrType<?>, IJadxAttribute>` 使用。
 * 泛型参数 `T` 记录了该类型对应的属性实现类，这样 `storage.get(AType.X)` 无需强转即可
 * 得到正确类型（见 [AttributeStorage.get]）。
 *
 * **为什么不用 enum？** enum 无法携带“泛型类型参数”这层信息；而这里 `AType<EnumClassAttr>`
 * 之类的精确类型能让编译器帮忙做类型检查，避免运行期 `ClassCastException`。
 *
 * **Kotlin 转换说明**：所有常量放入 `companion object`，Kotlin 侧写 `AType.ENUM_CLASS`。
 *
 * @param T 该属性类型对应的属性实现类
 */
class AType<T : IJadxAttribute> : IJadxAttrType<T> {

	companion object {
		// ==================== 类 / 方法 / 字段 / 指令 通用 ====================

		/** 代码注释列表 */
		val CODE_COMMENTS: AType<AttrList<CodeComment>> = AType()

		/** 重命名原因 */
		val RENAME_REASON: AType<RenameReasonAttr> = AType()

		// ==================== 类 / 方法 通用 ====================

		/** 反编译失败的错误列表 */
		val JADX_ERROR: AType<AttrList<JadxError>> = AType()

		/** 反编译附加信息（注释） */
		val JADX_COMMENTS: AType<JadxCommentsAttr> = AType()

		// ==================== 仅类 ====================

		val ENUM_CLASS: AType<EnumClassAttr> = AType()

		val ENUM_MAP: AType<EnumMapAttr> = AType()

		val CLASS_TYPE_VARS: AType<ClassTypeVarsAttr> = AType()

		val ANONYMOUS_CLASS: AType<AnonymousClassAttr> = AType()

		val INLINED: AType<InlinedAttr> = AType()

		val DECOMPILE_MODE_OVERRIDE: AType<DecompileModeOverrideAttr> = AType()

		// ==================== 仅字段 ====================

		val FIELD_INIT_INSN: AType<FieldInitInsnAttr> = AType()

		val FIELD_REPLACE: AType<FieldReplaceAttr> = AType()

		// ==================== 仅方法 ====================

		val LOCAL_VARS_DEBUG_INFO: AType<LocalVarsDebugInfoAttr> = AType()

		val METHOD_INLINE: AType<MethodInlineAttr> = AType()

		val METHOD_REPLACE: AType<MethodReplaceAttr> = AType()

		val BRIDGED_BY: AType<MethodBridgeAttr> = AType()

		val SKIP_MTH_ARGS: AType<SkipMethodArgsAttr> = AType()

		val METHOD_OVERRIDE: AType<MethodOverrideAttr> = AType()

		val METHOD_TYPE_VARS: AType<MethodTypeVarsAttr> = AType()

		val TRY_BLOCKS_LIST: AType<AttrList<TryCatchBlockAttr>> = AType()

		val METHOD_CODE_FEATURES: AType<CodeFeaturesAttr> = AType()

		val METHOD_THROWS: AType<MethodThrowsAttr> = AType()

		// ==================== 仅区域（region） ====================

		val DECLARE_VARIABLES: AType<DeclareVariablesAttr> = AType()

		// ==================== 仅基本块（block） ====================

		val PHI_LIST: AType<PhiListAttr> = AType()

		val FORCE_RETURN: AType<ForceReturnAttr> = AType()

		val LOOP: AType<AttrList<LoopInfo>> = AType()

		val EDGE_INSN: AType<AttrList<EdgeInsnAttr>> = AType()

		val SPECIAL_EDGE: AType<AttrList<SpecialEdgeAttr>> = AType()

		val TMP_EDGE: AType<TmpEdgeAttr> = AType()

		val TRY_BLOCK: AType<TryCatchBlockAttr> = AType()

		val EXC_SPLIT_CROSS: AType<ExcSplitCrossAttr> = AType()

		// ==================== 基本块或指令 ====================

		val EXC_HANDLER: AType<ExcHandlerAttr> = AType()

		val EXC_CATCH: AType<CatchAttr> = AType()

		// ==================== 仅指令 ====================

		val LOOP_LABEL: AType<LoopLabelAttr> = AType()

		val JUMP: AType<AttrList<JumpInfo>> = AType()

		val METHOD_DETAILS: AType<IMethodDetails> = AType()

		val GENERIC_INFO: AType<GenericInfoAttr> = AType()

		val REGION_REF: AType<RegionRefAttr> = AType()

		// ==================== 仅寄存器 ====================

		val REG_DEBUG_INFO: AType<RegDebugInfoAttr> = AType()
	}
}
