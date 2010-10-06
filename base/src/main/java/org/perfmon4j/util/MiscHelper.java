/*
 *	Copyright 2008 Follett Software Company 
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.perfmon4j.PerfMon;


public class MiscHelper {
    static private final Logger logger = LoggerFactory.initLogger(MiscHelper.class);
    
    static private final String[] MEASUREMENT_UNITS_MILLISECOND = {"MS", "MILLI", "MILLIS", "MILLISECOND", "MILLISECONDS"};
    static private final String[] MEASUREMENT_UNITS_SECOND = {"S", "SEC", "SECS", "SECOND", "SECONDS"};
    static private final String[] MEASUREMENT_UNITS_HOUR = {"H", "HR", "HOUR", "HOURS"};
    
    private MiscHelper() {
    }
    

    // Pattern.quote() can not be used in order to retain Java 1.5 compatibility
    private static String quoteForRegEx(String input) {
    	return "\\Q" + input + "\\E";
    }
    
    public static Integer parseInteger(String key, String sourceString) {
        Integer result = null;
        if (sourceString != null) {
            Pattern pattern = Pattern.compile(
               quoteForRegEx(key) + "=(\\d++)");
            Matcher matcher = pattern.matcher(sourceString);
            if (matcher.find()) {
                result = new Integer(matcher.group(1));
            }
        }
        return result;
    }
    static public long convertIntervalStringToMillis(String interval, long defaultValue) {
    	return convertIntervalStringToMillis(interval, defaultValue, "");
    }

    
    /*----------------------------------------------------------------------------*/
    static public long convertIntervalStringToMillis(String interval, long defaultValue, String defaultUnit) {
        long result = defaultValue;
        
        if (interval != null) {
            interval = interval.trim();
        }
        
        if (interval != null && !interval.equals("")) {
            String[] intervalComponents = interval.split("\\s");
    
            if (intervalComponents.length > 0) {
                try {
                    long    value = new Long(intervalComponents[0]).longValue();
            
                    if (value == 0) {   // special case for 0; use default value
                        result = defaultValue;
                    } else {
                        String measurementUnit = intervalComponents.length > 1 ? intervalComponents[1] : defaultUnit;
                        result = value * getMultiplierForMeasurementUnit(measurementUnit);
                    }
        
                } catch (NumberFormatException nfe) {
                   logger.logWarn("Unable to convert: \"" + interval + "\" - using default");
                }
            }
        }
        return Math.abs(result);
    }
   

/*----------------------------------------------------------------------------*/    
    static private long getMultiplierForMeasurementUnit(String measurementUnit) {
        long result = 1000 * 60;
        
        if (stringExists(measurementUnit, MEASUREMENT_UNITS_MILLISECOND)) {
            result = 1;
        } else if (stringExists(measurementUnit, MEASUREMENT_UNITS_SECOND)) {
            result = 1000;
        } else if (stringExists(measurementUnit, MEASUREMENT_UNITS_HOUR)) {
            result = 1000 * 60 * 60;
        }
        
        return result;
    }
        
/*----------------------------------------------------------------------------*/    
    public static boolean stringExists(String str, String[] strings) {
        boolean result = false;
        for (int i = 0; i < strings.length; i++) {
            if (strings[i].equalsIgnoreCase(str)) {
                result = true;
                break;
            }
        }
        return result;
    }

    
/*----------------------------------------------------------------------------*/    
    public static boolean objectExists(Object obj, Object[] objs) {
        boolean result = false;
        for (int i = 0; i < objs.length; i++) {
            if (objs[i].equals(obj)) {
                result = true;
                break;
            }
        }
        return result;
    }

    public static String getMillisDisplayable(long millis) {
        String result = null;
        final long second = 1000;
        final long minute = 60 * second;
        
        if (millis % minute == 0) {
            long minutes = millis/minute;
            if (minutes == 1) {
                result = minutes + " minute";
            } else {
                result = minutes + " minutes";
            }
        } else if (millis % second == 0) {
            long seconds = millis/second;
            if (seconds == 1) {
                result = seconds + " second";
            } else {
                result = seconds + " seconds";
            }
        } else  {
            result = millis + " ms";
        }
        
        return result;
    }
    
    
    /**
     * This is a relatively crude attempt to increase the grainulairty of
     * the millisecond timer beyound the level available via System.currentTimeMillis.
     * 
     * The goal is to return a time that unlike timeNanos references a unique data/time.  However
     * unlike currentTimeMillis it also does not tend to round up to the nearest 5ms.
     * 
     */
    private static final long SYSTEM_TIME_NANO_TIMER_DIFF;
    
    static {
        long millis = System.currentTimeMillis();
        long nanos = System.nanoTime();
        
        SYSTEM_TIME_NANO_TIMER_DIFF = millis - (nanos/1000000);
    }
    public static final boolean USE_TIGHT_RESOLUTION = false;
    
    public static long currentTimeWithMilliResolution() {
        if (USE_TIGHT_RESOLUTION) {
            return (System.nanoTime()/1000000) + SYSTEM_TIME_NANO_TIMER_DIFF;
        } else {
            return System.currentTimeMillis();
        }
    }

    /*----------------------------------------------------------------------------*/    
    static public String formatTimeAsString(long value) {
        return formatTimeAsString(new Long(value));
        
    }
    
