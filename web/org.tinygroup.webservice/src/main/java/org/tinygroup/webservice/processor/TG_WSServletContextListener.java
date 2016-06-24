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
package org.tinygroup.webservice.processor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.xml.ws.WebServiceException;

import org.tinygroup.cepcore.CEPCore;
import org.tinygroup.commons.tools.StringUtil;
import org.tinygroup.event.ServiceInfo;
import org.tinygroup.logger.LogLevel;
import org.tinygroup.logger.Logger;
import org.tinygroup.logger.LoggerFactory;
import org.tinygroup.weblayer.listener.ServletContextHolder;
import org.tinygroup.weblayer.listener.TinyListenerProcessor;
import org.tinygroup.weblayer.listener.TinyServletContext;
import org.tinygroup.webservice.config.ContextParam;
import org.tinygroup.webservice.config.ContextParams;
import org.tinygroup.webservice.config.PastPattern;
import org.tinygroup.webservice.config.SkipPattern;
import org.tinygroup.webservice.manager.ContextParamManager;
import org.tinygroup.webservice.util.WebserviceUtil;
import org.tinygroup.xmlparser.node.XmlNode;
import org.tinygroup.xmlparser.parser.XmlParser;
import org.tinygroup.xmlparser.parser.XmlStringParser;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.resources.WsservletMessages;
import com.sun.xml.ws.transport.http.DeploymentDescriptorParser;
import com.sun.xml.ws.transport.http.servlet.ServletAdapter;
import com.sun.xml.ws.transport.http.servlet.ServletAdapterList;
import com.sun.xml.ws.transport.http.servlet.WSServlet;
import com.sun.xml.ws.transport.http.servlet.WSServletDelegate;

