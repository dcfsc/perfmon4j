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

package org.perfmon4j.remotemanagement.intf;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

public class MonitorDefinition implements Serializable {
	private static final long serialVersionUID = ManagementVersion.MAJOR_VERSION;	
	
	public static final Type INTERVAL_TYPE = new Type("INTERVAL");
	
	final private String name;
	final private MonitorDefinition.Type type;
	final List<FieldDefinition> fields;

	public MonitorDefinition(String name, MonitorDefinition.Type type, List<FieldDefinition> fields) {
		this.name = name;
		this.type = type;
		this.fields = fields;
	}

	public String getName() {
		return name;
	}

	public MonitorDefinition.Type getType() {
		return type;
	}
	
	public Iterator<FieldDefinition> getFieldItr() {
		return fields.iterator();
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "(name:" + name + ", type:" + type + ")";
	}

	public static String buildIntervalMonitorKey(String intervalMonitor) {
		return INTERVAL_TYPE.desc + ":" + intervalMonitor;
	}
	
	public static String getIntervalMonitorName(String monitorKey) {
		final String prefix = INTERVAL_TYPE.desc + ":";
		String result = null;
		
		if (monitorKey != null && 
				monitorKey.startsWith(prefix)
				&& monitorKey.length() > prefix.length()) {
			result = monitorKey.substring(prefix.length());
		}
		return result;
	}	
	
	public static final class Type implements Serializable {
		private static final long serialVersionUID = ManagementVersion.MAJOR_VERSION;
		
		final private String desc;
		
		private Type(String desc) {
			this.desc = desc;
		}

		public String getDesc() {
			return desc;
		}
		
		@Override
		public String toString() {
			return desc;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((desc == null) ? 0 : desc.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Type other = (Type) obj;
			if (desc == null) {
				if (other.desc != null)
					return false;
			} else if (!desc.equals(other.desc))
				return false;
			return true;
		}
	}
	
}
