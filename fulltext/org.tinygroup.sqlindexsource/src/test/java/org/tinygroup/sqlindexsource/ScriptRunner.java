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
package org.tinygroup.sqlindexsource;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.tinygroup.commons.tools.Resources;

/**
 * Tool to run database scripts
 */
public class ScriptRunner {

	private static final String DEFAULT_DELIMITER = ";";

	private Connection connection;
	private String driver;
	private String url;
	private String username;
	private String password;

	private boolean stopOnError;
	private boolean autoCommit;

	private PrintWriter logWriter = new PrintWriter(System.out);
	private PrintWriter errorLogWriter = new PrintWriter(System.err);

	private String delimiter = DEFAULT_DELIMITER;
	private boolean fullLineDelimiter = false;

	/**
	 * Default constructor
	 */
	public ScriptRunner(Connection connection, boolean autoCommit,
			boolean stopOnError) {
		this.connection = connection;
		this.autoCommit = autoCommit;
		this.stopOnError = stopOnError;
	}

	public void setDelimiter(String delimiter, boolean fullLineDelimiter) {
		this.delimiter = delimiter;
		this.fullLineDelimiter = fullLineDelimiter;
	}

	public ScriptRunner(String driver, String url, String username,
			String password, boolean autoCommit, boolean stopOnError) {
		this.driver = driver;
		this.url = url;
		this.username = username;
		this.password = password;
		this.autoCommit = autoCommit;
		this.stopOnError = stopOnError;
	}

	/**
	 * Setter for logWriter property
	 * 
	 * @param logWriter
	 *            - the new value of the logWriter property
	 */
	public void setLogWriter(PrintWriter logWriter) {
		this.logWriter = logWriter;
	}

	/**
	 * Setter for errorLogWriter property
	 * 
	 * @param errorLogWriter
	 *            - the new value of the errorLogWriter property
	 */
	public void setErrorLogWriter(PrintWriter errorLogWriter) {
		this.errorLogWriter = errorLogWriter;
	}

	/**
	 * Runs an SQL script (read in using the Reader parameter)
	 * 
	 * @param reader
	 *            - the source of the script
	 */
	public void runScript(Reader reader) throws Exception {
		if (connection == null) {
			DriverManager
					.registerDriver((Driver) Resources.instantiate(driver));
			Connection conn = DriverManager.getConnection(url, username,
					password);
			try {
				if (conn.getAutoCommit() != autoCommit) {
					conn.setAutoCommit(autoCommit);
				}
				runScript(conn, reader);
			} finally {
				conn.close();
			}
		} else {
			boolean originalAutoCommit = connection.getAutoCommit();
			try {
				if (originalAutoCommit != this.autoCommit) {
					connection.setAutoCommit(this.autoCommit);
				}
				runScript(connection, reader);
			} finally {
				connection.setAutoCommit(originalAutoCommit);
			}
		}
	}

	/**
	 * Runs an SQL script (read in using the Reader parameter) using the
	 * connection passed in
	 * 
	 * @param conn
	 *            - the connection to use for the script
	 * @param reader
	 *            - the source of the script
	 * @throws SQLException
	 *             if any SQL errors occur
	 * @throws IOException
	 *             if there is an error reading from the Reader
	 */
	private void runScript(Connection conn, Reader reader) throws IOException,
			SQLException {
		StringBuffer command = null;
		try {
			LineNumberReader lineReader = new LineNumberReader(reader);
			String line = null;
			while ((line = lineReader.readLine()) != null) {
				if (command == null) {
					command = new StringBuffer();
				}
				String trimmedLine = line.trim();
				if (trimmedLine.startsWith("--") || trimmedLine.equals("--")) {
					println(trimmedLine);
				} else if (!fullLineDelimiter
						&& trimmedLine.endsWith(getDelimiter())
						|| fullLineDelimiter
						&& trimmedLine.equals(getDelimiter())) {
					command.append(line.substring(0,
							line.lastIndexOf(getDelimiter())));
					command.append(" ");
					Statement statement = conn.createStatement();

					println(command);

					boolean hasResults = false;
					if (stopOnError) {
						hasResults = statement.execute(command.toString());
					} else {
						try {
							statement.execute(command.toString());
						} catch (SQLException e) {
							e.fillInStackTrace();
							printlnError("Error executing: " + command);
							printlnError(e);
						}
					}

					if (autoCommit && !conn.getAutoCommit()) {
						conn.commit();
					}

					ResultSet rs = statement.getResultSet();
					if (hasResults && rs != null) {
						ResultSetMetaData md = rs.getMetaData();
						int cols = md.getColumnCount();
						for (int i = 1; i <= cols; i++) {
							String name = md.getColumnLabel(i);
							print(name + "\t");
						}
						println("");
						while (rs.next()) {
							for (int i = 1; i <= cols; i++) {
								String value = rs.getString(i);
								print(value + "\t");
							}
							println("");
						}
					}

					command = null;
					try {
						statement.close();
						rs.close();
					} catch (Exception e) {
						// Ignore to workaround a bug in Jakarta DBCP
					}
					Thread.yield();
				} else {
					command.append(line);
					command.append(" ");
				}
			}
			if (!autoCommit) {
				conn.commit();
			}
		} catch (SQLException e) {
			printlnError("Error executing: " + command);
			printlnError(e);
			throw e;
		} catch (IOException e) {
			printlnError("Error executing: " + command);
			printlnError(e);
			throw e;
		} finally {
			conn.rollback();
			flush();
		}
	}

	private String getDelimiter() {
		return delimiter;
	}

	private void print(Object o) {
		if (logWriter != null) {
			logWriter.println(o);
		}
	}

	private void println(Object o) {
		if (logWriter != null) {
			logWriter.println(o);
		}
	}

	private void printlnError(Object o) {
		if (errorLogWriter != null) {
			errorLogWriter.println(o);
		}
	}

	private void flush() {
		if (logWriter != null) {
			logWriter.flush();
		}
		if (errorLogWriter != null) {
			errorLogWriter.flush();
		}
	}

}
