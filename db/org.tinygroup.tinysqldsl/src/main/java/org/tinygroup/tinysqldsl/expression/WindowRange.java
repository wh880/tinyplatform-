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

public class WindowRange {

    private WindowOffset start;
    private WindowOffset end;

    public WindowOffset getEnd() {
        return end;
    }

    public void setEnd(WindowOffset end) {
        this.end = end;
    }

    public WindowOffset getStart() {
        return start;
    }

    public void setStart(WindowOffset start) {
        this.start = start;
    }


    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(" BETWEEN");
        buffer.append(start);
        buffer.append(" AND");
        buffer.append(end);
        return buffer.toString();
    }
}
