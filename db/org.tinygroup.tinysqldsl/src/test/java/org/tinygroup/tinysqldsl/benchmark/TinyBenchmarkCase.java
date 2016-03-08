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
package org.tinygroup.tinysqldsl.benchmark;

import org.tinygroup.tinysqldsl.Insert;

/**
 *
 * @author Young
 *
 */
public abstract class TinyBenchmarkCase {

    private final String name;

    public TinyBenchmarkCase(String name) {
        super();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract void execute(Insert insert) throws Exception;
}
