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
package org.tinygroup.weblayer;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import java.io.IOException;


/**
 * filter包装接口
 * @author renhui
 *
 */
public interface FilterWrapper {

	void filterWrapper(WebContext context, TinyFilterHandler handler) throws IOException, ServletException;
	 
	 void addHttpFilter(String filterName,String filterBeanName, Filter filter);
	 /**
	  * 增加后置filter方法
	  * @param filterName
	  * @param filterBeanName
	  * @param filter
	  */
	 void addPostHttpFilter(String filterName,String filterBeanName, Filter filter);
	 
	 void init()throws ServletException;
	 
	 void destroy();
	 
}
