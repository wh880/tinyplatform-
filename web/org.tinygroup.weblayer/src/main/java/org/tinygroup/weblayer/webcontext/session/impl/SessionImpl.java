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
package org.tinygroup.weblayer.webcontext.session.impl;

import static java.util.Collections.emptyMap;
import static org.tinygroup.commons.tools.Assert.assertNotNull;
import static org.tinygroup.commons.tools.Assert.unreachableCode;
import static org.tinygroup.commons.tools.CollectionUtil.asList;
import static org.tinygroup.commons.tools.CollectionUtil.createHashMap;
import static org.tinygroup.commons.tools.CollectionUtil.createHashSet;
import static org.tinygroup.commons.tools.CollectionUtil.createLinkedHashMap;
import static org.tinygroup.commons.tools.CollectionUtil.createLinkedHashSet;
import static org.tinygroup.commons.tools.ObjectUtil.isEquals;
import static org.tinygroup.commons.tools.StringUtil.trimToNull;

import java.io.Serializable;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.tinygroup.commons.tools.StringUtil;
import org.tinygroup.commons.tools.ToStringBuilder;
import org.tinygroup.commons.tools.ToStringBuilder.MapBuilder;
import org.tinygroup.logger.LogLevel;
import org.tinygroup.logger.Logger;
import org.tinygroup.logger.LoggerFactory;
import org.tinygroup.weblayer.configmanager.TinyListenerConfigManager;
import org.tinygroup.weblayer.configmanager.TinyListenerConfigManagerHolder;
import org.tinygroup.weblayer.webcontext.session.HttpHeaderSessionStore;
import org.tinygroup.weblayer.webcontext.session.Session;
import org.tinygroup.weblayer.webcontext.session.SessionAttributeInterceptor;
import org.tinygroup.weblayer.webcontext.session.SessionConfig;
import org.tinygroup.weblayer.webcontext.session.SessionInterceptor;
import org.tinygroup.weblayer.webcontext.session.SessionLifecycleListener;
import org.tinygroup.weblayer.webcontext.session.SessionManager;
import org.tinygroup.weblayer.webcontext.session.SessionModel;
import org.tinygroup.weblayer.webcontext.session.SessionModelEncoder;
import org.tinygroup.weblayer.webcontext.session.SessionStore;
import org.tinygroup.weblayer.webcontext.session.SessionStore.StoreContext;
import org.tinygroup.weblayer.webcontext.session.SessionWebContext;
import org.tinygroup.weblayer.webcontext.session.model.SessionManagerFactory;

/**
 * 实现了<code>HttpSession</code>接口。
 * <p>
 * 注意，每个request均会创建独立的session对象，此对象本身不是线程安全的，不能被多线程同时访问。但其后备的session
 * store是线程安全的。
 * </p>
 */
public class SessionImpl implements HttpSession,Serializable,Session {
	private final static Logger log = LoggerFactory
			.getLogger(SessionImpl.class);
	private transient final HttpSessionInternal sessionInternal = new HttpSessionInternal();
	private String sessionID;
	private transient SessionWebContext webContext;
	private String modelKey;
	private SessionModelImpl model;
	private boolean isNew;
	private transient Map<String, SessionAttribute> attrs = createHashMap();
	private transient Map<String, Object> storeStates = createHashMap();
	private boolean invalidated = false;
	private boolean cleared = false;
	private transient Set<String> clearingStores = createHashSet();

