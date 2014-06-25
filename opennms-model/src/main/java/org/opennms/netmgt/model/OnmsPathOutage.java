/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.model;

import java.net.InetAddress;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * <p>OnmsPathOutage class</p>
 * 
 * @author <a href="ryan@mail1.opennms.com"> Ryan Lambeth </a>
 */
@Entity
@Table(name="onmspathoutage")
public class OnmsPathOutage {
	
	private int m_nodeId;
	private InetAddress m_criticalPathIp;
	private String m_criticalPathServiceName;
	
	/**
	 * <p>Contructor for OnmsPathOutage</p>
	 * 
	 * @param an int
	 * @param an InetAddress
	 * @param a String
	 */
	public OnmsPathOutage(int nodeId, InetAddress criticalPathIp, String criticalPathServiceName) {
		m_nodeId = nodeId;
		m_criticalPathIp = criticalPathIp;
		m_criticalPathServiceName = criticalPathServiceName;
	}
	
	/**
	 * <p>Getter for field <code>m_nodeId</code>.</p>
	 * 
	 * @return an int
	 */
	@Id
	@Column(name="nodeid", nullable = false)
	public int getNodeId() {
		return m_nodeId;
	}
	
	/**
	 * <p>Setter for field <code>m_nodeId</code>.</p>
	 * 
	 * @param an int
	 */
	public void setNodeId(int nodeId) {
		m_nodeId = nodeId;
	}
	
	/**
	 * <p>Getter for field <code>m_criticalPathIp</code>.</p>
	 * 
	 * @return an InetAddress
	 */
	@Column(name="criticalpathip", nullable = false)
	public InetAddress getCriticalPathIp() {
		return m_criticalPathIp;
	}
	
	/**
	 * <p>Setter for field <code>m_criticalPathIp</code>.</p>
	 * 
	 * @param an InetAddress
	 */
	public void setCriticalPathIp(InetAddress criticalPathIp) {
		m_criticalPathIp = criticalPathIp;
	}
	
	/**
	 * <p>Getter for field <code>m_criticalPathServiceName</code>.</p>
	 * 
	 * @return a String
	 */
	@Column(name="criticalpathservicename")
	public String getCriticalPathServiceName() {
		return m_criticalPathServiceName;
	}
	
	/**
	 * <p>Setter for field <code>m_criticalPathServiceName</code>.</p>
	 * 
	 * @param a String
	 */
	public void setCriticalPathServiceName(String criticalPathServiceName) {
		m_criticalPathServiceName = criticalPathServiceName;
	}
	
}
