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

import org.tinygroup.tinysqldsl.base.Alias;
import org.tinygroup.tinysqldsl.base.Column;
import org.tinygroup.tinysqldsl.expression.FragmentExpressionSql;
import org.tinygroup.tinysqldsl.expression.relational.ExistsExpression;
import org.tinygroup.tinysqldsl.formitem.SubSelect;
import org.tinygroup.tinysqldsl.selectitem.Top;

import static org.tinygroup.tinysqldsl.ComplexSelect.union;
import static org.tinygroup.tinysqldsl.ComplexSelect.unionAll;
import static org.tinygroup.tinysqldsl.CustomTable.CUSTOM;
import static org.tinygroup.tinysqldsl.ScoreTable.TSCORE;
import static org.tinygroup.tinysqldsl.Select.*;
import static org.tinygroup.tinysqldsl.base.FragmentSql.*;
import static org.tinygroup.tinysqldsl.base.StatementSqlBuilder.and;
import static org.tinygroup.tinysqldsl.base.StatementSqlBuilder.or;
import static org.tinygroup.tinysqldsl.formitem.SubSelect.subSelect;
import static org.tinygroup.tinysqldsl.select.Join.*;
import static org.tinygroup.tinysqldsl.select.OrderByElement.asc;
import static org.tinygroup.tinysqldsl.select.OrderByElement.desc;
import static org.tinygroup.tinysqldsl.selectitem.Top.top;

/**
 * Created by luoguo on 2015/3/11.
 */
public class TestSelect {
    public static void main(String[] args) {
        System.out.println(selectFrom(CUSTOM));

        System.out.println(select(
                customSelectItem("%s-%s", CUSTOM.NAME, CUSTOM.AGE))
                .from(CUSTOM));

        System.out.println(select(
                customSelectItem("%s-%s as remainder", CUSTOM.NAME, CUSTOM.AGE))
                .from(CUSTOM));

        System.out.println(select(
                customSelectItem("upper(%s)-%s", CUSTOM.NAME, CUSTOM.AGE))
                .from(CUSTOM));

        System.out.println(selectFrom(CUSTOM).orderBy(desc(CUSTOM.NAME)));

        System.out.println(selectFrom(CUSTOM).orderBy(asc(CUSTOM.ID)));

        System.out.println(selectFrom(CUSTOM).orderBy(asc(CUSTOM.ID), desc(CUSTOM.NAME)));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.NAME.eq("abc")));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.NAME.like("abc")));

        System.out.println(selectFrom(CUSTOM).where(
                CUSTOM.NAME.rightLike("abc")));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.NAME.isNull()));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.NAME.isNotNull()));

        System.out.println(selectFrom(CUSTOM).where(
                or(CUSTOM.NAME.like("abc"), CUSTOM.AGE.gt(20))));

        System.out.println(selectFrom(CUSTOM).where(
                and(CUSTOM.NAME.like("abc"), CUSTOM.AGE.gt(20))));

        System.out.println(selectFrom(CUSTOM)
                .where(CUSTOM.NAME.leftLike("abc")));

        System.out
                .println(selectFrom(CUSTOM).where(CUSTOM.AGE.between(23, 25)));

        System.out.println(select(CUSTOM.AGE.max()).from(CUSTOM).groupBy(
                CUSTOM.NAME, CUSTOM.AGE));
        System.out.println(select(CUSTOM.AGE.min()).from(CUSTOM));
        System.out.println(select(CUSTOM.AGE.avg()).from(CUSTOM));
        System.out.println(select(CUSTOM.AGE.count().as("num")).from(CUSTOM));
        System.out.println(select(CUSTOM.AGE.count().as("num", true)).from(CUSTOM));
        System.out.println(select(CUSTOM.AGE.sum()).from(CUSTOM));
        System.out.println(select(CUSTOM.NAME.distinct()).from(CUSTOM)
                .forUpdate());

        System.out.println(select(CUSTOM.NAME, CUSTOM.AGE, TSCORE.SCORE)
                .from(CUSTOM)
                .join(leftJoin(TSCORE, CUSTOM.NAME.eq(TSCORE.NAME))).sql());

        System.out.println(select(fragmentSelect("custom.name,custom.age"))
                .from(fragmentFrom("custom custom")).where(
                        fragmentCondition("custom.name=?", "悠悠然然")));

        CustomTable alias = CUSTOM.as("sdsf");
