package org.apache.ibatis.reflection;

import org.apache.ibatis.domain.misc.CustomBeanWrapperFactory;
import org.apache.ibatis.domain.misc.RichType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

/**
 * @author: QM
 * @since: 2023/10/20 20:47
 */
class BeanWrapperTest {

  /**
   * 获得指定属性的 getting 方法的返回值
   */
  @Test
  void getGetterType() {
    RichType object = new RichType();

    if (true) {
      object.setRichType(new RichType());
      object.getRichType().setRichMap(new HashMap());
      object.getRichType().getRichMap().put("nihao", "123");
    }

    MetaObject meta = MetaObject.forObject(object, SystemMetaObject.DEFAULT_OBJECT_FACTORY, new CustomBeanWrapperFactory(), new DefaultReflectorFactory());
    Class<?> clazz = meta.getObjectWrapper().getGetterType("richType.richMap.nihao");
    System.out.println(clazz);
  }
}
