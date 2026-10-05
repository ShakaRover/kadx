package kadx.api.plugins.input.data

import kadx.api.plugins.input.data.attributes.IKadxAttribute

/**
 * 字段数据接口：一个完整字段的元信息（引用 + 访问标志 + 自定义属性）。
 *
 * **背景**：输入插件解析出的每个字段都实现本接口，kadx-core 据此创建
 * [kadx.api.dex.tree.FieldNode]。继承自 [IFieldRef] 获得"父类/名字/类型"三要素。
 */
public interface IFieldData : IFieldRef {

	/** @return 访问标志位（见 [AccessFlags]，如 ACC_PUBLIC、ACC_STATIC）*/
	public val accessFlags: Int

	/** @return 字段携带的自定义属性列表（注解等，可为空列表）*/
	public val attributes: List<IKadxAttribute>
}
