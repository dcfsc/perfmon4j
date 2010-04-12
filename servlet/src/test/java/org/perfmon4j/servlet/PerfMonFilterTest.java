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

package org.perfmon4j.servlet;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import junit.framework.TestCase;

import org.mockito.Mockito;
import org.perfmon4j.Appender;
import org.perfmon4j.PerfMon;
import org.perfmon4j.PerfMonConfiguration;
import org.perfmon4j.PerfMonData;
import org.perfmon4j.ThreadTraceConfig;
import org.perfmon4j.ThreadTraceData;

public class PerfMonFilterTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		PerfMon.deInit();
		super.tearDown();
	}

	private static class MyChain implements FilterChain {
		ThreadTraceConfig.TriggerValidator validators[] = null;
		private final boolean throwExcepton;
		
		MyChain(boolean throwException) {
			this.throwExcepton = throwException;
		}
		
		
		public void doFilter(ServletRequest arg0, ServletResponse arg1)
				throws IOException, ServletException {
			validators = ThreadTraceConfig.getValidatorsOnThread();
			if (throwExcepton) {
				throw new IOException("Bogus");
			}
		}
	}
	
	private MyChain runRequestThroughChain(boolean throwException) throws Exception {
		TestAppender.threadTraceOutputCount = 0;
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class); 
		HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
		MyChain chain = new MyChain(throwException);
		FilterConfig config = Mockito.mock(FilterConfig.class);
		
		PerfMonFilter filter = new PerfMonFilter();
		filter.init(config);
		
		try {
			filter.doFilter(request, response, chain);
			if (throwException) {
				fail("Expected exception to be throw");
			}
		} catch (Exception ex) {
			if (!throwException) {
				throw ex;
			}
		}

		return chain;
	}
	
	public static class TestAppender extends Appender {
		static int threadTraceOutputCount = 0;

		public TestAppender(AppenderID id) {
			super(id);
		}
		
		public void outputData(PerfMonData data) {
			if (data instanceof ThreadTraceData) {
				threadTraceOutputCount++;
				System.out.println("Ouput: " + threadTraceOutputCount  + data.toAppenderString());
			}
		}
	}
	
	
	public void testPerfMon4jNotActive() throws Exception {
		PerfMon.deInit();
		
		MyChain chain = runRequestThroughChain(false);
		assertEquals("Should not put any validators on the thread when we do not have active thread traces", 0, chain.validators.length);
	}

	
	private void configurePerfMon(ThreadTraceConfig traceConfig) throws Exception {
		PerfMonConfiguration config = new PerfMonConfiguration();
		config.defineAppender("Basic", TestAppender.class.getName(), "1 second");
		
		traceConfig.addAppender(config.getAppenderForName("Basic"));
		config.addThreadTraceConfig("WebRequest", traceConfig);
		
		PerfMon.configure(config);
	}
	
	public void testPerfMonActiveButNoThreadTraceWithTriggers() throws Exception {
		configurePerfMon(new ThreadTraceConfig());
		MyChain chain = runRequestThroughChain(false);
		
		assertEquals("Request should have created stack trace", 1, TestAppender.threadTraceOutputCount);
		assertEquals("Should not put any validators on the thread when we do not have active thread traces", 0, chain.validators.length);
	}

	
	public void testHttpRequestTriggerIsActive() throws Exception {
		ThreadTraceConfig traceConfig = new ThreadTraceConfig();
		ThreadTraceConfig.HTTPRequestTrigger t = new ThreadTraceConfig.HTTPRequestTrigger("bibID", "100");
		traceConfig.setTriggers(new ThreadTraceConfig.Trigger[]{t});
		configurePerfMon(traceConfig);
		
		MyChain chain = runRequestThroughChain(false);
		
		assertEquals("Request should NOT have created stack trace request DOES not match our trigger", 
				0, TestAppender.threadTraceOutputCount);
		assertEquals("Filter should have inserted validator on stack", 1, chain.validators.length);
		assertTrue("Should be request validator", chain.validators[0] instanceof PerfMonFilter.HttpRequestValidator);

		assertEquals("Filter must not leave validators on the thread", 0, ThreadTraceConfig.getValidatorsOnThread().length);
	}
	
	public void testHttpRequestTriggerIsValid() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getParameterValues("bibID")).thenReturn(new String[]{"100", "101", "102"});
		PerfMonFilter.HttpRequestValidator v = new PerfMonFilter.HttpRequestValidator(request);
		
		ThreadTraceConfig.HTTPRequestTrigger tMatch = new ThreadTraceConfig.HTTPRequestTrigger("bibID", "101");
		assertTrue("expected match for bibID=100",  v.isValid(tMatch));
		
		ThreadTraceConfig.HTTPRequestTrigger tNoMatch = new ThreadTraceConfig.HTTPRequestTrigger("bibID", "201");
		assertFalse("expected match for bibID=201",  v.isValid(tNoMatch));
	}

	public void testHttpSessionTriggerIsValid() {
		HttpSession session = Mockito.mock(HttpSession.class);
		Mockito.when(session.getAttribute("userID")).thenReturn("200");
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getSession(false)).thenReturn(session);
		
		PerfMonFilter.HttpSessionValidator v = new PerfMonFilter.HttpSessionValidator(request);
		
		ThreadTraceConfig.HTTPSessionTrigger tMatch = new ThreadTraceConfig.HTTPSessionTrigger("userID", "200");
		assertTrue("expected match for userID=200",  v.isValid(tMatch));
		
		ThreadTraceConfig.HTTPSessionTrigger tNoMatch = new ThreadTraceConfig.HTTPSessionTrigger("userID", "201");
		assertFalse("expected match for userID=201",  v.isValid(tNoMatch));
	}

	public void testHttpSessionTriggerIsActive() throws Exception {
		ThreadTraceConfig traceConfig = new ThreadTraceConfig();
		ThreadTraceConfig.HTTPSessionTrigger t1 = new ThreadTraceConfig.HTTPSessionTrigger("userID", "200");
		
		traceConfig.setTriggers(new ThreadTraceConfig.Trigger[]{t1});
		configurePerfMon(traceConfig);
		
		MyChain chain = runRequestThroughChain(false);
		
		assertEquals("Filter should have inserted validator on stack", 1, chain.validators.length);
		assertEquals("Filter must not leave validators on the thread", 0, ThreadTraceConfig.getValidatorsOnThread().length);
	}

	public void testTriggersAreCleanedUpOnException() throws Exception {
		ThreadTraceConfig traceConfig = new ThreadTraceConfig();
		ThreadTraceConfig.HTTPRequestTrigger t1 = new ThreadTraceConfig.HTTPRequestTrigger("bibID", "100");
		ThreadTraceConfig.HTTPSessionTrigger t2 = new ThreadTraceConfig.HTTPSessionTrigger("userID", "200");
		
		traceConfig.setTriggers(new ThreadTraceConfig.Trigger[]{t1, t2});
		
		configurePerfMon(traceConfig);
		
		MyChain chain = null;
		chain = runRequestThroughChain(true);
		
		assertEquals("Filter should have inserted validators on stack", 2, chain.validators.length);
		assertEquals("Filter must NOT leave validators on the thread", 0, ThreadTraceConfig.getValidatorsOnThread().length);
	}
	
	
	
	
}