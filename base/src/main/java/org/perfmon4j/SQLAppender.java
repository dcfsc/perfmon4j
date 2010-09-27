/*
 *	Copyright 2008, 2009, 2010 Follett Software Company 
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
 * 	Follett Software Company
 * 	1391 Corporate Drive
 * 	McHenry, IL 60050
 * 
*/
package org.perfmon4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import org.perfmon4j.util.JDBCHelper;
import org.perfmon4j.util.Logger;
import org.perfmon4j.util.LoggerFactory;
import org.perfmon4j.util.MedianCalculator;
import org.perfmon4j.util.ThresholdCalculator;
import org.perfmon4j.util.MedianCalculator.MedianResult;
import org.perfmon4j.util.ThresholdCalculator.ThresholdResult;

public abstract class SQLAppender extends Appender {
	private static final Logger logger = LoggerFactory.initLogger(SQLAppender.class);
	private String dbSchema = null;
	private String insertCategoryPS = null;
	private String selectCategoryPS = null;
	private String insertIntervalPS = null;
	private String insertThresholdPS = null;
	
	public SQLAppender(AppenderID id) {
		super(id);
	}

	protected abstract Connection getConnection() throws SQLException;
	protected abstract void releaseConnection(Connection conn);
	protected abstract void resetConnection();
   
	
	@Override
	public void outputData(PerfMonData data) {
		Connection conn = null;
		try {
			conn = getConnection();
			if (data instanceof IntervalData) {
				outputIntervalData(conn, (IntervalData)data);
			} else if (data instanceof SQLWriteable){
				((SQLWriteable)data).writeToSQL(conn, dbSchema);
			} else {
				logger.logWarn("SKIPPING! Data type not supported by appender: " + data.getClass().getName());
			}
		} catch(SQLException ex) { 
			resetConnection();
			logger.logError("Error in output data", ex);
			ex.printStackTrace();
		} finally {
			if (conn != null) {
				releaseConnection(conn);
			}
		}
	}

	public String getInsertCategoryPS() {
		if (insertCategoryPS == null) {
			insertCategoryPS = String.format(INSERT_CATEGORY_PS, dbSchema == null ? "" : (dbSchema + "."));
		}
		return insertCategoryPS;
	}

	
	public String getSelectCategoryPS() {
		if (selectCategoryPS == null) {
			selectCategoryPS = String.format(SELECT_CATEGORY_PS, dbSchema == null ? "" : (dbSchema + "."));
		}
		return selectCategoryPS;
	}

	public String getInsertIntervalPS(boolean includeOptionalCols) {
		if (insertIntervalPS == null) {
			insertIntervalPS = String.format(INSERT_INTERVAL_PS, dbSchema == null ? "" : (dbSchema + "."));
		}
		return insertIntervalPS;
	}

	public String getInsertThresholdPS() {
		if (insertThresholdPS == null) {
			insertThresholdPS = String.format(INSERT_THRESHOLD_PS, dbSchema == null ? "" : (dbSchema + "."));
		}
		return insertThresholdPS;
	}
	private final static String INSERT_CATEGORY_PS = "INSERT INTO %sP4JCategory (CategoryName) VALUES(?)";
	private final static String SELECT_CATEGORY_PS = "SELECT CategoryID FROM %sP4JCategory WHERE CategoryName=?";
	
	private final static String INSERT_INTERVAL_PS = "INSERT INTO %sP4JIntervalData (CategoryID, StartTime, EndTime, " +
		"TotalHits, TotalCompletions, MaxActiveThreads, MaxActiveThreadsSet, MaxDuration, " +
		"MaxDurationSet, MinDuration, MinDurationSet, averageDuration, standardDeviation,  " +
		"normalizedThroughputPerMinute, durationSum, durationSumOfSquares, medianDuration, " +
		"SQLMaxDuration, SQLMaxDurationSet, SQLMinDuration, SQLMinDurationSet, " +
		"SQLAverageDuration, SQLStandardDeviation, SQLDurationSum, SQLDurationSumOfSquares) " +
		"VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
		"?, ?, ?, ?)";
	
	private final static String INSERT_THRESHOLD_PS = "INSERT INTO %sP4JIntervalThreshold (intervalID, ThresholdMillis, " +
		"CompletionsOver, PercentOver) VALUES(?, ?, ?, ?)";

