/*
 *	Copyright 2015 Follett School Solutions 
 *
 *	This file is part of PerfMon4j(tm).
 *
 * 	Perfmon4j is free software: you can redistribute it and/or modify
 * 	it under the terms of the GNU Lesser General Public License, version 3,
 * 	as published by the Free Software Foundation.  This program is distributed
 * 	WITHOUT ANY WARRANTY OF ANY KIND, WITHOUT AN IMPLIED WARRANTY OF MERCHANTIBILITY,
 * 	OR FITNESS FOR A PARTICULAR PURPOSE.  You should have received a copy of the GNU Lesser General Public 
 * 	License, Version 3, along with this program.  If not, you can obtain the LGPL v.s at 
 * 	http://www.gnu.org/licenses/
 * 	
 * 	perfmon4j@fsc.follett.com
 * 	David Deuchert
 * 	Follett School Solutions
 * 	1391 Corporate Drive
 * 	McHenry, IL 60050
 * 
*/

package web.org.perfmon4j.restdatasource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.perfmon4j.dbupgrader.UpdateOrCreateDb;
import org.perfmon4j.util.JDBCHelper;
import org.perfmon4j.util.JDBCHelper.DriverCache;

import web.org.perfmon4j.restdatasource.util.DateTimeHelper;


public class BaseDatabaseSetup  {
//	public static final String SCHEMA = "TEST";
	public static final String JDBC_URL = "jdbc:derby:memory:mydb"; 
	public static final String JDBC_DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
	private static final DateTimeHelper helper = new DateTimeHelper();
	protected Connection connection = null;
	
	
	public BaseDatabaseSetup() {
	}

	public void setUpDatabase() throws Exception {
		setUpDatabase(null);
	}

	
	public void setUpDatabase(String thirdPartyExtensions) throws Exception {
		connection = JDBCHelper.createJDBCConnection(DriverCache.DEFAULT, JDBC_DRIVER, null, JDBC_URL + ";create=true", null, null);
		connection.setAutoCommit(true);		
		
		Set<String> parameters = new HashSet<String>();
		parameters.add("driverClass=org.apache.derby.jdbc.EmbeddedDriver");
		parameters.add("jdbcURL=" + JDBC_URL);
		parameters.add("driverJarFile=EMBEDDED");
		
		if (thirdPartyExtensions != null) {
			parameters.add("thirdPartyExtensions=" + thirdPartyExtensions);
		}
		
		// Start with an empty database...
		UpdateOrCreateDb.main(parameters.toArray(new String[]{}));
		
//		new dblook(new String[]{"-d", JDBC_URL, "-verbose"});
		
	}

	public void tearDownDatabase() throws Exception {
		JDBCHelper.closeNoThrow(connection);
		
		try {
			JDBCHelper.createJDBCConnection(DriverCache.DEFAULT, JDBC_DRIVER, null, JDBC_URL + ";drop=true", null, null);
		} catch (SQLException sn) {
		}
	}

	long getID(Statement stmt) throws SQLException {
		ResultSet rs = null;
		try {
			rs = stmt.getGeneratedKeys();
			if (!rs.next()) {
				throw new SQLException("Expected a generated key");
			} else {
				return rs.getLong(1);
			}
			
		} finally {
			JDBCHelper.closeNoThrow(rs);
		}
	}
	
	
	public long addSystem(String systemName) throws SQLException {
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate("INSERT INTO P4JSystem (SystemName) VALUES('" + systemName + "')", Statement.RETURN_GENERATED_KEYS);
			return getID(stmt);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}
	
