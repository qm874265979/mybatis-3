/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装器接口，基于 MetaClass 工具类，定义对指定对象的各种操作。或者可以说，ObjectWrapper 是 MetaClass 的指定类的具象化。
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 获得指定属性的值
   *
   * @param prop PropertyTokenizer 对象，相当于键
   * @return 值
   */
  Object get(PropertyTokenizer prop);

  /**
   * 设置属性值
   *
   * @param prop PropertyTokenizer 对象，相当于键
   * @param value 值
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * {@link MetaClass#findProperty(String, boolean)}
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * {@link MetaClass#getGetterNames()}
   */
  String[] getGetterNames();

  /**
   * {@link MetaClass#getSetterNames()}
   */
  String[] getSetterNames();

  /**
   * 获得指定属性的 setting 方法的方法参数
   * {@link MetaClass#getSetterType(String)}
   */
  Class<?> getSetterType(String name);

  /**
   * 获得指定属性的 getting 方法的返回值
   * {@link MetaClass#getGetterType(String)}
   */
  Class<?> getGetterType(String name);

  /**
   * 是否有指定属性的 setting 方法
   * {@link MetaClass#hasSetter(String)}
   */
  boolean hasSetter(String name);

  /**
   * 是否有指定属性的 getting 方法
   * {@link MetaClass#hasGetter(String)}
   */
  boolean hasGetter(String name);

  /**
   * 创建指定属性的值
   * 关于这个方法，可能比较难理解，可以调试下 MetaObjectTest#shouldGetAndSetNestedMapPairUsingArraySyntax() 这个单元测试方法。
   * {@link MetaObject#forObject(Object, ObjectFactory, ObjectWrapperFactory, ReflectorFactory)}
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  /**
   * 是否为集合
   */
  boolean isCollection();

  /**
   * 添加元素到集合
   */
  void add(Object element);

  /**
   * 添加多个元素到集合
   */
  <E> void addAll(List<E> element);

}
