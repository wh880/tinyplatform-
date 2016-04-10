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
package org.tinygroup.cepcoreimpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tinygroup.beancontainer.BeanContainerFactory;
import org.tinygroup.cepcore.CEPCore;
import org.tinygroup.cepcore.CEPCoreOperator;
import org.tinygroup.cepcore.EventProcessor;
import org.tinygroup.cepcore.EventProcessorChoose;
import org.tinygroup.cepcore.EventProcessorRegisterTrigger;
import org.tinygroup.cepcore.aop.CEPCoreAopManager;
import org.tinygroup.cepcore.exception.ServiceNotFoundException;
import org.tinygroup.cepcore.impl.WeightChooser;
import org.tinygroup.cepcore.util.CEPCoreUtil;
import org.tinygroup.commons.tools.StringUtil;
import org.tinygroup.context.Context;
import org.tinygroup.event.Event;
import org.tinygroup.event.ServiceInfo;
import org.tinygroup.event.ServiceRequest;
import org.tinygroup.event.central.Node;
import org.tinygroup.logger.LogLevel;
import org.tinygroup.logger.Logger;
import org.tinygroup.logger.LoggerFactory;
import org.tinygroup.xmlparser.node.XmlNode;

/**
 * 当服务执行远程调用时 如果有多个远程处理器可用，则根据配置的EventProcessorChoose choose进行调用
 * 如果未曾配置，则默认为chooser生成权重chooser进行处理
 * 
 * @author chenjiao
 * 
 */
public class CEPCoreImpl implements CEPCore {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(CEPCoreImpl.class);
	
	private static final String ASYN_TAG = "asyn-thread-pool-config";
	private static final String ASYN_POOL_ATTRIBUTE = "bean";
	private Map<String, List<EventProcessor>> serviceIdMap = new HashMap<String, List<EventProcessor>>();
	// 服务版本，每次注册注销都会使其+1;
	ExecutorService executor = null;
	private static int serviceVersion = 0;
	/**
	 * 存放所有的EventProcessor
	 */
	private Map<String, EventProcessor> processorMap = new HashMap<String, EventProcessor>();
	/**
	 * 为本地service建立的map，便于通过serviceId迅速找到service
	 */
	private Map<String, ServiceInfo> localServiceMap = new HashMap<String, ServiceInfo>();
	/**
	 * 为远程service建立的map，便于通过serviceId迅速找到service
	 */
	private Map<String, ServiceInfo> remoteServiceMap = new HashMap<String, ServiceInfo>();
	/**
	 * 本地service列表
	 */
	private List<ServiceInfo> localServices = new ArrayList<ServiceInfo>();
	/**
	 * 为eventProcessor每次注册时所拥有的service信息 key为eventProcessor的id
	 */
	private Map<String, List<ServiceInfo>> eventProcessorServices = new HashMap<String, List<ServiceInfo>>();

	/**
	 * 
	 */
	private Map<EventProcessor, List<String>> regexMap = new HashMap<EventProcessor, List<String>>();
	/**
	 * 存放拥有正则表达式的EventProcessor，没有的不会放入此list
	 */
	private List<EventProcessor> processorList = new ArrayList<EventProcessor>();

	private String nodeName;
	private CEPCoreOperator operator;
	private EventProcessorChoose chooser;
	private List<EventProcessorRegisterTrigger> triggers = new ArrayList<EventProcessorRegisterTrigger>();

	// private byte[] lock = new byte[0];
	public CEPCoreOperator getOperator() {
		return operator;
	}

	public void startCEPCore(CEPCore cep) {
		operator.startCEPCore(cep);
	}

	public void stopCEPCore(CEPCore cep) {
		operator.stopCEPCore(cep);
	}

	public void setOperator(CEPCoreOperator operator) {
		this.operator = operator;
		this.operator.setCEPCore(this);
	}

