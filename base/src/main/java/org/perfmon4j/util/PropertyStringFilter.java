/*
 *	Copyright 2008-2017 Follett Software Company 
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
 * 	1391 Corparate Drive
 * 	McHenry, IL 60050
 * 
*/
package org.perfmon4j.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyStringFilter {
    /**
     * @param args
     */
    final static String test = "dave${last.name}dave${first.name}dave";
    final static Pattern REQUEST_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");
    final private static String ENV_PREFIX = "env.";

    private Map<String, String> envVariables;
    private final Properties properties;
    
    public PropertyStringFilter() {
    	this(null, true);
    }

    public PropertyStringFilter(boolean includeEnvVariables) {
    	this(null, includeEnvVariables);
    }

    public PropertyStringFilter(Properties properties) {
    	this(properties, true);
    }
    
    public PropertyStringFilter(Properties properties, boolean includeEnvVariables) {
    	if (includeEnvVariables) {
    		this.envVariables = System.getenv();
    	} else {
    		this.envVariables = null;
    	}
    	this.properties = properties != null ? properties : System.getProperties();
    }
    
    private String replaceAll(String source, String find, String replace) {
        String result = source;
        int index = result.indexOf(find);
        
        while (index >= 0) {
            result = result.replace(find, replace);
            index = result.indexOf(find, index + replace.length());
        }
        
        return result;
    }
    
    public String doFilter(String sourceString) {
        List<String> recursionPreventionList = new ArrayList<String>();
        return doFilter(sourceString, recursionPreventionList);
    }
    
    private String doFilter(String sourceString, List<String> recursionPreventionList) {
        String result = sourceString;
        if (sourceString != null) {
	        Matcher matcher = REQUEST_PATTERN.matcher(sourceString);
	       
	        while (matcher.find()) {
	            final String key = matcher.group(1);
	            String value = getProperty(key);
	            if (value != null) {
	                if (!recursionPreventionList.contains(key)) {
	                    try {
	                        recursionPreventionList.add(key);
	                        value = doFilter(value, recursionPreventionList);
	                        result = replaceAll(result, "${" + key + "}", value);
	                    } finally {
	                        recursionPreventionList.remove(key);
	                    }
	                }
	            }
	        }
        }
        return result;
    }

    private String getProperty(String key) {
    	String result = null;
    	
    	// If the key starts with "env." prefer the envir
    	if (key.startsWith(ENV_PREFIX)) {
    		String keyWithoutPrefix = key.replace(ENV_PREFIX, "");
    		if (envVariables != null) {
    			result = envVariables.get(keyWithoutPrefix);
    		} 
    		if (result == null) {
    			result = properties.getProperty(keyWithoutPrefix);
    		}
    		if (result == null) {
    			// For backwards compatibility look for a property 
    			// containing the original key with the "env." prefix
    			result = properties.getProperty(key);
    		}
    	} else {
	    	result = properties.getProperty(key);
	    	if (result == null && envVariables != null) {
	    		result = envVariables.get(key);
	    	}
    	}
    	
    	return result;
    }
    
    // Test only method
    void setMockEnvVariables_TEST_ONLY(Map<String, String> mockEnvVariables) {
    	envVariables = mockEnvVariables;
    }    
    
    @Deprecated
    public static String filter(String sourceString) {
    	return new PropertyStringFilter().doFilter(sourceString);
    }
 
    @Deprecated
    public static String filter(Properties props, String sourceString) {
    	return new PropertyStringFilter(props).doFilter(sourceString);
    }
}
