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

package org.perfmon4j.java.management;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.perfmon4j.SnapShotData;
import org.perfmon4j.instrument.snapshot.SnapShotGenerator;
import org.perfmon4j.instrument.snapshot.SnapShotGenerator.Bundle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JVMSnapShotTest extends TestCase {

	
	public static final String TEST_ALL_TEST_TYPE = "UNIT";

/*----------------------------------------------------------------------------*/
    public JVMSnapShotTest(String name) {
        super(name);
    }
    
    /*----------------------------------------------------------------------------*/    
    public void testValidateDataInterface() throws Exception {
    	Bundle bundle = SnapShotGenerator.generateBundle(JVMSnapShot.class);
    	SnapShotData data = bundle.newSnapShotData();
    	
    	assertTrue("Should implement interface", data instanceof 
    			JVMSnapShot.JVMData);
    	
    	JVMSnapShot.JVMData d = (JVMSnapShot.JVMData)data;
    	Method methods[] = JVMSnapShot.JVMData.class.getDeclaredMethods();
    	for (int i = 0; i < methods.length; i++) {
    		try {
    			methods[i].invoke(d, new Object[]{});
    		} catch (InvocationTargetException it) {
    			fail("Invalid method on data interface.  method name: " + methods[i].getName());
    		}
		}
    }
  
/*----------------------------------------------------------------------------*/    
    public static void main(String[] args) {
        BasicConfigurator.configure();
        Logger.getLogger(JVMSnapShotTest.class.getPackage().getName()).setLevel(Level.DEBUG);
        String[] testCaseName = {JVMSnapShotTest.class.getName()};

        TestRunner.main(testCaseName);
    }

/*----------------------------------------------------------------------------*/
    public static junit.framework.Test suite() {
        String testType = System.getProperty("UNIT");
        TestSuite newSuite = new TestSuite();

        // Here is where you can specify a list of specific tests to run.
        // If there are no tests specified, the entire suite will be set in the if
        // statement below.
//        newSuite.addTest(new GarbageCollectorSnapShotTest("testGetGarbageCollectors"));

        // Here we test if we are running testunit or testacceptance (testType will
        // be set) or if no test cases were added to the test suite above, then
        // we run the full suite of tests.
        if (testType != null || newSuite == null || (newSuite.countTestCases() < 1)) {
            newSuite = new TestSuite(JVMSnapShotTest.class);
        }

        return( newSuite);
    }
}
