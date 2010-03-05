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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import org.perfmon4j.Appender.AppenderID;
import org.perfmon4j.SnapShotMonitor.SnapShotMonitorID;
import org.perfmon4j.util.Logger;
import org.perfmon4j.util.LoggerFactory;
import org.perfmon4j.util.MiscHelper;


public class PerfMonConfiguration {
    private static final Logger logger = LoggerFactory.initLogger(PerfMonConfiguration.class);
    
    public final static long DEFAULT_APPENDER_INTERVAL = 60 * 60 * 1000; 
    
    private final Map<String, MonitorConfig> monitorMap = new HashMap();
    private final Map<String, Appender.AppenderID> appenderMap = new HashMap();
    private final Map<String, SnapShotMonitorConfig> snapShotMonitors = new HashMap();
    private final Map<String, ThreadTraceConfig> threadTraceConfigs = new HashMap();
    
    // This list will be filled with the name of any classes that could not be found
    // while processing the config.
    // The format will be: "<PerfMonElement>: <className>"
    private Set<String> classNotFoundInfo = new HashSet<String>();
    
    
/*----------------------------------------------------------------------------*/
    public void defineAppender(String name, String className, 
        String interval) {
        defineAppender(name, className, interval, null);
    }
    
/*----------------------------------------------------------------------------*/
    public void defineAppender(String name, String className, 
        String interval, Properties attributes) {
        if (appenderMap.get(name) == null) {
            appenderMap.put(name, AppenderID.getAppenderID(className, convertIntervalStringToMillis(interval), attributes));
        }
    }
    
/*----------------------------------------------------------------------------*/
    public void defineMonitor(String monitorName) {
        if (PerfMon.ROOT_MONITOR_NAME.equalsIgnoreCase(monitorName)) {
            monitorName = ""; // Make sure root always sorts to the top..
        }
        
        if ( monitorMap.get(monitorName) == null) {
            monitorMap.put(monitorName, new MonitorConfig());
        }
    }

/*----------------------------------------------------------------------------*/
    public String[] getMonitorArray() {
        String result[] = monitorMap.keySet().toArray(new String[]{});
        Arrays.sort(result);
        
        if (result.length > 0 && result[0].equals("")) {
            result[0] = PerfMon.ROOT_MONITOR_NAME;
        }
        
        return result;
    }
    
    public Appender.AppenderID getAppenderForName(String appenderName) {
        return appenderMap.get(appenderName);
    }
    
/*----------------------------------------------------------------------------*/
    public void attachAppenderToMonitor(String monitorName, String appenderName) throws InvalidConfigException {
        attachAppenderToMonitor(monitorName, appenderName, PerfMon.APPENDER_PATTERN_PARENT_AND_ALL_DESCENDENTS);
    }
    
/*----------------------------------------------------------------------------*/
    public void attachAppenderToMonitor(String monitorName, String appenderName, String appenderPattern) throws InvalidConfigException {
        String nameToSearch = monitorName;
        if (PerfMon.ROOT_MONITOR_NAME.equalsIgnoreCase(monitorName)) {
            nameToSearch = ""; 
        }
        MonitorConfig config = monitorMap.get(nameToSearch);
        if (config == null) {
            throw new InvalidConfigException("Monitor: \"" + monitorName + "\" not defined.");
        }
        Appender.AppenderID appenderID = appenderMap.get(appenderName);
        if (appenderID == null) {
            throw new InvalidConfigException("Appender: \"" + appenderName + "\" not defined.");
        }
        config.addAppender(appenderID, appenderPattern);
    }

/*----------------------------------------------------------------------------*/
    public void attachAppenderToSnapShotMonitor(String monitorName, String appenderName) throws InvalidConfigException {
        SnapShotMonitorConfig config = snapShotMonitors.get(monitorName);
        if (config == null) {
            throw new InvalidConfigException("SnapShotMonitor not defined. monitorName=" + monitorName);
        }
        
        Appender.AppenderID appenderID = appenderMap.get(appenderName);
        if (appenderID == null) {
            throw new InvalidConfigException("Appender not defined. appenderName=" + appenderName);
        }
        
        config.addAppender(appenderID);
    }
    
    public Map<String, ThreadTraceConfig> getThreadTraceConfigMap() {
        return threadTraceConfigs;
    }
    
/*----------------------------------------------------------------------------*/
    public SnapShotMonitorID defineSnapShotMonitor(String name, String className) {
        return defineSnapShotMonitor(name, className, null);
    }

/*----------------------------------------------------------------------------*/
    public SnapShotMonitorID defineSnapShotMonitor(String name, String className, Properties attributes) {
        SnapShotMonitorID result = null;
        
        if (snapShotMonitors.containsKey(name)) {
            logger.logWarn("Duplicate snapShotMonitor name found name=" + name);
        }
        
        result = SnapShotMonitor.getSnapShotMonitorID(className, name, attributes);
        snapShotMonitors.put(name, new SnapShotMonitorConfig(result));
        
        return result;
    }
    
/*----------------------------------------------------------------------------*/  
    public SnapShotMonitorConfig[] getSnapShotMonitorArray() {
        return snapShotMonitors.values().toArray(new SnapShotMonitorConfig[]{});
    }
    
