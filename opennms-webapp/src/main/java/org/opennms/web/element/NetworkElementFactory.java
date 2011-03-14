//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2002-2009 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Modifications:
//
// 2009 Aug 28: Restore search and display capabilities for non-ip interfaces
// 2007 May 29: Add "id" for Interface model object. - dj@opennms.org
// 2003 Feb 05: Added ORDER BY to SQL statement.
//
// Orignal code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.opennms.com/
//

package org.opennms.web.element;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletContext;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.opennms.core.resource.Vault;
import org.opennms.core.utils.ByteArrayComparator;
import org.opennms.core.utils.DBUtils;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.LogUtils;
import org.opennms.netmgt.EventConstants;
import org.opennms.netmgt.dao.CategoryDao;
import org.opennms.netmgt.dao.DataLinkInterfaceDao;
import org.opennms.netmgt.dao.IpInterfaceDao;
import org.opennms.netmgt.dao.MonitoredServiceDao;
import org.opennms.netmgt.dao.NodeDao;
import org.opennms.netmgt.dao.ServiceTypeDao;
import org.opennms.netmgt.dao.SnmpInterfaceDao;
import org.opennms.netmgt.linkd.DbIpRouteInterfaceEntry;
import org.opennms.netmgt.linkd.DbStpInterfaceEntry;
import org.opennms.netmgt.linkd.DbStpNodeEntry;
import org.opennms.netmgt.linkd.DbVlanEntry;
import org.opennms.netmgt.model.OnmsArpInterface;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsCriteria;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsRestrictions;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.OnmsArpInterface.StatusType;
import org.opennms.netmgt.model.OnmsIpInterface.PrimaryType;
import org.opennms.web.api.Util;
import org.opennms.web.svclayer.AggregateStatus;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * The source for all network element business objects (nodes, interfaces,
 * services). Encapsulates all lookup functionality for the network element
 * business objects in one place.
 *
 * To use this factory to lookup network elements, you must first initialize the
 * Vault with the database connection manager * and JDBC URL it will use. Call
 * the init method to initialize the factory. After that, you can call any
 * lookup methods.
 *
 * @author <A HREF="larry@opennms.org">Larry Karnowski </A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS </A>
 */
@Transactional(readOnly=true)
public class NetworkElementFactory implements InitializingBean, NetworkElementFactoryInterface {
    
    //private static NetworkElementFactory s_networkElemFactory;
    
    @Autowired
    NodeDao m_nodeDao;
    
    @Autowired
    private IpInterfaceDao m_ipInterfaceDao;
    
    @Autowired
    private SnmpInterfaceDao m_snmpInterfaceDao;
    
    @Autowired
    private DataLinkInterfaceDao m_dataLinkInterfaceDao;

    @Autowired
    private MonitoredServiceDao m_monSvcDao;
    
    @Autowired
    private ServiceTypeDao m_serviceTypeDao;
    
    @Autowired
    private CategoryDao m_categoryDao;
    
    
    /**
     * A mapping of service names (strings) to service identifiers (integers).
     */
    protected Map<String, Integer> serviceName2IdMap;

    /**
     * A mapping of service identifiers (integers) to service names (strings).
     */
    protected Map<Integer, String> serviceId2NameMap;

    
    public static NetworkElementFactoryInterface getInstance(ServletContext servletContext) {
        return getInstance(WebApplicationContextUtils.getWebApplicationContext(servletContext));    
    }



    public static NetworkElementFactoryInterface getInstance(ApplicationContext appContext) {
    	return appContext.getBean(NetworkElementFactoryInterface.class);
    }