/*----------------------------------------------------------------------------*/    
    static public String formatTimeAsString(Long value) {
        String result = "";
        if (value != null && value.longValue() != PerfMon.NOT_SET) {
            result = String.format("%tH:%tM:%tS:%tL", value, value, value, value);
        }
        return result;
    }

/*----------------------------------------------------------------------------*/    
    static public String formatDateTimeAsString(Long value) {
        return formatDateTimeAsString(value, false);
    }
    
/*----------------------------------------------------------------------------*/    
    static public String formatDateTimeAsString(long value) {
        return formatDateTimeAsString(value, false);
    }

 /*----------------------------------------------------------------------------*/    
    static public String formatDateTimeAsString(long v, boolean includeMillis) {
        return formatDateTimeAsString(new Long(v), includeMillis, false);
    }
   
 /*----------------------------------------------------------------------------*/    
    static public String formatDateTimeAsString(Long v, boolean includeMillis) {
        return formatDateTimeAsString(v, includeMillis, false);
    }
    
/*----------------------------------------------------------------------------*/    
    static public String formatDateTimeAsString(long v, boolean includeMillis, boolean includeParens) {
        return formatDateTimeAsString(new Long(v), includeMillis, includeParens);
    }
    
/*----------------------------------------------------------------------------*/    
    static public String formatDateTimeAsString(Long v, boolean includeMillis, boolean includeParens) {
        String result = "";
        if (v != null && v.longValue() != PerfMon.NOT_SET) {
            if (includeMillis) {
                result = String.format("%tY-%tm-%td %tH:%tM:%tS:%tL", v, v, v, v, v, v, v);
            } else {
                result = String.format("%tY-%tm-%td %tH:%tM:%tS", v, v, v, v, v, v);
            }
            if (includeParens) {
                result = "(" + result + ")";
            }
        }
        return result;
    }
    
/*----------------------------------------------------------------------------*/    
    static public double calcVariance(int sampleCount, long total, long sumOfSquares) {
        double result = 0;
        
        if (sampleCount > 1) {
            result = ((sumOfSquares - ((total * total)/(double)sampleCount))/
                ((double)sampleCount-1));
        }
        
        return result;
    }
    