	public long addInterval(long systemID, long categoryID, String endTime) throws SQLException {
		return addInterval(systemID, categoryID, endTime, 1);
	}

	
	public long addInterval(long systemID, long categoryID, String endTime, int factor) throws SQLException {
		Timestamp end = new Timestamp(helper.parseDateTime(endTime).getTimeForEnd());
		Timestamp start = new Timestamp(end.getTime() - (60 * 1000));
		
		/*
			CREATE TABLE "TEST"."P4JINTERVALDATA" ("INTERVALID" INTEGER NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1), 
			"CATEGORYID" INTEGER NOT NULL, 
			"STARTTIME" TIMESTAMP NOT NULL, 
			"ENDTIME" TIMESTAMP NOT NULL, 
			"TOTALHITS" BIGINT NOT NULL, 
			"TOTALCOMPLETIONS" BIGINT NOT NULL, 
			"MAXACTIVETHREADS" INTEGER NOT NULL, 
			"MAXACTIVETHREADSSET" TIMESTAMP, 
			"MAXDURATION" INTEGER NOT NULL, 
			"MAXDURATIONSET" TIMESTAMP, 
			"MINDURATION" INTEGER NOT NULL, 
			"MINDURATIONSET" TIMESTAMP, 
			"AVERAGEDURATION" DECIMAL(18,2) NOT NULL, 
			"MEDIANDURATION" DECIMAL(18,2), 
			"STANDARDDEVIATION" DECIMAL(18,2) NOT NULL, 
			"NORMALIZEDTHROUGHPUTPERMINUTE" DECIMAL(18,2) NOT NULL, 
			"DURATIONSUM" BIGINT NOT NULL, 
			"DURATIONSUMOFSQUARES" BIGINT NOT NULL, 
			"SQLMAXDURATION" INTEGER, 
			"SQLMAXDURATIONSET" TIMESTAMP, 
			"SQLMINDURATION" INTEGER, 
			"SQLMINDURATIONSET" TIMESTAMP, 
			"SQLAVERAGEDURATION" DECIMAL(18,2), 
			"SQLSTANDARDDEVIATION" DECIMAL(18,2), 
			"SQLDURATIONSUM" BIGINT, 
			"SQLDURATIONSUMOFSQUARES" BIGINT, 
			"SYSTEMID" INTEGER NOT NULL DEFAULT 1);
		*/
		final String SQL = "INSERT INTO P4JIntervalData (CATEGORYID, "
				+ "STARTTIME, ENDTIME, TOTALHITS, TOTALCOMPLETIONS, MAXACTIVETHREADS, "
				+ "MAXACTIVETHREADSSET, MAXDURATION, MAXDURATIONSET, MINDURATION, MINDURATIONSET, "
				+ "AVERAGEDURATION, MEDIANDURATION, STANDARDDEVIATION, NORMALIZEDTHROUGHPUTPERMINUTE, DURATIONSUM, "
				+ "DURATIONSUMOFSQUARES, SQLMAXDURATION, SQLMAXDURATIONSET, SQLMINDURATION, SQLMINDURATIONSET, "
				+ "SQLAVERAGEDURATION, SQLSTANDARDDEVIATION, SQLDURATIONSUM, SQLDURATIONSUMOFSQUARES, SYSTEMID)\r\n"
				+ "VALUES(?, "
				+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
				+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
//System.out.println(SQL);		
		PreparedStatement stmt = null;
		try {
			stmt = connection.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS);
			int p = 1;
			stmt.setLong(p++, categoryID); 		// CATEGORYID
			
			stmt.setTimestamp(p++, start); 	   	// STARTTIME
			stmt.setTimestamp(p++, end);	   	// ENDTIME	
			stmt.setInt(p++, 10 * factor);	   			// TOTALHITS	
			stmt.setInt(p++, 10 * factor);	   			// TOTALCOMPLETIONS	
			stmt.setInt(p++, 5 * factor);	   			// MAXACTIVETHREDS	
			
			stmt.setTimestamp(p++, end);	   	// MAXACTIVETHREADSSET	
			stmt.setInt(p++, 50 * factor);	   			// MAXDURATION	
			stmt.setTimestamp(p++, end);	   	// MAXDURATIONSET	
			stmt.setInt(p++, 0);	   			// MINDURATION	
			stmt.setTimestamp(p++, start);	   	// MINDURATIONSET	
			
			stmt.setDouble(p++, 10.3 * factor);	   		// AVERAGEDURATION	
			stmt.setDouble(p++, 11.5 * factor);	   		// MEDIANDURATION	
			stmt.setDouble(p++, 3.4 * factor);	   		// STANDARDDEVIATION	
			stmt.setDouble(p++, 10 * factor);	   		// NORMALIZEDTHROUGHPUTPERMINUTE	
			stmt.setInt(p++, 1000 * factor);	   			// DURATIONSUM	

			stmt.setInt(p++, 10000 * factor);	   		// DURATIONSUMOFSQUARES	
			stmt.setInt(p++, 45 * factor);	   			// SQLMAXDURATION	
			stmt.setTimestamp(p++, end);	   	// SQLMAXDURATIONSET	
			stmt.setInt(p++, 0);	   			// SQLMINDURATION	
			stmt.setTimestamp(p++, start);	   	// SQLMINDURATIONSET	

			stmt.setDouble(p++, 1.5 * factor);	// SQLAVERAGEDURATION	
			stmt.setDouble(p++, .65 * factor);	// SQLSTANDARDDEVIATION	
			stmt.setInt(p++, 200 * factor);	   	// SQLDURATIONSUM	
			stmt.setInt(p++, 2000 * factor);	// SQLDURATIONSUMOFSQUARES	
			stmt.setLong(p++, systemID);	   	// SYSTEMID	
			
			stmt.executeUpdate();
			return getID(stmt);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}
	
	
	public long addCategory(String category) throws SQLException {
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate("INSERT INTO P4JCategory (CategoryName) VALUES('" + category + "')", Statement.RETURN_GENERATED_KEYS);
			return getID(stmt);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}

	public void addJVMObservation(long systemID, long endTime) throws SQLException {
		Map<String, Object> overrideValues = new HashMap<String, Object>();
		
		overrideValues.put("SYSTEMID", Long.valueOf(systemID));
		overrideValues.put("STARTTIME", new Timestamp(endTime - 60000));
		overrideValues.put("ENDTIME", new Timestamp(endTime));
		
		String sql = buildDefaultInsertStatement("P4JVMSnapShot", overrideValues);
		
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate(sql);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}