	/** 创建一个session对象。 */
	public SessionImpl(String sessionID, SessionWebContext webContext,
			boolean isNew, boolean create) {
		this.sessionID = assertNotNull(sessionID, "no sessionID");
		this.webContext = webContext;
		this.modelKey = webContext.getSessionConfig().getModelKey();

		EventType event;

		// 进到这里的session可能有四种情况：
		// 1. Requested sessionID为空
		// 2. Requested sessionID所对应的session不存在
		// 3. Requested sessionID所对应的session已经过期
		// 3.5 Requested sessionID和model中的session ID不匹配，视作session过期
		// 4. Requested sessionID所对应的session存在且合法
		if (isNew) {
			event = EventType.CREATED;

			// 情况1：创建新的model，并保存之。
			log.logMessage(LogLevel.DEBUG,
					"No session ID was found in cookie or URL.  A new session will be created.");
			sessionInternal.invalidate();
		} else {
			model = (SessionModelImpl) sessionInternal.getAttribute(modelKey);

			if (model == null) {
				event = EventType.CREATED;

				// 情况2：创建新的model，并保存之。
				log.logMessage(
						LogLevel.DEBUG,
						"Session state was not found for sessionID \"{0}\".  A new session will be created.",
						sessionID);
				isNew = true;
				sessionInternal.invalidate();
			} else {
				boolean expired = false;

				String modelSessionID = trimToNull(model.getSessionID());

				// 检查SessionID的值是否相等，并兼容SessionModel中没有设置SessionID的场景
				// 特殊情况：model中未包含session ID，这时，不作检查，以request中的sessionID为准
				if (modelSessionID != null && !modelSessionID.equals(sessionID)) {
					// 情况3.5 视作过期
					expired = true;
					log.logMessage(
							LogLevel.WARN,
							"Requested session ID \"{0}\" does not match the ID in session model \"{1}\".  "
									+ "Force expired the session.", sessionID,
							modelSessionID);
				}
				// Session model被返回前，会先被decode。因此，修改所返回的session
				// model对象，并不会影响store中的对象值。
				model.setSession(this); // update the session config &
										// session
										// id in model
				expired |= model.isExpired();

				if (expired) {
					event = EventType.RECREATED;

					// 情况3：更新model如同新建的一样，同时清除老数据。
					log.logMessage(
							LogLevel.DEBUG,
							"Session has expired: sessionID={}, created at {}, last accessed at {}, "
									+ "maxInactiveInterval={}, forceExpirationPeriod={}",
							modelSessionID,
							new Date(model.getCreationTime()),
							new Date(model.getLastAccessedTime()),
							model.getMaxInactiveInterval(),
							getSessionWebContext().getSessionConfig()
                                    .getForceExpirationPeriod());

					isNew = true;
					sessionInternal.invalidate();
				} else {
					event = EventType.VISITED;

					// 情况4：更新model的最近访问时间。
					log.logMessage(
							LogLevel.TRACE,
							"Activate session: sessionID={}, last accessed at {}, maxInactiveInterval={}",
							modelSessionID,
							new Date(model.getLastAccessedTime()),
							model.getMaxInactiveInterval());
					model.touch();
				}
			}
		}

		this.isNew = isNew;

		// 确保model attribute的modified=true
		sessionInternal.setAttribute(modelKey, model);

		// 调用session lifecycle listener
		fireEvent(event);
		sessionActivated();
	}

	/**
	 * 取得创建该session的request context。
	 * 
	 * @return request context
	 */
	public SessionWebContext getSessionWebContext() {
		return webContext;
	}

	/**
	 * 取得当前的model。
	 * 
	 * @return model对象
	 */
	public SessionModel getSessionModel() {
		return model;
	}

	/**
	 * 取得session ID。
	 * 
	 * @return session ID
	 */
	public String getId() {
		return sessionID;
	}

	/**
	 * 取得session的创建时间。
	 * 
	 * @return 创建时间戮
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 */
	public long getCreationTime() {
		assertValid("getCreationTime");
		return sessionInternal.getCreationTime();
	}

	/**
	 * 取得最近访问时间。
	 * 
	 * @return 最近访问时间戮
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 */
	public long getLastAccessedTime() {
		assertValid("getLastAccessedTime");
		return model.getLastAccessedTime();
	}

	/**
	 * 取得session的最大不活动期限，超过此时间，session就会失效。
	 * 
	 * @return 不活动期限的秒数
	 */
	public int getMaxInactiveInterval() {
		assertModel("getMaxInactiveInterval");
		return model.getMaxInactiveInterval();
	}
	
	public String getSessionID() {
		assertModel("getSessionID");
		return model.getSessionID();
	}

	public boolean isExpired() {
		assertModel("isExpired");
		return model.isExpired();
	}