/*----------------------------------------------------------------------------*/    
    static public double calcStdDeviation(int sampleCount, long total, long sumOfSquares) {
        double result = 0;
        
        double variance = calcVariance(sampleCount, total, sumOfSquares);
        if (variance > 0) {
            result = Math.sqrt(variance);
        }
        
        return result;
    }


    final static private String PADDING = "...................................................................................";
    
	public static String formatTextDataLine(int labelLength, String label, String data) {
		return formatTextDataLine(labelLength, label, data, "");
	}

    
    public static String formatTextDataLine(int labelLength, String label, String data, String suffix) {
		if (label.length() >= labelLength) {
			label = label.substring(0, labelLength - 1);
		}
		
		if (suffix == null) {
			suffix = "";
		}
		
		
		String result = label + PADDING.substring(0, labelLength - label.length()) + " " + data + suffix + "\r\n";
		
		return result;
	}

    public static String formatTextDataLine(int labelLength, String label, float value, String suffix) {
		return formatTextDataLine(labelLength, label, value, suffix, 2);
	}

	public static String formatTextDataLine(int labelLength, String label, float value, String suffix, int precision) {
		return formatTextDataLine(labelLength, label, String.format("%."+ precision + "f%s", new Float(value), suffix));
	}
	
	public static  ObjectName appendWildCard(ObjectName base) {
		ObjectName result = base;
		try {
			String canonicalName = base.getCanonicalName();
			if (!canonicalName.endsWith(",*")) {
				result = new ObjectName(canonicalName + ",*");
			}
		} catch (MalformedObjectNameException e) {
			logger.logWarn("Unable to add wildCard to queryName: " + base.getCanonicalName());
		} 
		return result;
	}


	public static MBeanServer findMBeanServer(String domainName) {
		MBeanServer result = null;
		
		Iterator<MBeanServer> itr = MBeanServerFactory.findMBeanServer(null).iterator();
		while (itr.hasNext() && result == null) {
			MBeanServer server = itr.next();
			
			if ((domainName == null) || domainName.equals(server.getDefaultDomain())) {
				result = server;
			}
		}
		
		if (result == null && "jboss".equalsIgnoreCase(domainName)) {
			//  We are in JBoss and we did not find the MBeanServer based on the domain
			//  name...  Use reflections to call the MBeanServerLocator class.
			try {
				Class clazz = Class.forName("org.jboss.mx.util.MBeanServerLocator", true, PerfMon.getClassLoader());
				Method m = clazz.getMethod("locateJBoss", new Class[]{});
				result = (MBeanServer)m.invoke(null,new Object[]{});
				logger.logDebug("Found JBoss MBean Server via org.jboss.mx.util.MBeanServerLocator.locateJBoss()");
			} catch (Exception e) {
				logger.logDebug("Unable to Find JBoss MBean Server via org.jboss.mx.util.MBeanServerLocator.locateJBoss()", e);
			}
		}
		return result;
	}
	
	
	// Will iterator over the MBeans that match query name and sum the attributes.
	public static long sumMBeanAttributes(MBeanServer server, ObjectName queryName, String attrName) {
		long result = 0;
		Iterator<ObjectName> itr = server.queryNames(appendWildCard(queryName), null).iterator();
		while (itr.hasNext()) {
			ObjectName objName = itr.next();
			try {
				Object obj = server.getAttribute(objName, attrName);
				if (obj instanceof Number) {
					result += ((Number)obj).longValue();
				}
			} catch (Exception e) {
				logger.logWarn("Unable to retrieve attribute: " + attrName + " from Object: " + objName.toString(), e);
			}
		}
		return result;
	}

	public static String getInstanceNames(MBeanServer server, ObjectName queryName, String keyName) {
		String result = "";
		
		String prop = queryName.getKeyProperty(keyName);
		if (prop == null || "*".equals(prop)) {
			result = "Composite(";
			boolean firstElement = true;
			Iterator<ObjectName> itr = server.queryNames(appendWildCard(queryName), null).iterator();
			while (itr.hasNext()) {
				ObjectName objName = itr.next();
				if (!firstElement) {
					result += ", ";
				}
				result += "\"" + objName.getKeyProperty(keyName) + "\"";
				firstElement = false;
			}
			result += ")";
		} else {	
			result = prop;
		}
		return result;
	}
	
	public static boolean isRunningInJBossAppServer() {
		return System.getProperty("jboss.home.dir") != null;
	}
	
	
	private static void addFileToZipOutputStream(ZipOutputStream stream, File file, String path) throws IOException {
		if (file.getName().startsWith(".")) {
			logger.logDebug("Skipping hidden file/directory: " + file.getName());
			return;
		}
		
		if (!"".equals(path)) {
			path += "/";
		}
		path += file.getName();
		if (file.isDirectory()) {
			File files[] = file.listFiles();
			for (File f: files) {
				addFileToZipOutputStream(stream, f, path);
			}
		} else {
			ZipEntry entry = new ZipEntry(path);
			FileInputStream inFile = null;
			try {
				inFile = new FileInputStream(file);
				stream.putNextEntry(entry);
				
				byte buffer[] = new byte[1024];
				int bytesRead = 0;
				while ((bytesRead = inFile.read(buffer)) != -1) {
					stream.write(buffer, 0, bytesRead);
				}
				stream.closeEntry();
			} finally {
				if (inFile != null) {
					inFile.close();
				}
			}
		}
	}
	
	public static void createJarFile(String fileName, Properties manifest, File filesToJar[]) throws IOException {
		ZipOutputStream outStream = null;
		try {
			outStream = new ZipOutputStream(new FileOutputStream(fileName));
			
			ZipEntry entry = new ZipEntry("META-INF/MANIFEST.MF");
			outStream.putNextEntry(entry);
	
			outStream.write("Manifest-Version: 1.0\r\n".getBytes());
			outStream.write("Created-By: Perfmon4j\r\n".getBytes());

			
			Iterator<Map.Entry<Object, Object>> manifestItr = manifest.entrySet().iterator();
			while (manifestItr.hasNext()) {
				Map.Entry<Object, Object> e = manifestItr.next();
				outStream.write((e.getKey() + ": " + e.getValue() + "\r\n").getBytes());
			}
			outStream.closeEntry();
			
			for (File f : filesToJar) {
				if (f.isDirectory()) {
					File filesFromDirectory[] = f.listFiles(); 
					for (File f2 : filesFromDirectory) {
						if (!f.getName().startsWith(".")) {
							addFileToZipOutputStream(outStream, f2, "");
						} else {
							logger.logDebug("Skipping hidden file/directory: " + f.getName());
						}
					}
				} else {
					addFileToZipOutputStream(outStream, f, "");
				}
			}
			
		} finally {
			if (outStream != null) {
				outStream.flush();
				outStream.close();
			}
		}
	}


	public static long calcDateOnlyFromMillis(long currentTimeMillis) {
		Calendar cal = new GregorianCalendar();
		cal.setTimeInMillis(currentTimeMillis);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		
		return cal.getTimeInMillis();
	}
	
	
	public static String[] tokenizeCSVString(String src) {
		String[] result = null;
		
		if (src != null && !src.trim().equals("")) {
			List<String> x = new ArrayList<String>();
			StringTokenizer t = new StringTokenizer(src, ",");
			while (t.hasMoreTokens()) {
				String str = t.nextToken().trim();
				if (!str.equals("")) {
					x.add(str);
				}
			}
			result = x.toArray(new String[]{});
		}
		return result;
	}

}
