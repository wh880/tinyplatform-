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

import org.tinygroup.tinysqldsl.expression.LongValue;
import org.tinygroup.tinysqldsl.expression.arithmetic.Addition;

import static org.tinygroup.tinysqldsl.CustomTable.CUSTOM;
import static org.tinygroup.tinysqldsl.Update.update;

/**
 * Created by luoguo on 2015/3/11.
 */
public class TestUpdate {
    public static void main(String[] args) {
        System.out.println(update(CUSTOM).set(CUSTOM.NAME.value("abc"),
                CUSTOM.AGE.value(3)).where(CUSTOM.NAME.eq("悠然")));
        System.out.println(update(CUSTOM).set(
                CUSTOM.AGE.value(new Addition(CUSTOM.AGE, new LongValue(3))))
                .where(CUSTOM.NAME.eq("悠然")));
        System.out.println(update(CUSTOM).set(CUSTOM.NAME.value(null),
                CUSTOM.AGE.value(30)).where(CUSTOM.NAME.eq("flank")));
        System.out.println(update(CUSTOM).set(CUSTOM.NAME.value(null)));
    }

}
