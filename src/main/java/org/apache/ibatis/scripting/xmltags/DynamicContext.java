/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * 动态 SQL ，用于每次执行 SQL 操作时，记录动态 SQL 处理后的最终 SQL 字符串
 * @author Clinton Begin
 */
public class DynamicContext {

  /**
   * {@link #bindings} _parameter 的键，参数
   */
  public static final String PARAMETER_OBJECT_KEY = "_parameter";

  /**
   * {@link #bindings} _databaseId 的键，数据库编号
   */
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    // <1.2> 设置 OGNL 的属性访问器。其中，OgnlRuntime 是 ognl 库中的类。并且，ContextMap 对应的访问器是 ContextAccessor 类。
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  /**
   * 上下文的参数集合
   */
  private final ContextMap bindings;
  /**
   * 生成后的 SQL
   */
  private final StringJoiner sqlBuilder = new StringJoiner(" ");
  /**
   * 唯一编号。在 {@link org.apache.ibatis.scripting.xmltags.XMLScriptBuilder.ForEachHandler} 使用
   */
  private int uniqueNumber = 0;

  // 当需要使用到 OGNL 表达式时，parameterObject 非空。parameterObject 方法参数，当需要使用到 OGNL 表达式时，parameterObject 才会非空
  public DynamicContext(Configuration configuration, Object parameterObject) {
    // <1> 初始化 bindings 参数，创建 ContextMap 对象
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      //调用 Configuration#newMetaObject(Object object) 方法，创建 MetaObject 对象，实现对 parameterObject 参数的访问
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      boolean existsTypeHandler = configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass());
      bindings = new ContextMap(metaObject, existsTypeHandler);
    } else {
      bindings = new ContextMap(null, false);
    }
    // <2> 添加 bindings 的默认值
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }

  public void bind(String name, Object value) {
    bindings.put(name, value);
  }

  public void appendSql(String sql) {
    sqlBuilder.add(sql);
  }

  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  //每次请求，获得新的序号
  public int getUniqueNumber() {
    return uniqueNumber++;
  }

  /**
   * 是 DynamicContext 的内部静态类，继承 HashMap 类，上下文的参数集合。该类在 HashMap 的基础上，增加支持对 parameterMetaObject 属性的访问
   */
  static class ContextMap extends HashMap<String, Object> {
    private static final long serialVersionUID = 2977601501966151582L;
    /**
     * parameter 对应的 MetaObject 对象
     */
    private final MetaObject parameterMetaObject;
    private final boolean fallbackParameterObject;

    public ContextMap(MetaObject parameterMetaObject, boolean fallbackParameterObject) {
      this.parameterMetaObject = parameterMetaObject;
      this.fallbackParameterObject = fallbackParameterObject;
    }

    @Override
    public Object get(Object key) {
      // 如果有 key 对应的值，直接获得
      String strKey = (String) key;
      if (super.containsKey(strKey)) {
        return super.get(strKey);
      }

      if (parameterMetaObject == null) {
        return null;
      }

      // 从 parameterMetaObject 中，获得 key 对应的属性
      if (fallbackParameterObject && !parameterMetaObject.hasGetter(strKey)) {
        return parameterMetaObject.getOriginalObject();
      } else {
        // issue #61 do not modify the context when reading
        return parameterMetaObject.getValue(strKey);
      }
    }
  }

  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name) {
      Map map = (Map) target;

      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }

      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value) {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}