	/**
	 * 设置session的最大不活动期限，超过此时间，session就会失效。
	 * 
	 * @param maxInactiveInterval
	 *            不活动期限的秒数
	 */
	public void setMaxInactiveInterval(int maxInactiveInterval) {
		assertModel("setMaxInactiveInterval");
		model.setMaxInactiveInterval(maxInactiveInterval);
	}

	/**
	 * 取得当前session所属的servlet context。
	 * 
	 * @return <code>ServletContext</code>对象
	 */
	public ServletContext getServletContext() {
		return webContext.getServletContext();
	}

	/**
	 * 取得指定名称的attribute值。
	 * 
	 * @param name
	 *            attribute名称
	 * @return attribute的值
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 */
	public Object getAttribute(String name) {
		assertValid("getAttribute");
		return sessionInternal.getAttribute(name);
	}

	/**
	 * 取得所有attributes的名称。
	 * 
	 * @return attribute名称列表
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 */
	public Enumeration<String> getAttributeNames() {
		assertValid("getAttributeNames");

		Set<String> attrNames = getAttributeNameSet();

		final Iterator<String> i = attrNames.iterator();

		return new Enumeration<String>() {
			public boolean hasMoreElements() {
				return i.hasNext();
			}

			public String nextElement() {
				return i.next();
			}
		};
	}

	private Set<String> getAttributeNameSet() {
		SessionConfig sessionConfig = webContext.getSessionConfig();
		String[] storeNames = sessionConfig.getStores().getStoreNames();
		Set<String> attrNames = createLinkedHashSet();

		for (String storeName : storeNames) {
			SessionStore store = sessionConfig.getStores().getStore(storeName);

			for (String attrName : store.getAttributeNames(getId(),
					new StoreContextImpl(storeName))) {
				if (!isEquals(attrName, modelKey)) {
					attrNames.add(attrName);
				}
			}
		}

		for (SessionAttribute attr : attrs.values()) {
			if (attr.getValue() == null) {
				attrNames.remove(attr.getName());
			} else {
				attrNames.add(attr.getName());
			}
		}

		attrNames.remove(modelKey);

		return attrNames;
	}

	/**
	 * 设置指定名称的attribute值。
	 * 
	 * @param name
	 *            attribute名称
	 * @param value
	 *            attribute的值
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 * @throws IllegalArgumentException
	 *             如果指定的attribute名称不被支持
	 */
	public void setAttribute(String name, Object value) {
		assertValid("setAttribute");
		assertAttributeNameForModification("setAttribute", name);
		Object oldValue = getAttribute(name);
		sessionInternal.setAttribute(name, value);
		setAttributeListener(name, value, oldValue);
		valueBoundListener(name, value);
	}

	private void valueBoundListener(String name, Object value) {
		TinyListenerConfigManager configManager = TinyListenerConfigManagerHolder
				.getInstance();
		List<HttpSessionBindingListener> listeners = configManager
				.getSessionBindingListeners();
		HttpSessionBindingEvent event = new HttpSessionBindingEvent(this, name,
				value);
		if (value instanceof HttpSessionBindingListener) {
			for (HttpSessionBindingListener listener : listeners) {
				log.logMessage(LogLevel.DEBUG,
						"HttpSessionBindingListener:[{0}] will be valueBound",
						listener);
				listener.valueBound(event);
				log.logMessage(LogLevel.DEBUG,
						"HttpSessionBindingListener:[{0}] valueBound", listener);
			}
		}
	}

	private void valueUnBoundListener(String name) {
		TinyListenerConfigManager configManager = TinyListenerConfigManagerHolder
				.getInstance();
		List<HttpSessionBindingListener> listeners = configManager
				.getSessionBindingListeners();
		Object value = getAttribute(name);
		HttpSessionBindingEvent event = new HttpSessionBindingEvent(this, name);
		if (value instanceof HttpSessionBindingListener) {
			for (HttpSessionBindingListener listener : listeners) {
				log.logMessage(
						LogLevel.DEBUG,
						"HttpSessionBindingListener:[{0}] will be valueUnbound",
						listener);
				listener.valueUnbound(event);
				log.logMessage(LogLevel.DEBUG,
						"HttpSessionBindingListener:[{0}] valueUnbound",
						listener);
			}
		}
	}

