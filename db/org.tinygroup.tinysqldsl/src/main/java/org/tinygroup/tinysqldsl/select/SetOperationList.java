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
package org.tinygroup.tinysqldsl.select;

import org.tinygroup.tinysqldsl.base.SelectBody;
import org.tinygroup.tinysqldsl.base.StatementSqlBuilder;
import org.tinygroup.tinysqldsl.util.DslUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A database set operation. This operation consists of a list of plainSelects
 * connected by set operations (UNION,INTERSECT,MINUS,EXCEPT). All these
 * operations have the same priority.
 *
 */
public class SetOperationList implements SelectBody {

    private List<PlainSelect> plainSelects;
    private List<SetOperation> operations;
    private List<OrderByElement> orderByElements;
    private Limit limit;
    private Offset offset;
    private Fetch fetch;

    public List<OrderByElement> getOrderByElements() {
        return orderByElements;
    }

    public void setOrderByElements(List<OrderByElement> orderByElements) {
        this.orderByElements = orderByElements;
    }

    public List<PlainSelect> getPlainSelects() {
        return plainSelects;
    }

    public List<SetOperation> getOperations() {
        return operations;
    }

    public void setOpsAndSelects(List<PlainSelect> select,
                                 List<SetOperation> ops) {
        plainSelects = select;
        operations = ops;

        if (select.size() - 1 != ops.size()) {
            throw new IllegalArgumentException("list sizes are not valid");
        }
    }

    public void addOrderByElements(OrderByElement... orderBys) {
        if (orderByElements == null) {
            orderByElements = new ArrayList<OrderByElement>();
        }
        Collections.addAll(orderByElements, orderBys);
    }

    public Limit getLimit() {
        return limit;
    }

    public void setLimit(Limit limit) {
        this.limit = limit;
    }

    public Offset getOffset() {
        return offset;
    }

    public void setOffset(Offset offset) {
        this.offset = offset;
    }

    public Fetch getFetch() {
        return fetch;
    }

    public void setFetch(Fetch fetch) {
        this.fetch = fetch;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < plainSelects.size(); i++) {
            if (i != 0) {
                buffer.append(" ").append(operations.get(i - 1).toString())
                        .append(" ");
            }
            buffer.append("(").append(plainSelects.get(i).toString())
                    .append(")");
        }

        if (orderByElements != null) {
            buffer.append(DslUtil.orderByToString(orderByElements));
        }
        if (limit != null) {
            buffer.append(limit.toString());
        }
        if (offset != null) {
            buffer.append(offset.toString());
        }
        if (fetch != null) {
            buffer.append(fetch.toString());
        }
        return buffer.toString();
    }

    public void builderStatement(StatementSqlBuilder builder) {
        StringBuilder buffer = builder.getStringBuilder();
        for (int i = 0; i < getPlainSelects().size(); i++) {
            if (i != 0) {
                buffer.append(' ').append(getOperations().get(i - 1))
                        .append(' ');
            }
            buffer.append("(");
            PlainSelect plainSelect = getPlainSelects().get(i);
            plainSelect.builderStatement(builder);
            buffer.append(")");
        }
        if (getOrderByElements() != null) {
            builder.deparseOrderBy(getOrderByElements());
        }

        if (getLimit() != null) {
            builder.deparseLimit(getLimit());
        }
        if (getOffset() != null) {
            builder.deparseOffset(getOffset());
        }
        if (getFetch() != null) {
            builder.deparseFetch(getFetch());
        }
    }

    /**
     * list of set operations.
     */
    public enum SetOperationType {

        INTERSECT, EXCEPT, MINUS, UNION
    }
}
