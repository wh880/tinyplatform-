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
package org.tinygroup.tinysqldsl.base;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.tinygroup.commons.beanutil.BeanUtil;
import org.tinygroup.tinysqldsl.KeyGenerator;
import org.tinygroup.tinysqldsl.formitem.FromItem;
import org.tinygroup.tinysqldsl.selectitem.AllTableColumns;

import java.util.Map;

/**
 * 表对象 Created by luoguo on 2015/3/11.
 */
public class Table implements FromItem, MultiPartName {
    public final AllTableColumns ALL = new AllTableColumns(this);
    /**
     * 模式名
     */
    private String schemaName;
    /**
     * 表名
     */
    private String name;
    /**
     * 表的别名
     */
    private Alias alias;

    private Map<String, KeyGenerator> generatorMap = new CaseInsensitiveMap();

    private Class pojoType;

    private boolean autoGeneratedKeys = true;


    public Table() {
    }

    public Table(String name) {
        this.name = name;
    }

    public Table(String schemaName, String name) {
        this(name);
        this.schemaName = schemaName;
    }

    public Table(String schemaName, String name, String alias) {
        this(schemaName, name);
        this.alias = new Alias(alias);
    }

    public Table(String schemaName, String name, String alias, boolean withAs) {
        this(schemaName, name);
        this.alias = new Alias(alias, withAs);
    }

    public <T extends Table> T as(String aliasName) {
        return (T) as(aliasName, false);
    }

    public <T extends Table> T as(String aliasName, boolean withAs) {
        try {
            T table = (T) BeanUtil.deepCopy(this);
            table.setAlias(new Alias(aliasName, withAs));
            return table;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String string) {
        schemaName = string;
    }

    public String getName() {
        return name;
    }

    public void setName(String string) {
        name = string;
    }

    public Alias getAlias() {
        return alias;
    }

    public void setAlias(Alias alias) {
        this.alias = alias;
    }

    public String getFullyQualifiedName() {
        String fqn = "";

        if (schemaName != null) {
            fqn += schemaName;
        }
        if (!(fqn == null || fqn.length() == 0)) {
            fqn += ".";
        }

        if (name != null) {
            fqn += name;
        }

        return fqn;
    }

    public String getReferenceName() {
        if (alias != null) {
            return alias.getName();
        }
        return getFullyQualifiedName();
    }

    public String toString() {
        return getFullyQualifiedName()
                + ((alias != null) ? alias.toString() : "");
    }

    public void builderFromItem(StatementSqlBuilder builder) {
        StringBuilder buffer = builder.getStringBuilder();
        buffer.append(getFullyQualifiedName());
        Alias alias = getAlias();
        if (alias != null) {
            buffer.append(alias);
        }
    }

    public Map<String, KeyGenerator> getGeneratorMap() {
        return generatorMap;
    }

    public Class getPojoType() {
        return pojoType;
    }

    public boolean isAutoGeneratedKeys() {
        return autoGeneratedKeys;
    }

}
