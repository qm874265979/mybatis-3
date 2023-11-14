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
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 实现 Executor 接口，支持二级缓存的 Executor 的实现类
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class CachingExecutor implements Executor {

  /**
   * 被委托的 Executor 对象
   */
  private final Executor delegate;
  /**
   * TransactionalCacheManager 对象
   */
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  public CachingExecutor(Executor delegate) {
    // <1> 设置 delegate 属性，为被委托的 Executor 对象
    this.delegate = delegate;
    // <2> 调用 delegate 属性的 #setExecutorWrapper(Executor executor) 方法，设置 delegate 被当前执行器所包装
    delegate.setExecutorWrapper(this);
  }

  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      // issues #499, #524 and #573
      // 如果强制回滚，则回滚 TransactionalCacheManager
      if (forceRollback) {
        tcm.rollback();
      } else {
        // 如果强制提交，则提交 TransactionalCacheManager
        tcm.commit();
      }
    } finally {
      // 执行 delegate 对应的方法
      delegate.close(forceRollback);
    }
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    // 如果需要清空缓存，则进行清空
    flushCacheIfRequired(ms);
    // 执行 delegate 对应的方法
    return delegate.update(ms, parameterObject);
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    // 如果需要清空缓存，则进行清空
    flushCacheIfRequired(ms);
    // 执行 delegate 对应的方法
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // 获得 BoundSql 对象
    BoundSql boundSql = ms.getBoundSql(parameterObject);
    // 创建 CacheKey 对象
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
    // 查询
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
      throws SQLException {
    // <1> 调用 MappedStatement#getCache() 方法，获得 Cache 对象，即当前 MappedStatement 对象的二级缓存
    Cache cache = ms.getCache();
    // <2> 如果有 Cache 对象，说明该 MappedStatement 对象，有设置二级缓存
    if (cache != null) {
      // <2.1> 调用 #flushCacheIfRequired(MappedStatement ms) 方法，如果需要清空缓存，则进行清空
      flushCacheIfRequired(ms);
      // <2.2>当 MappedStatement#isUseCache() 方法，返回 true 时，才使用二级缓存。默认开启。可通过 @Options(useCache = false) 或 <select useCache="false"> 方法，关闭
      if (ms.isUseCache() && resultHandler == null) {
        // 暂时忽略，存储过程相关
        ensureNoOutParams(ms, boundSql);
        @SuppressWarnings("unchecked")
        // <2.3> 从二级缓存中，获取结果
        List<E> list = (List<E>) tcm.getObject(cache, key);
        if (list == null) {
          // <2.4.1> 如果不存在，调用 delegate 属性的 #query(...) 方法，再从数据库中查询
          list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          // <2.4.2> 调用 TransactionalCacheManager#put(Cache cache, CacheKey key, Object value) 方法，缓存结果到二级缓存中。
          // 当然，正如上文所言，实际上，此处结果还没添加到二级缓存中。那具体是怎么样的呢？答案见 TransactionalCache
          tcm.putObject(cache, key, list); // issue #578 and #116
        }
        // <2.5> 如果存在，则直接返回结果
        return list;
      }
    }
    // <3> 如果没有 Cache 对象，说明该 MappedStatement 对象，未设置二级缓存，则调用 delegate 属性的 #query(...) 方法，直接从数据库中查询
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }

  @Override
  public void commit(boolean required) throws SQLException {
    // 执行 delegate 对应的方法
    delegate.commit(required);
    // 提交 TransactionalCacheManager
    tcm.commit();
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    try {
      // 执行 delegate 对应的方法
      delegate.rollback(required);
    } finally {
      if (required) {
        // 回滚 TransactionalCacheManager
        tcm.rollback();
      }
    }
  }

  private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
        if (parameterMapping.getMode() != ParameterMode.IN) {
          throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
        }
      }
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return delegate.isCached(ms, key);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    delegate.deferLoad(ms, resultObject, property, key, targetType);
  }

  @Override
  public void clearLocalCache() {
    delegate.clearLocalCache();
  }

  /**
   * 通过 @Options(flushCache = Options.FlushCachePolicy.TRUE) 或 <select flushCache="true"> 方式，开启需要清空缓存
   */
  private void flushCacheIfRequired(MappedStatement ms) {
    Cache cache = ms.getCache();
    if (cache != null && ms.isFlushCacheRequired()) {// 是否需要清空缓存
      // 调用 TransactionalCacheManager#clear() 方法，清空缓存。
      // 注意，此时清空的仅仅是当前事务中查询数据产生的缓存。
      // 而真正的清空，在事务的提交时。这是为什么呢？还是因为二级缓存是跨 Session 共享缓存，在事务尚未结束时，不能对二级缓存做任何修改
      tcm.clear(cache);
    }
  }

  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}