	private long getOrCreateCategory(Connection conn, String categoryName) throws SQLException {
		long result = 0;
		final boolean oracleConnection = JDBCHelper.isOracleConnection(conn);
		PreparedStatement stmtQuery = null;
		PreparedStatement stmtInsert = null;
		ResultSet rs = null;
		
		try {
			stmtQuery = conn.prepareStatement(getSelectCategoryPS());
			stmtQuery.setString(1, categoryName);
			rs = stmtQuery.executeQuery();
			if (!rs.next()) {
				JDBCHelper.closeNoThrow(rs);
				rs = null;
		
				if (oracleConnection) {
					stmtInsert = conn.prepareStatement(getInsertCategoryPS(), new int[]{1});
				} else {
					stmtInsert = conn.prepareStatement(getInsertCategoryPS(), Statement.RETURN_GENERATED_KEYS);
				}
				stmtInsert.setString(1, categoryName);
				stmtInsert.execute();
				
				rs = stmtInsert.getGeneratedKeys();
				rs.next();
			}
			result = rs.getLong(1);
		} finally {
			JDBCHelper.closeNoThrow(rs);
			JDBCHelper.closeNoThrow(stmtQuery);
			JDBCHelper.closeNoThrow(stmtInsert);
		}
		return result;
	}
	
	private Timestamp buildTimestampOrNull(long time) {
		return time > 0 ? new Timestamp(time) : null;
	}
	
