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
package org.tinygroup.weixin.common;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.util.List;

/**
 * 一组URL配置信息
 * Created by luoguo on 2015/5/25.
 */
@XStreamAlias("url-configs")
public class UrlConfigs {
    
	@XStreamImplicit
    List<UrlConfig> urlConfigs;

    public List<UrlConfig> getUrlConfigs() {
        return urlConfigs;
    }

    public void setUrlConfigs(List<UrlConfig> urlConfigs) {
        this.urlConfigs = urlConfigs;
    }
}
