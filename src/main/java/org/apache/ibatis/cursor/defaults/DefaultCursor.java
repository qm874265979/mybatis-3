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
package org.apache.ibatis.cursor.defaults;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * <p>实现 Cursor 接口，默认 Cursor 实现类</p>
 * This is the default implementation of a MyBatis Cursor.
 * This implementation is not thread safe.
 *
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
public class DefaultCursor<T> implements Cursor<T> {

  // ResultSetHandler stuff
  private final DefaultResultSetHandler resultSetHandler;
  private final ResultMap resultMap;
  private final ResultSetWrapper rsw;
  private final RowBounds rowBounds;
  /**
   * ObjectWrapperResultHandler 对象
   */
  protected final ObjectWrapperResultHandler<T> objectWrapperResultHandler = new ObjectWrapperResultHandler<>();

  /**
   * CursorIterator 对象，游标迭代器。
   */
  private final CursorIterator cursorIterator = new CursorIterator();
  /**
   * 是否开始迭代
   * {@link #iterator()}
   */
  private boolean iteratorRetrieved;

  /**
   * 游标状态
   */
  private CursorStatus status = CursorStatus.CREATED;
  /**
   * 已完成映射的行数
   */
  private int indexWithRowBound = -1;

  private enum CursorStatus {

    /**
     * A freshly created cursor, database ResultSet consuming has not started.
     */
    CREATED,
    /**
     * A cursor currently in use, database ResultSet consuming has started.
     */
    OPEN,
    /**
     * 已关闭，并未完全消费
     * A closed cursor, not fully consumed.
     */
    CLOSED,
    /**
     * 已关闭，并且完全消费
     * A fully consumed cursor, a consumed cursor is always closed.
     */
    CONSUMED
  }

  public DefaultCursor(DefaultResultSetHandler resultSetHandler, ResultMap resultMap, ResultSetWrapper rsw, RowBounds rowBounds) {
    this.resultSetHandler = resultSetHandler;
    this.resultMap = resultMap;
    this.rsw = rsw;
    this.rowBounds = rowBounds;
  }

  @Override
  public boolean isOpen() {
    return status == CursorStatus.OPEN;
  }

  @Override
  public boolean isConsumed() {
    return status == CursorStatus.CONSUMED;
  }

  @Override
  public int getCurrentIndex() {
    return rowBounds.getOffset() + cursorIterator.iteratorIndex;
  }

  // 通过 iteratorRetrieved 属性，保证有且仅返回一次 cursorIterator 对象
  @Override
  public Iterator<T> iterator() {
    // 如果已经获取，则抛出 IllegalStateException 异常
    if (iteratorRetrieved) {
      throw new IllegalStateException("Cannot open more than one iterator on a Cursor");
    }
    if (isClosed()) {
      throw new IllegalStateException("A Cursor is already closed.");
    }
    // 标记已经获取
    iteratorRetrieved = true;
    return cursorIterator;
  }

  @Override
  public void close() {
    if (isClosed()) {
      return;
    }

    ResultSet rs = rsw.getResultSet();
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    } finally {
      status = CursorStatus.CLOSED;
    }
  }

  /**
   * 遍历下一条记录
   */
  protected T fetchNextUsingRowBound() {
    // 调用 #fetchNextObjectFromDatabase() 方法，遍历下一条记录
    T result = fetchNextObjectFromDatabase();
    // 循环跳过 rowBounds 的索引
    while (objectWrapperResultHandler.fetched && indexWithRowBound < rowBounds.getOffset()) {
      result = fetchNextObjectFromDatabase();
    }
    // 返回记录
    return result;
  }

  /**
   * 遍历下一条记录
   */
  protected T fetchNextObjectFromDatabase() {
    // <1> 如果已经关闭，返回 null
    if (isClosed()) {
      return null;
    }

    try {
      // <2> 设置状态为 CursorStatus.OPEN
      objectWrapperResultHandler.fetched = false;
      status = CursorStatus.OPEN;
      // <3> 调用 DefaultResultSetHandler#handleRowValues(...) 方法，遍历下一条记录。
      // 也就是说，回到了 [handleRowValues()」 的流程。遍历的下一条件记录，会暂存到 objectWrapperResultHandler.result 中
      if (!rsw.getResultSet().isClosed()) {
        resultSetHandler.handleRowValues(rsw, resultMap, objectWrapperResultHandler, RowBounds.DEFAULT, null);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    // <4> 复制给 next
    T next = objectWrapperResultHandler.result;
    // <5> 增加 indexWithRowBound
    if (objectWrapperResultHandler.fetched) {
      indexWithRowBound++;
    }
    // No more object or limit reached
    // <6> 没有更多记录，或者到达 rowBounds 的限制索引位置，则关闭游标，并设置状态为 CursorStatus.CONSUMED
    if (!objectWrapperResultHandler.fetched || getReadItemsCount() == rowBounds.getOffset() + rowBounds.getLimit()) {
      close();
      status = CursorStatus.CONSUMED;
    }
    // <7> 置空 objectWrapperResultHandler.result 属性
    objectWrapperResultHandler.result = null;
    // <8> 返回下一条结果
    return next;
  }

  private boolean isClosed() {
    return status == CursorStatus.CLOSED || status == CursorStatus.CONSUMED;
  }

  private int getReadItemsCount() {
    return indexWithRowBound + 1;
  }

  /**
   * DefaultCursor 的内部静态类，实现 ResultHandler 接口
   * @param <T>
   */
  protected static class ObjectWrapperResultHandler<T> implements ResultHandler<T> {

    /**
     * 结果对象
     */
    protected T result;
    /**
     * 是否获取
     */
    protected boolean fetched;

    @Override
    public void handleResult(ResultContext<? extends T> context) {
      // <1> 暂存 「DefaultResultSetHandler」 处理的 ResultSet 的当前行的结果
      this.result = context.getResultObject();
      // <2> 通过调用 ResultContext#stop() 方法，暂停 DefaultResultSetHandler 在向下遍历下一条记录，从而实现每次在调用 CursorIterator#hasNext() 方法，只遍历一行 ResultSet 的记录
      context.stop();
      fetched = true;
    }
  }

  /**
   * DefaultCursor 的内部类，实现 Iterator 接口，游标的迭代器实现类
   */
  protected class CursorIterator implements Iterator<T> {

    /**
     * 结果对象，提供给 {@link #next()} 返回
     * Holder for the next object to be returned.
     */
    T object;

    /**
     * 索引位置
     * Index of objects returned using next(), and as such, visible to users.
     */
    int iteratorIndex = -1;

    @Override
    public boolean hasNext() {
      // <1> 判断标志是否获取到记录
      if (!objectWrapperResultHandler.fetched) {
        //如果没有获取到，则调用#fetchNextUsingRowBound()方法获取记录
        object = fetchNextUsingRowBound();
      }
      //否则返回true，代表有纪录
      return objectWrapperResultHandler.fetched;
    }

    @Override
    public T next() {
      // Fill next with object fetched from hasNext()
      T next = object;

      //<4> 如果未获取则获取下一条记录
      if (!objectWrapperResultHandler.fetched) {
        next = fetchNextUsingRowBound();
      }

      //若已获取，则返回
      if (objectWrapperResultHandler.fetched) {
        //标志未获取
        objectWrapperResultHandler.fetched = false;
        //置空
        object = null;
        //索引加一
        iteratorIndex++;
        //返回结果
        return next;
      }
      // 如果 next 为空，说明没有记录，抛出 NoSuchElementException 异常
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Cannot remove element from Cursor");
    }
  }
}
