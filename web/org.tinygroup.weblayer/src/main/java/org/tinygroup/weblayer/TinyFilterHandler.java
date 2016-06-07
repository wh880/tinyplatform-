/**
 *  Copyright (c) 1997-2013, www.tinygroup.org (tinygroup@126.com).
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

import static org.tinygroup.weblayer.webcontext.parser.ParserWebContext.DEFAULT_CHARSET_ENCODING;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tinygroup.beancontainer.BeanContainerFactory;
import org.tinygroup.commons.tools.Assert;
import org.tinygroup.commons.tools.CollectionUtil;
import org.tinygroup.commons.tools.StringUtil;
import org.tinygroup.logger.LogLevel;
import org.tinygroup.logger.Logger;
import org.tinygroup.logger.LoggerFactory;
import org.tinygroup.weblayer.exceptionhandler.WebExceptionHandlerManager;
import org.tinygroup.weblayer.webcontext.CommitMonitor;
import org.tinygroup.weblayer.webcontext.SimpleWebContext;
import org.tinygroup.weblayer.webcontext.TwoPhaseCommitWebContext;
import org.tinygroup.weblayer.webcontext.buffered.BufferedWebContext;
import org.tinygroup.weblayer.webcontext.parser.ParserWebContext;
import org.tinygroup.weblayer.webcontext.util.QueryStringParser;
import org.tinygroup.weblayer.webcontext.util.WebContextUtil;

/**
 * 完成filter处理的中间类
 * 
 * @author renhui
 */
public class TinyFilterHandler {

	private static final String SEARCH_STR = "?";
	private static final Logger logger = LoggerFactory
			.getLogger(TinyFilterHandler.class);
	private TinyFilterManager tinyFilterManager;
	private TinyProcessorManager tinyProcessorManager;
	private FilterChain filterChain;
	private String servletPath;
	private WebContext context;
	private List<Pattern> passthruPatterns;
	/**
	 * 在request中保存TinyFilterHandler的键名。
	 */
	private static final String WEB_CONTEXT_OWNER_KEY = "_web_context_owner_";

	public TinyFilterHandler(String servletPath, FilterChain filterChain,
			WebContext context, TinyFilterManager tinyFilterManager,
			TinyProcessorManager tinyProcessorManager, List<Pattern> passthruPatterns) {
		super();
		this.context = context;
		this.filterChain = filterChain;
		this.servletPath = servletPath;
		this.tinyFilterManager = tinyFilterManager;
		this.tinyProcessorManager = tinyProcessorManager;
		this.passthruPatterns=passthruPatterns;
	}

	public WebContext getContext() {
		return context;
	}

	public void setContext(WebContext context) {
		this.context = context;
	}

	/**
	 * 采用tiny-filter方式来处理
	 * 
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	public void tinyFilterProcessor(HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		logger.logMessage(LogLevel.DEBUG, "请求路径：<{}>", servletPath);
		List<TinyFilter> tinyFilters = tinyFilterManager
				.getTinyFiltersWithUrl(servletPath);
		WebContext wrapperContext = null;
		try {
			wrapperContext = getWebContext(context, tinyFilters, request,
					response);
			// 如果请求已经结束，则不执行进一步的处理。例如，当requestContext已经被重定向了，则立即结束请求的处理。
			if (isRequestFinished(tinyFilters.size(), wrapperContext)) {
				return;
			}
			servletPath = getServletPath(wrapperContext.getRequest(),
					wrapperContext);// 重新获取路径
			// 可能之前被修改过
			if (isRequestPassedThru(servletPath)||!tinyProcessorManager.execute(servletPath, wrapperContext)) {
				giveUpControl(wrapperContext);
				return;
			}
		} catch (Exception e) {
			if (wrapperContext == null) {
				wrapperContext = WebContextUtil.getWebContext(request);
			}
			handleException(wrapperContext, e, context.getRequest(),
					context.getResponse());
		} finally {
			postProcess(wrapperContext, tinyFilters);
		}
	}

	private String getServletPath(HttpServletRequest request,
			WebContext webContext) {
		String servletPath = (String) request
				.getAttribute(TinyHttpFilter.DEFAULT_PAGE_KEY);
		if (StringUtil.isBlank(servletPath)) {
			servletPath = request.getServletPath();
			if (StringUtil.isBlank(servletPath)) {
				servletPath = request.getPathInfo();
			}
			if(StringUtil.isBlank(servletPath)){//兼容tomcat8的处理情况，如果servletPath为空，设置为"/"
				servletPath="/";
			}
		}
		if (StringUtil.contains(servletPath, SEARCH_STR)) {
			parserRequestPath(servletPath, request, webContext);
		}
		return StringUtil.substringBefore(servletPath, SEARCH_STR);
	}

	/**
	 * 解析请求路径servletPath的参数信息，把参数信息存放在上下文中
	 * 
	 * @param request
	 * @param servletPath
	 */
	private void parserRequestPath(String servletPath,
			HttpServletRequest request, final WebContext webContext) {
		ParserWebContext requestContext = WebContextUtil.findWebContext(
				request, ParserWebContext.class);
		String charset = requestContext.isUseBodyEncodingForURI() ? request
				.getCharacterEncoding() : requestContext.getURIEncoding();
		QueryStringParser parser = new QueryStringParser(charset,
				DEFAULT_CHARSET_ENCODING) {
			protected void add(String key, String value) {
				webContext.put(key, value);
			}
		};
		parser.parse(StringUtil.substringAfter(servletPath, SEARCH_STR));
	}
	
