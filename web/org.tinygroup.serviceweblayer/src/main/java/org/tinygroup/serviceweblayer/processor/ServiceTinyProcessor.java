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
package org.tinygroup.serviceweblayer.processor;

import com.alibaba.fastjson.serializer.SerializerFeature;
import org.tinygroup.cepcore.CEPCore;
import org.tinygroup.context.Context;
import org.tinygroup.convert.objectjson.fastjson.ObjectToJson;
import org.tinygroup.convert.objectxml.xstream.ObjectToXml;
import org.tinygroup.event.Event;
import org.tinygroup.event.Parameter;
import org.tinygroup.event.ServiceInfo;
import org.tinygroup.event.ServiceRequest;
import org.tinygroup.serviceweblayer.ServiceMappingManager;
import org.tinygroup.serviceweblayer.config.ServiceViewMapping;
import org.tinygroup.weblayer.AbstractTinyProcessor;
import org.tinygroup.weblayer.WebContext;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

public class ServiceTinyProcessor extends AbstractTinyProcessor {
	ServiceMappingManager manager;
	CEPCore core;
	ObjectToXml<Object> objectToXml = new ObjectToXml<Object>();
	ObjectToJson<Object> objectToJson = new ObjectToJson<Object>(
			SerializerFeature.DisableCircularReferenceDetect);

	public CEPCore getCore() {
		return core;
	}

	public void setCore(CEPCore core) {
		this.core = core;
	}

	public ServiceMappingManager getManager() {
		return manager;
	}

	public void setManager(ServiceMappingManager manager) {
		this.manager = manager;
	}

	private Object callService(String serviceId, Context context) {
		// CEPCore core = SpringBeanContainer.getBean(CEPCore.CEP_CORE_BEAN);
		Event event = new Event();
		ServiceRequest sq = new ServiceRequest();
		sq.setServiceId(serviceId);
		sq.setContext(context);
		event.setServiceRequest(sq);
		core.process(event);

		ServiceInfo info = core.getServiceInfo(serviceId);
		List<Parameter> resultsParam = info.getResults();
		if (resultsParam == null || resultsParam.size() == 0) {
			return null;
		}
		return event.getServiceRequest().getContext()
				.get(resultsParam.get(0).getName());
	}

	public void reallyProcess(String urlString, WebContext context)
			throws ServletException, IOException {
		int lastSplash = urlString.lastIndexOf('/');
		int lastDot = urlString.lastIndexOf('.');
		String serviceId = urlString.substring(lastSplash + 1, lastDot);
		Object result = callService(serviceId, context);
		if (urlString.endsWith("servicexml") && result != null) {// 返回xml
			context.getResponse().getWriter()
					.write(objectToXml.convert(result));
		} else if (urlString.endsWith(".servicejson") && result != null) {// 返回json
			context.getResponse().getWriter()
					.write(objectToJson.convert(result));

		} else if (urlString.endsWith(".servicepage")) {// 返回页面
			ServiceViewMapping viewMapping = manager
					.getServiceViewMapping(serviceId);
			if (viewMapping == null) {
				throw new RuntimeException(serviceId + "对应的展现视图不存在！");
			}
			String path = viewMapping.getPath();
			checkPath(serviceId, path);
			pageJump(path, viewMapping.getType(), context);
		} else if (urlString.endsWith(".servicepagelet")) {// 返回页面片断
			ServiceViewMapping viewMapping = manager
					.getServiceViewMapping(serviceId);
			if (viewMapping == null) {
				throw new RuntimeException(serviceId + "对应的展现视图不存在！");
			}
			String path = viewMapping.getPath();
			checkPath(serviceId, path);
			if (path.endsWith(".page")) {
				path = path + "let";
			}
			pageJump(path, viewMapping.getType(), context);
		}
	}

	private void pageJump(String path, String type, WebContext context)
			throws ServletException, IOException {
		if ("forward".equals(type)) {
			context.getRequest().getRequestDispatcher(path)
					.forward(context.getRequest(), context.getResponse());
		} else if ("redirect".equals(type)) {
			String contextPath = context.get("TINY_CONTEXT_PATH");
			if (path.startsWith("/")) {
				contextPath = contextPath + path;
			} else {
				contextPath = contextPath + "/" + path;
			}
			context.getResponse().sendRedirect(contextPath);
		} else {
			throw new RuntimeException(type + "跳转类型不正确，只能是forward或者redirect");
		}
	}

	private void checkPath(String serviceId, String path) {
		if (path == null) {
			throw new RuntimeException(serviceId + "对应的展现视图不存在！");
		}
	}

	@Override
	protected void customInit() throws ServletException {

	}
}
