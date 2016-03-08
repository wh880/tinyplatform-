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
package org.tinygroup.tinysqldsl.expression;

import org.tinygroup.tinysqldsl.base.StatementSqlBuilder;
import org.tinygroup.tinysqldsl.formitem.SubSelect;

public class AnyComparisonExpression implements Expression {

    private SubSelect subSelect;

    public AnyComparisonExpression(SubSelect subSelect) {
        this.subSelect = subSelect;
    }

    public SubSelect getSubSelect() {
        return subSelect;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(" ANY ").append(subSelect);
        return buffer.toString();
    }

    public void builderExpression(StatementSqlBuilder builder) {
        builder.appendSql(" ANY ");
        getSubSelect().builderExpression(builder);
    }

}
