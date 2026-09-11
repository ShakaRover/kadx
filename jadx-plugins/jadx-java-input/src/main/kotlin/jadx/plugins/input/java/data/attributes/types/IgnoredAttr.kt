package jadx.plugins.input.java.data.attributes.types

import jadx.plugins.input.java.data.attributes.IJavaAttribute

/**
 * 被忽略的 attribute 占位类型。
 *
 **做什么**：Deprecated、Synthetic、Module 等属性携带的信息已由注解或 access flag 表达，
 * 无需单独解析数据；[JavaAttrType] 里这些条目绑定 null 读取器并标记为本类型，
 * 仅用于"知道这个属性存在但跳过它"。
 */
class IgnoredAttr : IJavaAttribute