    /**
	 * 对于需要被passthru的request，不执行TinyProcessor，而是立即把控制交还给filter chain。
     * 该功能适用于仅将weblayer视作普通的filter，而filter chain的接下来的部分将可使用weblayer所提供的TinyFilter功能。
	 * @param servletPath
	 * @return
	 */
	private boolean isRequestPassedThru(String servletPath) {
		if(!CollectionUtil.isEmpty(passthruPatterns)){
			for (Pattern pattern : passthruPatterns) {
				if (pattern.matcher(servletPath).matches()) {
					logger.logMessage(LogLevel.DEBUG, "Passed through request: {0}",servletPath);
					return true;
				}
			}
		}
		return false;
	}
    

	private void giveUpControl(WebContext wrapperContext) throws IOException,
			ServletException {
		logger.logMessage(LogLevel.DEBUG,
				"放弃控制，将控制权返回给servlet engine,请求路径：<{}>", servletPath);
		// 1. 关闭buffering
		BufferedWebContext brc = WebContextUtil.findWebContext(wrapperContext,
				BufferedWebContext.class);
		if (brc != null) {
			try {
				brc.setBuffering(false);
			} catch (IllegalStateException e) {
				// getInputStream或getWriter已经被调用了，不能更改buffering参数。
			}
		}
		// 2. 取消contentType的设置
		try {
			wrapperContext.getResponse().setContentType(null);
		} catch (Exception e) {
			// ignored, 有可能有的servlet engine不支持null参数
		}
		filterChain.doFilter(wrapperContext.getRequest(),
				wrapperContext.getResponse());
	}

	private WebContext getWebContext(WebContext context,
			List<TinyFilter> tinyFilters, HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		WebContext webContext = WebContextUtil.getWebContext(context
				.getRequest());
		if (webContext == null) {
			webContext = createWrapperContext(context, tinyFilters, request,
					response);
			context.getRequest().setAttribute(WEB_CONTEXT_OWNER_KEY, this);
		}
		return webContext;
	}

	private WebContext createWrapperContext(WebContext context,
			List<TinyFilter> tinyFilters, HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		SimpleWebContext innerWebContext = new SimpleWebContext(context, this,
				request, response);
		WebContext wrapperedContext = innerWebContext;
		WebContextUtil.setWebContext(wrapperedContext);
		logger.logMessage(LogLevel.DEBUG, "tiny-filter开始进行前置处理操作");
		try {
			for (TinyFilter filter : tinyFilters) {
				wrapperedContext = filter.wrapContext(wrapperedContext);
				if (filter != null) {
					logger.logMessage(LogLevel.DEBUG, "tiny-filter<{}>进行前置处理",
							filter.getClass().getName());
					filter.preProcess(wrapperedContext);
					WebContextUtil.setWebContext(wrapperedContext);
				}
			}
		} finally {
			wrapperedContext = WebContextUtil.getWebContext(request);
			innerWebContext.setTopWebContext(wrapperedContext);
		}
		logger.logMessage(LogLevel.DEBUG, "tiny-filter前置处理操作结束");
		logger.logMessage(LogLevel.DEBUG, "Created a new web context: {}",
				wrapperedContext);
		return wrapperedContext;
	}