	private void setAttributeListener(String name, Object value, Object oldValue) {
		TinyListenerConfigManager configManager = TinyListenerConfigManagerHolder
				.getInstance();
		List<HttpSessionAttributeListener> listeners = configManager
				.getSessionAttributeListeners();
		HttpSessionBindingEvent event = new HttpSessionBindingEvent(this, name,
				value);
		if (value != null) {
			if (oldValue == null) {
				for (HttpSessionAttributeListener listener : listeners) {
					log.logMessage(
							LogLevel.DEBUG,
							"HttpSessionAttributeListener:[{0}] will be attributeAdded",
							listener);
					listener.attributeAdded(event);
					log.logMessage(
							LogLevel.DEBUG,
							"HttpSessionActivationListener:[{0}] attributeAdded",
							listener);
				}
			} else {
				for (HttpSessionAttributeListener listener : listeners) {
					log.logMessage(
							LogLevel.DEBUG,
							"HttpSessionAttributeListener:[{0}] will be attributeReplaced,the oldValue:[{1}]",
							listener, oldValue);
					listener.attributeReplaced(event);
					log.logMessage(
							LogLevel.DEBUG,
							"HttpSessionAttributeListener:[{0}] attributeReplaced",
							listener);
				}
			}
		}
	}

	/**
	 * 删除一个attribute。
	 * 
	 * @param name
	 *            要删除的attribute名称
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 */
	public void removeAttribute(String name) {
		assertValid("removeAttribute");
		assertAttributeNameForModification("removeAttribute", name);
		setAttribute(name, null);
		removeAttributeListener(name);
		valueUnBoundListener(name);
	}

	private void removeAttributeListener(String name) {
		TinyListenerConfigManager configManager = TinyListenerConfigManagerHolder
				.getInstance();
		List<HttpSessionAttributeListener> listeners = configManager
				.getSessionAttributeListeners();
		HttpSessionBindingEvent event = new HttpSessionBindingEvent(this, name);
		for (HttpSessionAttributeListener listener : listeners) {
			log.logMessage(
					LogLevel.DEBUG,
					"HttpSessionAttributeListener:[{0}] will be attributeRemoved",
					listener);
			listener.attributeRemoved(event);
			log.logMessage(LogLevel.DEBUG,
					"HttpSessionAttributeListener:[{0}]  attributeRemoved",
					listener);
		}
	}

	/**
	 * 使一个session作废。
	 * 
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 */
	public void invalidate() {
		assertValid("invalidate");
		if(invalidated){
			return;
		}
		if(!StringUtil.isBlank(webContext.getSessionConfig().getSessionManagerBeanId())){
			SessionManager manager = SessionManagerFactory
					.getSessionManager(webContext.getSessionConfig()
							.getSessionManagerBeanId(), this.getClass()
							.getClassLoader());
			manager.expireSession(this);
		}
		fireEvent(EventType.INVALIDATED);
		Set<String> attrNames = getAttributeNameSet();
		for (String name : attrNames) {
			removeAttribute(name);
		}
		sessionInternal.invalidate();
		invalidated = true;
	}

	/**
	 * 清除一个session。
	 * 
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 */
	public void clear() {
		assertValid("clear");
		sessionInternal.invalidate();
	}

	/** 判断当前session是否非法。 */
	public boolean isInvalidated() {
		return invalidated;
	}

	/**
	 * 当前session是否为新的？
	 * 
	 * @return 如果是新的，则返回<code>true</code>
	 * @throws IllegalStateException
	 *             如果session已经invalidated
	 */
	public boolean isNew() {
		assertValid("isNew");
		return isNew;
	}

	/**
	 * 确保model已经被取得，即session已被初始化。
	 * 
	 * @param methodName
	 *            当前正要执行的方法
	 */
	protected void assertModel(String methodName) {
		if (model == null) {
			throw new IllegalStateException("Cannot call method " + methodName
					+ ": the session has not been initialized");
		}
	}

