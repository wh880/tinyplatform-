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
package org.tinygroup.cepcoreimpl.test;

import java.util.ArrayList;
import java.util.List;

import org.tinygroup.cepcore.CEPCore;
import org.tinygroup.cepcore.EventProcessor;
import org.tinygroup.event.Event;
import org.tinygroup.event.ServiceInfo;
import org.tinygroup.logger.LogLevel;
import org.tinygroup.logger.Logger;
import org.tinygroup.logger.LoggerFactory;

public class EventProcessorForTestSameLocalService implements EventProcessor {
	List<ServiceInfo> list = new ArrayList<ServiceInfo>();
	private static Logger LOGGER = LoggerFactory
			.getLogger(EventProcessorForTestSameLocalService.class);
	private boolean enable = true;
	private String id;
	public void process(Event event) {
		String serviceId = event.getServiceRequest().getServiceId();
		LOGGER.logMessage(LogLevel.INFO, "执行服务:{}", serviceId);
	}

	public void setCepCore(CEPCore cepCore) {

	}

	public List<ServiceInfo> getServiceInfos() {
		return list;
	}

	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}

	public int getType() {
		return EventProcessor.TYPE_LOCAL;
	}

	public int getWeight() {
		return 0;
	}

	public List<String> getRegex() {
		return null;
	}

	public boolean isRead() {
		return false;
	}

	public void setRead(boolean read) {

	}

	public boolean isEnable() {
		return enable;
	}

	public void setEnable(boolean enable) {
		this.enable = enable;
	}

}
