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
package org.tinygroup.tinysqldsl.util;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author renhui
 *
 */
public class ParsedSql {

    private String originalSql;

    private List parameterNames = new ArrayList();

    private List parameterIndexes = new ArrayList();

    private int namedParameterCount;

    private int unnamedParameterCount;

    private int totalParameterCount;


    /**
     * Create a new instance of the {@link ParsedSql} class.
     *
     * @param originalSql the SQL statement that is being (or is to be) parsed
     */
    ParsedSql(String originalSql) {
        this.originalSql = originalSql;
    }

    /**
     * Return the SQL statement that is being parsed.
     */
    String getOriginalSql() {
        return this.originalSql;
    }


    /**
     * Add a named parameter parsed from this SQL statement.
     *
     * @param parameterName the name of the parameter
     * @param startIndex    the start index in the original SQL String
     * @param endIndex      the end index in the original SQL String
     */
    void addNamedParameter(String parameterName, int startIndex, int endIndex) {
        this.parameterNames.add(parameterName);
        this.parameterIndexes.add(new int[]{startIndex, endIndex});
    }

    /**
     * Return all of the parameters (bind variables) in the parsed SQL statement.
     * Repeated occurences of the same parameter name are included here.
     */
    List getParameterNames() {
        return this.parameterNames;
    }

    /**
     * Return the parameter indexes for the specified parameter.
     *
     * @param parameterPosition the position of the parameter
     *                          (as index in the parameter names List)
     * @return the start index and end index, combined into
     * a int array of length 2
     */
    int[] getParameterIndexes(int parameterPosition) {
        return (int[]) this.parameterIndexes.get(parameterPosition);
    }

    /**
     * Return the count of named parameters in the SQL statement.
     * Each parameter name counts once; repeated occurences do not count here.
     */
    int getNamedParameterCount() {
        return this.namedParameterCount;
    }

    /**
     * Set the count of named parameters in the SQL statement.
     * Each parameter name counts once; repeated occurences do not count here.
     */
    void setNamedParameterCount(int namedParameterCount) {
        this.namedParameterCount = namedParameterCount;
    }

    /**
     * Return the count of all of the unnamed parameters in the SQL statement.
     */
    int getUnnamedParameterCount() {
        return this.unnamedParameterCount;
    }

    /**
     * Set the count of all of the unnamed parameters in the SQL statement.
     */
    void setUnnamedParameterCount(int unnamedParameterCount) {
        this.unnamedParameterCount = unnamedParameterCount;
    }

    /**
     * Return the total count of all of the parameters in the SQL statement.
     * Repeated occurences of the same parameter name do count here.
     */
    int getTotalParameterCount() {
        return this.totalParameterCount;
    }

    /**
     * Set the total count of all of the parameters in the SQL statement.
     * Repeated occurences of the same parameter name do count here.
     */
    void setTotalParameterCount(int totalParameterCount) {
        this.totalParameterCount = totalParameterCount;
    }

    /**
     * Exposes the original SQL String.
     */
    public String toString() {
        return this.originalSql;
    }

}
