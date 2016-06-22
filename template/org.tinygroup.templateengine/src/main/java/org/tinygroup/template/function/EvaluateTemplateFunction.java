/**
 * Copyright (c) 1997-2013, www.tinygroup.org (luo_guo@icloud.com).
 * <p/>
 * Licensed under the GPL, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.gnu.org/licenses/gpl.html
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinygroup.template.function;

import org.tinygroup.template.Template;
import org.tinygroup.template.TemplateContext;
import org.tinygroup.template.TemplateException;
import org.tinygroup.template.loader.StringResourceLoader;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Created by luoguo on 2014/6/9.
 */
public class EvaluateTemplateFunction extends AbstractTemplateFunction {

    public EvaluateTemplateFunction() {
        super("eval,evaluate");
    }

    public Object execute(Template template, TemplateContext context, Object... parameters) throws TemplateException {
    	//如果参数为空或者非String类型
        if (parameters.length == 0 || parameters[0]==null) {
           return null;
        }
        if(!(parameters[0] instanceof String)){
           notSupported(parameters);
        }
        String stringTemplate = parameters[0].toString();
        StringResourceLoader stringResourceLoader = new StringResourceLoader();
        stringResourceLoader.setTemplateEngine(getTemplateEngine());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        stringResourceLoader.createTemplate(stringTemplate).render(context, outputStream);
        try {
            return new String(outputStream.toByteArray(), template.getTemplateEngine().getEncode());
        } catch (UnsupportedEncodingException e) {
            throw new TemplateException(e);
        }
    }
}

