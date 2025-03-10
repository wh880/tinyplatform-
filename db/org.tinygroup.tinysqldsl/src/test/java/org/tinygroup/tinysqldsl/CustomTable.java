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

import org.tinygroup.tinysqldsl.base.Column;
import org.tinygroup.tinysqldsl.base.Table;

/**
 * Created by luoguo on 2015/3/11.
 */
public class CustomTable extends Table {
    public static final CustomTable CUSTOM = new CustomTable();
    public final Column ID = new Column(this, "id");
    public final Column NAME = new Column(this, "name");
    public final Column AGE = new Column(this, "age");

    public CustomTable() {
        super("custom");
    }

    public CustomTable(String schemaName, String alias) {
        super(schemaName, "custom", alias);
    }

    public CustomTable(String schemaName, String alias, boolean withAs) {
        super(schemaName, "custom", alias, withAs);
    }
}
