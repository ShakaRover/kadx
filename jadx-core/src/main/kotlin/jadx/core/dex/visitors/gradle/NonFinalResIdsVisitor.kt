package jadx.core.dex.visitors.gradle

import jadx.api.plugins.input.data.annotations.AnnotationVisibility
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import jadx.core.dex.attributes.nodes.CodeFeaturesAttr
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.IFieldInfoRef
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.regions.SwitchRegion
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.FixSwitchOverEnum
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.regions.DepthRegionTraversal
import jadx.core.dex.visitors.regions.IRegionIterativeVisitor
import jadx.core.export.GradleInfoStorage
import jadx.core.utils.android.AndroidResourcesUtils
import jadx.core.utils.exceptions.JadxException

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
@JadxVisitor(
	name = "NonFinalResIdsVisitor",
	desc = "Detect usage of android resource constants in cases where constant expressions are required.",
	runAfter = [FixSwitchOverEnum::class],
)
class NonFinalResIdsVisitor :
	AbstractVisitor(),
	IRegionIterativeVisitor {

	private var nonFinalResIdsFlagRequired = false

	private lateinit var gradleInfoStorage: GradleInfoStorage

	@Throws(JadxException::class)
	override fun init(root: RootNode) {
		gradleInfoStorage = root.gradleInfoStorage
	}

	@Throws(JadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (nonFinalResIdsFlagRequired) {
			return false
		}
		val annotationsList = cls.get(JadxAttrType.ANNOTATION_LIST)
		if (visitAnnotationList(annotationsList)) {
			return false
		}
		return super.visit(cls)
	}

	private fun isCustomResourceClass(cls: ClassInfo): Boolean {
		val parentClass = cls.parentClass ?: return false
		return parentClass.shortName == "R" && parentClass.fullName != "android.R"
	}

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		val annotationsList = mth.get(JadxAttrType.ANNOTATION_LIST)
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
