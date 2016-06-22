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
package org.tinygroup.uiengine.fileresolver;

import com.thoughtworks.xstream.XStream;
import org.tinygroup.fileresolver.impl.AbstractFileProcessor;
import org.tinygroup.logger.LogLevel;
import org.tinygroup.uiengine.config.UIComponents;
import org.tinygroup.uiengine.manager.UIComponentManager;
import org.tinygroup.vfs.FileObject;
import org.tinygroup.xstream.XStreamFactory;

/**
 * UI组件定义文件
 * 
 * @author luoguo
 * 
 */
public class UIComponentFileProcessor extends AbstractFileProcessor {
	UIComponentManager manager ;
	
	
	public UIComponentManager getManager() {
		return manager;
	}


	public void setManager(UIComponentManager manager) {
		this.manager = manager;
	}


	public void process() {
		XStream stream = XStreamFactory
				.getXStream(UIComponentManager.UIComponentManager_XSTREAM);
		for (FileObject fileObject : deleteList) {
			LOGGER.logMessage(LogLevel.INFO, "正在移除uicomponent文件[{0}]",
					fileObject.getAbsolutePath());
			UIComponents uiComponents = (UIComponents) caches.get(fileObject.getAbsolutePath());
			if(uiComponents!=null){
				manager.removeUIComponents(uiComponents);
				caches.remove(fileObject.getAbsolutePath());
			}
			LOGGER.logMessage(LogLevel.INFO, "移除uicomponent文件[{0}]结束",
					fileObject.getAbsolutePath());
		}
		for (FileObject fileObject : changeList) {
			LOGGER.logMessage(LogLevel.INFO, "正在加载uicomponent文件[{0}]",
					fileObject.getAbsolutePath());
			UIComponents oldUiComponents = (UIComponents) caches.get(fileObject.getAbsolutePath());
			if(oldUiComponents!=null){
				manager.removeUIComponents(oldUiComponents);
			}	
			UIComponents uiComponents = convertFromXml(stream,fileObject);
			uiComponents.setResourcePath(fileObject.getAbsolutePath());
			manager.addUIComponents(uiComponents);
			caches.put(fileObject.getAbsolutePath(), uiComponents);
			LOGGER.logMessage(LogLevel.INFO, "加载uicomponent文件[{0}]结束",
					fileObject.getAbsolutePath());
		}
		manager.compute();
	}


	@Override
	protected boolean checkMatch(FileObject fileObject) {
		return fileObject.getFileName().endsWith(".ui.xml");
	}

}
