/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.enlinkd;


import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opennms.core.spring.BeanUtils;
import org.opennms.netmgt.config.EnhancedLinkdConfig;
import org.opennms.netmgt.config.SnmpPeerFactory;
import org.opennms.netmgt.daemon.AbstractServiceDaemon;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.enlinkd.scheduler.ReadyRunnable;
import org.opennms.netmgt.enlinkd.scheduler.Scheduler;
import org.opennms.netmgt.model.topology.BridgeForwardingTableEntry;
import org.opennms.netmgt.model.topology.BridgeTopologyException;
import org.opennms.netmgt.model.topology.BroadcastDomain;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.proxy.LocationAwareSnmpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

/**
 * <p>
 * Linkd class.
 * </p>
 * 
 * @author ranger
 * @version $Id: $
 */
public class EnhancedLinkd extends AbstractServiceDaemon {
    private final static Logger LOG = LoggerFactory.getLogger(EnhancedLinkd.class);
    /**
     * The log4j category used to log messages.
     */
    private static final String LOG_PREFIX = "enlinkd";

    /**
     * Rescan scheduler thread
     */
    private Scheduler m_scheduler;

    /**
     * The DB connection read and write handler
     */
    private EnhancedLinkdService m_queryMgr;

    /**
     * Linkd Configuration Initialization
     */

    private EnhancedLinkdConfig m_linkdConfig;

    /**
     * List that contains Linkable Nodes.
     */
    private List<Node> m_nodes;

    /**
     * Event handler
     */
    private volatile EventForwarder m_eventForwarder;

    @Autowired
    private LocationAwareSnmpClient m_locationAwareSnmpClient;

    private volatile Set<Integer> m_bridgecollectionsscheduled = new HashSet<>();
    /**
     * <p>
     * Constructor for EnhancedLinkd.
     * </p>
     */
    public EnhancedLinkd() {
        super(LOG_PREFIX);
    }

    /**
     * <p>
     * onInit
     * </p>
     */
    protected void onInit() {
        BeanUtils.assertAutowiring(this);

        Assert.state(m_eventForwarder != null,
                     "must set the eventForwarder property");

        LOG.debug("init: Loading nodes.....");
        m_nodes = m_queryMgr.getSnmpNodeList();
        Assert.notNull(m_nodes);
        LOG.debug("init: Nodes loaded.");
        LOG.debug("init: Loading Bridge Topology.....");
        m_queryMgr.loadBridgeTopology();
        LOG.debug("init: Bridge Topology loaded.");

        scheduleCollection();
        if (m_nodes.size() > 0 && m_linkdConfig.useBridgeDiscovery()) {
            scheduleDiscoveryBridgeDomain();
        }
    }
    
    public void scheduleDiscoveryBridgeDomain() {
            DiscoveryBridgeDomains discoverbridge=
                    new DiscoveryBridgeDomains(this);
            LOG.debug("scheduleDiscoveryBridgeDomain: Scheduling {}",
                     discoverbridge.getInfo());
            discoverbridge.setScheduler(m_scheduler);
            discoverbridge.schedule();
    }

    private void scheduleCollection() {
        synchronized (m_nodes) {
            for (final Node node : m_nodes) {
                scheduleCollectionForNode(node);
            }
        }
    }

