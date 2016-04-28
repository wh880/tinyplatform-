/**
 *  Copyright (c) 1997-2013, www.tinygroup.org (luo_guo@icloud.com).
 *
 *  Licensed under the GPL, Version 3.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.gnu.org/licenses/gpl.html
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.tinygroup.tinymvc.impl;

import org.tinygroup.tinymvc.HandlerExecutionChain;
import org.tinygroup.tinymvc.MappingModelExecute;
import org.tinygroup.weblayer.WebContext;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * 
 * 功能说明: 请求映射的执行接口的默认实现

 * 开发人员: renhui <br>
 * 开发时间: 2013-4-24 <br>
 * <br>
 */
public class MappingModelExecuteImpl implements MappingModelExecute {

	
	public void execute(HandlerExecutionChain chain, WebContext context) throws ServletException, IOException {
		chain.setContext(context);
		try {
			chain.execute();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

}