//		CUSTOM.setAlias(new Alias("dsff"));
        System.out.println(select(CUSTOM.NAME).from(
                subSelect(select(alias.ALL).from(alias), "custom", true)).where(
                CUSTOM.ID.eq("2324")));
        System.out.println(select(CUSTOM.ID.count()).from(CUSTOM).groupBy(
                CUSTOM.AGE));
        System.out.println(select(CUSTOM.ID.count()).from(CUSTOM).groupBy(
                CUSTOM.NAME, CUSTOM.AGE));
        System.out.println(union(selectFrom(CUSTOM), selectFrom(CUSTOM)));

        System.out.println(unionAll(selectFrom(CUSTOM), selectFrom(CUSTOM)));

        System.out.println(select(CUSTOM.AGE, CUSTOM.NAME).from(CUSTOM).join(
                rightJoin(TSCORE, TSCORE.NAME.eq(CUSTOM.NAME))));

        System.out.println(select(CUSTOM.AGE, CUSTOM.NAME).from(CUSTOM).join(
                fullJoin(TSCORE, TSCORE.NAME.eq(CUSTOM.NAME))));

        System.out.println(select(TSCORE.SCORE.sum()).from(TSCORE)
                .groupBy(TSCORE.NAME).having(TSCORE.SCORE.sum().gt(300)));

        System.out.println(select(top(10), CUSTOM.NAME).from(CUSTOM));

        System.out.println(select(new Top(10, false, true, true), CUSTOM.NAME)
                .from(CUSTOM));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.AGE.gt(30))
                .forUpdate());

        System.out.println(select(TSCORE.SCORE.avg(), TSCORE.NAME).from(TSCORE)
                .groupBy(TSCORE.NAME));

        System.out.println(select(TSCORE.SCORE.sum(), TSCORE.NAME).from(TSCORE)
                .having(TSCORE.SCORE.sum().gt(200))
                .orderBy(asc(TSCORE.SCORE.sum())));

        System.out.println(select(fragmentSelect("sum(score.score) s"),
                TSCORE.NAME).from(TSCORE)
                .having(fragmentCondition("s>?", "200"))
                .orderBy(asc(fragmentCondition("s"))));

        System.out
                .println(select(fragmentSelect("r"), CUSTOM.NAME, CUSTOM.AGE)
                        .from(fragmentFrom("(select ROWNUM r,custom.name,custom.age from custom where r>=1)"))
                        .where(fragmentCondition("r<=?", 10)));

        System.out.println(select(fragmentSelect("cc.r,cc.name,cc.age")).from(
                subSelect(select(fragmentSelect("r"), CUSTOM.NAME, CUSTOM.AGE)
                                .from(CUSTOM).where(fragmentCondition("r>?", 1)), "cc",
                        true)).where(fragmentCondition("r<=?", 10)));

        System.out.println(select(fragmentSelect("c.name,c.age")).from(CUSTOM));

        SubSelect sub = subSelect(select(top(4), fragmentSelect("*")).from(
                CUSTOM).orderBy(desc(CUSTOM.ID)));
        System.out.println(select(top(2), fragmentSelect("*")).from(sub)
                .orderBy(asc(new FragmentExpressionSql("c.id"))));

        System.out.println(select(CUSTOM.NAME).from(CUSTOM).where(
                CUSTOM.NAME.isNull()));

        System.out.println(select(CUSTOM.NAME).from(CUSTOM).where(
                CUSTOM.NAME.equal(null)));

        System.out.println(select(CUSTOM.NAME).from(CUSTOM).where(
                and(CUSTOM.AGE.greaterThan(null), CUSTOM.NAME.equal(null))));

        System.out.println(select(CUSTOM.NAME).from(CUSTOM).where(
                and(CUSTOM.AGE.greaterThan(33), CUSTOM.NAME.isNotEmpty())));

        System.out.println(select(CUSTOM.NAME).from(CUSTOM).where(
                or(and(CUSTOM.AGE.greaterThan(33), CUSTOM.NAME.equal("")),
                        CUSTOM.ID.eq("123"))));
        System.out.println(selectFrom(CUSTOM).where(CUSTOM.AGE.in(1, 5, 10)));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.AGE.in()));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.AGE.in(null, null)));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.AGE.notIn(1, 5, 10)));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.AGE.notIn(null, null, null)));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.AGE.notIn(1, null, 10)));

        System.out.println(selectFrom(CUSTOM).where(ExistsExpression.existsCondition(
                subSelect(selectFrom(CUSTOM).where(CUSTOM.AGE.in(10000, 100001))))));

        System.out.println(selectFrom(CUSTOM).where(ExistsExpression.notExistsCondition(
                subSelect(selectFrom(CUSTOM).where(CUSTOM.AGE.in(10000, 100001))))));

        System.out.println(selectFrom(CUSTOM).where(CUSTOM.AGE.inExpression(subSelect(select(CUSTOM.ID).from(CUSTOM).where(CUSTOM.AGE.in(10000, 100001))))));
        System.out.println(select(CUSTOM.ALL).from(CUSTOM, TSCORE));

        System.out.println(select(CUSTOM.NAME).from(CUSTOM, TSCORE).where(
                and(CUSTOM.AGE.greaterThan(null), CUSTOM.NAME.equal(null), CUSTOM.ID.eq(TSCORE.ID))));

        CustomTable man = CUSTOM.as("man");
        CustomTable womon = CUSTOM.as("womon");
        System.out.println(select(man.NAME, womon.NAME).from(man, womon));
        Column manName = man.NAME.as("manName");
        Column womonName = womon.NAME.as("womonName");
        System.out.println(select(manName, womonName).from(man, womon).where(manName.eq("test")));

        System.out.println(select(CUSTOM.ALL, TSCORE.ALL).from(CUSTOM).join(simpleJoin(TSCORE)).where(CUSTOM.ID.eq(1)));

        System.out.println(select(CUSTOM.AGE.distinct().count().as("sffs")).from(CUSTOM));

        System.out.println(select(CUSTOM.AGE.distinct().as("sffs")).from(CUSTOM));

        CUSTOM.ID.setAlias(new Alias("dsds"));

        System.out.println(select(CUSTOM.ID).from(CUSTOM).where(CUSTOM.ID.equal(111)).orderBy(asc(CUSTOM.ID), desc(CUSTOM.NAME)));

    }
}