    /**
     * This method schedules a {@link SnmpCollection} for node for each
     * package. Also schedule discovery link on package when not still
     * activated.
     * 
     * @param node
     */
    private void scheduleCollectionForNode(final Node node) {

        for (final NodeDiscovery snmpcoll : getSnmpCollections(node) ){
            LOG.debug("ScheduleCollectionForNode: Scheduling {}",
                snmpcoll.getInfo());
        	snmpcoll.setScheduler(m_scheduler);
            snmpcoll.schedule();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @param nodeid
     */
    public List<NodeDiscovery> getSnmpCollections(Node node) {
        List<NodeDiscovery> snmpcolls = new ArrayList<>();
        
        if (m_linkdConfig.useLldpDiscovery()) {
            LOG.debug("getSnmpCollections: adding Lldp Discovery: {}",
                    node);
            snmpcolls.add(new NodeDiscoveryLldp(this, node));
        }
        
        if (m_linkdConfig.useCdpDiscovery()) {
            LOG.debug("getSnmpCollections: adding Cdp Discovery: {}",
                    node);
             snmpcolls.add(new NodeDiscoveryCdp(this, node));   	
        }
        
        if (m_linkdConfig.useBridgeDiscovery()) {
        	LOG.debug("getSnmpCollections: adding IpNetToMedia Discovery: {}",
                    node);
        	snmpcolls.add(new NodeDiscoveryIpNetToMedia(this, node));
        	
        	LOG.debug("getSnmpCollections: adding Bridge Discovery: {}",
                    node);
        	snmpcolls.add(new NodeDiscoveryBridge(this, node));
        }

        if (m_linkdConfig.useOspfDiscovery()) {
            LOG.debug("getSnmpCollections: adding Ospf Discovery: {}",
                    node);
        	snmpcolls.add(new NodeDiscoveryOspf(this, node));
        }

        if (m_linkdConfig.useIsisDiscovery()) {
            LOG.debug("getSnmpCollections: adding Is-Is Discovery: {}",
                    node);
        	snmpcolls.add(new NodeDiscoveryIsis(this, node));
        }

        return snmpcolls;
    }

    public DiscoveryBridgeTopology getNodeBridgeDiscoveryTopology(BroadcastDomain domain) {
        return new DiscoveryBridgeTopology(this,domain);
    }
    /**
     * <p>
     * onStart
     * </p>
     */
    protected synchronized void onStart() {

        // start the scheduler
        //
        m_scheduler.start();
        
        // Set the status of the service as running.
        //

    }

    /**
     * <p>
     * onStop
     * </p>
     */
    protected synchronized void onStop() {
              // Stop the scheduler
        m_scheduler.stop();
        m_scheduler = null;
    }

    /**
     * <p>
     * onPause
     * </p>
     */
    protected synchronized void onPause() {
        m_scheduler.pause();
    }

    /**
     * <p>
     * onResume
     * </p>
     */
    protected synchronized void onResume() {
        m_scheduler.resume();
    }

    /**
     * <p>
     * getLinkableNodes
     * </p>
     * 
     * @return a {@link java.util.Collection} object.
     */
    public Collection<Node> getLinkableNodes() {
        synchronized (m_nodes) {
            return m_nodes;
        }
    }

    public boolean scheduleNodeCollection(int nodeid) {

        Node node = getNode(nodeid);
        if (node != null) {
            LOG.debug("scheduleNodeCollection: Found Scheduled Linkable node {}. Skipping ",
                            nodeid);
            return false;
        }

        // First of all get Linkable Node
        LOG.debug("scheduleNodeCollection: Loading node {} from database",
                        nodeid);
        node = m_queryMgr.getSnmpNode(nodeid);
        if (node == null) {
            LOG.warn("scheduleNodeCollection: Failed to get linkable node from database with ID {}. Exiting",
                           nodeid);
            return false;
        }

        synchronized (m_nodes) {
            LOG.debug("scheduleNodeCollection: adding node {} to the collection", node);
            m_nodes.add(node);
        }

        scheduleCollectionForNode(node);
        if (m_nodes.size() == 1 && m_linkdConfig.useBridgeDiscovery()) {
            scheduleDiscoveryBridgeDomain();
        }
        return true;
    }

    public boolean runSingleSnmpCollection(final int nodeId) {
        boolean allready = true;
        final Node node = m_queryMgr.getSnmpNode(nodeId);

        for (final NodeDiscovery snmpColl : getSnmpCollections(node)) {
            if (!snmpColl.isReady()) {
                allready = false;
                continue;
            }
            snmpColl.runDiscovery();
        }
        return allready;
    }

    public void runTopologyDiscovery() {
        final DiscoveryBridgeDomains snmpColl = new DiscoveryBridgeDomains(this);
        snmpColl.runDiscovery();
    }
    
    public void scheduleNodeBridgeTopologyDiscovery(BroadcastDomain domain, Map<Integer,Set<BridgeForwardingTableEntry>> updateBfpMap) {
        final DiscoveryBridgeTopology bridgediscovery = getNodeBridgeDiscoveryTopology(domain);
        for (Integer bridgeid: updateBfpMap.keySet()) {
            bridgediscovery.addUpdatedBFT(bridgeid, updateBfpMap.get(bridgeid));
        }
        LOG.debug("scheduleBridgeTopologyDiscovery: Scheduling {}",
                    bridgediscovery.getInfo());
        bridgediscovery.setScheduler(m_scheduler);
        bridgediscovery.schedule();
    }

    void wakeUpNodeCollection(int nodeid) {

        Node node = getNode(nodeid);

        if (node == null) {
            LOG.warn("wakeUpNodeCollection: node not found during scheduling with ID {}",
                           nodeid);
            scheduleNodeCollection(nodeid);
        } else {
            // get collections
            // get readyRunnuble
            // wakeup RR
            Collection<NodeDiscovery> collections = getSnmpCollections(node);
            LOG.info("wakeUpNodeCollection: fetched SnmpCollections from scratch, iterating over {} objects to wake them up",
                            collections.size());
            for (NodeDiscovery collection : collections) {
                ReadyRunnable rr = getReadyRunnable(collection);
                if (rr == null) {
                    LOG.warn("wakeUpNodeCollection: found null ReadyRunnable for nodeid {}", nodeid);
                    continue;
                } else {
                    rr.wakeUp();
                }
            }
        }

    }

    void deleteNode(int nodeid) {
        LOG.info("deleteNode: deleting LinkableNode for node {}",
                        nodeid);

        Node node = removeNode(nodeid);

        if (node == null) {
            LOG.warn("deleteNode: node not found: {}", nodeid);
        } else {
            Collection<NodeDiscovery> collections = getSnmpCollections(node);
            LOG.info("deleteNode: fetched SnmpCollections from scratch, iterating over {} objects to delete",
                            collections.size());
            for (NodeDiscovery collection : collections) {
                ReadyRunnable rr = getReadyRunnable(collection);

                if (rr == null) {
                    LOG.warn("deleteNode: found null ReadyRunnable for {}", collection.getInfo());
                    continue;
                } else {
                    rr.unschedule();
                }

            }
        }
        try {
            m_queryMgr.delete(nodeid);
        } catch (BridgeTopologyException e) {
            LOG.error("deleteNode: {}", e.getMessage());
        }

    }

    void rescheduleNodeCollection(int nodeid) {
        LOG.info("rescheduleNodeCollection: suspend collection LinkableNode for node {}",
                nodeid);
        
        Node node = getNode(nodeid);
        if (node == null) {
            LOG.warn("rescheduleNodeCollection: node not found: {}", nodeid);
        } else {
            Collection<NodeDiscovery> collections = getSnmpCollections(node);
            LOG.info("rescheduleNodeCollection: fetched SnmpCollections from scratch, iterating over {} objects to rescheduling",
                            collections.size());
            for (NodeDiscovery collection : collections) {
                ReadyRunnable rr = getReadyRunnable(collection);

                if (rr == null) {
                    LOG.warn("rescheduleNodeCollection: found null ReadyRunnable");
                    continue;
                } else {
                    rr.unschedule();
                    rr.schedule();
                }

            }

        }
    	
    }
    
    void suspendNodeCollection(int nodeid) {
        LOG.info("suspendNodeCollection: suspend collection LinkableNode for node {}",
                        nodeid);
   
        Node node = getNode(nodeid);

        if (node == null) {
            LOG.warn("suspendNodeCollection: found null ReadyRunnable");
        } else {
            // get collections
            // get readyRunnuble
            // suspend RR
            Collection<NodeDiscovery> collections = getSnmpCollections(node);
            LOG.info("suspendNodeCollection: fetched SnmpCollections from scratch, iterating over {} objects to suspend them down",
                            collections.size());
            for (NodeDiscovery collection : collections) {
                ReadyRunnable rr = getReadyRunnable(collection);
                if (rr == null) {
                    LOG.warn("suspendNodeCollection: suspend: node not found: {}",
                                   nodeid);
                    continue;
                } else {
                    rr.suspend();
                }
            }
        }

    }

    private ReadyRunnable getReadyRunnable(ReadyRunnable runnable) {
        LOG.info("getReadyRunnable: getting {} from scheduler",
                        runnable.getInfo());

        return m_scheduler.getReadyRunnable(runnable);

    }

    Node getNode(int nodeid) {
        synchronized (m_nodes) {
            for (Node node : m_nodes) {
                if (node.getNodeId() == nodeid)
                    return node;
            }
            return null;
        }
    }

    private Node removeNode(int nodeid) {
        synchronized (m_nodes) {
            Iterator<Node> ite = m_nodes.iterator();
            while (ite.hasNext()) {
                Node curNode = ite.next();
                if (curNode.getNodeId() == nodeid) {
                    ite.remove();
                    return curNode;
                }
            }
            return null;
        }
    }

    public EnhancedLinkdService getQueryManager() {
        return m_queryMgr;
    }

    /**
     * <p>
     * setQueryManager
     * </p>
     * 
     * @param queryMgr
     *            a {@link org.opennms.netmgt.linkd.EnhancedLinkdService} object.
     */
    public void setQueryManager(EnhancedLinkdService queryMgr) {
        m_queryMgr = queryMgr;
    }

    /**
     * <p>
     * getScheduler
     * </p>
     * 
     * @return a {@link org.opennms.netmgt.enlinkd.scheduler.Scheduler} object.
     */
    public Scheduler getScheduler() {
        return m_scheduler;
    }

    /**
     * <p>
     * setScheduler
     * </p>
     * 
     * @param scheduler
     *            a {@link org.opennms.netmgt.enlinkd.scheduler.Scheduler}
     *            object.
     */
    public void setScheduler(Scheduler scheduler) {
        m_scheduler = scheduler;
    }

    /**
     * <p>
     * getLinkdConfig
     * </p>
     * 
     * @return a {@link org.opennms.netmgt.config.LinkdConfig} object.
     */
    public EnhancedLinkdConfig getLinkdConfig() {
        return m_linkdConfig;
    }

    /**
     * <p>
     * setLinkdConfig
     * </p>
     * 
     * @param config
     *            a {@link org.opennms.netmgt.config.LinkdConfig} object.
     */
    public void setLinkdConfig(final EnhancedLinkdConfig config) {
        m_linkdConfig = config;
    }

    /**
     * @return the eventForwarder
     */
    public EventForwarder getEventForwarder() {
        return m_eventForwarder;
    }

    /**
     * @param eventForwarder
     *            the eventForwarder to set
     */
    public void setEventForwarder(EventForwarder eventForwarder) {
        this.m_eventForwarder = eventForwarder;
    }

	public String getSource() {
		return "enlinkd";
	}

    public SnmpAgentConfig getSnmpAgentConfig(InetAddress ipaddr, String location) {
    	return SnmpPeerFactory.getInstance().getAgentConfig(ipaddr, location);
    }

    public long getInitialSleepTime() {
    	return m_linkdConfig.getInitialSleepTime();
    }

    public long getRescanInterval() {
            return m_linkdConfig.getRescanInterval(); 
    }
    public long getBridgeTopologyInterval() {
        return m_linkdConfig.getBridgeTopologyInterval();
    }
    
    public int getDiscoveryBridgeThreads() {
        return m_linkdConfig.getDiscoveryBridgeThreads();
    }

    public LocationAwareSnmpClient getLocationAwareSnmpClient() {
        return m_locationAwareSnmpClient;
    }
    
    public int getMaxbft() {
    	return m_linkdConfig.getMaxBft();
    }
    
    public synchronized boolean collectBft(int nodeid) {
    	if (getQueryManager().getUpdateBftMap().size()+m_bridgecollectionsscheduled.size() >= m_linkdConfig.getMaxBft() )
    		return false;
    	synchronized (m_bridgecollectionsscheduled) {
        	m_bridgecollectionsscheduled.add(nodeid);
		}
    	return true;
    }
    
    public synchronized void collectedBft(int nodeid) {
    	synchronized (m_bridgecollectionsscheduled) {
        	m_bridgecollectionsscheduled.remove(nodeid);
		}
    }

}