	/**
	 * 确保session处于valid状态。
	 * 
	 * @param methodName
	 *            当前正要执行的方法
	 */
	protected void assertValid(String methodName) {
		assertModel(methodName);

		if (invalidated) {
			throw new IllegalStateException("Cannot call method " + methodName
					+ ": the session has already invalidated");
		}
	}

	/** 检查将要更改的attr name是否合法。 */
	protected void assertAttributeNameForModification(String methodName,
			String attrName) {
		if (modelKey.equals(attrName)) {
			throw new IllegalArgumentException("Cannot call method "
					+ methodName + " with attribute " + attrName);
		}
	}

	/** 临时存储session store以及要提交的数据。 */
	private static class StoreData {
		private final String storeName;
		private final SessionStore store;
		private final Map<String, Object> attrs = createHashMap();

		private StoreData(String storeName, SessionStore store) {
			this.storeName = storeName;
			this.store = store;
		}
	}

	/** 提交session的内容，删除的、新增的、修改的内容被保存。 */
	public void commit(boolean commitHeaders) {
		String[] storeNames = webContext.getSessionConfig().getStores()
				.getStoreNames();
		Map<String, StoreData> mappings = createHashMap();
		Map<String, StringBuilder> mayNotBeCommitted = null;

		// 按store对attrs进行分堆。
		boolean modified = false;

		for (Map.Entry<String, SessionAttribute> entry : attrs.entrySet()) {
			String attrName = entry.getKey();
			SessionAttribute attr = entry.getValue();

			if (attr.isModified()) {
				String storeName = attr.getStoreName();
				SessionStore store = attr.getStore();

				if (isApplicableToCommit(store, commitHeaders)) {
					StoreData data = mappings.get(storeName);

					if (data == null) {
						data = new StoreData(storeName, store);
						mappings.put(storeName, data);
					}

					Map<String, Object> storeAttrs = data.attrs;
					Object attrValue = attr.getValue();

					// 特殊处理model，将其转换成store中的值。
					if (attrValue instanceof SessionModel) {
						attrValue = webContext.getSessionConfig()
								.getSessionModelEncoders()[0]
								.encode((SessionModel) attrValue);
					} else {
						// 只检查非session model对象的modified状态
						modified = true;
					}

					storeAttrs.put(attrName, attrValue);
					attr.setModified(false); // 恢复modified状态，以防多余的警告信息
				} else if (!commitHeaders) {
					if (mayNotBeCommitted == null) {
						mayNotBeCommitted = createLinkedHashMap();
					}

					StringBuilder buf = mayNotBeCommitted.get(storeName);

					if (buf == null) {
						buf = new StringBuilder();
						mayNotBeCommitted.put(storeName, buf);
					}

					if (buf.length() > 0) {
						buf.append(", ");
					}

					buf.append(attrName);
				}
			}
		}

		if (mayNotBeCommitted != null) {
			for (Map.Entry<String, StringBuilder> entry : mayNotBeCommitted
					.entrySet()) {
				String storeName = entry.getKey();
				SessionStore store = webContext.getSessionConfig().getStores()
						.getStore(storeName);
				String attrNames = entry.getValue().toString();

				log.logMessage(
						LogLevel.WARN,
						"The following attributes may not be saved in {}[id={}], because the response has already been committed: {}",
						store.getClass().getSimpleName(),
						storeName, attrNames);
			}
		}

		// 如果既没有参数改变（即没有调用setAttribute和removeAttribute），
		// 也没有被清除（即没有调用invalidate和clear），并且isKeepInTouch=false，
		// 则不提交了，直接退出。
		if (!modified && !cleared
				&& !webContext.getSessionConfig().isKeepInTouch()) {
			return;
		}

		// 对每一个store分别操作。
		for (StoreData data : mappings.values()) {
			data.store.commit(data.attrs, getId(), new StoreContextImpl(
					data.storeName));
			clearingStores.remove(data.storeName); // 如果先clear后又设值，则一会儿不需要再进行clear，故清除clearing标记
		}

		// 假如invalidate和clear被调用，则检查剩余的store，通知它们清除当前的数据。
		if (cleared) {
			for (Iterator<String> i = clearingStores.iterator(); i.hasNext();) {
				String storeName = i.next();
				SessionStore store = webContext.getSessionConfig().getStores()
						.getStore(storeName);

				if (isApplicableToCommit(store, commitHeaders)) {
					Map<String, Object> storeAttrs = emptyMap();
					store.commit(storeAttrs, sessionID, new StoreContextImpl(
							storeName));
					i.remove(); // 清除clearing标记，以防重复clear
				} else if (!commitHeaders) {
					log.logMessage(LogLevel.WARN,
							"Session was cleared, but the data in {}[id={}] may not be cleared, "
									+ "because the response has already been committed.",
							store.getClass().getSimpleName(), storeName);
				}
			}
		}
		sessionPassivated();
	}