	public String getNodeName() {
		return nodeName;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	private void dealAddEventProcessor(EventProcessor eventProcessor) {
		processorMap.put(eventProcessor.getId(), eventProcessor);
		eventProcessor.setCepCore(this);

		List<ServiceInfo> serviceList = eventProcessor.getServiceInfos();
		eventProcessorServices.put(eventProcessor.getId(), serviceList);
		if (serviceList != null && !serviceList.isEmpty()) {
			if (EventProcessor.TYPE_REMOTE != eventProcessor.getType()) {
				addLocalServiceInfo(serviceList, eventProcessor);
			} else {
				addRemoteServiceInfo(serviceList, eventProcessor);
			}
			addServiceInfos(eventProcessor, serviceList);
		}

		addRegex(eventProcessor);
	}

	private void addRemoteServiceInfo(List<ServiceInfo> serviceList,
			EventProcessor eventProcessor) {
		for (ServiceInfo service : serviceList) {
			String serviceId = service.getServiceId();
			if (remoteServiceMap.containsKey(serviceId)) {
				ServiceInfo oldService = localServiceMap.get(serviceId);
				if (oldService.compareTo(service) != 0) {
					LOGGER.logMessage(LogLevel.ERROR, "RemoteService发生id重复");
					logConflictServiceInfo(serviceId, eventProcessor);
				}
			} else {
				remoteServiceMap.put(serviceId, service);
			}

		}
	}

	private void logConflictServiceInfo(String serviceId,EventProcessor eventProcessor){
		LOGGER.logMessage(LogLevel.ERROR,"冲突serviceId:{},当前来源:{}" ,serviceId,eventProcessor.getId());
		List<EventProcessor> list = serviceIdMap.get(serviceId);
		if(list==null){return;}
		for(EventProcessor e:list){
			LOGGER.logMessage(LogLevel.ERROR,"已有来源:{}" ,e.getId());
		}
	}

	private void addLocalServiceInfo(List<ServiceInfo> serviceList,
			EventProcessor eventProcessor) {
		for (ServiceInfo service : serviceList) {
			String serviceId = service.getServiceId();
			if (localServiceMap.containsKey(serviceId)) {
				ServiceInfo oldService = localServiceMap.get(serviceId);
				if (oldService.compareTo(service) != 0) {
					LOGGER.logMessage(LogLevel.ERROR, "LocalService发生id重复");
					logConflictServiceInfo(serviceId, eventProcessor);
				}
			} else {
				localServiceMap.put(serviceId, service);
				localServices.add(service);
			}
		}
	}

	private void addServiceInfos(EventProcessor eventProcessor,
			List<ServiceInfo> serviceList) {
		for (ServiceInfo service : serviceList) {
			String name = service.getServiceId();
			if (serviceIdMap.containsKey(name)) {
				List<EventProcessor> list = serviceIdMap.get(name);
				if (!list.contains(eventProcessor)) {
					list.add(eventProcessor);
				}
			} else {
				List<EventProcessor> list = new ArrayList<EventProcessor>();
				serviceIdMap.put(name, list);
				list.add(eventProcessor);
			}
		}
	}

	private void addRegex(EventProcessor eventProcessor) {
		if (eventProcessor.getRegex() != null
				&& !eventProcessor.getRegex().isEmpty()) {
			regexMap.put(eventProcessor, eventProcessor.getRegex());
			processorList.add(eventProcessor);
		}
	}

	public void registerEventProcessor(EventProcessor eventProcessor) {

		LOGGER.logMessage(LogLevel.INFO, "开始 注册EventProcessor:{}",
				eventProcessor.getId());
		changeVersion(eventProcessor);
		if (processorMap.containsKey(eventProcessor.getId())) {
			removeEventProcessorInfo(eventProcessor);
		}
		dealAddEventProcessor(eventProcessor);
		LOGGER.logMessage(LogLevel.INFO, "注册EventProcessor:{}完成",
				eventProcessor.getId());
	}

	private void removeEventProcessorInfo(EventProcessor eventProcessor) {
		if (!eventProcessorServices.containsKey(eventProcessor.getId())) {
			return;
		}
		List<ServiceInfo> serviceInfos = eventProcessorServices
				.get(eventProcessor.getId());
		for (ServiceInfo service : serviceInfos) {
			removeServiceInfo(eventProcessor, service);
		}

		if (eventProcessor.getRegex() != null
				&& !eventProcessor.getRegex().isEmpty()) {
			regexMap.remove(eventProcessor);
		}
	}

	private void removeServiceInfo(EventProcessor eventProcessor,
			ServiceInfo service) {
		String name = service.getServiceId();
		if (serviceIdMap.containsKey(name)) {
			localServices.remove(service);// 20150318调整代码localServices.remove(localServices。indexOf(service))，旧代码有可能是-1
			List<EventProcessor> list = serviceIdMap.get(name);
			if (list.contains(eventProcessor)) {
				list.remove(eventProcessor);
				if (list.isEmpty()) {
					serviceIdMap.remove(name);
					localServiceMap.remove(name);
				}
			}

		} else {
			// do nothing
		}
	}

	public void unregisterEventProcessor(EventProcessor eventProcessor) {

		LOGGER.logMessage(LogLevel.INFO, "开始 注销EventProcessor:{}",
				eventProcessor.getId());
		changeVersion(eventProcessor);
		processorMap.remove(eventProcessor.getId());
		removeEventProcessorInfo(eventProcessor);
		LOGGER.logMessage(LogLevel.INFO, "注销EventProcessor:{}完成",
				eventProcessor.getId());
	}

	private void changeVersion(EventProcessor eventProcessor) {
		if (eventProcessor.getType() == EventProcessor.TYPE_LOCAL) {
			LOGGER.logMessage(LogLevel.INFO,
					"本地EventProcessor变动,对CEPCORE服务版本进行变更");
			serviceVersion++;// 如果发生了本地EventProcessor变动，则改变版本
		}
	}

	public void process(Event event) {
		CEPCoreAopManager aopMananger = BeanContainerFactory.getBeanContainer(
				this.getClass().getClassLoader()).getBean(
				CEPCoreAopManager.CEPCORE_AOP_BEAN);
		// 前置Aop
		aopMananger.beforeHandle(event);
		ServiceRequest request = event.getServiceRequest();
		String eventNodeName = event.getServiceRequest().getNodeName();
		LOGGER.logMessage(LogLevel.DEBUG, "请求指定的执行节点为:{0}", eventNodeName);

		EventProcessor eventProcessor = getEventProcessor(request,
				eventNodeName);
		if (EventProcessor.TYPE_LOCAL == eventProcessor.getType()) {
			// local前置Aop
			aopMananger.beforeLocalHandle(event);
			try {
				// local处理
				// eventProcessor.process(event);
				deal(eventProcessor, event);
			} catch (RuntimeException e) {
				dealException(e, event);
				throw e;
			}
			// local后置Aop
			aopMananger.afterLocalHandle(event);
		} else {
			// remote前置Aop
			aopMananger.beforeRemoteHandle(event);

			try {
				// remote处理
				// eventProcessor.process(event);
				dealRemote(eventProcessor, event);
			} catch (RuntimeException e) {
				dealException(e, event);
				throw e;
			}
			// remote后置Aop
			aopMananger.afterRemoteHandle(event);
		}
		// 后置Aop
		aopMananger.afterHandle(event);
	}

	private void dealRemote(EventProcessor eventProcessor, Event event) {
		LOGGER.logMessage(LogLevel.DEBUG, "请求指定的执行处理器为:{0}",
				eventProcessor.getId());
		Context oldContext = event.getServiceRequest().getContext();
		ServiceParamUtil.changeEventContext(event, this, Thread.currentThread()
				.getContextClassLoader());
		eventProcessor.process(event);
		ServiceParamUtil.resetEventContext(event, this, oldContext);
	}

	private void deal(EventProcessor eventProcessor, Event event) {
		LOGGER.logMessage(LogLevel.DEBUG, "请求指定的执行处理器为:{0}",
				eventProcessor.getId());
		Context oldContext = event.getServiceRequest().getContext();
		ServiceParamUtil.changeEventContext(event, this, Thread.currentThread()
				.getContextClassLoader());
		if (event.getMode() == Event.EVENT_MODE_ASYNCHRONOUS) {
			LOGGER.logMessage(LogLevel.INFO, "请求{}为异步请求", event.getEventId());
			Event e = getEventClone(event);
			event.setMode(Event.EVENT_MODE_ASYNCHRONOUS);
			event.setType(Event.EVENT_TYPE_RESPONSE);
			// q调整为线程池
			SynchronousDeal thread = new SynchronousDeal(eventProcessor, e);
			// thread.start();
			if (!getExecutorService().isShutdown()) {
				getExecutorService().execute(thread);
				LOGGER.logMessage(LogLevel.INFO, "已开启异步请求{}执行线程",
						event.getEventId());
			} else {
				LOGGER.logMessage(LogLevel.INFO, "异步请求{}线程池已关闭，直接返回");
				return;
			}

		} else {
			eventProcessor.process(event);
		}
		ServiceParamUtil.resetEventContext(event, this, oldContext);
	}

	private Event getEventClone(Event event) {
		Event e = new Event();
		e.setEventId(event.getEventId());
		e.setServiceRequest(event.getServiceRequest());
		e.setType(event.getType());
		e.setGroupMode(event.getGroupMode());
		e.setMode(event.getMode());
		e.setPriority(event.getPriority());
		return e;
	}

	private void dealException(Throwable e, Event event) {
		CEPCoreUtil.handle(e, event, this.getClass().getClassLoader());
		Throwable t = e.getCause();
		while (t != null) {
			CEPCoreUtil.handle(t, event, this.getClass().getClassLoader());
			t = t.getCause();
		}
	}

	private EventProcessor getEventProcessorByRegex(
			ServiceRequest serviceRequest) {
		String serviceId = serviceRequest.getServiceId();
		for (EventProcessor p : processorList) {
			List<String> regex = regexMap.get(p);
			if (p.isEnable() && checkRegex(regex, serviceId)) {
				return p;
			}
		}
		throw new RuntimeException("没有找到合适的服务处理器");
	}

	private boolean checkRegex(List<String> regex, String serviceId) {
		for (String s : regex) {
			Pattern pattern = Pattern.compile(s);
			Matcher matcher = pattern.matcher(serviceId);
			boolean b = matcher.matches(); // 满足时，将返回true，否则返回false
			if (b) {
				return true;
			}
		}
		return false;
	}

	private EventProcessor getEventProcessor(ServiceRequest serviceRequest,
			String eventNodeName) {
		// 查找出所有包含该服务的EventProcessor
		String serviceId = serviceRequest
						.getServiceId();
		List<EventProcessor> allList = serviceIdMap.get(serviceId);
		if (allList == null) { // 如果取出来是空，先初始化便于处理
			allList = new ArrayList<EventProcessor>();
		}
		List<EventProcessor> list = new ArrayList<EventProcessor>();
		for (EventProcessor e : allList) {
			if(CEPCoreUtil.isTinySysService(serviceId)){
				list.add(e);
			}else if (e.isEnable()) {
				list.add(e);
			} else {
				LOGGER.logMessage(LogLevel.WARN,
						"EventProcessor:{} enable为false", e.getId());
			}
		}
		// 如果指定了执行节点名，则根据执行节点查找处理器
		if (!StringUtil.isBlank(eventNodeName)) {
			return findEventProcessor(serviceRequest, eventNodeName, list);
		}

		if (list.isEmpty()) {
			return getEventProcessorByRegex(serviceRequest);
		}
		return getEventProcessor(serviceRequest, list);
	}

	private EventProcessor findEventProcessor(ServiceRequest serviceRequest,
			String eventNodeName, List<EventProcessor> list) {
		boolean isCurrentNode = false;
		// 如果指定了执行节点，则判断执行节点是否是当前节点
		if (!StringUtil.isBlank(eventNodeName) && !StringUtil.isBlank(nodeName)) {
			LOGGER.logMessage(LogLevel.INFO, "当前节点NodeName:{}", nodeName);
			if (Node.checkEquals(eventNodeName, nodeName)) {
				isCurrentNode = true;
				LOGGER.logMessage(LogLevel.INFO, "请求指定的执行节点即当前节点");
			}
		}
		String serviceId = serviceRequest.getServiceId();
		if (!isCurrentNode) {
			return notCurrentNode(eventNodeName, list, serviceId);
		}
		return isCurrentNode(eventNodeName, list, serviceId);
	}

	private EventProcessor isCurrentNode(String eventNodeName,
			List<EventProcessor> list, String serviceId) {
		// 如果是当前节点，则判断查找出来的EventProcessor是否是本地处理器，如果是，则返回
		for (EventProcessor e : list) {
			if (EventProcessor.TYPE_LOCAL == e.getType()) {
				return e;
			}
		}
		throw new RuntimeException("当前服务器上不存在请求" + serviceId + "对应的事件处理器");
	}

	private EventProcessor notCurrentNode(String eventNodeName,
			List<EventProcessor> list, String serviceId) {
		// 如果不是当前节点，则根据节点名查找到指定节点
		// 首先判断该节点是否是存在于已查找到的包含该服务的list之中，如果包含则返回
		// 如果不包含，则读取该节点的正则表达式信息，如果匹配则返回
		for (String key : processorMap.keySet()) {
			if (Node.checkEquals(key, eventNodeName)) {
				EventProcessor e = processorMap.get(key);
				if (!e.isEnable()&&!CEPCoreUtil.isTinySysService(serviceId)) {
					LOGGER.logMessage(LogLevel.WARN,
							"EventProcessor:{} enable为false", e.getId());
					continue;
				}
				// 如果包含该服务的EventProcessor列表中存在该处理器，则返回
				if (list.contains(e)) {
					return e;
				}
				if (processorList.contains(e)) {
					List<String> regex = regexMap.get(e);
					if (checkRegex(regex, serviceId)) {
						return e;
					}
				}
				throw new RuntimeException("节点" + eventNodeName
						+ "对应的事件处理器上不存在服务:" + serviceId);
			}
		}
		throw new RuntimeException("当前服务器上不存在节点:" + eventNodeName + "对应的事件处理器");
	}

	private EventProcessor getEventProcessor(ServiceRequest serviceRequest,
			List<EventProcessor> list) {
		if (list.size() == 1) {
			return list.get(0);
		}

		// 如果有本地的 则直接返回本地的EventProcessor
		for (EventProcessor e : list) {
			if (e.getType() == EventProcessor.TYPE_LOCAL) {
				return e;
			}
		}
		// 如果全是远程EventProcessor,那么需要根据负载均衡机制计算
		return getEventProcessorChoose().choose(list);
	}

	public void start() {
		if (operator != null) {
			operator.startCEPCore(this);
		}
	}

	public void stop() {
		if (operator != null) {
			operator.stopCEPCore(this);
		}
		try {
			getExecutorService().shutdown();
		} catch (Exception e) {
			LOGGER.errorMessage("关闭CEPCore异步线程池时发生异常", e);
		}

	}

	public List<ServiceInfo> getServiceInfos() {
		return localServices;
	}

	private ServiceInfo getLocalServiceInfo(String serviceId) {
		return localServiceMap.get(serviceId);
	}

	private ServiceInfo getRemoteServiceInfo(String serviceId) {
		return remoteServiceMap.get(serviceId);
	}

	public ServiceInfo getServiceInfo(String serviceId) {
		ServiceInfo info = getLocalServiceInfo(serviceId);
		if (info == null) {
			info = getRemoteServiceInfo(serviceId);
		}
		if (info == null) {
			throw new ServiceNotFoundException(serviceId);
		}
		return info;

	}

	public void setEventProcessorChoose(EventProcessorChoose chooser) {
		this.chooser = chooser;
	}

	private EventProcessorChoose getEventProcessorChoose() {
		if (chooser == null) {
			chooser = new WeightChooser();
		}
		return chooser;
	}

	class SynchronousDeal implements Runnable {
		Event e;
		EventProcessor eventProcessor;

		public SynchronousDeal(EventProcessor eventProcessor, Event e) {
			this.e = e;
			this.eventProcessor = eventProcessor;
		}

		public void run() {
			eventProcessor.process(e);
		}
	}

	public void addEventProcessorRegisterTrigger(
			EventProcessorRegisterTrigger trigger) {
		triggers.add(trigger);
	}

	public void refreshEventProcessors() {
		for (EventProcessor processor : processorMap.values()) {
			if (!processor.isRead()) {
				registerEventProcessor(processor);
				processor.setRead(true);
			}
		}
		// if (operator != null && operator instanceof ArOperator) {
		// ((ArOperator) operator).reReg();
		// operator.getClass().getMethod("reReg");
		// }
		if (operator != null) {
			try {
				Method m = operator.getClass().getMethod("reReg");
				if (m != null) {
					m.invoke(operator);
				}
			} catch (IllegalArgumentException e) {
				LOGGER.errorMessage(e.getMessage(), e);
			} catch (IllegalAccessException e) {
				LOGGER.errorMessage(e.getMessage(), e);
			} catch (InvocationTargetException e) {
				LOGGER.errorMessage(e.getMessage(), e);
			} catch (SecurityException e) {
				LOGGER.errorMessage(e.getMessage(), e);
			} catch (NoSuchMethodException e) {
				LOGGER.errorMessage(e.getMessage(), e);
			}
		}
	}

	public List<EventProcessor> getEventProcessors() {
		List<EventProcessor> processors = new ArrayList<EventProcessor>();
		for (EventProcessor processor : processorMap.values()) {
			processors.add(processor);
		}
		return processors;
	}

	public int getServiceInfosVersion() {
		return serviceVersion;
	}

	public void setConfig(XmlNode config) {
		parseAsynPool(config);
	}

	private void parseAsynPool(XmlNode appConfig) {
		String configBean = ThreadPoolConfig.DEFAULT_THREADPOOL;
		if (appConfig == null) {
			LOGGER.logMessage(LogLevel.WARN,
					"未配置异步服务线程池config bean,使用默认配置bean:{}",
					ThreadPoolConfig.DEFAULT_THREADPOOL);
		} else if (appConfig.getSubNode(ASYN_TAG) == null) {
			LOGGER.logMessage(LogLevel.WARN, "未配置异步服务线程池节点：{}", ASYN_TAG);
		} else {
			configBean = appConfig.getSubNode(ASYN_TAG).getAttribute(
					ASYN_POOL_ATTRIBUTE);
		}
		if (StringUtil.isBlank(configBean)) {
			configBean = ThreadPoolConfig.DEFAULT_THREADPOOL;
			LOGGER.logMessage(LogLevel.WARN,
					"未配置异步服务线程池config bean,使用默认配置bean:{}",
					ThreadPoolConfig.DEFAULT_THREADPOOL);
		}
		initThreadPool(configBean);
	}

	private synchronized void initThreadPool(String configBean) {

		if (executor != null) {
			return;
		}
		ThreadPoolConfig poolConfig = BeanContainerFactory.getBeanContainer(
				this.getClass().getClassLoader()).getBean(configBean);
		executor = ThreadPoolFactory.getThreadPoolExecutor(poolConfig);
	}

	private ExecutorService getExecutorService() {
		if (executor == null) {
			LOGGER.logMessage(LogLevel.WARN,
					"未配置异步服务线程池config bean,使用默认配置bean:{}",
					ThreadPoolConfig.DEFAULT_THREADPOOL);
			initThreadPool(ThreadPoolConfig.DEFAULT_THREADPOOL);

		}
		return executor;
	}
}