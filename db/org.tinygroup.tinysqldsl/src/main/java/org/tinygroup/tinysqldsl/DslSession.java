/**
 * Copyright (c) 1997-2013, www.tinygroup.org (luo_guo@icloud.com).
 * <p>
 * Licensed under the GPL, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/gpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinygroup.tinysqldsl;

import java.util.List;
import java.util.Map;

/**
 * DSL SQL执行操作接口
 *
 * @author renhui
 */
public interface DslSession {
    /**
     * 执行Insert语句,返回值为受影响记录数
     *
     * @param insert
     * @return
     */
    int execute(Insert insert);

    /**
     * 执行Insert语句，返回值为自增长的主键值,由数据库来生成主键值.
     *
     * @param insert
     * @return
     */
    <T> T executeAndReturnObject(Insert insert);

    /**
     * 执行Insert语句，返回值为自增长的主键值,由数据库来生成主键值.
     *
     * @param insert
     * @param clazz  返回对象的类型
     * @return
     */
    <T> T executeAndReturnObject(Insert insert, Class<T> clazz);

    /**
     * 执行Insert语句，返回值为自增长的主键值。
     *
     * @param insert
     * @param autoGeneratedKeys true:由数据库来生成主键值,false:由应用层来生成主键
     * @return
     */
    <T> T executeAndReturnObject(Insert insert, Class<T> clazz, boolean autoGeneratedKeys);

    /**
     * 执行更新语句,默认是不忽略空值的
     *
     * @param update
     * @return
     */
    int execute(Update update);


    /**
     * 执行更新语句
     *
     * @param update     更新语句
     * @param ignoreNull 忽略空值
     * @return
     */
    int execute(Update update, boolean ignoreNull);

    /**
     * 执行删除语句
     *
     * @param delete
     * @return
     */
    int execute(Delete delete);

    /**
     * 返回一个结果，既然是有多个结果也只返回第一个结果
     *
     * @param select
     * @param requiredType
     * @param <T>
     * @return
     */
    <T> T fetchOneResult(Select select, Class<T> requiredType);

    /**
     * 把所有的结果变成一个对象数组返回
     *
     * @param select
     * @param requiredType
     * @param <T>
     * @return
     */
    <T> T[] fetchArray(Select select, Class<T> requiredType);

    /**
     * 把所有的结果变成一个对象列表返回
     *
     * @param select
     * @param requiredType
     * @param <T>
     * @return
     */
    <T> List<T> fetchList(Select select, Class<T> requiredType);

    /**
     * 返回一个结果，既然是有多个结果也只返回第一个结果
     *
     * @param complexSelect
     * @param requiredType
     * @param <T>
     * @return
     */
    <T> T fetchOneResult(ComplexSelect complexSelect, Class<T> requiredType);

    /**
     * 把所有的结果变成一个对象数组返回
     *
     * @param complexSelect
     * @param requiredType
     * @param <T>
     * @return
     */
    <T> T[] fetchArray(ComplexSelect complexSelect, Class<T> requiredType);

    /**
     * 把所有的结果变成一个对象列表返回
     *
     * @param complexSelect
     * @param requiredType
     * @param <T>
     * @return
     */
    <T> List<T> fetchList(ComplexSelect complexSelect, Class<T> requiredType);

    /**
     * 分页处理
     *
     * @param <T>
     * @param pageSelect
     * @param start
     * @param limit
     * @param isCursor
     * @param requiredType
     * @return
     */
    <T> Pager<T> fetchPage(Select pageSelect, int start, int limit, boolean isCursor, Class<T> requiredType);

    /**
     * 基于游标的分页方式，select对象生成的sql语句是不包含分页信息的
     *
     * @param <T>
     * @param pageSelect
     * @param start
     * @param limit
     * @param requiredType
     * @return
     */
    <T> Pager<T> fetchCursorPage(Select pageSelect, int start, int limit, Class<T> requiredType);

    /**
     * 基于方言的分页方式，select对象生成的sql语句是包含分页信息的
     *
     * @param <T>
     * @param pageSelect
     * @param start
     * @param limit
     * @param requiredType
     * @return
     */
    <T> Pager<T> fetchDialectPage(Select pageSelect, int start, int limit, Class<T> requiredType);

    /**
     * 查询总记录数
     *
     * @param select
     * @return
     */
    int count(Select select);

    int[] batchInsert(Insert insert, List<Map<String, Object>> params);

    int[] batchInsert(Insert insert, List<Map<String, Object>> params, int batchSize);

    /**
     * 批量新增
     *
     * @param insert            生成批量新增的sql语句
     * @param params            批量操作的参数
     * @param batchSize         一次批量操作的最大数量
     * @param autoGeneratedKeys 主键值是否由数据库自动生成
     * @return
     */
    int[] batchInsert(Insert insert, List<Map<String, Object>> params, int batchSize, boolean autoGeneratedKeys);

    /**
     * 批量新增 最大batchsize
     *
     * @param insert
     * @param params
     * @param autoGeneratedKeys
     * @return
     */
    int[] batchInsert(Insert insert, List<Map<String, Object>> params, boolean autoGeneratedKeys);

    <T> int[] batchInsert(Insert insert, Class<T> requiredType, List<T> params);

    <T> int[] batchInsert(Insert insert, Class<T> requiredType, List<T> params, int batchSize);

    /**
     * 批量新增
     *
     * @param insert            生成批量新增的sql语句
     * @param params            批量操作的参数
     * @param batchSize         一次批量操作的最大数量
     * @param autoGeneratedKeys 主键值是否由数据库自动生成
     * @return
     */
    <T> int[] batchInsert(Insert insert, Class<T> requiredType, List<T> params, int batchSize, boolean autoGeneratedKeys);

    int[] batchUpdate(Update update, List<List<Object>> params);

    int[] batchUpdate(Update update, Map<String, Object>[] params);

    <T> int[] batchUpdate(Update update, Class<T> requiredType, List<T> params);

    /**
     * 批量更新
     *
     * @param update    生成update 语句
     * @param params    参数
     * @param batchSize 一次批量更新的最大数量
     * @return
     */
    int[] batchUpdate(Update update, List<List<Object>> params, int batchSize);

    int[] batchUpdate(Update update, Map<String, Object>[] params, int batchSize);

    <T> int[] batchUpdate(Update update, Class<T> requiredType, List<T> params, int batchSize);

    int[] batchDelete(Delete delete, List<List<Object>> params);

    int[] batchDelete(Delete delete, Map<String, Object>[] params);

    <T> int[] batchDelete(Delete delete, Class<T> requiredType, List<T> params);

    /**
     * 批量删除
     *
     * @param delete    生成delete语句
     * @param params    参数
     * @param batchSize 一次批量删除的最大数量
     * @return
     */
    int[] batchDelete(Delete delete, List<List<Object>> params, int batchSize);

    int[] batchDelete(Delete delete, Map<String, Object>[] params, int batchSize);

    <T> int[] batchDelete(Delete delete, Class<T> requiredType, List<T> params, int batchSize);

}
