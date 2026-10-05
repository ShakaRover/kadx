package kadx.plugins.input.java.data.attributes

/**
 * Java class 文件 attribute（属性）的标记接口。
 *
 * **做什么**：所有 .class 属性解析结果（Code、InnerClasses、注解表等）的统一父类型，
 * 让 [JavaAttrStorage] 可以用同一种方式存放和按 [JavaAttrType] 查找不同种类的属性。
 *
 * **为什么是空接口**：各类 attribute 之间没有公共行为，只需要一个共同类型标签
 * 用于泛型约束（`JavaAttrType<T extends IJavaAttribute>`）。
 */
interface IJavaAttribute
