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
package org.tinygroup.service.registry;

import org.tinygroup.event.AbstractServiceInfo;
import org.tinygroup.event.Parameter;
import org.tinygroup.service.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务注册项，serviceId相当于功能号 groupId.rtifactId.name相当于原来的ID
 * 
 * @author luoguo
 * 
 */
public class ServiceRegistryItem extends AbstractServiceInfo {

	private static final long serialVersionUID = -6526088933543577083L;
	/**
	 * 服务本地名称，仅用于显示
	 */
	private String localName;
	/**
	 * 服务标识，唯一确定一个服务，如果重复，只有组织标识，模块标识，名称全部相同，且版本不同，才可以注册
	 */
	private String serviceId;
	/**
	 * 描述
	 */
	private String description;
	/**
	 * 对应的服务
	 */
	private transient Service service = null;
	/**
	 * 输入参数描述列表
	 */
	private List<Parameter> parameters;
	/**
	 * 输出参数描述列表
	 */
	private List<Parameter> results;

	private String category;

	public void setCategory(String category) {
		this.category = category;
	}

	public String getLocalName() {
		return localName;
	}

	public void setLocalName(String localName) {
		this.localName = localName;
	}

	public List<Parameter> getParameters() {
		if (parameters == null) {
			parameters = new ArrayList<Parameter>();
		}
		return parameters;
	}

	public void setParameters(List<Parameter> parameters) {
		this.parameters = parameters;
	}

	public List<Parameter> getResults() {
		if (results == null) {
			results = new ArrayList<Parameter>();
		}
		return results;
	}

	public void setResults(List<Parameter> results) {
		this.results = results;
	}

	public String getServiceId() {
		return serviceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Service getService() {
		return service;
	}

	public void setService(Service service) {
		this.service = service;
	}

	

	public String getCategory() {
		return category;
	}

}