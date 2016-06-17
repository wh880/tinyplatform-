package org.tinygroup.sequence.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.tinygroup.logger.LogLevel;
import org.tinygroup.logger.Logger;
import org.tinygroup.logger.LoggerFactory;
import org.tinygroup.sequence.SequenceConstants;
import org.tinygroup.sequence.SequenceRange;
import org.tinygroup.sequence.exception.SequenceException;

/**
 * 高可用sequence 所使用的数据源的包装类, 可是实现对sequence故障数据源的踢出和恢复
 * 
 * @author zhaofeng.wang
 * @version $Id: SequenceDataSource.java, v 0.1 2011-12-22 下午04:46:44
 *          zhaofeng.wang Exp $
 */
public class SequenceDataSourceHolder {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(SequenceConstants.TINY_SEQUENCE_LOG_NAME);
	/**
	 * 数据源是否可用，默认可用
	 */
	private volatile boolean isAvailable = true;

	/**
	 * 真正的数据源
	 */
	private final DataSource ds;

	/**
	 * 非阻塞锁，用于控制只允许一个业务线程去重试；
	 */
	private final ReentrantLock lock = new ReentrantLock();
	/**
	 * 上次重试时间
	 */
	private volatile long lastRetryTime = 0;
	/**
	 * 异常次数
	 */
	private volatile int exceptionTimes = 0;
	/**
	 * 第一次捕获异常的时间，单位毫秒
	 */
	private volatile long firstExceptionTime = 0;
	/**
	 * 重试故障db的时间间隔，默认值设为30s,单位毫秒
	 */
	private final int retryBadDbInterval = 30000;

	/**
	 * 单位时间段，默认为5分钟，用于统计时间段内某个db抛异常的次数，单位毫秒
	 */
	private final int timeInterval = 300000;
	/**
	 * 单位时间内允许异常的次数，如果超过这个值便将数据源置为不可用
	 */
	private final int allowExceptionTimes = 20;

	/**
	 * sequence表名，默认为sequence
	 */
	private String tableName;

	/**
	 * 是否开启自动调整开关
	 */
	private boolean adjust;
	/**
	 * 格式：select value from sequence where name=?
	 */
	private String selectSql;
	/**
	 * 格式update table_name(default:sequence) set value=? ,gmt_modified=? where
	 * name=? and value=?
	 */
	private String updateSql;
	/**
	 * 格式: insert into
	 * table_name(default:sequence)(name,value,min_value,max_value
	 * ,step,gmt_create,gmt_modified) values(?,?,?,?,?,?,?)
	 */
	private String insertSql;

	/**
	 * 设置常用的参数
	 * 
	 * @param tableName
	 *            表名
	 * @param selectSql
	 *            select的sql语句
	 * @param updateSql
	 *            更新语句
	 * @param insertSql
	 *            插入语句
	 * @param adjust
	 *            是否开启自动调整开关
	 */
	public void setParameters(String tableName, String selectSql,
			String updateSql, String insertSql, boolean adjust) {
		this.tableName = tableName;
		this.selectSql = selectSql;
		this.updateSql = updateSql;
		this.insertSql = insertSql;
		this.adjust = adjust;
	}

	/**
	 * 构造函数
	 * 
	 * @param ds
	 */
	public SequenceDataSourceHolder(DataSource ds) {
		this.ds = ds;
	}

	public DataSource getDs() {
		return ds;
	}

	/**
	 * 在随机选择的数据源上获取sequence段
	 * 
	 * @param index
	 *            数据源序列号
	 * @param sequenceName
	 *            sequence名
	 * @param minValue
	 *            最小值
	 * @param maxValue
	 *            最大值
	 * @param innerStep
	 *            内步长
	 * @param outStep
	 *            外步长
	 * @param excludeIndexes
	 *            记录单次内已经故障的数据源
	 * @return 可用的sequence段
	 * @throws SequenceException
	 */
	public SequenceRange tryOnSelectedDataSource(int index,
			String sequenceName, long minValue, long maxValue, int innerStep,
			int outStep, List<Integer> excludeIndexes) throws SequenceException {
		if (isAvailable) {
			return tryOnAvailableDataSource(index, sequenceName, minValue,
					maxValue, innerStep, outStep, excludeIndexes);
		} else {
			return tryOnFailedDataSource(index, sequenceName, minValue,
					maxValue, innerStep, outStep, excludeIndexes);
		}
	}