	private void sessionPassivated() {
		TinyListenerConfigManager configManager = TinyListenerConfigManagerHolder
				.getInstance();
		List<HttpSessionActivationListener> listeners = configManager
				.getSessionActivationListeners();
		HttpSessionEvent event = new HttpSessionEvent(this);
		for (HttpSessionActivationListener listener : listeners) {
			log.logMessage(LogLevel.DEBUG,
					"HttpSessionActivationListener:[{0}] will be passivated",
					listener);
			listener.sessionWillPassivate(event);
			log.logMessage(LogLevel.DEBUG,
					"HttpSessionActivationListener:[{0}] passivated", listener);
		}

	}

	private boolean isApplicableToCommit(SessionStore store,
			boolean commitHeaders) {
		boolean isHttpHeaderStore = store instanceof HttpHeaderSessionStore;
		return commitHeaders == isHttpHeaderStore;
	}

	/** @deprecated no replacement */
	@Deprecated
	public javax.servlet.http.HttpSessionContext getSessionContext() {
		throw new UnsupportedOperationException(
				"No longer supported method: getSessionContext");
	}

	/** @deprecated use getAttribute instead */
	@Deprecated
	public Object getValue(String name) {
		return getAttribute(name);
	}

	/** @deprecated use getAttributeNames instead */
	@Deprecated
	public String[] getValueNames() {
		assertValid("getValueNames");

		Set<String> names = getAttributeNameSet();

		return names.toArray(new String[names.size()]);
	}

	/** @deprecated use setAttribute instead */
	@Deprecated
	public void putValue(String name, Object value) {
		setAttribute(name, value);
	}

	/** @deprecated use removeAttribute instead */
	@Deprecated
	public void removeValue(String name) {
		removeAttribute(name);
	}

	public String toString() {
		MapBuilder mb = new MapBuilder();
		MapBuilder attrsBuilder = new MapBuilder().setPrintCount(true)
				.setSortKeys(true);

		mb.append("sessionID", sessionID);
		mb.append("model", model);
		mb.append("isNew", isNew);
		mb.append("invalidated", invalidated);

		attrsBuilder.appendAll(attrs);
		attrsBuilder.remove(modelKey);

		mb.append("attrs", attrsBuilder);

		return new ToStringBuilder().append("HttpSession").append(mb)
				.toString();
	}

	private void fireEvent(EventType event) {
		fireSessionIntercept(event);
		fireSessionListener(event);
	}

	private void fireSessionIntercept(EventType event) {
		for (SessionInterceptor l : getSessionWebContext().getSessionConfig()
				.getSessionInterceptors()) {
			if (l instanceof SessionLifecycleListener) {
				SessionLifecycleListener listener = (SessionLifecycleListener) l;

				try {
					switch (event) {
					case RECREATED:
						listener.sessionInvalidated(this);
						break;
					case CREATED:
						listener.sessionCreated(this);
						break;
					case VISITED:
						listener.sessionVisited(this);
						break;

					case INVALIDATED:
						listener.sessionInvalidated(this);
						break;

					default:
						unreachableCode();
					}
				} catch (Exception e) {
					// 避免因listener出现异常导致应用的退出。
					log.errorMessage(
							"Listener \"" + listener.getClass().getSimpleName()
									+ "\" failed", e);
				}
			}
		}
	}