	public void addGCObservation(long systemID, long endTime, String instanceName) throws SQLException {
		Map<String, Object> overrideValues = new HashMap<String, Object>();
		
		overrideValues.put("SYSTEMID", Long.valueOf(systemID));
		overrideValues.put("STARTTIME", new Timestamp(endTime - 60000));
		overrideValues.put("ENDTIME", new Timestamp(endTime));
		overrideValues.put("INSTANCENAME", instanceName);
		
		String sql = buildDefaultInsertStatement("P4JGarbageCollection", overrideValues);
		
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate(sql);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}

	public void addFSSFetchPolicyObservation(long systemID, long endTime, String instanceName) throws SQLException {
		Map<String, Object> overrideValues = new HashMap<String, Object>();
		
		overrideValues.put("SYSTEMID", Long.valueOf(systemID));
		overrideValues.put("STARTTIME", new Timestamp(endTime - 60000));
		overrideValues.put("ENDTIME", new Timestamp(endTime));
		overrideValues.put("INSTANCENAME", instanceName);
		
		String sql = buildDefaultInsertStatement("FSSFetchPolicySnapshot", overrideValues);
		
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate(sql);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}

	public void addFSSFetchThreadPoolObservation(long systemID, long endTime) throws SQLException {
		Map<String, Object> overrideValues = new HashMap<String, Object>();
		
		overrideValues.put("SYSTEMID", Long.valueOf(systemID));
		overrideValues.put("STARTTIME", new Timestamp(endTime - 60000));
		overrideValues.put("ENDTIME", new Timestamp(endTime));
		
		String sql = buildDefaultInsertStatement("FSSFetchThreadPoolSnapshot", overrideValues);
		
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate(sql);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}
	
	
	public void addMemoryPoolObservation(long systemID, long endTime, String instanceName) throws SQLException {
		Map<String, Object> overrideValues = new HashMap<String, Object>();
		
		overrideValues.put("SYSTEMID", Long.valueOf(systemID));
		overrideValues.put("STARTTIME", new Timestamp(endTime - 60000));
		overrideValues.put("ENDTIME", new Timestamp(endTime));
		overrideValues.put("INSTANCENAME", instanceName);
		overrideValues.put("MEMORYTYPE", "Heap Memory");
		
		String sql = buildDefaultInsertStatement("P4JMemoryPool", overrideValues);
		
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate(sql);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}

	public void addThreadPoolObservation(long systemID, long endTime, String threadPoolOwner, String instanceName) throws SQLException {
		Map<String, Object> overrideValues = new HashMap<String, Object>();
		
		overrideValues.put("SYSTEMID", Long.valueOf(systemID));
		overrideValues.put("STARTTIME", new Timestamp(endTime - 60000));
		overrideValues.put("ENDTIME", new Timestamp(endTime));
		overrideValues.put("THREADPOOLOWNER", threadPoolOwner);
		overrideValues.put("INSTANCENAME", instanceName);
		
		String sql = buildDefaultInsertStatement("P4JThreadPoolMonitor", overrideValues);
		
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate(sql);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}
	
	
	public void addCacheObservation(long systemID, long endTime, String cacheType, String instanceName) throws SQLException {
		Map<String, Object> overrideValues = new HashMap<String, Object>();
		
		overrideValues.put("SYSTEMID", Long.valueOf(systemID));
		overrideValues.put("STARTTIME", new Timestamp(endTime - 60000));
		overrideValues.put("ENDTIME", new Timestamp(endTime));
		overrideValues.put("INSTANCENAME", instanceName);
		overrideValues.put("CACHETYPE", cacheType);
		
		String sql = buildDefaultInsertStatement("P4JCache", overrideValues);
		
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate(sql);
		} finally {
			JDBCHelper.closeNoThrow(stmt);
		}
	}
	
	
	private String buildDefaultInsertStatement(String tableName, Map<String, Object> overrideValues) throws SQLException {
		StringBuilder insertInto = new StringBuilder("INSERT INTO " + tableName + " (");
		StringBuilder values = new StringBuilder(") VALUES(");
		Statement stmt = null;
		ResultSet rs = null;
		try {
			stmt = connection.createStatement();
			rs = stmt.executeQuery("SELECT * FROM " + tableName);
			ResultSetMetaData md = rs.getMetaData();
			int columnCount = md.getColumnCount();
			
			for (int i = 0; i < columnCount; i++) {
				if (i > 0) {
					insertInto.append(", ");
					values.append(", ");
				}
				int colOffset = i + 1;
				String columnName = md.getColumnName(colOffset);
				insertInto.append(columnName);
				
				Object v = overrideValues.get(columnName);
				if (v == null) {
					v = Integer.valueOf(colOffset);
				}
				
				if (v instanceof Timestamp) {
					v = ((Timestamp)v).toString();
				}
				
				if (v instanceof String) {
					v = "'" + v + "'";
				} 
				
		
				values.append(v.toString());
			}
		} finally {
			JDBCHelper.closeNoThrow(rs);
			JDBCHelper.closeNoThrow(stmt);
		}
		
		values.append(")");
		insertInto.append(values.toString());
		
		return insertInto.toString();
	}

	public Connection getConnection() {
		return connection;
	}
}
