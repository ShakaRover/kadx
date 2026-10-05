package kadx.core.dex.visitors.gradle

import kadx.api.plugins.input.data.annotations.AnnotationVisibility
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import kadx.core.dex.attributes.nodes.CodeFeaturesAttr
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.IFieldInfoRef
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.regions.SwitchRegion
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.FixSwitchOverEnum
import kadx.core.dex.visitors.KadxVisitor
import kadx.core.dex.visitors.regions.DepthRegionTraversal
import kadx.core.dex.visitors.regions.IRegionIterativeVisitor
import kadx.core.export.GradleInfoStorage
import kadx.core.utils.android.AndroidResourcesUtils
import kadx.core.utils.exceptions.KadxException

/**
 * 检测 `switch` 中使用了「非 final 的 Android 资源常量」的情况。
 *
 * **背景**：Android Gradle 插件在非 final 资源 id 模式下，`R.id.xxx` 不再是编译期常量，
 * 不能直接用作 `switch case`。本 Pass 通过扫描注解（如 `@ViewById`）与
 * `switch` 的 case 键，发现这类用法后设置 [GradleInfoStorage.setNonFinalResIds]，
 * 从而让导出功能生成相应的 gradle 配置。
 *
 * **Kotlin 转换说明**：实现 [IRegionIterativeVisitor]（Kotlin `fun interface`）；
 * 注解值与 `Map.Entry` 遍历改为普通循环；`==` 引用比较改为 `===`。
 */
@KadxVisitor(
	name = "NonFinalResIdsVisitor",
	desc = "Detect usage of android resource constants in cases where constant expressions are required.",
	runAfter = [FixSwitchOverEnum::class],
)
class NonFinalResIdsVisitor :
	AbstractVisitor(),
	IRegionIterativeVisitor {

	private var nonFinalResIdsFlagRequired = false

	private lateinit var gradleInfoStorage: GradleInfoStorage

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		gradleInfoStorage = root.gradleInfoStorage
	}

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (nonFinalResIdsFlagRequired) {
			return false
		}
		val annotationsList = cls.get(KadxAttrType.ANNOTATION_LIST)
		if (visitAnnotationList(annotationsList)) {
			return false
		}
		return super.visit(cls)
	}

	private fun isCustomResourceClass(cls: ClassInfo): Boolean {
		val parentClass = cls.parentClass ?: return false
		return parentClass.shortName == "R" && parentClass.fullName != "android.R"
	}

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		val annotationsList = mth.get(KadxAttrType.ANNOTATION_LIST)
		if (visitAnnotationList(annotationsList)) {
			nonFinalResIdsFlagRequired = true
			return
		}

		if (nonFinalResIdsFlagRequired || !CodeFeaturesAttr.contains(mth, CodeFeaturesAttr.CodeFeature.SWITCH)) {
			return
		}
		DepthRegionTraversal.traverseIterative(mth, this)
	}

	private fun visitAnnotationList(annotationsList: AnnotationsAttr?): Boolean {
		if (annotationsList != null) {
			for (annotation in annotationsList.all) {
				if (annotation.visibility == AnnotationVisibility.SYSTEM) {
					continue
				}
				for ((_, encodedValue) in annotation.values) {
					val value = encodedValue.value
					if (value is IFieldInfoRef && isCustomResourceClass(value.getFieldInfo().declClass)) {
						gradleInfoStorage.isNonFinalResIds = true
						return true
					}
				}
			}
		}
		return false
	}

	override fun visitRegion(mth: MethodNode, region: IRegion): Boolean {
		if (nonFinalResIdsFlagRequired) {
			return false
		}
		if (region is SwitchRegion) {
			return detectSwitchOverResIds(region)
		}
		return false
	}

	private fun detectSwitchOverResIds(switchRegion: SwitchRegion): Boolean {
		for (caseInfo in switchRegion.cases) {
			for (key in caseInfo.keys) {
				if (key is FieldNode) {
					val topParentClass = key.topParentClass
					if (AndroidResourcesUtils.isResourceClass(topParentClass) && "android.R" != topParentClass.fullName) {
						this.nonFinalResIdsFlagRequired = true
						gradleInfoStorage.isNonFinalResIds = true
						return false
					}
				}
			}
		}
		return false
	}
}