	private void fireSessionListener(EventType event) {
		TinyListenerConfigManager configManager = TinyListenerConfigManagerHolder
				.getInstance();
		List<HttpSessionListener> listeners = configManager
				.getSessionListeners();
		HttpSessionEvent sessionEvent = new HttpSessionEvent(this);
		for (HttpSessionListener listener : listeners) {

			if (event == EventType.CREATED || event == EventType.RECREATED) {
				log.logMessage(LogLevel.DEBUG,
						"HttpSessionListener:[{0}] will be sessionCreated",
						listener);
				listener.sessionCreated(sessionEvent);
				log.logMessage(LogLevel.DEBUG,
						"HttpSessionListener:[{0}] sessionCreated", listener);
			} else if (event == EventType.INVALIDATED) {
				log.logMessage(LogLevel.DEBUG,
						"HttpSessionListener:[{0}] will be sessionDestroyed",
						listener);
				listener.sessionDestroyed(sessionEvent);
				log.logMessage(LogLevel.DEBUG,
						"HttpSessionListener:[{0}] sessionDestroyed", listener);
			}

		}
	}

	private void sessionActivated() {
		// 从store中加载session内容，触发HttpSessionActivationListener监听
		TinyListenerConfigManager configManager = TinyListenerConfigManagerHolder
				.getInstance();
		List<HttpSessionActivationListener> listeners = configManager
				.getSessionActivationListeners();
		HttpSessionEvent event = new HttpSessionEvent(this);
		for (HttpSessionActivationListener listener : listeners) {
			log.logMessage(LogLevel.DEBUG,
					"HttpSessionActivationListener:[{0}] will be activated",
					listener);
			listener.sessionDidActivate(event);
			log.logMessage(LogLevel.DEBUG,
					"HttpSessionActivationListener:[{0}] activated", listener);
		}
	}

	/** Session事件的类型。 */
	private enum EventType {
		CREATED, RECREATED, // 先invalidate然后再create
		INVALIDATED, VISITED
	}

	/** 存放session store的状态。 */
	private class StoreContextImpl implements StoreContext {
		private String storeName;

		public StoreContextImpl(String storeName) {
			this.storeName = storeName;
		}

		public Object getState() {
			return storeStates.get(storeName);
		}

		public void setState(Object stateObject) {
			if (stateObject == null) {
				storeStates.remove(storeName);
			} else {
				storeStates.put(storeName, stateObject);
			}
		}

		public StoreContext getStoreContext(String storeName) {
			return new StoreContextImpl(storeName);
		}

		public SessionWebContext getSessionWebContext() {
			return SessionImpl.this.getSessionWebContext();
		}

		public HttpSession getHttpSession() {
			return sessionInternal;
		}
	}

	/** 内部使用的session对象，不会抛出<code>IllegalStateException</code>异常。 */
	private class HttpSessionInternal implements HttpSession {
		public String getId() {
			return SessionImpl.this.getId();
		}

		public long getCreationTime() {
			return model == null ? 0 : model.getCreationTime();
		}

		public long getLastAccessedTime() {
			return SessionImpl.this.getLastAccessedTime();
		}

		public int getMaxInactiveInterval() {
			return SessionImpl.this.getMaxInactiveInterval();
		}

		public void setMaxInactiveInterval(int maxInactiveInterval) {
			SessionImpl.this.setMaxInactiveInterval(maxInactiveInterval);
		}

		public ServletContext getServletContext() {
			return SessionImpl.this.getServletContext();
		}

		public Object getAttribute(String name) {
			SessionAttribute attr = attrs.get(name);
			SessionConfig sessionConfig = webContext.getSessionConfig();
			Object value;

			if (attr == null) {
				String storeName = sessionConfig.getStoreMappings()
						.getStoreNameForAttribute(name);

				if (storeName == null) {
					value = null;
				} else {
					attr = new SessionAttribute(name, SessionImpl.this,
							storeName, new StoreContextImpl(storeName));
					value = attr.getValue();

					// 对于session model，需要对其解码
					if (value != null && modelKey.equals(name)) {
						value = decodeSessionModel(value); // 如果解码失败，则返回null
						attr.updateValue(value);
					}

					// 只有当value非空（store中包含了此对象），才把它放到attrs表中，否则可能会产生很多垃圾attr对象
					if (value != null) {
						attrs.put(name, attr);
					}
				}
			} else {
				value = attr.getValue();
			}

			return interceptGet(name, value);
		}

