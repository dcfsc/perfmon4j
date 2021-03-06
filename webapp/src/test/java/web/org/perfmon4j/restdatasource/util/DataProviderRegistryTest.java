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

package web.org.perfmon4j.restdatasource.util;


import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import javax.ws.rs.BadRequestException;

import junit.framework.TestCase;

import org.perfmon4j.RegisteredDatabaseConnections;
import org.perfmon4j.RegisteredDatabaseConnections.Database;

import web.org.perfmon4j.restdatasource.DataProvider;
import web.org.perfmon4j.restdatasource.DataSourceRestImpl.SystemID;
import web.org.perfmon4j.restdatasource.data.AggregationMethod;
import web.org.perfmon4j.restdatasource.data.Category;
import web.org.perfmon4j.restdatasource.data.CategoryTemplate;
import web.org.perfmon4j.restdatasource.data.Field;
import web.org.perfmon4j.restdatasource.data.MonitoredSystem;
import web.org.perfmon4j.restdatasource.data.query.advanced.ResultAccumulator;

public class DataProviderRegistryTest extends TestCase {

	private DataProviderRegistry registry;
	
	public DataProviderRegistryTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		
		registry = new DataProviderRegistry();
		registry.registerDataProvider(new TestDataProvider());
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testBasicResolve() {
		ParsedSeriesDefinition def = ParsedSeriesDefinition.parse("ABCD-EFGH.1~Person.student~shoeSize", "ABCD-EFGH")[0];
		
		SeriesField field = registry.resolveField(def, "series 1");
		assertNotNull(field);
		
		assertEquals("Should be the default aggregation method", AggregationMethod.AVERAGE,  field.getAggregationMethod());
		assertEquals("Category name", "Person.student", field.getCategory().getName());
		assertEquals("Category Template", "Person", field.getCategory().getTemplateName());
		assertEquals("Field name", "shoeSize", field.getField().getName());
		
		def = ParsedSeriesDefinition.parse("ABCD-EFGH.1~Person.student~hatSize", "ABCD-EFGH")[0];
		
		field = registry.resolveField(def, "series 1");
		assertNotNull(field);
		
		assertEquals("Should be the default aggregation method", AggregationMethod.SUM,  field.getAggregationMethod());
		assertEquals("Category name", "Person.student", field.getCategory().getName());
		assertEquals("Category Template", "Person", field.getCategory().getTemplateName());
		assertEquals("Field name", "hatSize", field.getField().getName());
	}
	

	public void testOverrideDefaultAggregationMethod() {
		ParsedSeriesDefinition def = ParsedSeriesDefinition.parse("MAX~ABCD-EFGH.1~Person.student~shoeSize", "ABCD-EFGH")[0];
		
		SeriesField field = registry.resolveField(def, "series 1");
		assertEquals("Should be the specified aggregation method", AggregationMethod.MAX,  field.getAggregationMethod());
	}

	public void testUnsupportedAggregationMethod() {
		ParsedSeriesDefinition def = ParsedSeriesDefinition.parse("MIN~ABCD-EFGH.1~Person.student~shoeSize", "ABCD-EFGH")[0];
		
		try {
			registry.resolveField(def, "series 1");
			fail("Expected an exception, MIN is not supported by field shoeSize");
		} catch (BadRequestException e) {
			assertEquals("Exception message", "Aggregation method MIN not supported for field shoeSize in Person category template", e.getMessage());
		}
	}

	public void testUnregisteredTemplate() {
		ParsedSeriesDefinition def = ParsedSeriesDefinition.parse("ABCD-EFGH.1~Pet.student~shoeSize", "ABCD-EFGH")[0];
		
		try {
			registry.resolveField(def, "series 1");
			fail("No template registered named Pet");
		} catch (BadRequestException e) {
			assertEquals("Exception message", "Category template Pet not found", e.getMessage());
		}
	}
	
	public void testInvalidField() {
		ParsedSeriesDefinition def = ParsedSeriesDefinition.parse("ABCD-EFGH.1~Person.student~height", "ABCD-EFGH")[0];	
		try {
			registry.resolveField(def, "series 1");
			fail("Template does not contain a height field");
		} catch (BadRequestException e) {
			assertEquals("Exception message", "Category template Person field height not found", e.getMessage());
		}
	}
	
	private static class TestDataProvider extends DataProvider {

		private final TestCategoryTemplate template = new TestCategoryTemplate();

		public TestDataProvider() {
			super("Person");
		}
		
		@Override
		public CategoryTemplate getCategoryTemplate() {
			return template;
		}

		@Override
		public void processResults(Connection conn, RegisteredDatabaseConnections.Database db, ResultAccumulator accumulator,
				SeriesField[] fields, long start, long end)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public Set<MonitoredSystem> lookupMonitoredSystems(Connection conn,
				Database database, long startTime, long endTime)
				throws SQLException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Set<Category> lookupMonitoredCategories(Connection conn,
				Database db, SystemID[] systems, long startTime, long endTime)
				throws SQLException {
			// TODO Auto-generated method stub
			return null;
		}
	}
	
	
	private static class TestCategoryTemplate extends CategoryTemplate {
		public TestCategoryTemplate() {
			super.setName("Person");
			
			Field shoeSize = new Field();
			shoeSize.setName("shoeSize");
			shoeSize.setAggregationMethods(new AggregationMethod[]{AggregationMethod.AVERAGE, AggregationMethod.MAX});
			shoeSize.setDefaultAggregationMethod(AggregationMethod.AVERAGE);

			Field hatSize = new Field();
			hatSize.setName("hatSize");
			hatSize.setAggregationMethods(new AggregationMethod[]{AggregationMethod.SUM, AggregationMethod.MIN});
			hatSize.setDefaultAggregationMethod(AggregationMethod.SUM);
			
			this.setFields(new Field[]{shoeSize, hatSize});
		}
	}
}
