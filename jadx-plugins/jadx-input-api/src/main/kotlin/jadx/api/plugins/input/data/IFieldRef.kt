package jadx.api.plugins.input.data

/**
 * 字段引用接口：定位某个类中的某个字段。
 *
 * **背景**：输入插件解析出的字段信息（如 Dex 的 field_id、class file 的 Field）
 * 都通过本接口暴露"父类 + 名字 + 类型"三要素，供 jadx-core 建立字段节点。
 */
public interface IFieldRef {

	/** @return 字段所属类的完整类型名（如 "com.example.Foo"）*/
	public val parentClassType: String?

	/** @return 字段名 */
	public val name: String?

	/** @return 字段的描述符/类型字符串（如 "I"、"Ljava/lang/String;"）*/
	public val type: String?
}