	/**
	 * 在可用的数据源上获取sequence段，如果发生异常，则进行统计
	 * 
	 * @param index
	 *            数据源序列号
	 * @param sequenceName
	 *            sequence名称
	 * @param minValue
	 *            最小值
	 * @param maxValue
	 *            最大值
	 * @param innerStep
	 *            内步长
	 * @param outStep
	 *            外步长
	 * @param excludeIndexes
	 *            记录单次内已经故障的数据源
	 * @return sequence段
	 * @throws SequenceException
	 */
	public SequenceRange tryOnAvailableDataSource(int index,
			String sequenceName, long minValue, long maxValue, int innerStep,
			int outStep, List<Integer> excludeIndexes) throws SequenceException {

		long adjustValue = -1; // 调整后的值
		long oldValue = -1; // 旧值，每次从db里取出来的上一次更新后的值,用于 乐观锁的 version字段
		long newValue = -1; // 新值，即将更新到db的值
		long beginValue = -1; // 此次即将返回的sequenceRange的起始值
		long endValue = -1; // 此次即将返回的sequenceRange的结束值
		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		// 查询一条记录
		try {
			con = ds.getConnection();
			stmt = con.prepareStatement(selectSql);
			stmt.setString(1, sequenceName);
			rs = stmt.executeQuery();
			if (!rs.next()) {
				throw new SequenceException("No sequence record in the table:"
						+ tableName + ",please initialize it!");
			}
			oldValue = rs.getLong(1);

			if (oldValue < 0 || oldValue > maxValue || oldValue < minValue) {
				StringBuilder message = new StringBuilder();
				message.append("Sequence value set error, currentValue = "
						+ oldValue + ",minValue=" + minValue + ",maxValue="
						+ maxValue);
				message.append(", please check table: ").append(tableName);
				throw new SequenceException(message.toString());
			}

			adjustValue = getAjustValue(index, oldValue, minValue, maxValue,
					innerStep, outStep, sequenceName, false);

			beginValue = adjustValue;
			if (beginValue >= maxValue) {
				beginValue = getAjustValue(index, minValue, minValue, maxValue,
						innerStep, outStep, sequenceName, true);
			}
			// 计算本次sequence段的结束值
			endValue = beginValue + innerStep;
			if (endValue > maxValue) {
				endValue = maxValue;
			} else {
				endValue = endValue - 1;
			}
			// 验证sequence段的起始值
			if (beginValue > endValue) {
				throw new SequenceException("SEQUENCE-VALUE-ERROR:beginValue="
						+ beginValue + " is larg than endValue=" + endValue);
			}

			newValue = beginValue + outStep;
			// 如果新值超出了最大值，则从新开始计数
			if (newValue > maxValue) {
				newValue = getAjustValue(index, minValue, minValue, maxValue,
						innerStep, outStep, sequenceName, true);
			}
		} catch (SQLException e) {
			LOGGER.logMessage(
					LogLevel.WARN,
					"WARN ## 取sequence范围过程中出错,db-index={0},oldValue={1},newValue={2}",
					e, index, oldValue, newValue);
			calculateExceptionTimes(index);
			excludeIndexes.add(index);
			return null;
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}
		}

