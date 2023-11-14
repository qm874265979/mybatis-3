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
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * TransactionalCache 管理器
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  /**
   * Cache 和 TransactionalCache 的映射
   * 为什么是一个 Map 对象呢？
   *  -因为在一次的事务过程中，可能有多个不同的 MappedStatement 操作，而它们可能对应多个 Cache 对象
   */
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  public Object getObject(Cache cache, CacheKey key) {
    // 首先，获得 Cache 对应的 TransactionalCache 对象
    // 然后从 TransactionalCache 对象中，获得 key 对应的值
    return getTransactionalCache(cache).getObject(key);
  }

  public void putObject(Cache cache, CacheKey key, Object value) {
    // 首先，获得 Cache 对应的 TransactionalCache 对象
    // 然后，添加 KV 到 TransactionalCache 对象中
    getTransactionalCache(cache).putObject(key, value);
  }

  //提交所有 TransactionalCache
  //通过调用该方法，TransactionalCache 存储的当前事务的缓存，会同步到其对应的 Cache 对象
  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  private TransactionalCache getTransactionalCache(Cache cache) {
    //从 transactionalCaches 获得 Cache 对象，对应的 TransactionalCache 对象。
    //如果不存在，则创建一个 TransactionalCache 对象，并添加到 transactionalCaches 中
    return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
  }

}
