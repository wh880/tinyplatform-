package org.tinygroup.lucene472;

import java.util.List;

import org.tinygroup.fulltext.FullTextConfigManager;
import org.tinygroup.lucene472.config.LuceneConfig;
import org.tinygroup.lucene472.config.LuceneConfigs;
import org.tinygroup.templateindex.config.BaseIndexConfig;

/**
 * Lucene配置管理器
 * @author yancheng11334
 *
 */
public interface LuceneConfigManager extends FullTextConfigManager<LuceneConfig>{

	public static final String DEFAULT_BEAN_NAME = "luceneConfigManager";
	
	/**
	 * 添加配置组
	 * @param configs
	 */
	public void addLuceneConfigs(LuceneConfigs configs);
	
	/**
	 * 删除配置组
	 * @param configs
	 */
	public void removeLuceneConfigs(LuceneConfigs configs);
    
	/**
	 * 获得索引配置项列表
	 * @return
	 */
	public List<BaseIndexConfig> getIndexConfigList();
}