		private Object interceptGet(String name, Object value) {
			for (SessionInterceptor l : getSessionWebContext()
					.getSessionConfig().getSessionInterceptors()) {
				if (l instanceof SessionAttributeInterceptor) {
					SessionAttributeInterceptor interceptor = (SessionAttributeInterceptor) l;
					value = interceptor.onRead(name, value);
				}
			}

			return value;
		}

		private Object decodeSessionModel(Object value) {
			SessionModel.Factory factory = new SessionModel.Factory() {
				public SessionModel newInstance(String sessionID,
						long creationTime, long lastAccessedTime,
						int maxInactiveInterval) {
					return new SessionModelImpl(sessionID, creationTime,
							lastAccessedTime, maxInactiveInterval);
				}
			};

			SessionModel model = null;
			SessionModelEncoder[] encoders = webContext.getSessionConfig()
					.getSessionModelEncoders();

			for (SessionModelEncoder encoder : encoders) {
				model = encoder.decode(value, factory);

				if (model != null) {
					break;
				}
			}

			if (model == null) {
				log.logMessage(LogLevel.WARN,
						"Could not decode session model {} by {} encoders",
						value, encoders.length);
			}

			return model;
		}

		public Enumeration<String> getAttributeNames() {
			return SessionImpl.this.getAttributeNames();
		}

		public void setAttribute(String name, Object value) {
			value = interceptSet(name, value);

			SessionAttribute attr = attrs.get(name);
			SessionConfig sessionConfig = webContext.getSessionConfig();

			if (attr == null) {
				String storeName = sessionConfig.getStoreMappings()
						.getStoreNameForAttribute(name);

				if (storeName == null) {
					throw new IllegalArgumentException(
							"No storage configured for session attribute: "
									+ name);
				} else {
					attr = new SessionAttribute(name, SessionImpl.this,
							storeName, new StoreContextImpl(storeName));
					attrs.put(name, attr);
				}
			}

			attr.setValue(value);
		}

		private Object interceptSet(String name, Object value) {
			for (SessionInterceptor l : getSessionWebContext()
					.getSessionConfig().getSessionInterceptors()) {
				if (l instanceof SessionAttributeInterceptor) {
					SessionAttributeInterceptor interceptor = (SessionAttributeInterceptor) l;
					value = interceptor.onWrite(name, value);
				}
			}
			return value;
		}

		public void removeAttribute(String name) {
			SessionImpl.this.removeAttribute(name);
		}

		public void invalidate() {
			SessionConfig sessionConfig = webContext.getSessionConfig();
			String[] storeNames = sessionConfig.getStores().getStoreNames();

			// 清除session数据
			attrs.clear();
			cleared = true;
			clearingStores.addAll(asList(storeNames));

			// 通知所有的store过期其数据
			for (String storeName : storeNames) {
				SessionStore store = sessionConfig.getStores().getStore(
						storeName);

				store.invaldiate(sessionID, new StoreContextImpl(storeName));
			}

			// 清除model
			if (model == null) {
				model = new SessionModelImpl(SessionImpl.this);
			} else {
				model.reset();
			}
		}

		public boolean isNew() {
			return SessionImpl.this.isNew();
		}

		/** @deprecated */
		@Deprecated
		public javax.servlet.http.HttpSessionContext getSessionContext() {
			return SessionImpl.this.getSessionContext();
		}

		/** @deprecated */
		@Deprecated
		public Object getValue(String name) {
			return SessionImpl.this.getValue(name);
		}

		/** @deprecated */
		@Deprecated
		public String[] getValueNames() {
			return SessionImpl.this.getValueNames();
		}

		/** @deprecated */
		@Deprecated
		public void putValue(String name, Object value) {
			SessionImpl.this.putValue(name, value);
		}

		/** @deprecated */
		@Deprecated
		public void removeValue(String name) {
			SessionImpl.this.removeValue(name);
		}
	}

}