public class TG_WSServletContextListener implements
		ServletContextAttributeListener, ServletContextListener {
	private CEPCore core;

	public CEPCore getCore() {
		return core;
	}

	public void setCore(CEPCore core) {
		this.core = core;
	}

	private ContextParamManager contextParamManager;

	public ContextParamManager getContextParamManager() {
		return contextParamManager;
	}

	public void setContextParamManager(ContextParamManager contextParamManager) {
		this.contextParamManager = contextParamManager;
	}

	private WSServletDelegate delegate;
	private List<ServletAdapter> adapters;

	public void attributeAdded(ServletContextAttributeEvent event) {
	}

	public void attributeRemoved(ServletContextAttributeEvent event) {
	}

	public void attributeReplaced(ServletContextAttributeEvent event) {
	}

	public void contextDestroyed(ServletContextEvent event) {
		if (delegate != null) { // the deployment might have failed.
			delegate.destroy();
		}
		// if (logger.isLoggable(Level.INFO)) {
		logger.logMessage(LogLevel.INFO,
				WsservletMessages.LISTENER_INFO_DESTROY());
		// }
	}

	private final static String PAST_TAG = "past-pattern";
	private final static String SKIP_TAG = "skip-pattern";
	private final static String SKIP_ATTRIBUTE = "pattern";
	private final static String PAST_ATTRIBUTE = "pattern";
	private Map<String, Pattern> skipPathPatternMap = new HashMap<String, Pattern>();
	private Map<String, Pattern> pastPathPatternMap = new HashMap<String, Pattern>();

	private List<ServiceInfo> getPublishService(String linsenterConfig) {

		if (linsenterConfig == null || "".equals(linsenterConfig)) {
			return new ArrayList<ServiceInfo>();
		}
		XmlParser<String> parser = new XmlStringParser();
		XmlNode xmlNode = parser.parse(linsenterConfig).getRoot();
		addSkipPathPattern(xmlNode);
		addSkipPathPattern(contextParamManager.getAllSkipPattens());
		addPastPathPattern(xmlNode);
		addPastPathPattern(contextParamManager.getAllPastPatterns());
		List<ServiceInfo> serviceInfos = core.getServiceInfos();
		List<ServiceInfo> publishList = new ArrayList<ServiceInfo>();
		for (ServiceInfo serviceInfo : serviceInfos) {
			String serviceId = serviceInfo.getServiceId();
			if (!isSkip(serviceId) && isPast(serviceId)) {
				publishList.add(serviceInfo);
			}
		}
		return publishList;
	}

	private void addPastPathPattern(List<PastPattern> allPastPatterns) {
		for (PastPattern pastPattern : allPastPatterns) {
			addPastPathPattern(pastPattern.getPattern());
		}
	}

	private void addSkipPathPattern(List<SkipPattern> allSkipPattens) {
		for (SkipPattern skipPattern : allSkipPattens) {
			addSkipPathPattern(skipPattern.getPattern());
		}
	}

	private void addSkipPathPattern(XmlNode xmlNode) {
		List<XmlNode> skipTags = xmlNode.getSubNodes(SKIP_TAG);
		if (skipTags == null) {
			return;
		}
		for (XmlNode tag : skipTags) {
			String pattern = tag.getAttribute(SKIP_ATTRIBUTE);
			addSkipPathPattern(pattern);
		}
	}

	public void addSkipPathPattern(String pattern) {
		skipPathPatternMap.put(pattern, Pattern.compile(pattern));
	}

	private void addPastPathPattern(XmlNode xmlNode) {
		List<XmlNode> pastTags = xmlNode.getSubNodes(PAST_TAG);
		if (pastTags == null) {
			return;
		}
		for (XmlNode tag : pastTags) {
			String pattern = tag.getAttribute(PAST_ATTRIBUTE);
			addPastPathPattern(pattern);
		}
	}

	public void addPastPathPattern(String pattern) {
		pastPathPatternMap.put(pattern, Pattern.compile(pattern));
	}

	boolean isSkip(String serviceId) {
		for (String patternString : skipPathPatternMap.keySet()) {
			Pattern pattern = skipPathPatternMap.get(patternString);
			Matcher matcher = pattern.matcher(serviceId);
			if (matcher.find()) {
				logger.logMessage(LogLevel.INFO, "服务<{}>由于匹配了忽略正则表达式<{}>而被忽略。",
						serviceId, patternString);
				return true;
			}
		}
		return false;
	}

	boolean isPast(String serviceId) {
		for (String patternString : pastPathPatternMap.keySet()) {
			Pattern pattern = pastPathPatternMap.get(patternString);
			Matcher matcher = pattern.matcher(serviceId);
			if (matcher.find()) {
				return true;
			}
		}
		logger.logMessage(LogLevel.INFO, "服务<{}>由于不匹配所有的Past正则表达式而被忽略。",
				serviceId);
		return false;
	}

	public void contextInitialized(ServletContextEvent event) {

		logger.logMessage(LogLevel.INFO,
				WsservletMessages.LISTENER_INFO_INITIALIZE());

		TinyServletContext context = (TinyServletContext) ServletContextHolder
				.getServletContext();
		initParam(contextParamManager.getAllContextParams(),context);
		String linsenterConfig = context
				.getInitParameter(TinyListenerProcessor.LISTENER_NODE_CONFIG);

		ClassLoader classLoader = Thread.currentThread()
				.getContextClassLoader();
		if (classLoader == null) {
			classLoader = getClass().getClassLoader();
		}
		String contextPath = StringUtil.substringAfter(
				context.getContextPath(), "/");
		String packageName = WebserviceUtil.PACKAGE_NAME + "."
				+ contextPath.toLowerCase();
		List<ServiceInfo> serviceInfos = getPublishService(linsenterConfig);
		for (ServiceInfo serviceInfo : serviceInfos) {
			try {
				logger.logMessage(LogLevel.INFO,
						"开始发布服务" + serviceInfo.getServiceId());
				createService(packageName, serviceInfo, classLoader, context);
				logger.logMessage(LogLevel.INFO,
						"发布服务" + serviceInfo.getServiceId() + "成功");
			} catch (Throwable e) {
				logger.errorMessage(
						WsservletMessages.LISTENER_PARSING_FAILED(e), e);
				// logger.logMessage(LogLevel.ERROR,
				// "发布服务" + serviceInfo.getServiceId() +
				// "失败"+WsservletMessages.LISTENER_PARSING_FAILED(e));

			}
		}
		if (adapters == null) {
			adapters = new ArrayList<ServletAdapter>();
		}
		delegate = createDelegate(adapters, context);
		context.setAttribute(WSServlet.JAXWS_RI_RUNTIME_INFO, delegate);
	}

	private void initParam(List<ContextParams> allContextParams,
			TinyServletContext servletContext) {
		for (ContextParams contextParams : allContextParams) {
			for (ContextParam contextParam : contextParams.getContextParams()) {
				servletContext.setInitParameter(contextParam.getName(),
						contextParam.getValue());
			}
		}

	}

	private void createService(String packageName, ServiceInfo serviceInfo,
			ClassLoader classLoader, ServletContext context) throws IOException {
		WebserviceUtil.genWSDL(serviceInfo, packageName);
		// Parse the descriptor file and build endpoint infos
		DeploymentDescriptorParser<ServletAdapter> parser = new DeploymentDescriptorParser<ServletAdapter>(
				classLoader, new ServletResourceLoader(context),
				createContainer(context), new ServletAdapterList());

		/**
		 * 将serviceInfos 写成 JAXWS_RI_RUNTIME
		 * 
		 * */
		URL sunJaxWsXml = context.getResource(JAXWS_RI_RUNTIME);
		if (sunJaxWsXml == null)
			throw new WebServiceException(
					WsservletMessages.NO_SUNJAXWS_XML(JAXWS_RI_RUNTIME));
		InputStream is = WebserviceUtil.getXmlInputStream(serviceInfo,
				packageName);
		if (adapters == null) {
			adapters = parser.parse(sunJaxWsXml.toExternalForm(), is);
		} else {
			adapters.addAll(parser.parse(sunJaxWsXml.toExternalForm(), is));
		}
	}

	/**
	 * Creates {@link Container} implementation that hosts the JAX-WS endpoint.
	 */
	protected @NotNull Container createContainer(ServletContext context) {
		return new ServletContainer(context);
	}

	/**
	 * Creates {@link WSServletDelegate} that does the real work.
	 */
	protected @NotNull WSServletDelegate createDelegate(
			List<ServletAdapter> adapters, ServletContext context) {
		return new WSServletDelegate(adapters, context);
	}

	private static final String JAXWS_RI_RUNTIME = "/WEB-INF/sun-jaxws.xml";

	private static final Logger logger = LoggerFactory
			.getLogger(com.sun.xml.ws.util.Constants.LoggingDomain
					+ ".server.http");
}
