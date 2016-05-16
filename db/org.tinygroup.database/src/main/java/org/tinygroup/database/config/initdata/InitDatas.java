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
package org.tinygroup.database.config.initdata;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.tinygroup.database.config.UsePackage;

import java.util.ArrayList;
import java.util.List;

@XStreamAlias("initDatas")
public class InitDatas {
	@XStreamAlias("use-packages")
	List<UsePackage> usePackages;
	@XStreamAlias("initData-list")
	List<InitData> initDataList;

	public List<UsePackage> getUsePackages() {
		if (usePackages == null) {
			usePackages = new ArrayList<UsePackage>();
		}
		return usePackages;
	}

	public void setUsePackages(List<UsePackage> usePackages) {
		this.usePackages = usePackages;
	}

	public List<InitData> getInitDataList() {
		if (initDataList == null) {
			initDataList = new ArrayList<InitData>();
		}
		return initDataList;
	}

	public void setInitDataList(List<InitData> initDataList) {
		this.initDataList = initDataList;
	}

}
