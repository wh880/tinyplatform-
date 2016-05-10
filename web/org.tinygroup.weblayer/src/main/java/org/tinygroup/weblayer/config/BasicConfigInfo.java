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
package org.tinygroup.weblayer.config;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.tinygroup.commons.tools.CollectionUtil;
import org.tinygroup.commons.tools.StringUtil;

import java.util.*;

public class BasicConfigInfo {

	@XStreamAsAttribute
	private String id;
	@XStreamAsAttribute
	private String name;
	@XStreamAsAttribute
	@XStreamAlias("class")
	private String className;
	@XStreamAsAttribute
	@XStreamAlias("bean")
	private String beanName;
	@XStreamImplicit
	private List<InitParam> params;
	private transient Map<String, String> parameterMap;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getBeanName() {
		return beanName;
	}

	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	public List<InitParam> getParams() {
		if (params == null) {
			params = new ArrayList<InitParam>();
		}
		return params;
	}

	public void setParams(List<InitParam> params) {
		this.params = params;
	}

	public String getConfigName() {
		String name = getName();
		if (StringUtil.isBlank(name)) {
			name = getId();
		}
		return name;
	}
	
	public String getConfigBeanName() {
		String beanName = getBeanName();
		if (StringUtil.isBlank(beanName)) {
			beanName = getClassName();
		}
		return beanName;
	}

	public String getParameterValue(String name) {
		initParams();
		return parameterMap.get(name);
	}

	public Iterator<String> getIterator() {
		initParams();
		return parameterMap.keySet().iterator();
	}

	private void initParams() {
		if (parameterMap == null) {
			parameterMap = new HashMap<String, String>();
			if (!CollectionUtil.isEmpty(params)) {
				for (InitParam param : params) {
					parameterMap.put(param.getName(), param.getValue());
				}
			}
		}
	}

	public Map<String, String> getParameterMap() {
		initParams();
		return parameterMap;
	}

	@Override
	public int hashCode() {
		return getConfigName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		if (obj instanceof BasicConfigInfo) {
			BasicConfigInfo other = (BasicConfigInfo) obj;
			return other.getConfigName().equals(this.getConfigName());
		}
		return false;

	}

}
