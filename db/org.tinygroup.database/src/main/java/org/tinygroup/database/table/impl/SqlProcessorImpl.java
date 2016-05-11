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
package org.tinygroup.database.table.impl;

import org.tinygroup.commons.tools.CollectionUtil;
import org.tinygroup.commons.tools.StringUtil;
import org.tinygroup.database.config.table.*;
import org.tinygroup.database.table.TableProcessor;
import org.tinygroup.database.table.TableSqlProcessor;
import org.tinygroup.database.util.DataBaseNameUtil;
import org.tinygroup.database.util.DataBaseUtil;
import org.tinygroup.metadata.config.stdfield.StandardField;
import org.tinygroup.metadata.util.MetadataUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SqlProcessorImpl implements TableSqlProcessor {

	private TableProcessor tableProcessor;

	public TableProcessor getTableProcessor() {
		return tableProcessor;
	}

	public void setTableProcessor(TableProcessor tableProcessor) {
		this.tableProcessor = tableProcessor;
	}

	protected abstract String getDatabaseType();

	public List<String> getCreateSql(Table table, String packageName) {
		List<String> list = new ArrayList<String>();
		// 生成表格创建语句
		list.addAll(getTableCreateSql(table, packageName));
		// 增加外键语句
		list.addAll(getForeignKeySqls(table, packageName));
		// 生成index
		list.addAll(getIndexCreateSql(table, packageName));
		return list;
	}

	public List<String> getForeignKeySqls(Table table, String packageName) {
		List<ForeignReference> foreigns = table.getForeignReferences();
		List<String> foreignSqls = new ArrayList<String>();
		if (!CollectionUtil.isEmpty(foreigns)) {
			for (ForeignReference foreignReference : foreigns) {
				Table foreignTable = tableProcessor
						.getTableById(foreignReference.getMainTable());
				String sql = String
						.format("ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s(%s);",
								table.getName(),
								foreignReference.getName(),
								getFieldStdFieldName(
										foreignReference.getForeignField(),
										table),
								foreignTable.getName(),
								getFieldStdFieldName(
										foreignReference.getReferenceField(),
										foreignTable));
				foreignSqls.add(sql);
			}
		}
		return foreignSqls;
	}

	public List<String> getTableCreateSql(Table table, String packageName) {
		StringBuffer ddlBuffer = new StringBuffer();
		List<String> list = new ArrayList<String>();
		// 生成表格主体
		appendHeader(ddlBuffer, table,list);
		appendBody(ddlBuffer, table,list);
		appendFooter(ddlBuffer, table,list);
		list.add(0, ddlBuffer.toString());
		return list;
	}

	public List<String> getIndexCreateSql(Table table, String packageName) {
		return appendIndexes(table);
	}

	public List<String> getUpdateSql(Table table, String packageName,
			Connection connection) throws SQLException {
		List<String> list = new ArrayList<String>();
		DatabaseMetaData metadata = connection.getMetaData();
		String catalog = connection.getCatalog();
		getTableColumnUpdate(table, packageName, metadata, catalog, list);
		getOtherUpdate(table, packageName, connection, list);
		return list;
	}

	protected void getOtherUpdate(Table table, String packageName,
			Connection connection, List<String> list) throws SQLException {
		getForeignUpdate(table, packageName, connection, list);
	}

	private void getForeignUpdate(Table table, String packageName,
			Connection connection, List<String> list) throws SQLException {

		String schema = DataBaseUtil.getSchema(table, connection.getMetaData());

		String sql = getQueryForeignSql(table, schema);
		if (sql != null) {
			List<ForeignReference> foreigns = table.getForeignReferences();
			List<ForeignReference> newForeigns = cloneReferences(foreigns);
			Statement statement = null;
			ResultSet rs = null;
			try {
				statement = connection.createStatement();
				rs = statement.executeQuery(sql);
				List<String> dropConstraints = new ArrayList<String>();
				while (rs.next()) {
					String constraName = rs.getString("CONSTRAINT_NAME");
					String columnName = rs.getString("COLUMN_NAME");
					String referenceTableName = rs
							.getString("REFERENCED_TABLE_NAME");
					String referenceColumnName = rs
							.getString("REFERENCED_COLUMN_NAME");
					ForeignReference foreignReference = new ForeignReference(
							constraName, referenceTableName,
							referenceColumnName, columnName);
					if (newForeigns.contains(foreignReference)) {
						newForeigns.remove(foreignReference);
					} else {
						dropConstraints.add(constraName);
					}
				}
				for (String dropConstraint : dropConstraints) {
					list.add(String.format(
							"ALTER TABLE %s DROP FOREIGN KEY %s",
							table.getName(), dropConstraint));
				}
				list.addAll(getForeignKeySqls(table, packageName));
			} finally {
				if (statement != null) {
					statement.close();
				}
				if (rs != null) {
					rs.close();
				}
			}
		}
	}

	protected String getQueryForeignSql(Table table, String schema) {
		return null;
	}

	private List<ForeignReference> cloneReferences(
			List<ForeignReference> foreigns) {
		List<ForeignReference> cloneForeigns = new ArrayList<ForeignReference>();
		for (ForeignReference foreignReference : foreigns) {
			cloneForeigns.add(foreignReference);
		}
		return cloneForeigns;
	}

	protected void getTableColumnUpdate(Table table, String packageName,
			DatabaseMetaData metadata, String catalog, List<String> list)
			throws SQLException {

		Map<String, Map<String, String>> dbColumns = getColumns(metadata,
				catalog, table);
		// 存放table中有但数据库表格中不存在的字段， 初始化时存放所有的字段信息
		Map<String, TableField> tableFieldDbNames = getFiledDbNames(table
				.getFieldList());
		// 存放table中有且数据库表格中存在的字段
		Map<String, TableField> existInTable = new HashMap<String, TableField>();
		// 存放table中无，但数据库中有的字段
		List<String> dropFields = checkTableColumn(dbColumns,
				tableFieldDbNames, existInTable);
		// 处理完所有表格列，若filedDbNames中依然有数据，则表格该数据是新增字段
		for (TableField field : tableFieldDbNames.values()) {
			// 如果新增的字段包含表格的主键,则直接drop整张表格，重新创建表格
			if (field.getPrimary()) {
				list.add(getDropSql(table, packageName));
				list.addAll(getCreateSql(table, packageName));
				return;
			}
			// StringBuffer ddlBuffer = new StringBuffer();
			// ddlBuffer.append(String.format("ALTER TABLE %s ADD ",
			// table.getName()));
			// appendField(ddlBuffer, packageName, field);
			// addlist.add(ddlBuffer.toString());
		}
		// 对于table\数据库中均有的字段的处理，这里是对比是否允许为空而进行相关修改
		List<String> existUpdateList = dealExistFields(existInTable, dbColumns,
				table);
		// 生成drop字段的sql
		List<String> droplist = dealDropFields(dropFields, table);
		// 生成add字段的sql
		List<String> addlist = dealAddFields(tableFieldDbNames, packageName,
				table);
		list.addAll(existUpdateList);
		list.addAll(droplist);
		list.addAll(addlist);
	}

	protected List<String> checkTableColumn(
			Map<String, Map<String, String>> columns,
			Map<String, TableField> fieldDbNames,
			Map<String, TableField> existInTable) {
		List<String> dropFields = new ArrayList<String>();
		for (String colum : columns.keySet()) {
			// 遍历当前表格所有列
			// 若存在于map，则不处理，切从map中删除该key
			// 若不存在于map，则从表格中删除该列
			String temp = colum.toUpperCase();
			if (fieldDbNames.containsKey(temp)) {
				existInTable.put(temp, fieldDbNames.get(temp));
				fieldDbNames.remove(temp);
				continue;
			}
			dropFields.add(colum);
		}
		return dropFields;
	}

	protected List<String> dealDropFields(List<String> dropFields, Table table) {
		List<String> droplist = new ArrayList<String>();
		for (String column : dropFields) {
			StringBuffer ddlBuffer = new StringBuffer();
			ddlBuffer.append(String.format("ALTER TABLE %s DROP COLUMN %s",
					table.getName(), column));
			droplist.add(ddlBuffer.toString());

		}
		return droplist;
	}

	protected List<String> dealAddFields(Map<String, TableField> fieldDbNames,
			String packageName, Table table) {
		List<String> addList = new ArrayList<String>();
		for (TableField field : fieldDbNames.values()) {
			StringBuffer ddlBuffer = new StringBuffer();
			ddlBuffer.append(String.format("ALTER TABLE %s ADD ",
					table.getName()));
			appendField(ddlBuffer, field,addList);
			addList.add(ddlBuffer.toString());
		}
		return addList;
	}

	protected List<String> dealExistFields(
			Map<String, TableField> existInTable,
			Map<String, Map<String, String>> dbColumns, Table table) {
		List<String> existUpdateList = new ArrayList<String>();
		for (String fieldName : existInTable.keySet()) {
			TableField field = existInTable.get(fieldName);
			if (field.getPrimary()) {
				continue;
			}
			StandardField standardField = MetadataUtil.getStandardField(field
					.getStandardFieldId(), this.getClass().getClassLoader());
			Map<String, String> attribute = dbColumns.get(fieldName);
			String tableDataType = MetadataUtil.getStandardFieldType(
					standardField.getId(), getDatabaseType(), this.getClass()
							.getClassLoader());
			String dbColumnType = getDbColumnType(attribute)
					.replaceAll(" ", "").toLowerCase();
			String standardFieldName = standardField.getName();
			if (dbColumnType.indexOf(tableDataType.replaceAll(" ", "")
					.toLowerCase()) == -1) {
				StringBuffer alterTypeBuffer = new StringBuffer();
				// 如果数据库中字段允许为空，但table中不允许为空
				if (field.getNotNull()
						&& Integer.parseInt(attribute.get(NULLABLE)) == DatabaseMetaData.columnNullable) {
					alterTypeBuffer.append(createNotNullSql(table.getName(),
							standardFieldName, tableDataType));
				} else if (!field.getNotNull()
						&& Integer.parseInt(attribute.get(NULLABLE)) == DatabaseMetaData.columnNoNulls) {
					alterTypeBuffer.append(createNullSql(table.getName(), standardFieldName,
							tableDataType));
				}else{
					alterTypeBuffer.append(createAlterTypeSql(table.getName(),
							standardFieldName, tableDataType));
				}

				// 非自增的字段设置字段默认值
				if(!field.isAutoIncrease()){
					String fieldDefaultValue = getDefaultValue(field,standardField);
					appendDefaultValue(fieldDefaultValue, alterTypeBuffer);
				}
				dealComment(alterTypeBuffer,standardField,existUpdateList);
				existUpdateList.add(alterTypeBuffer.toString());
			}
		}
		return existUpdateList;

	}

	protected abstract String createNotNullSql(String tableName,
			String fieldName, String tableDataType);

	protected abstract String createNullSql(String tableName, String fieldName,
			String tableDataType);

	protected abstract String createAlterTypeSql(String tableName,
			String fieldName, String tableDataType);

	protected void appendBody(StringBuffer ddlBuffer, Table table,List<String> list) {
		boolean isFirst = true;
		for (TableField field : table.getFieldList()) {
			if (!isFirst) {
				ddlBuffer.append(",");
			} else {
				isFirst = false;
			}
//			if(StringUtil.isBlank(field.getStandardFieldId()))	throw new RuntimeException(String.format("表[%s]中字段[%s]中的标准字段为空",table.getId(), field.getId()));
			appendField(ddlBuffer, field,list);
		}
	}

	protected void appendField(StringBuffer ddlBuffer, TableField field,List<String> list) {
		StandardField standardField = MetadataUtil.getStandardField(
				field.getStandardFieldId(), this.getClass().getClassLoader());
		ddlBuffer.append(String.format(" %s ",
				DataBaseUtil.getDataBaseName(standardField.getName())));
		ddlBuffer.append(" ");
		ddlBuffer.append(MetadataUtil.getStandardFieldType(standardField
				.getId(), getDatabaseType(), this.getClass().getClassLoader()));
		
		String fieldDefaultValue = getDefaultValue(field,standardField);
		
		// 非自增的字段设置字段默认值
		if(!field.isAutoIncrease()){	appendDefaultValue(fieldDefaultValue, ddlBuffer);}
		Boolean notNull = field.getNotNull();
		if (notNull != null && notNull.booleanValue()) {
			ddlBuffer.append(" NOT NULL");
		}

		Boolean primary = field.getPrimary();
		if (primary != null && primary.booleanValue()) {
			ddlBuffer.append(" PRIMARY KEY");
		} else {
			Boolean unique = field.getUnique();
			if (unique != null && unique.booleanValue()) {
				ddlBuffer.append(" UNIQUE");
			}
		}
		// 处理自增
		if (field.isAutoIncrease() && field.getPrimary()) {// 如果是自增而且是主键
			ddlBuffer.append(appendIncrease());
		}

		dealComment(ddlBuffer,standardField,list);
	}

	/**
	 * 处理comment
	 * @param ddlBuffer
	 * @param standardField
	 * @param list
     */
	private void dealComment(StringBuffer ddlBuffer, StandardField standardField,List<String> list){
		// 设置字段备注信息
		String title = standardField.getTitle();
		String description = standardField.getDescription();
		String comment = StringUtil.isBlank(title)?description:(StringUtil.isBlank(description)?title:(title+"\n"+description));
		appendComment(comment, ddlBuffer,list);
	}

	protected void appendComment(String comment, StringBuffer ddlBuffer,List<String> list) {
		if (!StringUtil.isBlank(comment)) {
			ddlBuffer.append(" COMMENT ").append("'").append(comment.replaceAll("'", "\\\\'").replaceAll("\"", "\\\\\""))
					.append("'");
		}
	}

	/**
	 * 在footer增加comment
	 * @param ddlBuffer
	 * @param table
	 * @param list
     */
	protected void appendFooterComment(StringBuffer ddlBuffer, Table table, List<String> list) {
		for (TableField field : table.getFieldList()) {

			StandardField standardField = MetadataUtil.getStandardField(field
					.getStandardFieldId(), this.getClass().getClassLoader());
			if (standardField.getDescription() == null){	continue;}
			String columnName = null;
			if (StringUtil.isBlank(table.getSchema())) {
				columnName = String.format("%s.%s", table.getName(),
						standardField.getName());
			} else {
				columnName = String.format("%s.%s.%s", table.getSchema(),table.getName(),
						standardField.getName());
			}
			StringBuffer commentBuffer = new StringBuffer();
			commentBuffer.append("COMMENT ON COLUMN ").append(columnName)
					.append(" IS ").append("'").append(standardField.getDescription()).append("'");
			list.add(commentBuffer.toString());
		}
	}

	// 如果是字符串类型，defaultValue必须是'XXX'格式
	protected void appendDefaultValue(String defaultValue,
			StringBuffer ddlBuffer) {
		if (!StringUtil.isBlank(defaultValue)) {
			ddlBuffer.append(" DEFAULT ").append(defaultValue);
		}
	}

	protected void appendDefaultValue(String defaultValue,
									  String ddlStr) {
		StringBuffer ddlBuffer = new StringBuffer(ddlStr);
		appendDefaultValue(defaultValue, ddlBuffer);
		ddlStr = ddlBuffer.toString();
	}

	protected String appendIncrease() {
		return "";
	}

	private List<String> appendIndexes(Table table) {
		List<String> indexSqlList = new ArrayList<String>();
		List<Index> list = table.getIndexList();
		if (list != null) {
			for (Index index : list) {
				indexSqlList.add(appendIndex(index, table,list));
			}
		}
		return indexSqlList;
	}

	private String appendIndex(Index index, Table table,List<Index> list) {
		// DROP INDEX index_name ON table_name
		// DROP INDEX table_name.index_name
		// DROP INDEX index_name
		StringBuffer ddlBuffer = new StringBuffer();
		Boolean unique = index.getUnique();
		if (unique != null && unique.booleanValue()) {
			ddlBuffer.append("CREATE UNIQUE INDEX ");
		} else {
			ddlBuffer.append("CREATE INDEX ");
		}

		ddlBuffer.append(getIndexName(index, table));

		ddlBuffer.append(" ON ");
		ddlBuffer.append(table.getName());
		ddlBuffer.append(" ( ");
		List<IndexField> fields = index.getFields();
		String fieldsStr = "";
		if (fields != null) {
			for (IndexField field : fields) {
				fieldsStr = fieldsStr + ","
						+ getFieldStdFieldName(field.getField(), table);

				if (!StringUtil.isEmpty(field.getDirection())) {
					fieldsStr = fieldsStr + " " + field.getDirection();
				}
			}
			if (fieldsStr.startsWith(",")) {
				fieldsStr = fieldsStr.substring(1);
			}
		}
		ddlBuffer.append(fieldsStr);
		ddlBuffer.append(" ); ");
		return ddlBuffer.toString();
	}

	protected String getIndexName(Index index, Table table) {
		String indexName = null;

		if (table.getSchema() == null || "".equals(table.getSchema())) {
			indexName = index.getName();
		} else {
			indexName = String.format("%s.%s", table.getSchema(),
					index.getName());
		}

		return indexName;
	}

	private String getFieldStdFieldName(String fieldId, Table table) {
		for (TableField field : table.getFieldList()) {
			if (field.getId().equals(fieldId)) {
				StandardField standardField = MetadataUtil.getStandardField(
						field.getStandardFieldId(), this.getClass()
								.getClassLoader());
				return DataBaseUtil.getDataBaseName(standardField.getName());
			}
		}
		throw new RuntimeException(String.format(
				"未找到ID：%s的表格字段(或该表格字段对应的标准字段)", fieldId));
	}

	protected void appendFooter(StringBuffer ddlBuffer, Table table,List<String> list) {
		ddlBuffer.append(")");
	}

	private void appendHeader(StringBuffer ddlBuffer, Table table,List<String> list) {
		ddlBuffer.append(String.format("CREATE TABLE %s (", table.getName()));
	}

	private Map<String, TableField> getFiledDbNames(List<TableField> fields) {
		Map<String, TableField> filedDbNames = new HashMap<String, TableField>();
		for (TableField field : fields) {
			StandardField standardField = MetadataUtil.getStandardField(field
					.getStandardFieldId(), this.getClass().getClassLoader());
			String filedDbName = DataBaseUtil.getDataBaseName(standardField
					.getName());
			filedDbNames.put(DataBaseNameUtil.getColumnNameFormat(filedDbName),
					field);
		}
		return filedDbNames;
	}

	public String getDropSql(Table table, String packageName) {
		return String.format("DROP TABLE %s", table.getName());
	}

	protected Map<String, Map<String, String>> getColumns(
			DatabaseMetaData metadata, String catalog, Table table)
			throws SQLException {
		String schema = DataBaseUtil.getSchema(table, metadata);
		String tableName = table.getNameWithOutSchema();
		return getColumns(metadata, catalog, schema, tableName);
	}

	private Map<String, Map<String, String>> getColumns(
			DatabaseMetaData metadata, String catalog, String schema,
			String tableName) throws SQLException {
		ResultSet colRet = null;
		Map<String, Map<String, String>> map = new HashMap<String, Map<String, String>>();
		try {
			colRet = metadata.getColumns(catalog, schema, tableName, "%");
			boolean exist = false;
			while (colRet.next()) {
				getMapByResultSet(colRet, map);
				exist = true;
			}
			if (!exist) {
				colRet.close();
				colRet = metadata.getColumns(catalog, schema,
						tableName.toUpperCase(), "%");
				while (colRet.next()) {
					getMapByResultSet(colRet, map);
					exist = true;
				}
				if (!exist) {
					colRet.close();
					colRet = metadata.getColumns(catalog, schema.toUpperCase(),
							tableName.toUpperCase(), "%");
					while (colRet.next()) {
						getMapByResultSet(colRet, map);
						exist = true;
					}
				}
			}
		} finally {
			if (colRet != null) {
				colRet.close();
			}
		}
		return map;
	}

	private void getMapByResultSet(ResultSet colRet,
			Map<String, Map<String, String>> map) throws SQLException {
		Map<String, String> attributes = new HashMap<String, String>();
		String columnName = colRet.getString(COLUMN_NAME);
		attributes.put(NULLABLE, colRet.getString(NULLABLE));
		attributes.put(TYPE_NAME, colRet.getString(TYPE_NAME));
		attributes.put(COLUMN_SIZE, colRet.getString(COLUMN_SIZE));
		attributes.put(DECIMAL_DIGITS, colRet.getString(DECIMAL_DIGITS));
		map.put(columnName.toUpperCase(), attributes);
	}

	public boolean checkTableExist(Table table, Connection connection)
			throws SQLException {
		ResultSet resultset = null;
		DatabaseMetaData metadata = connection.getMetaData();
		try {
			String schema = DataBaseUtil.getSchema(table, metadata);
			resultset = metadata.getTables(connection.getCatalog(), schema,
					table.getNameWithOutSchema(), new String[] { "TABLE" });
			if (resultset.next()) {
				return true;
			} else {
				resultset.close();// 关闭上次打开的
				resultset = metadata.getTables(connection.getCatalog(), schema,
						table.getNameWithOutSchema().toUpperCase(),
						new String[] { "TABLE" });
				if (resultset.next()) {
					return true;
				} else {
					resultset.close();
					resultset = metadata.getTables(connection.getCatalog(),
							schema.toUpperCase(), table.getNameWithOutSchema()
									.toUpperCase(), new String[] { "TABLE" });
					if (resultset.next()) {
						return true;
					}
				}
			}
		} finally {
			if (resultset != null) {
				resultset.close();
			}
		}
		return false;
	}

	protected String getDbColumnType(Map<String, String> attributes) {
		String lengthInfo = attributes.get(COLUMN_SIZE);
		if (attributes.get(DECIMAL_DIGITS) != null) {
			lengthInfo = lengthInfo + "," + attributes.get(DECIMAL_DIGITS);
		}
		return String.format("%s(%s)", attributes.get(TYPE_NAME), lengthInfo);
	}
	
	private String getDefaultValue(TableField field,StandardField standardField){
		String fieldDefaultValue = field.getDefaultValue();
		if(StringUtil.isBlank(fieldDefaultValue)){
			fieldDefaultValue = standardField.getDefaultValue();
		}
		return fieldDefaultValue;
	}

}