    public Appender.AppenderID[] getAllDefinedAppenders() {
        return appenderMap.values().toArray(new Appender.AppenderID[]{});
        
    }
    
/*----------------------------------------------------------------------------*/
    private static class MonitorConfig {
        private final Set<Appender.AppenderID> appenderSet = new HashSet<Appender.AppenderID>();
        private final Map<Appender.AppenderID, String> patternMap = new HashMap<Appender.AppenderID, String>();
        private final Properties attributes = new Properties();
        
        
        private MonitorConfig() {
        }
        
        private void addAppender(Appender.AppenderID appenderID, String appenderPattern) {
            appenderSet.add(appenderID);
            patternMap.put(appenderID, appenderPattern);
        }
        
        private void setAttribute(String key, String value) {
            attributes.setProperty(key, value);
        }
    }

/*----------------------------------------------------------------------------*/
    public static class SnapShotMonitorConfig {
        private final SnapShotMonitorID monitorID;
        private final Set<Appender.AppenderID> appenderSet = new HashSet<Appender.AppenderID>();
        
        
        private SnapShotMonitorConfig(SnapShotMonitorID monitorID) {
            this.monitorID = monitorID;
        }
        
        private void addAppender(Appender.AppenderID appenderID) {
            appenderSet.add(appenderID);
        }
        
        public SnapShotMonitorID getMonitorID() {
            return monitorID;
        }
        
        public Appender.AppenderID[] getAppenders() {
            return appenderSet.toArray(new Appender.AppenderID[]{});
        }
    }
    
    
/*----------------------------------------------------------------------------*/
    public static final class AppenderAndPattern {
        final private Appender appender;
        final private String appenderPattern;
        
        AppenderAndPattern(Appender.AppenderID appenderID, String appenderPattern) throws InvalidConfigException {
            this.appender = Appender.getOrCreateAppender(appenderID);
            this.appenderPattern = (appenderPattern == null) ? PerfMon.APPENDER_PATTERN_PARENT_AND_ALL_DESCENDENTS :
                appenderPattern;
        }

        public Appender getAppender() {
            return appender;
        }

        public String getAppenderPattern() {
            return appenderPattern;
        }
    }
    
/*----------------------------------------------------------------------------*/
    static long convertIntervalStringToMillis(String interval) {
        return MiscHelper.convertIntervalStringToMillis(interval, DEFAULT_APPENDER_INTERVAL);
    }
    
    public AppenderAndPattern[] getAppendersForMonitor(String monitorName) throws InvalidConfigException {
    	return getAppendersForMonitor(monitorName, null);
    }
    
    
/*----------------------------------------------------------------------------*/    
    public AppenderAndPattern[] getAppendersForMonitor(String monitorName, PerfMonConfiguration perfMonConfig) throws InvalidConfigException {
        String nameToSearch = monitorName;
        if (PerfMon.ROOT_MONITOR_NAME.equalsIgnoreCase(monitorName)) {
            nameToSearch = ""; 
        }
        MonitorConfig config = monitorMap.get(nameToSearch);
        if (config == null) {
            throw new InvalidConfigException("Monitor: \"" + monitorName + "\" not defined.");
        }
        
        List<AppenderAndPattern> appenders = new Vector<AppenderAndPattern>();
        Iterator<Appender.AppenderID> itr = config.appenderSet.iterator();
        while (itr.hasNext()) {
            Appender.AppenderID id = itr.next();
            try {
            	appenders.add(new AppenderAndPattern(id, config.patternMap.get(id)));
            } catch (InvalidConfigException ie) {
            	if (perfMonConfig != null) {
            		perfMonConfig.getClassNotFoundInfo().add("Appender: " + id.getClassName());
            	} else {
            		logger.logWarn("Unable to load appender: " + id.getClassName());
            	}
            }
        }
        
        return appenders.toArray(new AppenderAndPattern[]{});
    }

    public void addThreadTraceConfig(String monitorKey, ThreadTraceConfig threadTraceConfig) {
        threadTraceConfigs.put(monitorKey, threadTraceConfig);
    }
    
    public boolean isPartialLoad() {
    	return !classNotFoundInfo.isEmpty();
    }
    
    public Set<String> getClassNotFoundInfo() {
    	return classNotFoundInfo;
    }
}
