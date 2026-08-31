package jadx.api.plugins.input.insns.custom

/**
 * 自定义指令载荷标记接口。
 *
 * **背景**：某些输入格式（Dex / class file）的指令带有"附加数据块"，
 * 如 switch 跳转表的键值对、数组初始化元素等。这些附加数据不遵循普通操作数编码，
 * 统一用 payload（载荷）表示，本接口是所有载荷类型的公共父类型。
 *
 * **具体子类型**：[ISwitchPayload]（switch 跳转表）、[IArrayPayload]（数组元素）。
 */
public interface ICustomPayload
