/*
 *	Copyright 2011 Follett Software Company 
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
package org.perfmon4j.remotemanagement;

import java.io.File;
import java.io.FileFilter;
import java.util.Properties;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.perfmon4j.PerfMon;
import org.perfmon4j.PerfMonTimer;
import org.perfmon4j.remotemanagement.intf.RemoteInterface;
import org.perfmon4j.remotemanagement.intf.SimpleRunnable;
import org.perfmon4j.remotemanagement.intf.ThinRunnableInVM;
import org.perfmon4j.util.Logger;
import org.perfmon4j.util.LoggerFactory;
import org.perfmon4j.util.MiscHelper;


public class RemoteImplTest extends TestCase {
	@SuppressWarnings("unused")
	private static Logger logger = LoggerFactory.initLogger(RemoteImplTest.class);
	
	public static final String TEST_ALL_TEST_TYPE = "UNIT";

	private File perfmon4jManagementInterface = null;
	
/*----------------------------------------------------------------------------*/
    public RemoteImplTest(String name) {
        super(name);
    }
    
/*----------------------------------------------------------------------------*/
    public void setUp() throws Exception {
        super.setUp();
        
        perfmon4jManagementInterface = File.createTempFile("perfmon4j-remote-management", "tmpdir");
        perfmon4jManagementInterface.delete(); // Just wanted the unique temporary file name.
        perfmon4jManagementInterface.mkdir();
        
        perfmon4jManagementInterface = new File(perfmon4jManagementInterface, "perfmon4j-remote-management.jar");
        
        Properties props = new Properties();	
		
		File classesFolder = new File("./target/classes");
		if (!classesFolder.exists()) {
			classesFolder = new File("./base/target/classes");
		}
		
		File testClassesFolder = new File("./target/test-classes");
		if (!testClassesFolder.exists()) {
			testClassesFolder = new File("./base/target/test-classes");
		}
		
		assertTrue("Could not find classes folder in: "  + classesFolder.getCanonicalPath(), classesFolder.exists());
	
        MiscHelper.createJarFile(perfmon4jManagementInterface.getAbsolutePath(), props, 
        		new File[]{classesFolder, testClassesFolder}, new MyFilter());
        
        System.out.println("perfmon4j jar file: " + perfmon4jManagementInterface.getCanonicalPath());
        
        RemoteImpl.registerRMIListener(8571);
    }
    
    /*----------------------------------------------------------------------------*/
    public void tearDown() throws Exception {
    	RemoteImpl.unregisterRMIListener();
    	
    	File folder = perfmon4jManagementInterface.getParentFile();
        perfmon4jManagementInterface.delete();
        folder.delete();
    	super.tearDown();
    }
    
    
    public static final class MyFilter implements FileFilter {

		public boolean accept(File pathname) {
			boolean result = true;
			
			if (!pathname.isDirectory()) {
				String p = pathname.getAbsolutePath();
				p = p.replaceAll("\\\\", "/");
				result = p.contains("classes/org/perfmon4j/remotemanagement/intf")
					|| p.contains("test-classes/org/perfmon4j/remotemanagement/intf/SimpleRunnable");
			}
			return result;
		}
    }
    

    public void testSystemPortIsSetAsSystemProperty() {
    	assertEquals("8571", System.getProperty(RemoteInterface.P4J_LISTENER_PORT));
    	assertNotNull(RemoteImpl.getRegisteredPort());
    	assertEquals(8571, RemoteImpl.getRegisteredPort().intValue());
    	
    	RemoteImpl.unregisterRMIListener();
    	
    	assertNull(RemoteImpl.getRegisteredPort());
    	assertNull(System.getProperty(RemoteInterface.P4J_LISTENER_PORT));
    }
    
    
    public void testConnect() throws Exception {
    	String result = ThinRunnableInVM.run(SimpleRunnable.TestSimpleConnect.class, "", perfmon4jManagementInterface);
//System.out.println(result);
    	assertTrue("Session should have been established", result.contains("Retrieved sessionID:"));
    }

    
    public void testMajorVersionMismatch() throws Exception {
    	String result = ThinRunnableInVM.run(SimpleRunnable.TestMajorVersionMismatch.class, "", perfmon4jManagementInterface);
//System.out.println(result);
		assertTrue("Should have caught incompatible version exception", 
				result.contains("IncompatibleClientVersionException thrown"));
    }
    
    public void testGetMonitorsIncludesIntervalMonitors() throws Exception {
    	ExternalAppender.setEnabled(true);
    	
    	PerfMonTimer t = PerfMonTimer.start("testGetMonitorsIncludesIntervalMonitors");
    	PerfMonTimer.stop(t);
    	PerfMon.deInit();	
    	
    	String result = ThinRunnableInVM.run(SimpleRunnable.TestGetMonitors.class, "", perfmon4jManagementInterface);
    	ExternalAppender.setEnabled(true);

    	System.out.println(result);
		assertTrue("Should contain the interval monitor", 
				result.contains("Monitor: INTERVAL:testGetMonitorsIncludesIntervalMonitors"));
		assertTrue("Validate we retrieved the monitor Definition", 
				result.contains("FieldDefinition(monitorDefinition:INTERVAL, fieldName:MaxActiveThreadCount, fieldType:INTEGER)"));
    }

/*----------------------------------------------------------------------------*/    
    public static void main(String[] args) {
        BasicConfigurator.configure();
        System.setProperty("Perfmon4j.debugEnabled", "true");
		
        org.apache.log4j.Logger.getLogger(RemoteImplTest.class.getPackage().getName()).setLevel(Level.INFO);
        String[] testCaseName = {RemoteImplTest.class.getName()};

        TestRunner.main(testCaseName);
    }

    
/*----------------------------------------------------------------------------*/
    public static junit.framework.Test suite() {
    	String testType = System.getProperty("UNIT");
//    	if (testType == null) {
//    		System.setProperty("Perfmon4j.debugEnabled", "true");
//    		System.setProperty("JAVASSIST_JAR",  "G:\\projects\\perfmon4j\\.repository\\javassist\\javassist\\3.10.0.GA\\javassist-3.10.0.GA.jar");
//    	}
        TestSuite newSuite = new TestSuite();

        // Here is where you can specify a list of specific tests to run.
        // If there are no tests specified, the entire suite will be set in the if
        // statement below.
//		newSuite.addTest(new RemoteImplTest("testGetMonitorsIncludesIntervalMonitors"));

        // Here we test if we are running testunit or testacceptance (testType will
        // be set) or if no test cases were added to the test suite above, then
        // we run the full suite of tests.
        if (testType != null || newSuite == null || (newSuite.countTestCases() < 1)) {
            newSuite = new TestSuite(RemoteImplTest.class);
        }

        return( newSuite);
    }
}