	/**
	 * 处理异常e的过程
	 * 
	 * @param webContext
	 * @param e
	 */
	private void handleException(WebContext webContext, Throwable e,
			HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (webContext != null) {
			request = webContext.getRequest();
			response = webContext.getResponse();
		}
		try {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} catch (Exception ee) {
			// ignore this exception
		}
		clearBuffer(response);
		// 进行自定义异常处理
		WebExceptionHandlerManager exceptionHandlerManager = BeanContainerFactory
				.getBeanContainer(this.getClass().getClassLoader()).getBean(
						WebExceptionHandlerManager.MANAGER_BEAN);
		exceptionHandlerManager.handler(e, webContext);
	}

	/**
	 * 清除buffer
	 * 
	 * @param response
	 */
	private void clearBuffer(HttpServletResponse response) {
		if (!response.isCommitted()) {
			response.resetBuffer();
		}
	}

	/**
	 * 判断请求是否已经结束。如果请求被重定向了，则表示请求已经结束。
	 * 
	 * @param webContext
	 * @return
	 */
	private boolean isRequestFinished(int filterSize, WebContext webContext) {
		WebContext wrapContext = webContext;
		for (int i = 0; i < filterSize; i++) {
			if (wrapContext != null) {
				if (wrapContext.isRequestFinished()) {
					return true;
				}
				wrapContext = wrapContext.getWrappedWebContext();
			}
		}
		return false;
	}

	private void postProcess(WebContext webContext, List<TinyFilter> tinyFilters) {
		if (webContext == null) {
			return;
		}
		try {
			if (this == webContext.getRequest().getAttribute(
					WEB_CONTEXT_OWNER_KEY)) {
				webContext.getRequest().removeAttribute(WEB_CONTEXT_OWNER_KEY);
				commitWebRequest(webContext, tinyFilters);
			}
		} catch (Exception e) {
			logger.errorMessage("Exception occurred while commit rundata", e);
		}
		setContext(webContext);
	}

	/**
	 * 提交web请求操作
	 * 
	 * @param webContext
	 * @param tinyFilters
	 * @throws IOException
	 * @throws ServletException
	 */
	private void commitWebRequest(WebContext webContext,
			List<TinyFilter> tinyFilters) throws ServletException, IOException {
		logger.logMessage(LogLevel.DEBUG, "tiny-filter开始进行后置处理操作");
		CommitMonitor monitor = getCommitMonitor(webContext);
		synchronized (monitor) {
			if (!monitor.isCommitted()) {
				boolean doCommitHeaders = !monitor.isHeadersCommitted();
				monitor.setCommitted(true);
				HttpServletRequest request = webContext.getRequest();
				int size = tinyFilters.size() - 1;
				WebContext wrapperedContext = webContext;
				for (int i = size; i >= 0; i--) {
					TinyFilter filter = tinyFilters.get(i);
					if (filter != null) {
						logger.logMessage(LogLevel.DEBUG,
								"tiny-filter<{}>进行后置处理", filter.getClass()
										.getName());
						if (wrapperedContext instanceof TwoPhaseCommitWebContext
								&& doCommitHeaders) {
							((TwoPhaseCommitWebContext) wrapperedContext)
									.commitHeaders();
						}
						filter.postProcess(wrapperedContext);
					}
					wrapperedContext = wrapperedContext.getWrappedWebContext();

				}
				// 将request和requestContext断开
				WebContextUtil.removeWebContext(request);
				logger.logMessage(LogLevel.DEBUG, "Committed request: {}",
						request);
			}
		}
		logger.logMessage(LogLevel.DEBUG, "tiny-filter后置处理操作结束");
	}

	public String getServletPath() {
		return servletPath;
	}

	/**
	 * 头部提交，保证内容只提交一次
	 * 
	 * @param wrapperedContext
	 */
	public void commitHeaders(WebContext wrapperedContext) {
		CommitMonitor monitor = getCommitMonitor(wrapperedContext);

		synchronized (monitor) {
			if (!monitor.isHeadersCommitted()) {
				monitor.setHeadersCommitted(true);

				for (WebContext rc = wrapperedContext; rc != null; rc = rc
						.getWrappedWebContext()) {
					if (rc instanceof TwoPhaseCommitWebContext) {
						TwoPhaseCommitWebContext tpc = (TwoPhaseCommitWebContext) rc;
						logger.logMessage(LogLevel.TRACE,
								"Committing headers: {}", tpc.getClass()
										.getSimpleName());
						tpc.commitHeaders();
					}
				}
			}
		}

	}

	private CommitMonitor getCommitMonitor(WebContext webContext) {
		CommitMonitor monitor = WebContextUtil.findWebContext(webContext,
				SimpleWebContext.class);
		return Assert.assertNotNull(monitor, "no monitor");
	}

}