		// 更新该记录
		try {
			stmt = con.prepareStatement(updateSql);
			stmt.setLong(1, newValue);
			long gmt_modified = System.currentTimeMillis();
			stmt.setTimestamp(2, new Timestamp(gmt_modified));
			stmt.setString(3, sequenceName);
			stmt.setLong(4, oldValue);

			// 更新db
			int affectedRows = stmt.executeUpdate();
			if (affectedRows == 0) {
				// 如果此次没有更新记录，说明并发情况产生了，返回null
				LOGGER.logMessage(LogLevel.WARN,
						"WARN ## 更新sequence记录失败，oldValue={0},newValue={1}",
						oldValue, newValue);
				return null;
			}
			LOGGER.logMessage(
					LogLevel.WARN,
					"WARN ## Update the sequence of {0} th dataSource to {1} from {2}",
					index, newValue, oldValue);

			return new SequenceRange(beginValue, endValue);
		} catch (SQLException e) {
			LOGGER.logMessage(
					LogLevel.WARN,
					"WARN ## 更新sequence过程中出错,index={0},oldValue={1},newValue={2}",
					e, index, oldValue, newValue);
			calculateExceptionTimes(index);
			excludeIndexes.add(index);
			return null;
		} finally {
			try {
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}
		}
	}

	/**
	 * 本方法用于调整value到该db的sequence 区间段，调整跨度最大为一个outStep；<br>
	 * 一般情况下，db的sequence的值都是有规律的进行改动，如不被外在因素改动的话，不会进行调整；但是在新增了可以设置 minValue
	 * 功能之后，<br>
	 * 在初始化之前，须由业务指定minValue以及maxValue,
	 * 以及value值，且value须满足条件minValue<=value<=maxValue;<br>
	 * 因此在以下两种情况下会进行sequence的调整：<br>
	 * 1）指定的value值不符合规律 value= index*innerStep;<br>
	 * 2）在达到最大值之后，会从seq的最小值开始循环，但是指定的最小值不符合规律 index*innnerStep;<br>
	 * 
	 * 假设 oldValue= m*outStep+n，adjustValue=i*outStep+index*innerStep;<br>
	 * 那么 adjustValue=(oldValue-oldValue%
	 * outStep)+j*outStep+index*innerStep(其中j=0 or 1)<br>
	 * 推理过程：<br>
	 * 1)oldValue%outStep= n <br>
	 * 2)oldValue-oldValue%outStep= m*outStep，<br>
	 * 3)adjustValue= (m+j)*outStep+index*innderStep<br>
	 * 最后转换成比较 m 和 i 的大小，分为三种情况：<br>
	 * 1)m=i=0，则checkValue=index * innerStep；<br>
	 * 2)m=i>0, 则checkValue= (oldValue-oldValue% outStep)+index*innerStep；<br>
	 * 3)m-i=-1,则checkValue= (oldValue-oldValue%
	 * outStep)+outStep+index*innerStep；<br>
	 * 
	 * @param index
	 * @param oldValue
	 * @param innerStep
	 * @param outStep
	 * @param sequenceName
	 * @return
	 * @throws SequenceException
	 */
	private long getAjustValue(int index, long oldValue, long minValue,
			long maxValue, int innerStep, int outStep, String sequenceName,
			boolean isLoop) throws SequenceException {
		if (minValue > maxValue || minValue + innerStep > maxValue) {
			throw new SequenceException("ERROR ## SET-VALUE-ERROR:thread-name="
					+ Thread.currentThread().getName() + "minValue=" + minValue
					+ ",maxValue=" + maxValue + ",innerStep=" + innerStep
					+ ",outStep" + outStep);
		}
		long adjustValue = oldValue;
		if ((!check(index, oldValue, innerStep, outStep))) {
			if (adjust) {
				long value1 = index * innerStep;
				long value2 = (oldValue - oldValue % outStep) + index
						* innerStep;
				long value3 = (oldValue - oldValue % outStep) + outStep + index
						* innerStep;
				if (value1 >= oldValue) {
					adjustValue = value1;
				} else if (value2 >= oldValue) {
					adjustValue = value2;
				} else if (value3 >= oldValue) {
					adjustValue = value3;
				}
				LOGGER.logMessage(
						LogLevel.WARN,
						"WARN ## SEQUENCE-AJUST-SUCCESS:thread-name={0},sequenceName={1},dbIndex={2},oldValue={3},adjustValue={4},isLoop={5}",
						Thread.currentThread().getName(), sequenceName, index,
						oldValue, adjustValue, isLoop);
				// 判断调整后的值是否合法
				if (adjustValue < minValue || adjustValue > maxValue) {
					throw new SequenceException(
							"WARN ## AJUST-VALUE-FAILURED:thread-name={0},seqName={1},dbIndex={2},ajustValue={3},minValue={4},maxValue={5}",
							Thread.currentThread().getName(), sequenceName,
							index, adjustValue, minValue, maxValue);
				}
			} else {
				throw new SequenceException(
						"SEQUENCE-VALUE-ERROR:thread-name={0},seqName={1},db-index={2},oldValue={3},innerStep={4},outStep={5},seq值错误，覆盖到其他范围段了！请修改数据库，或者开启adjust开关！",
						Thread.currentThread().getName(), sequenceName, index,
						oldValue, innerStep, outStep);
			}
		}

		return adjustValue;
	}

	/**
	 * 获取当前db里所有的sequence记录的指定字段值
	 * 
	 * @param selectSql
	 *            ：select name,min_value,max_value,step from
	 *            table_name(default:sequence)
	 * @param nameColumn
	 *            sequence列名
	 * @param minValueColumnName
	 *            最小值
	 * @param maxValueColumnName
	 *            最大值
	 * @param innerStepColumnName
	 *            内步长
	 * @return Map<String, Map<String, Object>>
	 *         外层key标识sequence名字，内层key表示最小、最大值以及步长等；
	 * @throws SQLException
	 */
	public Map<String, Map<String, Object>> getAllSequenceRecordName(
			String selectSql, String nameColumn, String minValueColumnName,
			String maxValueColumnName, String innerStepColumnName)
			throws SQLException {
		Map<String, Map<String, Object>> records = new HashMap<String, Map<String, Object>>(
				0);
		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		// 查询所有记录
		try {
			con = ds.getConnection();
			stmt = con.prepareStatement(selectSql);
			rs = stmt.executeQuery();
			while (rs.next()) {
				String name = rs.getString(nameColumn);
				long min = rs.getLong(minValueColumnName);
				long max = rs.getLong(maxValueColumnName);
				int step = rs.getInt(innerStepColumnName);
				if (records.get(name) == null) {
					Map<String, Object> keyAndValue = new HashMap<String, Object>(
							0);
					keyAndValue.put(minValueColumnName, min);
					keyAndValue.put(maxValueColumnName, max);
					keyAndValue.put(innerStepColumnName, step);
					records.put(name, keyAndValue);
				}
			}
		} catch (SQLException e) {
			LOGGER.error("get all the sequence record failed!", e);
			throw e;
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}
		}
		return records;
	}

	/**
	 * 根据sequenceName 获取当前db里的指定sequence记录的各字段值
	 * 
	 * @param selectSql
	 *            : select value,min_value,max_value,step from sequence where
	 *            name=?
	 * @param nameColumn
	 *            sequence列名
	 * @param minValueColumnName
	 *            最小值
	 * @param maxValueColumnName
	 *            最大值
	 * @param innerStepColumnName
	 *            内步长
	 * @return
	 * @throws SQLException
	 * @throws SequenceException
	 */
	public Map<String, Map<String, Object>> getSequenceRecordByName(
			String selectSql, String minValueColumnName,
			String maxValueColumnName, String innerStepColumnName,
			String sequenceName) throws SQLException, SequenceException {
		Map<String, Map<String, Object>> records = new HashMap<String, Map<String, Object>>(
				0);
		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		// 查询一条记录,如果该记录不存在，就报异常
		try {
			con = ds.getConnection();
			stmt = con.prepareStatement(selectSql);
			stmt.setString(1, sequenceName);
			rs = stmt.executeQuery();
			if (!rs.next()) {
				throw new SequenceException(
						"can not find the record, name={0}", sequenceName);
			}
			long min = rs.getLong(minValueColumnName);
			long max = rs.getLong(maxValueColumnName);
			int step = rs.getInt(innerStepColumnName);
			if (records.get(sequenceName) == null) {
				Map<String, Object> keyAndValue = new HashMap<String, Object>(0);
				keyAndValue.put(minValueColumnName, min);
				keyAndValue.put(maxValueColumnName, max);
				keyAndValue.put(innerStepColumnName, step);
				records.put(sequenceName, keyAndValue);
			}
		} catch (SQLException e) {
			throw new SequenceException(
					"ERROR ## get the sequence record failed,name={0}", e,
					sequenceName);
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}
		}
		return records;
	}

	/**
	 * 在单线程重试的时候，取到连接后真正和数据库建立连接；
	 * 
	 * @param dbIndex
	 * @return
	 */
	public boolean tryToConnectDataBase(int dbIndex) {
		Long beginTime = System.currentTimeMillis();
		boolean isSussessful = true;
		String sql = "select 'x' ";
		Connection con = null;
		Statement stmt = null;
		try {
			con = ds.getConnection();
			stmt = con.createStatement();
			stmt.executeQuery(sql);
			LOGGER.logMessage(LogLevel.WARN,
					"ERROR ## 单线程" + Thread.currentThread().getName()
							+ "利用sql=" + sql + "真正做校验连接该数据源" + dbIndex
							+ "成功，耗时为:"
							+ (System.currentTimeMillis() - beginTime));
		} catch (SQLException e) {
			LOGGER.logMessage(LogLevel.WARN,
					"WARN ## 单线程{0}利用sql={1}真正做校验连接该数据源{2}失败,耗时为:{3}ms", e,
					Thread.currentThread().getName(), sql, dbIndex,
					System.currentTimeMillis() - beginTime);
			isSussessful = false;
		} finally {
			try {
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}
		}
		return isSussessful;
	}

	/**
	 * 在故障的数据源上进行单线程重试，每隔2s允许一个业务单线程进入重试状态, 如果访问成功则标记该数据源为可用，否则去寻找其他可用数据源。<br>
	 * 采用的是非阻塞锁实现的并发控制。
	 * 
	 * @param index
	 *            数据源标号
	 * @param sequenceName
	 *            sequence名称
	 * @param minValue
	 *            最小值
	 * @param maxValue
	 *            最大值
	 * @param innerStep
	 *            内步长
	 * @param outStep
	 *            外步长
	 * @param excludeIndexes
	 *            单次查询db排除掉的故障数据源标识集合
	 * @return
	 */
	public SequenceRange tryOnFailedDataSource(int index, String sequenceName,
			long minValue, long maxValue, int innerStep, int outStep,
			List<Integer> excludeIndexes) {

		boolean isTry = System.currentTimeMillis() - lastRetryTime > retryBadDbInterval;
		// 符合2s的时间间隔，并且能拿到非阻塞锁的时候开始进入单线程重试状态
		if (isTry && lock.tryLock()) {
			try {
				boolean isSussessful = tryToConnectDataBase(index);
				if (!isSussessful) {
					excludeIndexes.add(index);
					return null;
				}
				this.isAvailable = true;
				exceptionTimes = 0;
				return tryOnAvailableDataSource(index, sequenceName, maxValue,
						minValue, outStep, innerStep, excludeIndexes);
			} catch (SequenceException e) {
				LOGGER.logMessage(LogLevel.WARN,
						"WARN ## 单线程{0}尝试故障数据源{1}时失败，现在去寻找其他可用的数据源！", Thread
								.currentThread().getName(), index);
				excludeIndexes.add(index);
				return null;
			} finally {
				lastRetryTime = System.currentTimeMillis();
				lock.unlock();
			}
		} else {
			excludeIndexes.add(index);
			return null;
		}
	}

	/**
	 * 统计异常次数，分为以下几种情况：<br>
	 * (1)如果数据源已经不可用了，则直接抛异常，不再统计；
	 * (2)如果当前时间和第一次异常时间未超过指定的时间间隔阈值，则异常次数累计加1,否则重新清零计数；
	 * (3)如果加1后异常次数超过允许异常次数，则报异常；<br>
	 * 
	 * @param index
	 *            数据源标号
	 */
	private synchronized void calculateExceptionTimes(int index) {
		if (!isAvailable) {
			LOGGER.logMessage(LogLevel.WARN,
					"WARN ## 数据源{0}已经不可用了，不用再统计异常次数了！", index);
			return;
		}
		if (exceptionTimes == 0) {
			firstExceptionTime = System.currentTimeMillis();
		}
		long currentTime = System.currentTimeMillis();
		// 小于指定时间间隔则累加异常次数，如果异常次数超过某个阈值就将该数据源置为不可用；否则清零重新计数
		if (currentTime - this.firstExceptionTime <= timeInterval) {
			++exceptionTimes;
			LOGGER.logMessage(
					LogLevel.WARN,
					"WARN ## 数据源{0}单位时间内第{1}次异常，当前时间：{2}，首次异常时间：{3}，时间间隔为：{4}ms.",
					index, exceptionTimes, getCurrentDateTime(currentTime),
					getCurrentDateTime(firstExceptionTime), currentTime
							- firstExceptionTime);
			if (exceptionTimes >= allowExceptionTimes) {
				this.isAvailable = false;
				LOGGER.logMessage(LogLevel.WARN, "WARN ## 数据源{0}在时间{1}被踢出",
						index, getCurrentDateTime(null));
			}
		} else {
			LOGGER.logMessage(LogLevel.WARN,
					"WARN ## 统计异常次数超过单位时间间隔,上次单位时间间隔内异常次数为{0}次,现在开始重新计数！",
					exceptionTimes);
			this.exceptionTimes = 0;
		}
	}

	/**
	 * 初始化sequence 记录，如果db里存在该记录就跳过；否则对此进行初始化
	 * 
	 * @param index
	 *            db标识
	 * @param sequenceName
	 *            sequence名称
	 * @param innerStep
	 *            内步长
	 * @param outStep
	 *            外步长
	 * @param minValue
	 *            最小值
	 * @param maxValue
	 *            最大值
	 * @param valueColumnName
	 *            值列名
	 * @throws SequenceException
	 */
	public void initSequenceRecord(int index, String sequenceName,
			int innerStep, int outStep, long minValue, long maxValue,
			String valueColumnName) throws SequenceException {

		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			con = ds.getConnection();
			stmt = con.prepareStatement(selectSql);
			stmt.setString(1, sequenceName);// sequence名称
			rs = stmt.executeQuery();
			int rowNum = 0;
			if (rs.next()) {
				rowNum++;
				long oldValue = rs.getLong(valueColumnName);
				if (!check(index, oldValue, innerStep, outStep)) {
					if (adjust) {
						adjustUpdate(con, index, oldValue, sequenceName,
								innerStep, outStep, minValue, maxValue);
					} else {
						throw new SequenceException(
								"ERROR ## 数据源{0}的初始值有问题,请检查db或者开启ajust开关！",
								index);
					}
				}
			}
			if (rowNum == 0) {
				if (adjust) {// 不存在，插入这条记录
					this.adjustInsert(con, index, sequenceName, innerStep,
							outStep, minValue, maxValue);
				} else {
					throw new SequenceException(
							"ERROR ## 数据源{0}的sequence名为{1}的初始值不存在,请检查db或者开启ajust开关！",
							index, sequenceName);
				}
			}
		} catch (SQLException e) {
			throw new SequenceException(
					"ERROR ## 初始化sequence失败,sequence name={0}", e, sequenceName);
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
				if (con != null) {
					con.close();
					con = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}
		}
	}

	/**
	 * 用于在初始化过程中，更新出错的sequence值 格式update table_name(default:sequence) set
	 * value=? ,gmt_modified=? where name=? and value=?
	 * 
	 * @param index
	 *            db标识
	 * @param oldValue
	 *            old value
	 * @param name
	 *            sequence名
	 * @param innerStep
	 *            内步长
	 * @param outStep
	 *            外步长
	 * @throws SequenceException
	 */
	private void adjustUpdate(Connection con, int index, long oldValue,
			String name, int innerStep, int outStep, long minValue,
			long maxValue) throws SequenceException {
		long newValue = getAjustValue(index, oldValue, minValue, maxValue,
				innerStep, outStep, name, false);
		PreparedStatement stmt = null;
		try {
			stmt = con.prepareStatement(updateSql);
			stmt.setLong(1, newValue);// 更新后的值
			stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));// 修改时间
			stmt.setString(3, name);// sequence名称
			stmt.setLong(4, oldValue);// 更新前的值
			int affectedRows = stmt.executeUpdate();
			if (affectedRows == 0) {
				throw new SequenceException(
						"ERROR ## update the record failed!");
			}
		} catch (Exception e) {
			throw new SequenceException(
					"ERROR ## 更新 sequence 出错,datasouce index={0},sequence name={1},oldvalue={2},newValue={3}",
					e, index, name, oldValue, newValue);
		} finally {
			try {
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}
		}
	}

	/**
	 * 插入一条记录 格式: insert into
	 * table_name(default:sequence)(name,value,min_value,max_value
	 * ,step,gmt_create,gmt_modified) values(?,?,?,?,?,?,?)
	 * 
	 * @param index
	 *            db序列标识
	 * @param name
	 *            sequence 名称
	 * @param innerStep
	 *            内步长
	 * @param minValue
	 *            最小值
	 * @param maxValue
	 *            最大值
	 * @throws SQLException
	 * @throws SequenceException
	 * @throws SequenceException
	 */
	private void adjustInsert(Connection con, int index, String name,
			int innerStep, int outStep, long minValue, long maxValue)
			throws SQLException, SequenceException {
		PreparedStatement stmt = null;
		try {
			stmt = con.prepareStatement(insertSql);
			stmt.setString(1, name);// 字段名
			long value = getAjustValue(index, minValue, minValue, maxValue,
					innerStep, outStep, name, false);
			stmt.setLong(2, value);// 初始值
			stmt.setLong(3, minValue);// 最小值
			stmt.setLong(4, maxValue);// 最大值
			stmt.setInt(5, innerStep);// 内步长
			stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));// 创建时间
			stmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));// 修改时间
			stmt.executeUpdate();
		} catch (SQLException e) {
			// 捕获住异常后，判断记录是否已经插入，如果已插入则不抛异常
			if (isHaveInserted(con, name)) {
				LOGGER.logMessage(LogLevel.WARN,
						"WARN ## The record has inserted, name={0}", name);
			} else {
				throw new SequenceException(
						"ERROR ## the record is not insert to db,name={0}", e,
						name);
			}
		} finally {
			try {
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}
		}
	}

	/**
	 * 是否插入了该记录
	 * 
	 * @param sequenceName
	 * @return
	 */
	private boolean isHaveInserted(Connection con, String sequenceName) {
		boolean isHaveInserted = false;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = con.prepareStatement(selectSql);
			stmt.setString(1, sequenceName);
			rs = stmt.executeQuery();
			if (rs.next()) {
				isHaveInserted = true;
			}
		} catch (SQLException e) {
			LOGGER.logMessage(LogLevel.WARN,
					"WARN ## select the record error,sequence name={0}",
					sequenceName);
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (stmt != null) {
					stmt.close();
					stmt = null;
				}
			} catch (Exception e) {
				LOGGER.error("ERROR ## close resources has an error", e);
			}

		}
		return isHaveInserted;
	}

	/**
	 * 检查取得的sequence value值是不是合法
	 * 
	 * @param index
	 *            数据源序列标识
	 * @param value
	 *            当前squence值
	 * @param innerStep
	 *            内步长
	 * @param outStep
	 *            外步长
	 * @return 是否合法
	 */
	private boolean check(int index, long value, int innerStep, int outStep) {
		return (value % outStep) == (index * innerStep);
	}

	/**
	 * 获取当前的时间的格式化字符串
	 * 
	 * @param time
	 * @return
	 */
	private String getCurrentDateTime(Long time) {
		java.util.Date now;
		if (time != null) {
			now = new java.util.Date(time);
		} else {
			now = new java.util.Date();
		}
		java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss");
		return format.format(now);
	}

}