	private void outputIntervalData(Connection conn, IntervalData data) throws SQLException {
		if (data.getTimeStart() <= 0 || data.getTimeStop() <= 0) {
			logger.logWarn("Skipping SQL insert for data timeStart and/or timeEnd missing");
			return;
		}
		final boolean oracleConnection = JDBCHelper.isOracleConnection(conn);
		boolean autoCommit = conn.getAutoCommit();
		boolean doRollback = false;
		
		PreparedStatement insertIntervalStmt = null;
		PreparedStatement insertThresholdStmt = null;
		ResultSet generatedKeys = null;
		try {
			if (autoCommit) {
				conn.setAutoCommit(false);
			}
			long categoryID = getOrCreateCategory(conn, data.getOwner().getName());
			long startTime = data.getTimeStart();
			long endTime = data.getTimeStop();
			long totalHits = data.getTotalHits();
			long totalCompletions = data.getTotalCompletions();
			long maxActiveThreads = data.getMaxActiveThreadCount();
			Timestamp maxActiveThreadsSet = buildTimestampOrNull(data.getTimeMaxActiveThreadCountSet());
			long maxDuration = data.getMaxDuration();
			Timestamp maxDurationSet = buildTimestampOrNull(data.getTimeMaxDurationSet());
			long minDuration = data.getMinDuration();
			Timestamp minDurationSet = buildTimestampOrNull(data.getTimeMaxDurationSet());
			double averageDuration = data.getAverageDuration();
			double standardDeviation = data.getStdDeviation();
			double normalizedThroughputPerMinute = data.getThroughputPerMinute();
			long durationSum = data.getTotalDuration();
			long durationSumOfSquares = data.getSumOfSquares();
			Double medianDuration = null;

			long maxSQLDuration = data.getMaxSQLDuration();
			Timestamp maxSQLDurationSet = buildTimestampOrNull(data.getTimeMaxSQLDurationSet());
			long minSQLDuration = data.getMinSQLDuration();
			Timestamp minSQLDurationSet = buildTimestampOrNull(data.getTimeMinSQLDurationSet());
			double averageSQLDuration = data.getAverageSQLDuration();
			double sqlStdDeviation = data.getSQLStdDeviation();
			long totalSQLDuration = data.getTotalSQLDuration();
			long sumOfSQLSquares = data.getSumOfSQLSquares();
			
			MedianCalculator calc = data.getMedianCalculator();
			MedianResult medianResult = null;
			if (calc != null) {
				medianResult = calc.getMedian();
			}
			if (medianResult != null && medianResult.getOverflowFlag() == 0) {
				Double d = medianResult.getResult();
				if (d == null) {
					d = Double.valueOf(0.0);
				}
				medianDuration = d;
			}
			
			boolean includeOptionalCols = (
					SQLTime.isEnabled() 
					&& !data.isSQLMonitor());
			
			if (oracleConnection) {
				insertIntervalStmt = conn.prepareStatement(getInsertIntervalPS(includeOptionalCols), new int[]{1});
			} else {
				insertIntervalStmt = conn.prepareStatement(getInsertIntervalPS(includeOptionalCols), Statement.RETURN_GENERATED_KEYS);
			}
			insertIntervalStmt.setLong(1, categoryID);
			insertIntervalStmt.setTimestamp(2, new Timestamp(startTime));
			insertIntervalStmt.setTimestamp(3, new Timestamp(endTime));
			insertIntervalStmt.setLong(4, totalHits);
			insertIntervalStmt.setLong(5, totalCompletions);
			insertIntervalStmt.setLong(6, maxActiveThreads);
			insertIntervalStmt.setObject(7, maxActiveThreadsSet, Types.TIMESTAMP);
			insertIntervalStmt.setLong(8, maxDuration);
			insertIntervalStmt.setObject(9, maxDurationSet, Types.TIMESTAMP);
			insertIntervalStmt.setLong(10, minDuration);
			insertIntervalStmt.setObject(11, minDurationSet, Types.TIMESTAMP);
			insertIntervalStmt.setDouble(12, averageDuration);
			insertIntervalStmt.setDouble(13, standardDeviation);
			insertIntervalStmt.setDouble(14, normalizedThroughputPerMinute);
			insertIntervalStmt.setLong(15, durationSum);
			insertIntervalStmt.setLong(16, durationSumOfSquares);
			insertIntervalStmt.setObject(17, medianDuration, Types.DOUBLE);
			if (includeOptionalCols) {
				insertIntervalStmt.setLong(18, maxSQLDuration);
				insertIntervalStmt.setObject(19, maxSQLDurationSet, Types.TIMESTAMP);
				insertIntervalStmt.setLong(20, minSQLDuration);
				insertIntervalStmt.setObject(21, minSQLDurationSet, Types.TIMESTAMP);
				insertIntervalStmt.setDouble(22, averageSQLDuration);
				insertIntervalStmt.setDouble(23, sqlStdDeviation);
				insertIntervalStmt.setLong(24, totalSQLDuration);
				insertIntervalStmt.setLong(25, sumOfSQLSquares);
			} else {
				insertIntervalStmt.setObject(18, null, Types.INTEGER);
				insertIntervalStmt.setObject(19, null, Types.TIMESTAMP);
				insertIntervalStmt.setObject(20, null, Types.INTEGER);
				insertIntervalStmt.setObject(21, null, Types.TIMESTAMP);
				insertIntervalStmt.setObject(22, null, Types.DOUBLE);
				insertIntervalStmt.setObject(23, null, Types.DOUBLE);
				insertIntervalStmt.setObject(24, null, Types.INTEGER);
				insertIntervalStmt.setObject(25, null, Types.INTEGER);
			}
			insertIntervalStmt.execute();
			generatedKeys = insertIntervalStmt.getGeneratedKeys();
			generatedKeys.next();
			long intervalID = generatedKeys.getLong(1);
			
			// Now write out the thresholds...
			ThresholdCalculator thCalc = data.getThresholdCalculator();
			if (thCalc != null) {
				insertThresholdStmt = conn.prepareStatement(getInsertThresholdPS());
				
				long thresholds[] = thCalc.getThresholdMillis();
				for (int i = 0; i < thresholds.length; i++) {
					ThresholdResult thResult = thCalc.getResult(thresholds[i]);
					
					insertThresholdStmt.setLong(1, intervalID);
					insertThresholdStmt.setLong(2, thresholds[i]);
					insertThresholdStmt.setLong(3, thResult.getCountOverThreshold());
					insertThresholdStmt.setFloat(4, thResult.getPercentOverThreshold());
					
					insertThresholdStmt.execute();
				}
			}
		} catch (SQLException se){
			doRollback = true;
			throw se;
		} finally {
			try {
				JDBCHelper.closeNoThrow(generatedKeys);
				JDBCHelper.closeNoThrow(insertThresholdStmt);
				JDBCHelper.closeNoThrow(insertIntervalStmt);
				if (doRollback) {
					JDBCHelper.rollbackNoThrow(conn);
				} else {
					conn.commit();
				}
			} finally {
				if (autoCommit) {
					conn.setAutoCommit(true);
				}
			}
		}
	}

	public String getDbSchema() {
		return dbSchema;
	}

	public void setDbSchema(String dbSchema) {
		this.dbSchema = dbSchema;

		// Clear out so they will be regenerated with the new schema
		this.insertCategoryPS = null;
		this.insertIntervalPS = null;
		this.insertThresholdPS = null;
		this.selectCategoryPS = null;
	}
}
