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

import java.sql.Time;

/**
 * A Time in the form {t 'hh:mm:ss'}
 */
public class TimeValue implements Expression {

    private Time value;

    public TimeValue(String value) {
        this.value = Time.valueOf(value.substring(1, value.length() - 1));
    }

    public TimeValue() {
        super();
    }

    public Time getValue() {
        return value;
    }

    public void setValue(Time d) {
        value = d;
    }

    public String toString() {
        return "{t '" + value + "'}";
    }

    public void builderExpression(StatementSqlBuilder builder) {
        StringBuilder buffer = builder.getStringBuilder();
        buffer.append("{t '").append(getValue().toString()).append("'}");
    }
}