    private static final Comparator<Interface> INTERFACE_COMPARATOR = new InterfaceComparator();

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodeLabel(int)
	 */
    public String getNodeLabel(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.add(Restrictions.eq("id", nodeId));
        List<OnmsNode> nodes = m_nodeDao.findMatching(criteria);
        
        if(nodes.size() > 0) {
            OnmsNode node = nodes.get(0);
            return node.getLabel();
        }else {
            return null;
        }
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getIpPrimaryAddress(int)
	 */
    public String getIpPrimaryAddress(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.add(Restrictions.and(Restrictions.eq("node.id", nodeId), Restrictions.eq("isSnmpPrimary", PrimaryType.PRIMARY)));
        
        List<OnmsIpInterface> ifaces = m_ipInterfaceDao.findMatching(criteria);
        
        if(ifaces.size() > 0) {
            OnmsIpInterface iface = ifaces.get(0);
            return iface.getIpAddressAsString();
        }else{
            return null;
        }
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNode(int)
	 */
    public Node getNode(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.add(Restrictions.eq("id",nodeId));
        
        List<OnmsNode> nodes = m_nodeDao.findMatching(criteria);
        if(nodes.size() > 0 ) {
            OnmsNode onmsNode = nodes.get(0);
            return onmsNode2Node(onmsNode);
        }else {
            return null;
        }

    }

    

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllNodes()
	 */
    public Node[] getAllNodes() {
        OnmsCriteria criteria =  new OnmsCriteria(OnmsNode.class);
        criteria.add(Restrictions.or(Restrictions.isNull("type"), Restrictions.ne("type", "D")));
        criteria.addOrder(Order.asc("label"));
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }
    
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesLike(java.lang.String)
	 */
    public Node[] getNodesLike(String nodeLabel) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("assetRecord", "assetRecord");
        criteria.add(Restrictions.and(Restrictions.ilike("label", nodeLabel, MatchMode.ANYWHERE), Restrictions.or(Restrictions.isNull("type"), Restrictions.ne("type", "D"))));
        criteria.addOrder(Order.asc("label"));
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithIpLike(java.lang.String)
	 */
    public Node[] getNodesWithIpLike(String iplike) {
        if(iplike == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }
        OnmsCriteria nodeCrit = new OnmsCriteria(OnmsNode.class, "node");
        nodeCrit.createCriteria("ipInterfaces", "iface")
            .add(OnmsRestrictions.ipLike(iplike))
            .add(Restrictions.ne("isManaged", "D"));
        nodeCrit.add(Restrictions.ne("type", "D"));
        nodeCrit.addOrder(Order.asc("label"));
        nodeCrit.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(nodeCrit));
    }

    

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithService(int)
	 */
    public Node[] getNodesWithService(int serviceId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("assetRecord", "assetRecord");
        criteria.createAlias("ipInterfaces", "iface");
        criteria.createAlias("iface.monitoredServices", "svc");
        criteria.createAlias("svc.serviceType", "svcType").add(Restrictions.eq("svcType.id", serviceId));
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithPhysAddr(java.lang.String)
	 */
    public Node[] getNodesWithPhysAddr(String macAddr) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("assetRecord", "assetRecord");
        criteria.createAlias("snmpInterfaces", "snmpIfaces", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("arpInterfaces", "arpIfaces", OnmsCriteria.LEFT_JOIN);
        criteria.add(Restrictions.ne("type", "D"));
        criteria.add(
                Restrictions.or(
                        Restrictions.ilike("snmpIfaces.physAddr", macAddr, MatchMode.ANYWHERE),
                        Restrictions.ilike("arpIfaces.physAddr", macAddr, MatchMode.ANYWHERE))
                );
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        criteria.addOrder(Order.asc("label"));
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }


    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithPhysAddrAtInterface(java.lang.String)
	 */
    public Node[] getNodesWithPhysAddrAtInterface(String macAddr) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("arpInterfaces", "arpIfaces");
        criteria.add(Restrictions.ne("type", "D"));
        criteria.add(Restrictions.ilike("arpIfaces.physAddr", macAddr, MatchMode.ANYWHERE));
        criteria.addOrder(Order.asc("label"));
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithPhysAddrFromSnmpInterface(java.lang.String)
	 */
    public Node[] getNodesWithPhysAddrFromSnmpInterface(String macAddr) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("snmpInterfaces", "snmpIface");
        criteria.add(Restrictions.ne("type", "D"));
        criteria.add(Restrictions.ilike("snmpIface.physAddr", macAddr, MatchMode.ANYWHERE));
        criteria.addOrder(Order.asc("label"));
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithIfAlias(java.lang.String)
	 */
    public Node[] getNodesWithIfAlias(String ifAlias) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("snmpInterfaces", "snmpIface");
        criteria.add(Restrictions.ne("type", "D"));
        criteria.add(Restrictions.ilike("snmpIface.ifAlias", ifAlias, MatchMode.ANYWHERE));
        criteria.addOrder(Order.asc("label"));
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getHostname(java.lang.String)
	 */
    public String getHostname(String ipAddress) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.add(Restrictions.eq("ipAddress", ipAddress));
        criteria.add(Restrictions.isNotNull("ipHostName"));
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        List<OnmsIpInterface> ipIfaces = m_ipInterfaceDao.findMatching(criteria);
        
        if(ipIfaces.size() > 0) {
            OnmsIpInterface iface = ipIfaces.get(0);
            return iface.getIpHostName();
        }
        
        return null;
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getInterface(int)
	 */
    public Interface getInterface(int ipInterfaceId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.add(Restrictions.eq("id", ipInterfaceId));
        criteria.setFetchMode("snmpInterface", FetchMode.JOIN);
        
        List<OnmsIpInterface> ifaces = m_ipInterfaceDao.findMatching(criteria);
        
        if(ifaces.size() > 0) {
            return onmsIpInterface2Interface(ifaces.get(0));
        }
        
        return null;
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getInterface(int, java.lang.String)
	 */
    public Interface getInterface(int nodeId, String ipAddress) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("node", "node");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.eq("ipAddress", ipAddress));
        criteria.setFetchMode("snmpInterface", FetchMode.JOIN);
        
        List<OnmsIpInterface> ifaces = m_ipInterfaceDao.findMatching(criteria);
        return ifaces.size() > 0 ? onmsIpInterface2Interface(ifaces.get(0)) : null;
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getInterface(int, java.lang.String, int)
	 */
    public Interface getInterface(int nodeId, String ipAddress, int ifIndex) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("node", "node");
        criteria.createAlias("snmpInterface", "snmpIface");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.eq("ipAddress", ipAddress));
        criteria.add(Restrictions.eq("snmpIface.ifIndex", ifIndex));
        
        List<OnmsIpInterface> ifaces = m_ipInterfaceDao.findMatching(criteria);
        
        return ifaces.size() > 0 ? onmsIpInterface2Interface(ifaces.get(0)) : null;
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getSnmpInterface(int, int)
	 */
    public Interface getSnmpInterface(int nodeId, int ifIndex) {
        OnmsCriteria criteria  = new OnmsCriteria(OnmsSnmpInterface.class);
        criteria.createAlias("node", "node");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.eq("ifIndex", ifIndex));
        
        List<OnmsSnmpInterface> snmpIfaces = m_snmpInterfaceDao.findMatching(criteria);
        if(snmpIfaces.size() > 0) {
            return onmsSnmpInterface2Interface(snmpIfaces.get(0));
        }
        return null;
    }
    

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getInterfacesWithIpAddress(java.lang.String)
	 */
    public Interface[] getInterfacesWithIpAddress(String ipAddress) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("node", "node");
        criteria.createAlias("snmpInterface", "snmpIface");
        criteria.add(Restrictions.eq("ipAddress", ipAddress));
        
        
        return onmsIpInterfaces2InterfaceArray(m_ipInterfaceDao.findMatching(criteria));
    }

    

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getInterfacesWithIfAlias(int, java.lang.String)
	 */
    public Interface[] getInterfacesWithIfAlias(int nodeId, String ifAlias) {
        
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("node", "node");
        criteria.createAlias("snmpInterface", "snmpIface");
        criteria.createAlias("node.assetRecord", "assetRecord");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.ilike("snmpIface.ifAlias", ifAlias, MatchMode.ANYWHERE));
        criteria.add(Restrictions.ne("isManaged", "D"));
        
        return onmsIpInterfaces2InterfaceArray(m_ipInterfaceDao.findMatching(criteria));
    }
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#nodeHasIfAliases(int)
	 */
    public boolean nodeHasIfAliases(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("node", "node");
        criteria.createAlias("node.assetRecord", "assetRecord");
        criteria.createAlias("snmpInterface", "snmpIface");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.ilike("snmpIface.ifAlias", "_", MatchMode.ANYWHERE));
        
        List<OnmsIpInterface> ifaces = m_ipInterfaceDao.findMatching(criteria);
        if(ifaces.size() > 0) {
            return true;
        }else {
            return false;
        }
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllInterfacesOnNode(int)
	 */
    public Interface[] getAllInterfacesOnNode(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("node", "node");
        criteria.createAlias("snmpInterface", "snmpIface");
        criteria.createAlias("node.assetRecord", "assetRecord");
        criteria.add(Restrictions.eq("node.id", nodeId));
        
        return onmsIpInterfaces2InterfaceArray(m_ipInterfaceDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllSnmpInterfacesOnNode(int)
	 */
    public Interface[] getAllSnmpInterfacesOnNode(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsSnmpInterface.class);
        criteria.createAlias("node", "node");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.addOrder(Order.asc("ifIndex"));
        
        return onmsSnmpInterfaces2InterfaceArray(m_snmpInterfaceDao.findMatching(criteria));
    }

    private Interface[] onmsSnmpInterfaces2InterfaceArray(
            List<OnmsSnmpInterface> snmpIfaces) {
        List<Interface> intfs = new LinkedList<Interface>();
        
        for(OnmsSnmpInterface snmpIface : snmpIfaces) {
            intfs.add(onmsSnmpInterface2Interface(snmpIface));
        }
        
        return intfs.toArray(new Interface[intfs.size()]);
    }
    
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getActiveInterfacesOnNode(int)
	 */
    
    public Interface[] getActiveInterfacesOnNode(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("node", "node");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.ne("isManaged", "D"));
        
        return onmsIpInterfaces2InterfaceArray(m_ipInterfaceDao.findMatching(criteria));
    }

    /*
     * Returns all interfaces, including their SNMP information
     */
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllInterfaces()
	 */
    public Interface[] getAllInterfaces() {
        return getAllInterfaces(true);
    }

    /*
     * Returns all interfaces, but only includes snmp data if includeSNMP is true
     * This may be useful for pages that don't need snmp data and don't want to execute
     * a sub-query per interface!
     *
     * @param includeSNMP a boolean.
     * @return an array of {@link org.opennms.web.element.Interface} objects.
     */
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllInterfaces(boolean)
	 */
    public Interface[] getAllInterfaces(boolean includeSnmp) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("snmpInterface", "snmpInterface", OnmsCriteria.LEFT_JOIN);
        if(!includeSnmp) {
            return onmsIpInterfaces2InterfaceArray(m_ipInterfaceDao.findMatching(criteria));
        }else {
            return onmsIpInterfaces2InterfaceArrayWithSnmpData(m_ipInterfaceDao.findMatching(criteria));
        }
        
        
    }


    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllManagedIpInterfaces(boolean)
	 */
    public Interface[] getAllManagedIpInterfaces(boolean includeSNMP) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsIpInterface.class);
        criteria.createAlias("snmpInterface", "snmpInterface", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("node", "node");
        criteria.add(Restrictions.ne("isManaged", "D"));
        criteria.add(Restrictions.ne("ipAddress", "0.0.0.0"));
        criteria.add(Restrictions.isNotNull("ipAddress"));
        criteria.addOrder(Order.asc("ipHostName"));
        criteria.addOrder(Order.asc("node.id"));
        criteria.addOrder(Order.asc("ipAddress"));
        
        if(!includeSNMP) {
            return onmsIpInterfaces2InterfaceArray(m_ipInterfaceDao.findMatching(criteria));
        }else {
            return onmsIpInterfaces2InterfaceArrayWithSnmpData(m_ipInterfaceDao.findMatching(criteria));
        }
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getService(int, java.lang.String, int)
	 */
    public Service getService(int nodeId, String ipAddress, int serviceId) {
        if (ipAddress == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }
        OnmsCriteria criteria = new OnmsCriteria(OnmsMonitoredService.class);
        criteria.createAlias("ipInterface", "ipInterface", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("ipInterface.node", "node", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("serviceType", "serviceType", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("ipInterface.snmpInterface", "snmpIface", OnmsCriteria.LEFT_JOIN);
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.eq("ipInterface.ipAddress", ipAddress));
        criteria.add(Restrictions.eq("serviceType.id", serviceId));
        
        List<OnmsMonitoredService> monSvcs = m_monSvcDao.findMatching(criteria);
        if(monSvcs.size() > 0) {
            return onmsMonitoredService2Service(monSvcs.get(0));
        }else {
            return null;
        }
        
    }
    
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getService(int)
	 */
    public Service getService(int ifServiceId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsMonitoredService.class);
        criteria.createAlias("ipInterface", "ipInterface", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("ipInterface.node", "node", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("ipInterface.snmpInterface", "snmpIface",  OnmsCriteria.LEFT_JOIN);
        criteria.add(Restrictions.eq("id", ifServiceId));
        criteria.addOrder(Order.asc("status"));
        
        List<OnmsMonitoredService> monSvcs = m_monSvcDao.findMatching(criteria);
        
        if(monSvcs.size() > 0) {
            return onmsMonitoredService2Service(monSvcs.get(0));
        }else {
            return null;
        }
        
        
    }
    

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllServices()
	 */
    public Service[] getAllServices() {
        return onmsMonitoredServices2ServiceArray(m_monSvcDao.findAll());
    }

    

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getServicesOnInterface(int, java.lang.String)
	 */
    public Service[] getServicesOnInterface(int nodeId, String ipAddress) {
        return getServicesOnInterface(nodeId, ipAddress, false);
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getServicesOnInterface(int, java.lang.String, boolean)
	 */
    public Service[] getServicesOnInterface(int nodeId, String ipAddress, boolean includeDeletions) {
        if (ipAddress == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }
        
        OnmsCriteria criteria = new OnmsCriteria(OnmsMonitoredService.class);
        criteria.createAlias("ipInterface", "ipInterface");
        criteria.createAlias("ipInterface.node", "node");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.eq("ipInterface.ipAddress", ipAddress));
        
        if(!includeDeletions) {
            criteria.add(Restrictions.ne("status", "D"));
        }
        
        return onmsMonitoredServices2ServiceArray(m_monSvcDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getServicesOnNode(int)
	 */
    public Service[] getServicesOnNode(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsMonitoredService.class);
        criteria.createAlias("ipInterface", "ipInterface");
        criteria.createAlias("ipInterface.snmpInterface", "snmpIface", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("ipInterface.node", "node");
        criteria.createAlias("serviceType", "serviceType");
        criteria.add(Restrictions.eq("node.id", nodeId));
        
        return onmsMonitoredServices2ServiceArray(m_monSvcDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getServicesOnNode(int, int)
	 */
    public Service[] getServicesOnNode(int nodeId, int serviceId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsMonitoredService.class);
        criteria.createAlias("ipInterface", "ipInterface");
        criteria.createAlias("ipInterface.node", "node");
        criteria.createAlias("ipInterface.snmpInterface", "snmpInterface", OnmsCriteria.LEFT_JOIN);
        criteria.createAlias("serviceType", "serviceType");
        criteria.add(Restrictions.eq("node.id", nodeId));
        criteria.add(Restrictions.eq("serviceType.id", serviceId));
        
        return onmsMonitoredServices2ServiceArray(m_monSvcDao.findMatching(criteria));
    }
    
    /**
     * This method returns the data from the result set as an array of Node
     * objects.
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.Node} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static Node[] rs2Nodes(ResultSet rs) throws SQLException {
        if (rs == null) {
            throw new IllegalArgumentException("rs parameter cannot be null");
        }

        List<Node> nodes = new LinkedList<Node>();

        while (rs.next()) {
            Node node = new Node();

            node.m_nodeId = rs.getInt("nodeId");
            node.m_dpname = rs.getString("dpName");

            Timestamp timestamp = rs.getTimestamp("nodeCreateTime");
            if (timestamp != null) {
                node.m_nodeCreateTime = Util.formatDateToUIString(new Date((timestamp).getTime()));
            }
            
            Integer nodeParentID = rs.getInt("nodeParentID");
            if (nodeParentID != null) {
                node.m_nodeParent = nodeParentID.intValue();
            }

            String nodeType = rs.getString("nodeType");
            if (nodeType != null) {
                node.m_nodeType = nodeType.charAt(0);
            }

            node.m_nodeSysId = rs.getString("nodeSysOID");
            node.m_nodeSysName = rs.getString("nodeSysName");
            node.m_nodeSysDescr = rs.getString("nodeSysDescription");
            node.m_nodeSysLocn = rs.getString("nodeSysLocation");
            node.m_nodeSysContact = rs.getString("nodeSysContact");
            node.m_label = rs.getString("nodelabel");
            node.m_operatingSystem = rs.getString("operatingsystem");
            node.m_foreignSource = rs.getString("foreignSource");
            node.m_foreignId = rs.getString("foreignId");

            nodes.add(node);
        }
        
        return nodes.toArray(new Node[nodes.size()]);
    }
    
    private Node onmsNode2Node(OnmsNode onmsNode) {
        Node node = new Node();
        node.m_nodeId = onmsNode.getId();
        
        if(onmsNode.getDistPoller() != null) {
            node.m_dpname = onmsNode.getDistPoller().getName();
        }
        
        if(onmsNode.getCreateTime() != null) {
            node.m_nodeCreateTime = Util.formatDateToUIString(onmsNode.getCreateTime());
        }
        
        if(onmsNode.getParent() != null) {
            node.m_nodeParent = onmsNode.getParent().getId();
        }
        
        if(onmsNode.getType() != null) {
            node.m_nodeType = onmsNode.getType().charAt(0);
        }
    
        node.m_nodeSysId = onmsNode.getSysObjectId();
        node.m_nodeSysName = onmsNode.getSysName();
        node.m_nodeSysDescr = onmsNode.getSysDescription();
        node.m_nodeSysLocn = onmsNode.getSysLocation();
        node.m_nodeSysContact = onmsNode.getSysContact();
        node.m_label = onmsNode.getLabel();
        node.m_operatingSystem = onmsNode.getOperatingSystem();
        node.m_foreignSource = onmsNode.getForeignSource();
        node.m_foreignId = onmsNode.getForeignId();
        
        
        return node;
    }

    public AtInterface getAtInterfaceForOnmsNode(final OnmsNode onmsNode, final String ipAddr) {
        for (final OnmsArpInterface iface : onmsNode.getArpInterfaces()) {
            final String ifaceAddress = iface.getIpAddress();
            if (ifaceAddress != null && ifaceAddress.equals(ipAddr)) {
                return new AtInterface(onmsNode.getId(), iface.getSourceNode().getId(), iface.getIfIndex(), iface.getIpAddress(), iface.getPhysAddr(), iface.getLastPoll().toString(), iface.getStatus().getCharCode());
            }
        }
        return null;
    }
    
    /**
     * This method returns the data from the result set as an vector of
     * ipinterface objects.
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.Interface} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static Interface[] rs2Interfaces(ResultSet rs) throws SQLException {
        List<Interface> intfs = new ArrayList<Interface>();

        while (rs.next()) {
            
            Object element = null;
            Interface intf = new Interface();

            intf.m_id = rs.getInt("id");
            intf.m_nodeId = rs.getInt("nodeid");
            intf.m_ifIndex = rs.getInt("ifIndex");
            intf.m_ipStatus = rs.getInt("ipStatus");
            intf.m_ipHostName = rs.getString("ipHostname");
            intf.m_ipAddr = rs.getString("ipAddr");
            
            element = rs.getString("isManaged");
            if (element != null) {
                intf.m_isManaged = ((String) element).charAt(0);
            }

            element = rs.getTimestamp("ipLastCapsdPoll");
            if (element != null) {
                intf.m_ipLastCapsdPoll = Util.formatDateToUIString(new Date(((Timestamp) element).getTime()));
            }

            intfs.add(intf);
        }

        Collections.sort(intfs, INTERFACE_COMPARATOR);
        return intfs.toArray(new Interface[intfs.size()]);

    }

    /**
     * This method returns the data from the result set as an vector of
     * interface objects for non-ip interfaces.
     *
     * @return Interface[]
     * @throws java.sql.SQLException if any.
     * @param rs a {@link java.sql.ResultSet} object.
     */
    protected static Interface[] rs2SnmpInterfaces(ResultSet rs) throws SQLException {
        List<Interface> intfs = new ArrayList<Interface>();

        while (rs.next()) {
            
            Interface intf = new Interface();

            intf.m_nodeId = rs.getInt("nodeid");
            intf.m_ipAddr = rs.getString("ipaddr");
            intf.m_snmpIfIndex = rs.getInt("snmpifindex");
            intf.m_snmpIpAdEntNetMask = rs.getString("snmpIpAdEntNetMask");
            intf.m_snmpPhysAddr = rs.getString("snmpPhysAddr");
            intf.m_snmpIfDescr = rs.getString("snmpIfDescr");
            intf.m_snmpIfName = rs.getString("snmpIfName");
            intf.m_snmpIfType = rs.getInt("snmpIfType");
            intf.m_snmpIfOperStatus = rs.getInt("snmpIfOperStatus");
            intf.m_snmpIfSpeed = rs.getLong("snmpIfSpeed");
            intf.m_snmpIfAdminStatus = rs.getInt("snmpIfAdminStatus");
            intf.m_snmpIfAlias = rs.getString("snmpIfAlias");
            
            Object element = rs.getString("snmpPoll");
            if (element != null) {
                intf.m_isSnmpPoll = ((String) element).charAt(0);
            }

            element = rs.getTimestamp("snmpLastCapsdPoll");
            if (element != null) {
                intf.m_snmpLastCapsdPoll = Util.formatDateToUIString(new Date(((Timestamp) element).getTime()));
            }

            element = rs.getTimestamp("snmpLastSnmpPoll");
            if (element != null) {
                intf.m_snmpLastSnmpPoll = Util.formatDateToUIString(new Date(((Timestamp) element).getTime()));
            }

            intfs.add(intf);
        }

        Collections.sort(intfs, INTERFACE_COMPARATOR);
        return intfs.toArray(new Interface[intfs.size()]);

    }

    
    /**
     * <p>augmentInterfacesWithSnmpData</p>
     *
     * @param intfs an array of {@link org.opennms.web.element.Interface} objects.
     * @param conn a {@link java.sql.Connection} object.
     * @throws java.sql.SQLException if any.
     */
    protected static void augmentInterfacesWithSnmpData(Interface[] intfs, Connection conn) throws SQLException {
        if (intfs == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        for (int i = 0; i < intfs.length; i++) {
            if (intfs[i].getIfIndex() != 0) {
                PreparedStatement pstmt;
                ResultSet rs;
                try {
                    pstmt = conn.prepareStatement("SELECT * FROM SNMPINTERFACE WHERE NODEID=? AND SNMPIFINDEX=?");
                    d.watch(pstmt);
                    pstmt.setInt(1, intfs[i].getNodeId());
                    pstmt.setInt(2, intfs[i].getIfIndex());

                    rs = pstmt.executeQuery();
                    d.watch(rs);
                    
                    if (rs.next()) {
                        intfs[i].m_snmpIfIndex = rs.getInt("snmpifindex");
                        intfs[i].m_snmpIpAdEntNetMask = rs.getString("snmpIpAdEntNetMask");
                        intfs[i].m_snmpPhysAddr = rs.getString("snmpPhysAddr");
                        intfs[i].m_snmpIfDescr = rs.getString("snmpIfDescr");
                        intfs[i].m_snmpIfName = rs.getString("snmpIfName");
                        intfs[i].m_snmpIfType = rs.getInt("snmpIfType");
                        intfs[i].m_snmpIfOperStatus = rs.getInt("snmpIfOperStatus");
                        intfs[i].m_snmpIfSpeed = rs.getLong("snmpIfSpeed");
                        intfs[i].m_snmpIfAdminStatus = rs.getInt("snmpIfAdminStatus");
                        intfs[i].m_snmpIfAlias = rs.getString("snmpIfAlias");
                        
                        Object element = rs.getString("snmpPoll");
                        if (element != null) {
                            intfs[i].m_isSnmpPoll = ((String) element).charAt(0);
                        }

                        element = rs.getTimestamp("snmpLastCapsdPoll");
                        if (element != null) {
                            intfs[i].m_snmpLastCapsdPoll = Util.formatDateToUIString(new Date(((Timestamp) element).getTime()));
                        }

                        element = rs.getTimestamp("snmpLastSnmpPoll");
                        if (element != null) {
                            intfs[i].m_snmpLastSnmpPoll = Util.formatDateToUIString(new Date(((Timestamp) element).getTime()));
                        }

                    }

                    pstmt = conn.prepareStatement("SELECT issnmpprimary FROM ipinterface WHERE nodeid=? AND ifindex=? AND ipaddr=?");
                    d.watch(pstmt);
                    pstmt.setInt(1, intfs[i].getNodeId());
                    pstmt.setInt(2, intfs[i].getIfIndex());
                    pstmt.setString(3, intfs[i].getIpAddress());

                    rs = pstmt.executeQuery();
                    d.watch(rs);

                    if (rs.next()) {
                        intfs[i].m_isSnmpPrimary = rs.getString("issnmpprimary");
                    }
                } finally {
                    d.cleanUp();
                }
            }
        }
    }
    

    /**
     * <p>rs2Services</p>
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.Service} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static Service[] rs2Services(ResultSet rs) throws SQLException {
        List<Service> services = new ArrayList<Service>();

        while (rs.next()) {
            Service service = new Service();

            Object element = null;
            
            service.setId(rs.getInt("id"));
            service.setNodeId(rs.getInt("nodeid"));
            service.setIpAddress(rs.getString("ipaddr"));

            element = rs.getTimestamp("lastgood");
            if (element != null) {
                service.setLastGood(Util.formatDateToUIString(new Date(((Timestamp) element).getTime())));
            }

            service.setServiceId(rs.getInt("serviceid"));
            service.setServiceName(rs.getString("servicename"));

            element = rs.getTimestamp("lastfail");
            if (element != null) {
                service.setLastFail(Util.formatDateToUIString(new Date(((Timestamp) element).getTime())));
            }

            service.setNotify(rs.getString("notify"));

            element = rs.getString("status");
            if (element != null) {
                service.setStatus(((String) element).charAt(0));
            }

            services.add(service);
        }

        return services.toArray(new Service[services.size()]);
    }
    
    private Service[] onmsMonitoredServices2ServiceArray(List<OnmsMonitoredService> monSvcs) {
        List<Service> svcs = new LinkedList<Service>();
        for(OnmsMonitoredService monSvc : monSvcs) {
            Service service = onmsMonitoredService2Service(monSvc);
            
            svcs.add(service);
        }
        
        
        return svcs.toArray(new Service[svcs.size()]);
    }

    private Service onmsMonitoredService2Service(OnmsMonitoredService monSvc) {
        Service service = new Service();
        service.setId(monSvc.getId());
        service.setNodeId(monSvc.getNodeId());
        
        service.setIpAddress(monSvc.getIpAddressAsString());
        service.setServiceId(monSvc.getServiceId());
        service.setServiceName(monSvc.getServiceName());
        if(monSvc.getLastGood() != null) {
            service.setLastGood(monSvc.getLastGood().toString());
        }
        if(monSvc.getLastFail() != null) {
            service.setLastFail(monSvc.getLastFail().toString());
        }
        service.setNotify(monSvc.getNotify());
        if(monSvc.getStatus() != null) {
            service.setStatus(monSvc.getStatus().charAt(0));
        }
        return service;
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getServiceNameFromId(int)
	 */
    public String getServiceNameFromId(int serviceId) {
        if (serviceId2NameMap == null) {
            createServiceIdNameMaps();
        }

        String serviceName = serviceId2NameMap.get(new Integer(serviceId));

        return (serviceName);
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getServiceIdFromName(java.lang.String)
	 */
    public int getServiceIdFromName(String serviceName) {
        if (serviceName == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        int serviceId = -1;

        if (serviceName2IdMap == null) {
            createServiceIdNameMaps();
        }

        Integer value = serviceName2IdMap.get(serviceName);

        if (value != null) {
            serviceId = value.intValue();
        }

        return (serviceId);
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getServiceIdToNameMap()
	 */
    public Map<Integer, String> getServiceIdToNameMap(){
        if (serviceId2NameMap == null) {
            createServiceIdNameMaps();
        }

        return (new HashMap<Integer, String>(serviceId2NameMap));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getServiceNameToIdMap()
	 */
    public Map<String, Integer> getServiceNameToIdMap(){
        if (serviceName2IdMap == null) {
            createServiceIdNameMaps();
        }

        return (new HashMap<String, Integer>(serviceName2IdMap));
    }

    protected void createServiceIdNameMaps() {
        HashMap<Integer, String> idMap = new HashMap<Integer, String>();
        HashMap<String, Integer> nameMap = new HashMap<String, Integer>();
        
        List<OnmsServiceType> services = getServiceTypeDao().findAll();
        for(OnmsServiceType servType : services) {
            idMap.put(servType.getId(), servType.getName());
            nameMap.put(servType.getName(), servType.getId());
        }

        serviceId2NameMap = idMap;
        serviceName2IdMap = nameMap;
    }
    
    

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesLikeAndIpLike(java.lang.String, java.lang.String, int)
	 */
    public Node[] getNodesLikeAndIpLike(String nodeLabel, String iplike, int serviceId) {
        if (nodeLabel == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }
        OnmsCriteria nodeCrit = new OnmsCriteria(OnmsNode.class);
        nodeCrit.createAlias("assetRecord", "assetRecord");
        nodeCrit.add(Restrictions.ilike("label", nodeLabel));
        nodeCrit.createCriteria("ipInterfaces")
            .add(OnmsRestrictions.ipLike(iplike))
            .createAlias("monitoredServices", "monSvcs")
            .createAlias("monSvcs.serviceType", "serviceType")
            .add(Restrictions.eq("serviceType.id", serviceId));
        nodeCrit.addOrder(Order.asc("label"));
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(nodeCrit));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesLike(java.lang.String, int)
	 */
    public Node[] getNodesLike(String nodeLabel, int serviceId) {
        if (nodeLabel == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("assetRecord", "assetRecord");
        criteria.createAlias("ipInterfaces", "iface");
        criteria.createAlias("iface.monitoredServices", "monSvcs");
        criteria.createAlias("monSvcs.serviceType", "serviceType");
        criteria.add(Restrictions.ilike("label", nodeLabel, MatchMode.ANYWHERE));
        criteria.add(Restrictions.eq("serviceType.id", serviceId));
        criteria.add(Restrictions.ne("type", "D"));
        criteria.addOrder(Order.asc("label"));
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithIpLike(java.lang.String, int)
	 */
    public Node[] getNodesWithIpLike(String iplike, int serviceId) {
        if (iplike == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        OnmsCriteria nodeCrit = new OnmsCriteria(OnmsNode.class);
        nodeCrit.createAlias("assetRecord", "assetRecord");
        nodeCrit.createCriteria("ipInterfaces", "iface")
            .createAlias("monitoredServices", "monSvcs")
            .createAlias("monSvcs.serviceType", "serviceType")
            .add(OnmsRestrictions.ipLike(iplike))
            .add(Restrictions.eq("serviceType.id", serviceId));
        nodeCrit.add(Restrictions.ne("type", "D"));
        nodeCrit.addOrder(Order.asc("label"));
        nodeCrit.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(nodeCrit));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllNodes(int)
	 */
    public Node[] getAllNodes(int serviceId) {
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("ipInterfaces", "ipInterfaces");
        criteria.createAlias("ipInterfaces.monitoredServices", "monSvcs");
        criteria.add(Restrictions.ne("type", "D"));
        criteria.add(Restrictions.eq("monSvcs.serviceType.id", serviceId));
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesFromPhysaddr(java.lang.String)
	 */
    public Node[] getNodesFromPhysaddr(String AtPhysAddr) {
        if (AtPhysAddr == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }
        OnmsCriteria criteria = new OnmsCriteria(OnmsNode.class);
        criteria.createAlias("assetRecord", "assetRecord");
        criteria.createAlias("arpInterfaces", "arpInterfaces");
        criteria.add(Restrictions.ilike("arpInterfaces.physAddr", AtPhysAddr, MatchMode.ANYWHERE));
        criteria.add(Restrictions.ne("arpInterfaces.status", StatusType.DELETED));
        criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        return onmsNodes2Nodes(m_nodeDao.findMatching(criteria));
    }
    

    /**
     * <p>getAtInterface</p>
     *
     * @param nodeID a int.
     * @param ipaddr a {@link java.lang.String} object.
     * @return a {@link org.opennms.web.element.AtInterface} object.
     * @throws java.sql.SQLException if any.
     */
    public AtInterface getAtInterface(int nodeID, String ipaddr, ServletContext servletContext)
            throws SQLException {

        if (ipaddr == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        AtInterface[] nodes = null;
        AtInterface node = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM ATINTERFACE WHERE NODEID = ? AND IPADDR = ? AND STATUS != 'D'");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            stmt.setString(2, ipaddr);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
            
            nodes = rs2AtInterface(rs);
        } finally {
            d.cleanUp();
        }
        if (nodes.length > 0) {
            return nodes[0];
        }
        return node;
    }
    
    /* (non-Javadoc)
     * @see org.opennms.web.element.NetworkElementFactoryInterface#getAtInterface(int, java.lang.String)
     */
    public AtInterface getAtInterface(int nodeId, String ipAddr) {
        OnmsNode node = m_nodeDao.get(nodeId);
        return getAtInterfaceForOnmsNode(node, ipAddr);
    }

    /**
     * <p>getIpRoute</p>
     *
     * @param nodeID a int.
     * @return an array of {@link org.opennms.web.element.IpRouteInterface} objects.
     * @throws java.sql.SQLException if any.
     */
    public IpRouteInterface[] getIpRoute(int nodeID, ServletContext servletContext) throws SQLException {

        IpRouteInterface[] nodes = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM IPROUTEINTERFACE WHERE NODEID = ? AND STATUS != 'D' ORDER BY ROUTEDEST");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
            
            nodes = rs2IpRouteInterface(rs);
        } finally {
            d.cleanUp();
        }

        return nodes;
    }
    
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getIpRoute(int)
	 */
    public IpRouteInterface[] getIpRoute(final int nodeId) {
        try {
            return getIpRoute(nodeId, null);
        } catch (final SQLException e) {
            LogUtils.warnf(this, e, "An error occurred getting the IP Route for node %d", nodeId);
            return null;
        }
    }

    /**
     * <p>getIpRoute</p>
     *
     * @param nodeID a int.
     * @param ifindex a int.
     * @return an array of {@link org.opennms.web.element.IpRouteInterface} objects.
     * @throws java.sql.SQLException if any.
     */
    public IpRouteInterface[] getIpRoute(int nodeID, int ifindex)
            throws SQLException {

        IpRouteInterface[] nodes = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM IPROUTEINTERFACE WHERE NODEID = ? AND ROUTEIFINDEX = ? AND STATUS != 'D' ORDER BY ROUTEDEST");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            stmt.setInt(2, ifindex);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
            
            nodes = rs2IpRouteInterface(rs);
        } finally {
            d.cleanUp();
        }

        return nodes;
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#isParentNode(int)
	 */
    public boolean isParentNode(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(org.opennms.netmgt.model.DataLinkInterface.class);
        criteria.add(Restrictions.eq("nodeParentId", nodeId));
        criteria.add(Restrictions.ne("status", "D"));
        
        List<org.opennms.netmgt.model.DataLinkInterface> dlis = m_dataLinkInterfaceDao.findMatching(criteria);
        
        if(dlis.size() > 0) {
            return true;
        }else {
            return false;
        }
        
    }

    /**
     * <p>isBridgeNode</p>
     *
     * @param nodeID a int.
     * @return a boolean.
     * @throws java.sql.SQLException if any.
     */
    public boolean isBridgeNode(int nodeID) throws SQLException {

        boolean isPN = false;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM STPNODE WHERE NODEID = ? AND STATUS != 'D' ");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
            rs.next();
            int count = rs.getInt(1);

            if (count > 0) {
                isPN = true;
            }
        } finally {
            d.cleanUp();
        }

        return isPN;
    }

    /**
     * <p>isRouteInfoNode</p>
     *
     * @param nodeID a int.
     * @return a boolean.
     * @throws java.sql.SQLException if any.
     */
    public boolean isRouteInfoNode(int nodeID) throws SQLException {

        boolean isRI = false;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM IPROUTEINTERFACE WHERE NODEID = ? AND STATUS != 'D' ");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
            
            rs.next();
            int count = rs.getInt(1);

            if (count > 0) {
                isRI = true;
            }
        } finally {
            d.cleanUp();
        }

        return isRI;
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getDataLinksOnNode(int)
	 */
    public DataLinkInterface[] getDataLinksOnNode(int nodeID) {
        DataLinkInterface[] normalnodes = getDataLinks(nodeID);
        DataLinkInterface[] parentnodes = getDataLinksFromNodeParent(nodeID);
//        if(s_networkElemFactory != null) {
//            normalnodes = s_networkElemFactory.getDataLinks(nodeID);
//            parentnodes = s_networkElemFactory.getDataLinksFromNodeParent(nodeID);
//        }
        
        DataLinkInterface[] nodes = new DataLinkInterface[normalnodes.length+parentnodes.length]; 
        int j = 0;

        for (DataLinkInterface normalnode : normalnodes) {
        	nodes[j++] = normalnode;
        	
        }
        
        for (DataLinkInterface parentnode : parentnodes) {
        	nodes[j++] = parentnode;
        }

        return nodes;
    	
    }

    /**
     * <p>getLinkedNodeIdOnNode</p>
     *
     * @param nodeID a int.
     * @return a {@link java.util.Set} object.
     * @throws java.sql.SQLException if any.
     */
    public Set<Integer> getLinkedNodeIdOnNode(int nodeID) throws SQLException {
        Set<Integer> nodes = new TreeSet<Integer>();
        Integer node = null;
        
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            PreparedStatement stmt = conn.prepareStatement("SELECT distinct(nodeparentid) as parentid FROM DATALINKINTERFACE WHERE NODEID = ? AND STATUS != 'D'");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
    	    while (rs.next()) {
	            Object element = new Integer(rs.getInt("parentid"));
	            if (element != null) {
	                node = ((Integer) element);
	            }
	            nodes.add(node);
	        }

            stmt = conn.prepareStatement("SELECT distinct(nodeid) as parentid FROM DATALINKINTERFACE WHERE NODEPARENTID = ? AND STATUS != 'D'");
            d.watch(stmt);
		    stmt.setInt(1, nodeID);
		    rs = stmt.executeQuery();
		    d.watch(rs);
    	    while (rs.next()) {
	            Object element = new Integer(rs.getInt("parentid"));
	            if (element != null) {
	                node = ((Integer) element);
	            }
	            nodes.add(node);
	        }
        } finally {
            d.cleanUp();
        }
        return nodes;
        
    }
    
    /**
     * <p>getLinkedNodeIdOnNode</p>
     *
     * @param nodeID a int.
     * @param conn a {@link java.sql.Connection} object.
     * @return a {@link java.util.Set} object.
     * @throws java.sql.SQLException if any.
     */
    public Set<Integer> getLinkedNodeIdOnNode(int nodeID,Connection conn) throws SQLException {
        Set<Integer> nodes = new TreeSet<Integer>();
        Integer node = null;
        
        PreparedStatement stmt;
        ResultSet rs;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            stmt = conn.prepareStatement("SELECT distinct(nodeparentid) as parentid FROM DATALINKINTERFACE WHERE NODEID = ? AND STATUS != 'D'");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            rs = stmt.executeQuery();
            d.watch(rs);
            while (rs.next()) {
                Object element = new Integer(rs.getInt("parentid"));
                if (element != null) {
                    node = ((Integer) element);
                }
                nodes.add(node);
            }

            stmt = conn.prepareStatement("SELECT distinct(nodeid) as parentid FROM DATALINKINTERFACE WHERE NODEPARENTID = ? AND STATUS != 'D'");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            rs = stmt.executeQuery();
            d.watch(rs);
            while (rs.next()) {
                Object element = new Integer(rs.getInt("parentid"));
                if (element != null) {
                    node = ((Integer) element);
                }
                nodes.add(node);
            }
        } finally {
            d.cleanUp();
        }
        return nodes;
        
    }    

    /**
     * <p>getLinkedNodeIdOnNodes</p>
     *
     * @param nodeIds a {@link java.util.Set} object.
     * @param conn a {@link java.sql.Connection} object.
     * @return a {@link java.util.Set} object.
     * @throws java.sql.SQLException if any.
     */
    public Set<Integer> getLinkedNodeIdOnNodes(Set<Integer> nodeIds, Connection conn) throws SQLException {
        List<Integer> nodes = new ArrayList<Integer>();
        if(nodeIds==null || nodeIds.size()==0){
        	return new TreeSet<Integer>();
        }
        
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
        	StringBuffer query = new StringBuffer("SELECT distinct(nodeparentid) as parentid FROM DATALINKINTERFACE WHERE NODEID IN (");
        	Iterator<Integer> it = nodeIds.iterator();
        	StringBuffer nodesStrBuff = new StringBuffer("");
        	while(it.hasNext()){
        		nodesStrBuff.append( (it.next()).toString());
        		if(it.hasNext()){
        			nodesStrBuff.append(", ");
        		}
        	}
        	query.append(nodesStrBuff);
        	query.append(") AND STATUS != 'D'");
        	
            PreparedStatement stmt = conn.prepareStatement(query.toString());
            d.watch(stmt);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
    	    while (rs.next()) {
	            nodes.add(new Integer(rs.getInt("parentid")));
	        }

            query = new StringBuffer("SELECT distinct(nodeid) as parentid FROM DATALINKINTERFACE WHERE NODEID IN (");
            query.append(nodesStrBuff);
        	query.append(") AND STATUS != 'D'");            
            rs = stmt.executeQuery();
            d.watch(rs);
    	    while (rs.next()) {
	            nodes.add(new Integer(rs.getInt("parentid")));
	        }
        } finally {
            d.cleanUp();
        }
        
        return new TreeSet<Integer>(nodes);
        
    }
    
    /**
     * <p>getDataLinks</p>
     *
     * @param nodeID a int.
     * @return an array of {@link org.opennms.web.element.DataLinkInterface} objects.
     */
    protected DataLinkInterface[] getDataLinks(int nodeId) {
        OnmsCriteria criteria = new OnmsCriteria(org.opennms.netmgt.model.DataLinkInterface.class);
        criteria.add(Restrictions.eq("nodeId", nodeId));
        criteria.add(Restrictions.ne("status", "D"));
        criteria.addOrder(Order.asc("ifIndex"));
        
        return dlis2DataLinkInterfaces(m_dataLinkInterfaceDao.findMatching(criteria));
    }

    /**
     * <p>getDataLinksFromNodeParent</p>
     *
     * @param nodeID a int.
     * @return an array of {@link org.opennms.web.element.DataLinkInterface} objects.
     */
    protected DataLinkInterface[] getDataLinksFromNodeParent(int parentNodeId) {
        OnmsCriteria criteria = new OnmsCriteria(org.opennms.netmgt.model.DataLinkInterface.class);
        criteria.add(Restrictions.eq("nodeParentId", parentNodeId));
        criteria.add(Restrictions.ne("status", "D"));
        criteria.addOrder(Order.asc("parentIfIndex"));
        
        return dlis2DataLinkInterfaces(m_dataLinkInterfaceDao.findMatching(criteria));
        
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getDataLinksOnInterface(int, int)
	 */
    public DataLinkInterface[] getDataLinksOnInterface(int nodeID, int ifindex){
        DataLinkInterface[] normalnodes = getDataLinks(nodeID,ifindex);
        DataLinkInterface[] parentnodes = getDataLinksFromNodeParent(nodeID,ifindex);
            
        DataLinkInterface[] nodes = new DataLinkInterface[normalnodes.length+parentnodes.length]; 
        int j = 0;

        for (DataLinkInterface normalnode : normalnodes) {
        	nodes[j++] = normalnode;
        	
        }
        
        for (DataLinkInterface parentnode : parentnodes) {
        	nodes[j++] = parentnode;
        }

        return nodes;
    	
    	
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getDataLinks(int, int)
	 */
    public DataLinkInterface[] getDataLinks(int nodeId, int ifIndex) {
        OnmsCriteria criteria = new OnmsCriteria(org.opennms.netmgt.model.DataLinkInterface.class);
        criteria.add(Restrictions.eq("nodeId", nodeId));
        criteria.add(Restrictions.ne("status", "D"));
        criteria.add(Restrictions.eq("ifIndex", ifIndex));
        
        return dlis2DataLinkInterfaces(m_dataLinkInterfaceDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getDataLinksFromNodeParent(int, int)
	 */
    public DataLinkInterface[] getDataLinksFromNodeParent(int nodeId, int ifIndex) {
        OnmsCriteria criteria = new OnmsCriteria(org.opennms.netmgt.model.DataLinkInterface.class);
        criteria.add(Restrictions.eq("nodeParentId", nodeId));
        criteria.add(Restrictions.eq("parentIfIndex", ifIndex));
        criteria.add(Restrictions.ne("status", "D"));
        
        return dlis2DataLinkInterfaces(m_dataLinkInterfaceDao.findMatching(criteria));
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getAllDataLinks()
	 */
    public DataLinkInterface[] getAllDataLinks() {
        OnmsCriteria criteria = new OnmsCriteria(org.opennms.netmgt.model.DataLinkInterface.class);
        criteria.add(Restrictions.ne("status", "D"));
        criteria.addOrder(Order.asc("nodeId"));
        criteria.addOrder(Order.asc("ifIndex"));
        
        return dlis2DataLinkInterfaces(m_dataLinkInterfaceDao.findMatching(criteria));
    }

    private DataLinkInterface[] dlis2DataLinkInterfaces( List<org.opennms.netmgt.model.DataLinkInterface> dataLinks) {
        List<DataLinkInterface> dLinks = new LinkedList<DataLinkInterface>();
        for(org.opennms.netmgt.model.DataLinkInterface dataLink : dataLinks) {
            dLinks.add(dli2DataLinkInterface(dataLink));
        }
        
        return dLinks.toArray(new DataLinkInterface[dLinks.size()]);
    }

    private DataLinkInterface dli2DataLinkInterface(
            org.opennms.netmgt.model.DataLinkInterface dataLink) {
        return new DataLinkInterface(
                        dataLink.getNodeId(), 
                        dataLink.getNodeParentId(), 
                        dataLink.getIfIndex(),
                        dataLink.getParentIfIndex(), 
                        null,
                        null,
                        dataLink.getLastPollTime().toString(),
                        dataLink.getStatus().charAt(0));
    }

    /**
     * <p>getVlansOnNode</p>
     *
     * @param nodeID a int.
     * @return an array of {@link org.opennms.web.element.Vlan} objects.
     * @throws java.sql.SQLException if any.
     */
    public Vlan[] getVlansOnNode(int nodeID) throws SQLException {
    	Vlan[] vlans = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            
            String sqlQuery = "SELECT * from vlan WHERE status != 'D' AND nodeid = ? order by vlanid;";

            PreparedStatement stmt = conn.prepareStatement(sqlQuery);
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);

            vlans = rs2Vlan(rs);
        } finally {
            d.cleanUp();
        }

        return vlans;
    }
    
    /**
     * <p>getStpInterface</p>
     *
     * @param nodeID a int.
     * @return an array of {@link org.opennms.web.element.StpInterface} objects.
     * @throws java.sql.SQLException if any.
     */
    public StpInterface[] getStpInterface(int nodeID)
            throws SQLException {

        StpInterface[] nodes = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);

            String sqlQuery = "SELECT DISTINCT(stpnode.nodeid) AS droot, stpinterfacedb.* FROM "
                    + "((SELECT DISTINCT(stpnode.nodeid) AS dbridge, stpinterface.* FROM "
                    + "stpinterface LEFT JOIN stpnode ON SUBSTR(stpportdesignatedbridge,5,16) = stpnode.basebridgeaddress " 
					+ "AND stpportdesignatedbridge != '0000000000000000'"
                    + "WHERE stpinterface.status != 'D' AND stpinterface.nodeid = ?) AS stpinterfacedb "
                    + "LEFT JOIN stpnode ON SUBSTR(stpportdesignatedroot, 5, 16) = stpnode.basebridgeaddress) order by stpinterfacedb.stpvlan, stpinterfacedb.ifindex;";

            PreparedStatement stmt = conn.prepareStatement(sqlQuery);
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
            
            nodes = rs2StpInterface(rs);
        } finally {
            d.cleanUp();
        }

        return nodes;
    }

    /**
     * <p>getStpInterface</p>
     *
     * @param nodeID a int.
     * @param ifindex a int.
     * @return an array of {@link org.opennms.web.element.StpInterface} objects.
     * @throws java.sql.SQLException if any.
     */
    public StpInterface[] getStpInterface(int nodeID, int ifindex)
            throws SQLException {

        StpInterface[] nodes = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            
            String sqlQuery = "SELECT DISTINCT(stpnode.nodeid) AS droot, stpinterfacedb.* FROM "
                + "((SELECT DISTINCT(stpnode.nodeid) AS dbridge, stpinterface.* FROM "
                + "stpinterface LEFT JOIN stpnode ON SUBSTR(stpportdesignatedbridge,5,16) = stpnode.basebridgeaddress "
				+ "AND stpportdesignatedbridge != '0000000000000000'"
                + "WHERE stpinterface.status != 'D' AND stpinterface.nodeid = ? AND stpinterface.ifindex = ?) AS stpinterfacedb "
                + "LEFT JOIN stpnode ON SUBSTR(stpportdesignatedroot, 5, 16) = stpnode.basebridgeaddress) order by stpinterfacedb.stpvlan, stpinterfacedb.ifindex;";

            PreparedStatement stmt = conn.prepareStatement(sqlQuery);
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            stmt.setInt(2, ifindex);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);
            
            nodes = rs2StpInterface(rs);
        } finally {
            d.cleanUp();
        }

        return nodes;
    }

    /**
     * <p>getStpNode</p>
     *
     * @param nodeID a int.
     * @return an array of {@link org.opennms.web.element.StpNode} objects.
     * @throws java.sql.SQLException if any.
     */
    public StpNode[] getStpNode(int nodeID) throws SQLException {

        StpNode[] nodes = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);
            PreparedStatement stmt = conn.prepareStatement("select distinct(e2.nodeid) as stpdesignatedrootnodeid, e1.* from (stpnode e1 left join stpnode e2 on substr(e1.stpdesignatedroot, 5, 16) = e2.basebridgeaddress) where e1.nodeid = ? AND e1.status != 'D' ORDER BY e1.basevlan");
            d.watch(stmt);
            stmt.setInt(1, nodeID);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);

            nodes = rs2StpNode(rs);
        } finally {
            d.cleanUp();
        }

        return nodes;
    }

    /**
     * This method returns the data from the result set as an array of
     * AtInterface objects.
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.AtInterface} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static AtInterface[] rs2AtInterface(ResultSet rs)
            throws SQLException {
        if (rs == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        List<AtInterface> atIfs = new ArrayList<AtInterface>();

        while (rs.next()) {
            // Non-null field
            Object element = new Integer(rs.getInt("nodeId"));
            int nodeId = ((Integer) element).intValue();

            // Non-null field
            element = rs.getString("ipaddr");
            String ipaddr = (String) element;

            // Non-null field
            element = rs.getString("atphysaddr");
            String physaddr = (String) element;

            // Non-null field
            element = rs.getTimestamp("lastpolltime");
            String lastPollTime = EventConstants.formatToString(new Date(
                    ((Timestamp) element).getTime()));

            // Non-null field
            element = new Integer(rs.getInt("sourcenodeID"));
            int sourcenodeid = ((Integer) element).intValue();

            // Non-null field
            element = new Integer(rs.getInt("ifindex"));
            int ifindex = ((Integer) element).intValue();

            // Non-null field
            element = rs.getString("status");
            char status = ((String) element).charAt(0);

            atIfs.add(new AtInterface(nodeId, sourcenodeid, ifindex, ipaddr, physaddr, lastPollTime, status));
        }

        return atIfs.toArray(new AtInterface[atIfs.size()]);
    }

    /**
     * This method returns the data from the result set as an array of
     * IpRouteInterface objects.
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.IpRouteInterface} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static IpRouteInterface[] rs2IpRouteInterface(ResultSet rs)
            throws SQLException {
        if (rs == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        List<IpRouteInterface> ipRtIfs = new ArrayList<IpRouteInterface>();

        while (rs.next()) {
            IpRouteInterface ipRtIf = new IpRouteInterface();

            Object element = new Integer(rs.getInt("nodeId"));
            ipRtIf.m_nodeId = ((Integer) element).intValue();

            element = rs.getString("routedest");
            ipRtIf.m_routedest = (String) element;

            element = rs.getString("routemask");
            ipRtIf.m_routemask = (String) element;

            element = rs.getString("routenexthop");
            ipRtIf.m_routenexthop = (String) element;

            element = rs.getTimestamp("lastpolltime");
            if (element != null) {
                ipRtIf.m_lastPollTime = EventConstants.formatToString(new Date(
                        ((Timestamp) element).getTime()));
            }

            element = new Integer(rs.getInt("routeifindex"));
            if (element != null) {
                ipRtIf.m_routeifindex = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("routemetric1"));
            if (element != null) {
                ipRtIf.m_routemetric1 = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("routemetric2"));
            if (element != null) {
                ipRtIf.m_routemetric2 = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("routemetric3"));
            if (element != null) {
                ipRtIf.m_routemetric4 = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("routemetric4"));
            if (element != null) {
                ipRtIf.m_routemetric4 = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("routemetric5"));
            if (element != null) {
                ipRtIf.m_routemetric5 = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("routetype"));
            if (element != null && ((Integer)element).intValue() > 0) {
                ipRtIf.m_routetype = ((Integer) element).intValue();
            } else {
                ipRtIf.m_routetype = DbIpRouteInterfaceEntry.ROUTE_TYPE_OTHER;
            }

            element = new Integer(rs.getInt("routeproto"));
            if (element != null) {
                ipRtIf.m_routeproto = ((Integer) element).intValue();
            }

            element = rs.getString("status");
            if (element != null) {
                ipRtIf.m_status = ((String) element).charAt(0);
            } else {
                ipRtIf.m_status = DbIpRouteInterfaceEntry.STATUS_UNKNOWN;
            }

            ipRtIfs.add(ipRtIf);
        }

        return ipRtIfs.toArray(new IpRouteInterface[ipRtIfs.size()]);
    }

    /**
     * This method returns the data from the result set as an array of
     * StpInterface objects.
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.StpInterface} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static StpInterface[] rs2StpInterface(ResultSet rs)
            throws SQLException {
        if (rs == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        List<StpInterface> stpIfs = new ArrayList<StpInterface>();

        while (rs.next()) {
            StpInterface stpIf = new StpInterface();

            Object element = new Integer(rs.getInt("nodeId"));
            stpIf.m_nodeId = ((Integer) element).intValue();

            element = rs.getTimestamp("lastpolltime");
            if (element != null) {
                stpIf.m_lastPollTime = EventConstants.formatToString(new Date(
                        ((Timestamp) element).getTime()));
            }

            element = new Integer(rs.getInt("bridgeport"));
            if (element != null) {
                stpIf.m_bridgeport = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("ifindex"));
            if (element != null) {
                stpIf.m_ifindex = ((Integer) element).intValue();
            }

            element = rs.getString("stpportdesignatedroot");
            stpIf.m_stpdesignatedroot = (String) element;

            element = new Integer(rs.getInt("stpportdesignatedcost"));
            if (element != null) {
                stpIf.m_stpportdesignatedcost = ((Integer) element).intValue();
            }

            element = rs.getString("stpportdesignatedbridge");
            stpIf.m_stpdesignatedbridge = (String) element;

            element = rs.getString("stpportdesignatedport");
            stpIf.m_stpdesignatedport = (String) element;

            element = new Integer(rs.getInt("stpportpathcost"));
            if (element != null) {
                stpIf.m_stpportpathcost = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("stpportstate"));
            if (element != null && ((Integer)element).intValue() > 0) {
                stpIf.m_stpportstate = ((Integer) element).intValue();
            } else {
                stpIf.m_stpportstate = DbStpInterfaceEntry.STP_PORT_DISABLED;
            }

            element = new Integer(rs.getInt("stpvlan"));
            if (element != null) {
                stpIf.m_stpvlan = ((Integer) element).intValue();
            }

            element = rs.getString("status");
            if (element != null) {
                stpIf.m_status = ((String) element).charAt(0);
            } else {
                stpIf.m_status = DbStpInterfaceEntry.STATUS_UNKNOWN;
            }

            element = new Integer(rs.getInt("dbridge"));
            if (element != null) {
                stpIf.m_stpbridgenodeid = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("droot"));
            if (element != null) {
                stpIf.m_stprootnodeid = ((Integer) element).intValue();
            }
            
            if (stpIf.get_ifindex() == -1 ) {
                stpIf.m_ipaddr = getIpAddress(stpIf.get_nodeId());
            } else {
                stpIf.m_ipaddr = getIpAddress(stpIf.get_nodeId(), stpIf
                        .get_ifindex());
            }

            stpIfs.add(stpIf);
        }

        return stpIfs.toArray(new StpInterface[stpIfs.size()]);
    }

    /**
     * This method returns the data from the result set as an array of StpNode
     * objects.
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.StpNode} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static StpNode[] rs2StpNode(ResultSet rs) throws SQLException {
        if (rs == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        List<StpNode> stpNodes = new ArrayList<StpNode>();

        while (rs.next()) {
            StpNode stpNode = new StpNode();

            Object element = new Integer(rs.getInt("nodeId"));
            stpNode.m_nodeId = ((Integer) element).intValue();

            element = rs.getString("basebridgeaddress");
            stpNode.m_basebridgeaddress = (String) element;

            element = rs.getString("stpdesignatedroot");
            stpNode.m_stpdesignatedroot = (String) element;

            element = rs.getTimestamp("lastpolltime");
            if (element != null) {
                stpNode.m_lastPollTime = EventConstants.formatToString(new Date(
                        ((Timestamp) element).getTime()));
            }

            element = new Integer(rs.getInt("basenumports"));
            if (element != null) {
                stpNode.m_basenumports = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("basetype"));
            if (element != null && ((Integer)element).intValue() > 0) {
                stpNode.m_basetype = ((Integer) element).intValue();
            } else {
                stpNode.m_basetype = DbStpNodeEntry.BASE_TYPE_UNKNOWN;
            }

            element = new Integer(rs.getInt("basevlan"));
            if (element != null) {
                stpNode.m_basevlan = ((Integer) element).intValue();
            }

            element = rs.getString("basevlanname");
            if (element != null) {
                stpNode.m_basevlanname = (String) element;
            }

            element = new Integer(rs.getInt("stppriority"));
            if (element != null) {
                stpNode.m_stppriority = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("stpprotocolspecification"));
            if (element != null && ((Integer)element).intValue() > 0) {
                stpNode.m_stpprotocolspecification = ((Integer) element).intValue();
            } else {
                stpNode.m_stpprotocolspecification = DbStpNodeEntry.STP_UNKNOWN;
            }

            element = new Integer(rs.getInt("stprootcost"));
            if (element != null) {
                stpNode.m_stprootcost = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("stprootport"));
            if (element != null) {
                stpNode.m_stprootport = ((Integer) element).intValue();
            }

            element = rs.getString("status");
            if (element != null) {
                stpNode.m_status = ((String) element).charAt(0);
            } else {
                stpNode.m_status = DbStpNodeEntry.STATUS_UNKNOWN;
            }

            element = new Integer(rs.getInt("stpdesignatedrootnodeid"));
            if (element != null) {
                stpNode.m_stprootnodeid = ((Integer) element).intValue();
            }

            stpNodes.add(stpNode);
        }

        return stpNodes.toArray(new StpNode[stpNodes.size()]);
    }

    /**
     * This method returns the data from the result set as an array of StpNode
     * objects.
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.Vlan} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static Vlan[] rs2Vlan(ResultSet rs) throws SQLException {
        if (rs == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        List<Vlan> vlan = new ArrayList<Vlan>();

        while (rs.next()) {

            // Non-null field
            Object element = new Integer(rs.getInt("nodeId"));
            int nodeId = ((Integer) element).intValue();

            // Non-null field
            element = rs.getInt("vlanId");
            int vlanid = ((Integer) element).intValue();

            // Non-null field
            element = rs.getString("vlanname");
            String vlanname = (String) element;

            // Non-null field
            element = rs.getTimestamp("lastpolltime");
            String lastpolltime = EventConstants.formatToString(new Date(
                        ((Timestamp) element).getTime()));

            element = new Integer(rs.getInt("vlantype"));
            int vlantype = DbVlanEntry.VLAN_TYPE_UNKNOWN;
            if (element != null) {
                vlantype = ((Integer) element).intValue();
            }

            element = new Integer(rs.getInt("vlanstatus"));
            int vlanstatus = DbVlanEntry.VLAN_STATUS_UNKNOWN;
            if (element != null) {
                vlanstatus= ((Integer) element).intValue();
            }

            // Non-null field
            element = rs.getString("status");
            char status = ((String) element).charAt(0);

            vlan.add(new Vlan(nodeId, vlanid, vlanname, vlantype, vlanstatus, lastpolltime, status));
        }

        return vlan.toArray(new Vlan[vlan.size()]);
    }


    /**
     * This method returns the data from the result set as an array of
     * DataLinkInterface objects.
     *
     * @param rs a {@link java.sql.ResultSet} object.
     * @return an array of {@link org.opennms.web.element.DataLinkInterface} objects.
     * @throws java.sql.SQLException if any.
     */
    protected static DataLinkInterface[] rs2DataLink(ResultSet rs)
            throws SQLException {
        if (rs == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }

        List<DataLinkInterface> dataLinkIfs = new ArrayList<DataLinkInterface>();

        while (rs.next()) {
            // Non-null field
            Object element = new Integer(rs.getInt("nodeId"));
            int nodeId = ((Integer) element).intValue();

            // Non-null field
            element = new Integer(rs.getInt("ifindex"));
            int ifindex = ((Integer) element).intValue();

            // Non-null field
            element = rs.getTimestamp("lastpolltime");
            String lastPollTime = EventConstants.formatToString(new Date(
                        ((Timestamp) element).getTime()));

            // Non-null field
            element = new Integer(rs.getInt("nodeparentid"));
            int nodeparentid = ((Integer) element).intValue();

            // Non-null field
            element = new Integer(rs.getInt("parentifindex"));
            int parentifindex = ((Integer) element).intValue();

            // Non-null field
            element = rs.getString("status");
            char status = ((String) element).charAt(0);

            String parentipaddress = getIpAddress(nodeparentid, parentifindex);

            String ipaddress = "";
            if (ifindex == -1 ) {
                ipaddress = getIpAddress(nodeId);
            } else {
                ipaddress = getIpAddress(nodeId, ifindex);
            }

            dataLinkIfs.add(new DataLinkInterface(nodeId, nodeparentid, ifindex, parentifindex, ipaddress, parentipaddress, lastPollTime, status));
        }

        return dataLinkIfs.toArray(new DataLinkInterface[dataLinkIfs.size()]);
    }

    /**
     * <p>invertDataLinkInterface</p>
     *
     * @param nodes an array of {@link org.opennms.web.element.DataLinkInterface} objects.
     * @return an array of {@link org.opennms.web.element.DataLinkInterface} objects.
     */
    protected static DataLinkInterface[] invertDataLinkInterface(DataLinkInterface[] nodes) {
    	for (int i=0; i<nodes.length;i++) {
    		DataLinkInterface dli = nodes[i];
    		dli.invertNodewithParent();
    		nodes[i] = dli;
    	}
    	
    	return nodes;
    }

    /**
     * <p>getIpAddress</p>
     *
     * @param nodeid a int.
     * @return a {@link java.lang.String} object.
     * @throws java.sql.SQLException if any.
     */
    protected static String getIpAddress(int nodeid) throws SQLException {

        String ipaddr = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);

            PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT(IPADDR) FROM IPINTERFACE WHERE NODEID = ?");
            d.watch(stmt);
            stmt.setInt(1, nodeid);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);

            while (rs.next()) {
                 ipaddr = rs.getString("ipaddr");
            }
        } finally {
            d.cleanUp();
        }

        return ipaddr;

    }

    /**
     * <p>getIpAddress</p>
     *
     * @param nodeid a int.
     * @param ifindex a int.
     * @return a {@link java.lang.String} object.
     * @throws java.sql.SQLException if any.
     */
    protected static String getIpAddress(int nodeid, int ifindex)
            throws SQLException {
        String ipaddr = null;
        final DBUtils d = new DBUtils(NetworkElementFactory.class);
        try {
            Connection conn = Vault.getDbConnection();
            d.watch(conn);

            PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT(IPADDR) FROM IPINTERFACE WHERE NODEID = ? AND IFINDEX = ? ");
            d.watch(stmt);
            stmt.setInt(1, nodeid);
            stmt.setInt(2, ifindex);
            ResultSet rs = stmt.executeQuery();
            d.watch(rs);

            while (rs.next()) {
                ipaddr = rs.getString("ipaddr");
            }
        } finally {
            d.cleanUp();
        }

        return ipaddr;

    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodeIdsWithIpLike(java.lang.String)
	 */
    public List<Integer> getNodeIdsWithIpLike(String iplike){
        if (iplike == null) {
            throw new IllegalArgumentException("Cannot take null parameters.");
        }
        
        OnmsCriteria nodeCrit = new OnmsCriteria(OnmsNode.class);
        nodeCrit.createCriteria("ipInterfaces", "iface").add(OnmsRestrictions.ipLike(iplike));
        nodeCrit.add(Restrictions.ne("type", "D"));
        nodeCrit.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
        
        List<Integer> nodeIds = new ArrayList<Integer>();
        List<OnmsNode> nodes = m_nodeDao.findMatching(nodeCrit);
        for(OnmsNode node : nodes) {
            nodeIds.add(node.getId());
        }
        
        return nodeIds;
    }
    


    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithCategories(org.springframework.transaction.support.TransactionTemplate, java.lang.String[], boolean)
	 */
    public Node[] getNodesWithCategories(TransactionTemplate transTemplate, final String[] categories1, final boolean onlyNodesWithDownAggregateStatus) {
        return transTemplate.execute(new TransactionCallback<Node[]>() {

            public Node[] doInTransaction(TransactionStatus arg0) {
                return getNodesWithCategories(categories1, onlyNodesWithDownAggregateStatus);
            }
            
        });
    }
    
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithCategories(java.lang.String[], boolean)
	 */
    public Node[] getNodesWithCategories(String[] categories, boolean onlyNodesWithDownAggregateStatus) {
        Collection<OnmsNode> ourNodes = getNodesInCategories(categories);
        
        if(onlyNodesWithDownAggregateStatus) {
            AggregateStatus as = new AggregateStatus(new HashSet<OnmsNode>(ourNodes));
            ourNodes = as.getDownNodes();
        }
        return convertOnmsNodeCollectionToNodeArray(ourNodes);
    }

    
    private Collection<OnmsNode> getNodesInCategories(String[] categoryStrings){
        ArrayList<OnmsCategory> categories = new ArrayList<OnmsCategory>();
        for(String categoryString : categoryStrings) {
            OnmsCategory category = getCategoryDao().findByName(categoryString);
            if(category != null) {
                categories.add(category);
            }else {
                throw new IllegalArgumentException("The Category " + categoryString + " does not exist");
            }
        }
        
        return m_nodeDao.findAllByCategoryList(categories);
    }

    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithCategories(org.springframework.transaction.support.TransactionTemplate, java.lang.String[], java.lang.String[], boolean)
	 */
    public Node[] getNodesWithCategories(TransactionTemplate transTemplate, final String[] categories1, final String[] categories2, final boolean onlyNodesWithDownAggregateStatus) {
        return transTemplate.execute(new TransactionCallback<Node[]>() {

            public Node[] doInTransaction(TransactionStatus status) {
                return getNodesWithCategories(categories1, categories2, onlyNodesWithDownAggregateStatus);
            }
            
        });
    }
    
    /* (non-Javadoc)
	 * @see org.opennms.web.element.NetworkElementFactoryInterfac#getNodesWithCategories(java.lang.String[], java.lang.String[], boolean)
	 */
    public Node[] getNodesWithCategories(String[] categories1, String[] categories2, boolean onlyNodesWithDownAggregateStatus) {
        ArrayList<OnmsCategory> c1 = new ArrayList<OnmsCategory>(categories1.length);
        for (String category : categories1) {
                c1.add(getCategoryDao().findByName(category));
        }
        ArrayList<OnmsCategory> c2 = new ArrayList<OnmsCategory>(categories2.length);
        for (String category : categories2) {
                c2.add(getCategoryDao().findByName(category));
        }
        
        Collection<OnmsNode> ourNodes1 = getNodesInCategories(categories1);
        Collection<OnmsNode> ourNodes2 = getNodesInCategories(categories2);
        
        Set<Integer> n2id = new HashSet<Integer>(ourNodes2.size());
        for (OnmsNode n2 : ourNodes2) {
            n2id.add(n2.getId()); 
        }

        Set<OnmsNode> ourNodes = new HashSet<OnmsNode>();
        for (OnmsNode n1 : ourNodes1) {
            if (n2id.contains(n1.getId())) {
                ourNodes.add(n1);
            }
        }
        
        if (onlyNodesWithDownAggregateStatus) {
            AggregateStatus as = new AggregateStatus(ourNodes);
            ourNodes = as.getDownNodes();
        }

        return convertOnmsNodeCollectionToNodeArray(ourNodes);
    }
    
    /**
     * <p>convertOnmsNodeCollectionToNodeArray</p>
     *
     * @param ourNodes a {@link java.util.Collection} object.
     * @return an array of {@link org.opennms.web.element.Node} objects.
     */
    public Node[] convertOnmsNodeCollectionToNodeArray(Collection<OnmsNode> ourNodes) {
        ArrayList<Node> theirNodes = new ArrayList<Node>(ourNodes.size());
        for (OnmsNode on : ourNodes) {
            theirNodes.add(new Node(on.getId().intValue(),
                                    0, //on.getParent().getId().intValue(),
                                    on.getLabel(),
                                    null, //on.getDpname(),
                                    on.getCreateTime().toString(),
                                    null, // on.getNodeSysId(),
                                    on.getSysName(),
                                    on.getSysDescription(),
                                    on.getSysLocation(),
                                    on.getSysContact(),
                                    on.getType().charAt(0),
                                    on.getOperatingSystem(),
                                    on.getForeignId(),
                                    on.getForeignSource()));

        }
        
        return theirNodes.toArray(new Node[0]);
    }
    
    private Node[] onmsNodes2Nodes( List<OnmsNode> onmsNodes) {
        List<Node> nodes = new LinkedList<Node>();
        for(OnmsNode onmsNode : onmsNodes) {
            nodes.add(onmsNode2Node(onmsNode));
        }
        
        return nodes.toArray(new Node[nodes.size()]);
    }
    
    private Interface onmsIpInterface2Interface(OnmsIpInterface ipIface) {
        
        
            
            Interface intf = new Interface();
            
            intf.m_id = ipIface.getId();
            if(ipIface.getNode() != null) {
                intf.m_nodeId = ipIface.getNode().getId();
            }
            
            if(ipIface.getSnmpInterface() != null) {
                intf.m_ifIndex = ipIface.getIfIndex();
            }
            intf.m_ipHostName = ipIface.getIpHostName();
            intf.m_ipAddr = ipIface.getIpAddressAsString();
            intf.m_isManaged = ipIface.getIsManaged().charAt(0);
            if(ipIface.getIpLastCapsdPoll() != null) {
                intf.m_ipLastCapsdPoll = Util.formatDateToUIString(ipIface.getIpLastCapsdPoll());
            }
            
            
            return intf;
        
        
        
    }
    
    private Interface[] onmsIpInterfaces2InterfaceArray(List<OnmsIpInterface> ipIfaces) {
        List<Interface> intfs = new LinkedList<Interface>();
        for(OnmsIpInterface iface : ipIfaces) {
            intfs.add(onmsIpInterface2Interface(iface));
        }
        
        Collections.sort(intfs, INTERFACE_COMPARATOR);
        return intfs.toArray(new Interface[intfs.size()]);
    }
    
    private Interface[] onmsIpInterfaces2InterfaceArrayWithSnmpData(List<OnmsIpInterface> ipIfaces) {
        List<Interface> intfs = new LinkedList<Interface>();
        for(OnmsIpInterface iface : ipIfaces) {
            Interface intf = onmsIpInterface2Interface(iface);
            if(iface.getSnmpInterface() != null) {
                OnmsSnmpInterface snmpIface = iface.getSnmpInterface();
                intf.m_snmpIfIndex = snmpIface.getIfIndex();
                intf.m_snmpIpAdEntNetMask = snmpIface.getNetMask();
                intf.m_snmpPhysAddr = snmpIface.getPhysAddr();
                intf.m_snmpIfDescr = snmpIface.getIfDescr();
                intf.m_snmpIfName = snmpIface.getIfName();
                if(snmpIface.getIfType() != null) {
                    intf.m_snmpIfType = snmpIface.getIfType();
                }
                
                intf.m_snmpIfOperStatus = snmpIface.getIfOperStatus();
                intf.m_snmpIfSpeed = snmpIface.getIfSpeed();
                if(snmpIface.getIfAdminStatus() != null) {
                    intf.m_snmpIfAdminStatus = snmpIface.getIfAdminStatus();
                }
                intf.m_snmpIfAlias = snmpIface.getIfAlias();
                
                Object element = snmpIface.getPoll();
                if (element != null) {
                    intf.m_isSnmpPoll = ((String) element).charAt(0);
                }

                java.util.Date capsdPoll = snmpIface.getLastCapsdPoll();
                if (capsdPoll != null) {
                    intf.m_snmpLastCapsdPoll = Util.formatDateToUIString(new Date((capsdPoll).getTime()));
                }

                java.util.Date snmpPoll = snmpIface.getLastSnmpPoll();
                if (snmpPoll != null) {
                    intf.m_snmpLastSnmpPoll = Util.formatDateToUIString(new Date((snmpPoll).getTime()));
                }
            }
            intfs.add(intf);
        }
        
        Collections.sort(intfs, INTERFACE_COMPARATOR);
        return intfs.toArray(new Interface[intfs.size()]);
    }
    
    private Interface onmsSnmpInterface2Interface(OnmsSnmpInterface snmpIface) {
            Interface intf = new Interface();
            intf.m_id = snmpIface.getId();
            if(snmpIface.getNode() != null) {
                intf.m_nodeId = snmpIface.getNode().getId();
            }
            
            intf.m_snmpIfIndex = snmpIface.getIfIndex();
            intf.m_snmpIpAdEntNetMask = snmpIface.getNetMask();
            intf.m_snmpPhysAddr = snmpIface.getPhysAddr();
            intf.m_snmpIfDescr = snmpIface.getIfDescr();
            intf.m_snmpIfName = snmpIface.getIfName();
            if(snmpIface.getIfType() != null) {
                intf.m_snmpIfType = snmpIface.getIfType();
            }
            if(snmpIface.getIfOperStatus() != null) {
                intf.m_snmpIfOperStatus = snmpIface.getIfOperStatus();
            }
            if(snmpIface.getIfSpeed() != null) {
                intf.m_snmpIfSpeed = snmpIface.getIfSpeed();
            }
            if(snmpIface.getIfAdminStatus() != null) {
                intf.m_snmpIfAdminStatus = snmpIface.getIfAdminStatus();
            }
            intf.m_snmpIfAlias = snmpIface.getIfAlias();
            
            return intf;
    }
    
    // FIXME: Do any of these @SuppressWarnings("unused") methods get reflected?  Or can we drop them?

    @SuppressWarnings("unused")
    private void setNodeDao(NodeDao nodeDao) {
        m_nodeDao = nodeDao;
    }

    @SuppressWarnings("unused")
    private NodeDao getNodeDao() {
        return m_nodeDao;
    }

    @SuppressWarnings("unused")
    private void setIpInterfaceDao(IpInterfaceDao ipInterfaceDao) {
        m_ipInterfaceDao = ipInterfaceDao;
    }

    @SuppressWarnings("unused")
    private IpInterfaceDao getIpInterfaceDao() {
        return m_ipInterfaceDao;
    }

    @SuppressWarnings("unused")
    private void setSnmpInterfaceDao(SnmpInterfaceDao snmpInterfaceDao) {
        m_snmpInterfaceDao = snmpInterfaceDao;
    }

    @SuppressWarnings("unused")
    private SnmpInterfaceDao getSnmpInterfaceDao() {
        return m_snmpInterfaceDao;
    }

    @SuppressWarnings("unused")
    private void setDataLinkInterfaceDao(DataLinkInterfaceDao dataLinkInterfaceDao) {
        m_dataLinkInterfaceDao = dataLinkInterfaceDao;
    }

    @SuppressWarnings("unused")
    private DataLinkInterfaceDao getDataLinkInterfaceDao() {
        return m_dataLinkInterfaceDao;
    }

    @SuppressWarnings("unused")
    private void setMonSvcDao(MonitoredServiceDao monSvcDao) {
        m_monSvcDao = monSvcDao;
    }

    @SuppressWarnings("unused")
    private MonitoredServiceDao getMonSvcDao() {
        return m_monSvcDao;
    }

    @SuppressWarnings("unused")
    private void setServiceTypeDao(ServiceTypeDao serviceTypeDao) {
        m_serviceTypeDao = serviceTypeDao;
    }

    private ServiceTypeDao getServiceTypeDao() {
        return m_serviceTypeDao;
    }

    @SuppressWarnings("unused")
    private void setCategoryDao(CategoryDao categoryDao) {
        m_categoryDao = categoryDao;
    }

    private CategoryDao getCategoryDao() {
        return m_categoryDao;
    }

    public static class InterfaceComparator implements Comparator<Interface> {
        public int compare(Interface o1, Interface o2) {

            // Sort by IP first if the IPs are non-0.0.0.0
            if (!"0.0.0.0".equals(o1.getIpAddress()) && !"0.0.0.0".equals(o2.getIpAddress())) {
                return new ByteArrayComparator().compare(InetAddressUtils.toIpAddrBytes(o1.getIpAddress()), InetAddressUtils.toIpAddrBytes(o2.getIpAddress()));
            } else {
                // Sort IPs that are non-0.0.0.0 so they are first
                if (!"0.0.0.0".equals(o1.getIpAddress())) {
                    return -1;
                } else if (!"0.0.0.0".equals(o2.getIpAddress())) {
                    return 1;
                }
            }
            return 0;
        }
    }

	public void afterPropertiesSet() throws Exception {
		Assert.notNull(m_nodeDao, "NodeDao must not be null");
		Assert.notNull(m_ipInterfaceDao, "IpinterfaceDao must not be null");
	}
}
