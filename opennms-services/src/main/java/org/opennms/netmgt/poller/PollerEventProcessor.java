/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2002-2014 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.poller;

import static org.opennms.core.utils.InetAddressUtils.addr;
import static org.opennms.core.utils.InetAddressUtils.str;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.opennms.core.utils.ConfigFileConstants;
import org.opennms.netmgt.config.PollerConfig;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.events.api.EventListener;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.model.events.EventUtils;
import org.opennms.netmgt.poller.pollables.PollableInterface;
import org.opennms.netmgt.poller.pollables.PollableNetwork;
import org.opennms.netmgt.poller.pollables.PollableNode;
import org.opennms.netmgt.poller.pollables.PollableService;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Parm;
import org.opennms.netmgt.xml.event.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author <a href="mailto:jamesz@opennms.com">James Zuo </a>
 * @author <a href="mailto:weave@oculan.com">Brian Weaver </a>
 * @author <a href="http://www.opennms.org/">OpenNMS </a>
 */
final class PollerEventProcessor implements EventListener {
    private static final Logger LOG = LoggerFactory.getLogger(PollerEventProcessor.class);

    private final Poller m_poller;

    /**
     * Create message selector to set to the subscription
     */
    private void createMessageSelectorAndSubscribe() {
        // Create the selector for the UEIs this service is interested in
        //
        List<String> ueiList = new ArrayList<>();

        // nodeGainedService
        ueiList.add(EventConstants.NODE_GAINED_SERVICE_EVENT_UEI);

        // serviceDeleted
        // deleteService
        /*
         * NOTE: deleteService is only generated by the PollableService itself.
         * Therefore, we ignore it. If future implementations allow other
         * subsystems to generate this event, we may have to listen for it as
         * well. 'serviceDeleted' is the response event that the outage manager
         * generates. We ignore this as well, since the PollableService has
         * already taken action at the time it generated 'deleteService'
         */
        ueiList.add(EventConstants.SERVICE_DELETED_EVENT_UEI);
        // ueiList.add(EventConstants.DELETE_SERVICE_EVENT_UEI);

        // serviceManaged
        // serviceUnmanaged
        // interfaceManaged
        // interfaceUnmanaged
        /*
         * NOTE: These are all ignored because the responsibility is currently
         * on the class generating the event to restart the poller service. If
         * that implementation is ever changed, this message selector should
         * listen for these and act on them.
         */
        // ueiList.add(EventConstants.SERVICE_MANAGED_EVENT_UEI);
        // ueiList.add(EventConstants.SERVICE_UNMANAGED_EVENT_UEI);
        // ueiList.add(EventConstants.INTERFACE_MANAGED_EVENT_UEI);
        // ueiList.add(EventConstants.INTERFACE_UNMANAGED_EVENT_UEI);
        // interfaceIndexChanged
        // NOTE: No longer interested in this event...if Capsd detects
        // that in interface's index has changed a
        // 'reinitializePrimarySnmpInterface' event is generated.
        // ueiList.add(EventConstants.INTERFACE_INDEX_CHANGED_EVENT_UEI);
        // interfaceReparented
        ueiList.add(EventConstants.INTERFACE_REPARENTED_EVENT_UEI);

        // reloadPollerConfig
        /*
         * NOTE: This is ignored because the reload is handled through an
         * autoaction.
         */
        // ueiList.add(EventConstants.RELOAD_POLLER_CONFIG_EVENT_UEI);
        // NODE OUTAGE RELATED EVENTS
        // 
        // nodeAdded
        /*
         * NOTE: This is ignored. The real trigger will be the first
         * nodeGainedService event, at which time the interface and node will be
         * created
         */
        // ueiList.add(EventConstants.NODE_ADDED_EVENT_UEI);
        // nodeDeleted
        ueiList.add(EventConstants.NODE_DELETED_EVENT_UEI);

        // nodeLabelChanged
        ueiList.add(EventConstants.NODE_LABEL_CHANGED_EVENT_UEI);

        // duplicateNodeDeleted
        ueiList.add(EventConstants.DUP_NODE_DELETED_EVENT_UEI);

        // nodeGainedInterface
        /*
         * NOTE: This is ignored. The real trigger will be the first
         * nodeGainedService event, at which time the interface and node will be
         * created
         */
        // ueiList.add(EventConstants.NODE_GAINED_INTERFACE_EVENT_UEI);
        // interfaceDeleted
        ueiList.add(EventConstants.INTERFACE_DELETED_EVENT_UEI);

        // suspendPollingService
        ueiList.add(EventConstants.SUSPEND_POLLING_SERVICE_EVENT_UEI);

        // resumePollingService
        ueiList.add(EventConstants.RESUME_POLLING_SERVICE_EVENT_UEI);

        // scheduled outage configuration change
        ueiList.add(EventConstants.SCHEDOUTAGES_CHANGED_EVENT_UEI);

        // update threshold configuration
        ueiList.add(EventConstants.THRESHOLDCONFIG_CHANGED_EVENT_UEI);

        // asset information updated
        ueiList.add(EventConstants.ASSET_INFO_CHANGED_EVENT_UEI);

        // categories updated
        ueiList.add(EventConstants.NODE_CATEGORY_MEMBERSHIP_CHANGED_EVENT_UEI);

        // node location change
        ueiList.add(EventConstants.NODE_LOCATION_CHANGED_EVENT_UEI);

        // for reloading poller configuration and re-scheduling pollers
        ueiList.add(EventConstants.RELOAD_DAEMON_CONFIG_UEI);

        // Subscribe to eventd
        getEventManager().addEventListener(this, ueiList);
    }

    /**
     * Process the event, construct a new PollableService object representing
     * the node/interface/service/pkg combination, and schedule the service for
     * polling.
     * 
     * If any errors occur scheduling the interface no error is returned.
     * 
     * @param event
     *            The event to process.
     * 
     */
    private void nodeGainedServiceHandler(final Event event) {
        // First make sure the service gained is in active state before trying to schedule

        final String ipAddr = event.getInterface();
        final Long nodeId = event.getNodeid();
        final String svcName = event.getService();

        String nodeLabel = EventUtils.getParm(event, EventConstants.PARM_NODE_LABEL);
        try {
            nodeLabel = getPoller().getQueryManager().getNodeLabel(nodeId.intValue());
        } catch (final Exception e) {
            LOG.error("Unable to retrieve nodeLabel for node {}", nodeId, e);
        }

        String nodeLocation = null;
        try {
            nodeLocation = getPoller().getQueryManager().getNodeLocation(nodeId.intValue());
        } catch (final Exception e) {
            LOG.error("Unable to retrieve nodeLocation for node {}", nodeId, e);
        }

        final PollableNode pnode = getNetwork().getNode(nodeId.intValue());
        if (pnode != null) {
            final PollableService service = pnode.getService(addr(ipAddr), svcName);
            if (service != null) {
                LOG.debug("Node {} gained service {} on IP {}, but it is already being polled!", nodeId, svcName, ipAddr);
                return;
            }
        }
        getPoller().scheduleService(nodeId.intValue(), nodeLabel, nodeLocation, ipAddr, svcName, pnode);

    }

    /**
     * This method is responsible for processing 'interfacReparented' events. An
     * 'interfaceReparented' event will have old and new nodeId parms associated
     * with it. Node outage processing hierarchy will be updated to reflect the
     * new associations.
     * 
     * @param event
     *            The event to process.
     * 
     */
    private void interfaceReparentedHandler(Event event) { 
        LOG.debug("interfaceReparentedHandler: processing interfaceReparented event for {}", event.getInterface());

        // Verify that the event has an interface associated with it
        if (event.getInterfaceAddress() == null)
            return;

        InetAddress ipAddr = event.getInterfaceAddress();

        // Extract the old and new nodeId's from the event parms
        String oldNodeIdStr = null;
        String newNodeIdStr = null;
        String parmName = null;
        Value parmValue = null;
        String parmContent = null;

        for (Parm parm : event.getParmCollection()) {
            parmName = parm.getParmName();
            parmValue = parm.getValue();
            if (parmValue == null)
                continue;
            else
                parmContent = parmValue.getContent();

            // old nodeid
            if (parmName.equals(EventConstants.PARM_OLD_NODEID)) {
                oldNodeIdStr = parmContent;
            }

            // new nodeid
            else if (parmName.equals(EventConstants.PARM_NEW_NODEID)) {
                newNodeIdStr = parmContent;
            }
        }

        // Only proceed provided we have both an old and a new nodeId
        //
        if (oldNodeIdStr == null || newNodeIdStr == null) {
            LOG.error("interfaceReparentedHandler: old and new nodeId parms are required, unable to process.");
            return;
        }

        PollableNode oldNode;
        PollableNode newNode;
        try {
            oldNode = getNetwork().getNode(Integer.parseInt(oldNodeIdStr));
            if (oldNode == null) {
                LOG.error("interfaceReparentedHandler: Cannot locate old node {} belonging to interface {}", oldNodeIdStr, ipAddr);
                return;
            }
            newNode = getNetwork().getNode(Integer.parseInt(newNodeIdStr));
            if (newNode == null) {
                LOG.error("interfaceReparentedHandler: Cannot locate new node {} to move interface to.  Also, grammar error: ended a sentence with a preposition.", newNodeIdStr);
                return;
            }

            PollableInterface iface = oldNode.getInterface(ipAddr);
            if (iface == null) {
                LOG.error("interfaceReparentedHandler: Cannot locate interface with ipAddr {} to reparent.", ipAddr);
                return;
            }

            iface.reparentTo(newNode);


        } catch (final NumberFormatException nfe) {
            LOG.error("interfaceReparentedHandler: failed converting old/new nodeid parm to integer, unable to process.");
            return;
        } 

    }

    /**
     * This method is responsible for removing a node's pollable service from
     * the pollable services list
     */
    private void nodeRemovePollableServiceHandler(Event event) {
        Long nodeId = event.getNodeid();
        InetAddress ipAddr = event.getInterfaceAddress();
        String svcName = event.getService();

        if (svcName == null) {
            LOG.error("nodeRemovePollableServiceHandler: service name is null, ignoring event");
            return;
        }

        PollableService svc = getNetwork().getService(nodeId.intValue(), ipAddr, svcName);
        svc.delete();

    }

    /**
     * This method is responsible for removing the node specified in the
     * nodeDeleted event from the Poller's pollable node map.
     */
    private void nodeDeletedHandler(Event event) {
        Long nodeId = event.getNodeid();
        final String sourceUei = event.getUei();

        // Extract node label and transaction No. from the event parms
        long txNo = -1L;
        String parmName = null;
        Value parmValue = null;
        String parmContent = null;

        for (Parm parm : event.getParmCollection()) {
            parmName = parm.getParmName();
            parmValue = parm.getValue();
            if (parmValue == null)
                continue;
            else
                parmContent = parmValue.getContent();

            // get the external transaction number
            if (parmName.equals(EventConstants.PARM_TRANSACTION_NO)) {
                String temp = parmContent;
                LOG.debug("nodeDeletedHandler:  parmName: {} /parmContent: {}", parmName, parmContent);
                try {
                    txNo = Long.valueOf(temp).longValue();
                } catch (final NumberFormatException nfe) {
                    LOG.warn("nodeDeletedHandler: Parameter {} cannot be non-numeric", EventConstants.PARM_TRANSACTION_NO, nfe);
                    txNo = -1;
                }
            }
        }

        Date closeDate = event.getTime();

        getPoller().getQueryManager().closeOutagesForNode(closeDate, event.getDbid(), nodeId.intValue());


        PollableNode node = getNetwork().getNode(nodeId.intValue());
        if (node == null) {
            LOG.error("Nodeid {} does not exist in pollable node map, unable to delete node.", nodeId);
            return;
        }
        node.delete();

    }

    private void nodeLabelChangedHandler(Event event) {
        Long nodeId = event.getNodeid();

        // Extract node label from the event parms
        for (Parm parm : event.getParmCollection()) {
            String parmName = parm.getParmName();
            Value parmValue = parm.getValue();
            if (parmValue == null) {
                continue;
            } else {
                if (parmName.equals(EventConstants.PARM_NEW_NODE_LABEL)){
                    String label = parmValue.getContent();
                    LOG.debug("nodeLabelChangedHandler: parmName: {} /parmContent: {}", parmName, label);
                    PollableNode node = getNetwork().getNode(nodeId.intValue());
                    if (node == null) {
                        LOG.warn("nodeLabelChangedHandler: nodeid {} does not exist in pollable node map, unable to update node label.", nodeId);
                    } else {
                        node.setNodeLabel(label);
                        return;
                    }
                }
            }
        }
    }

    private void interfaceDeletedHandler(Event event) {
        Long nodeId = event.getNodeid();
        String sourceUei = event.getUei();
        InetAddress ipAddr = event.getInterfaceAddress();

        // Extract node label and transaction No. from the event parms
        long txNo = -1L;
        String parmName = null;
        Value parmValue = null;
        String parmContent = null;

        for (Parm parm : event.getParmCollection()) {
            parmName = parm.getParmName();
            parmValue = parm.getValue();
            if (parmValue == null)
                continue;
            else
                parmContent = parmValue.getContent();

            // get the external transaction number
            if (parmName.equals(EventConstants.PARM_TRANSACTION_NO)) {
                String temp = parmContent;
                LOG.debug("interfaceDeletedHandlerHandler:  parmName: {} /parmContent: {}", parmName, parmContent);
                try {
                    txNo = Long.valueOf(temp).longValue();
                } catch (final NumberFormatException nfe) {
                    LOG.warn("interfaceDeletedHandlerHandler: Parameter {} cannot be non-numberic", EventConstants.PARM_TRANSACTION_NO, nfe);
                    txNo = -1;
                }
            }
        }

        Date closeDate = event.getTime();

        getPoller().getQueryManager().closeOutagesForInterface(closeDate, event.getDbid(), nodeId.intValue(), str(ipAddr));


        PollableInterface iface = getNetwork().getInterface(nodeId.intValue(), ipAddr);
        if (iface == null) {
            LOG.error("Interface {}/{} does not exist in pollable node map, unable to delete node.", nodeId, event.getInterface());
            return;
        }
        iface.delete();

    }

    /**
     * <p>
     * This method remove a deleted service from the pollable service list of
     * the specified interface, so that it will not be scheduled by the poller.
     * </p>
     */
    private void serviceDeletedHandler(Event event) {
        Long nodeId = event.getNodeid();
        InetAddress ipAddr = event.getInterfaceAddress();
        String service = event.getService();

        Date closeDate = event.getTime();

        getPoller().getQueryManager().closeOutagesForService(closeDate, event.getDbid(), nodeId.intValue(), str(ipAddr), service);

        PollableService svc = getNetwork().getService(nodeId.intValue(), ipAddr, service);
        if (svc == null) {
            LOG.error("Service {}/{}/{} does not exist in pollable node map, unable to delete service.", nodeId, event.getInterface(), service);
            return;
        }

        svc.delete();

    }

    private void reloadConfigHandler(Event event) {
        final String daemonName = "Pollerd";
        boolean isPoller = false;
        for (Parm parm : event.getParmCollection()) {
            if (EventConstants.PARM_DAEMON_NAME.equals(parm.getParmName()) && daemonName.equalsIgnoreCase(parm.getValue().getContent())) {
                isPoller = true;
                break;
            }
        }
        if (isPoller) {
            LOG.info("reloadConfigHandler: reloading poller configuration");
            final String targetFile = ConfigFileConstants.getFileName(ConfigFileConstants.POLLER_CONFIG_FILE_NAME);
            EventBuilder ebldr = null;
            try {
                getPollerConfig().update();
                rescheduleAllServices(event);
                // Preparing successful event
                ebldr = new EventBuilder(EventConstants.RELOAD_DAEMON_CONFIG_SUCCESSFUL_UEI, daemonName);
                ebldr.addParam(EventConstants.PARM_DAEMON_NAME, daemonName);
                ebldr.addParam(EventConstants.PARM_CONFIG_FILE_NAME, targetFile);
            } catch (Throwable e) {
                // Preparing failed event
                LOG.error("reloadConfigHandler: Error reloading/processing poller configuration: {}", e.getMessage(), e);
                ebldr = new EventBuilder(EventConstants.RELOAD_DAEMON_CONFIG_FAILED_UEI, daemonName);
                ebldr.addParam(EventConstants.PARM_DAEMON_NAME, daemonName);
                ebldr.addParam(EventConstants.PARM_CONFIG_FILE_NAME, targetFile);
                ebldr.addParam(EventConstants.PARM_REASON, e.getMessage());
            }
            finally {
                if (ebldr != null) {
                    getEventManager().sendNow(ebldr.getEvent());
                }
            }
        } else {
            LOG.warn("reloadConfigHandler: invalid parameters");
        }
    }

    /**
     * Constructor
     * 
     */
    PollerEventProcessor(Poller poller) {

        m_poller = poller;

        createMessageSelectorAndSubscribe();

        LOG.debug("Subscribed to eventd");
    }

    /**
     * Unsubscribe from eventd
     */
    public void close() {
        getEventManager().removeEventListener(this);
    }

    private EventIpcManager getEventManager() {
        return getPoller().getEventManager();
    }

    /**
     * This method is invoked by the EventIpcManager when a new event is
     * available for processing. Each message is examined for its Universal
     * Event Identifier and the appropriate action is taking based on each UEI.
     * 
     * @param event
     *            The event
     */
    @Override
    public void onEvent(Event event) {
        if (event == null)
            return;

        // print out the uei
        LOG.debug("PollerEventProcessor: received event, uei = {}", event.getUei());

        if(event.getUei().equals(EventConstants.SCHEDOUTAGES_CHANGED_EVENT_UEI)) {
            LOG.info("Reloading poller config factory and polloutages config factory");

            scheduledOutagesChangeHandler();

        } else if (event.getUei().equals(EventConstants.RELOAD_DAEMON_CONFIG_UEI))  {
            LOG.info("Reloading poller configuration in pollerd");

            reloadConfigHandler(event);
        } else if(!event.hasNodeid()) {
            // For all other events, if the event doesn't have a nodeId it can't be processed.

            LOG.info("PollerEventProcessor: no database node id found, discarding event");
        } else if (event.getUei().equals(EventConstants.NODE_GAINED_SERVICE_EVENT_UEI)) {
            // If there is no interface then it cannot be processed
            if (event.getInterface() == null) {
                LOG.info("PollerEventProcessor: no interface found, discarding event");
            } else {
                nodeGainedServiceHandler(event);
            }
        } else if (event.getUei().equals(EventConstants.RESUME_POLLING_SERVICE_EVENT_UEI)) {
            // If there is no interface then it cannot be processed
            if (event.getInterface() == null) {
                LOG.info("PollerEventProcessor: no interface found, cannot resume polling service, discarding event");
            } else {
                nodeGainedServiceHandler(event);
            }
        } else if (event.getUei().equals(EventConstants.SUSPEND_POLLING_SERVICE_EVENT_UEI)) {
            // If there is no interface then it cannot be processed
            if (event.getInterface() == null) {
                LOG.info("PollerEventProcessor: no interface found, cannot suspend polling service, discarding event");
            } else {
                nodeRemovePollableServiceHandler(event);
            }
        } else if (event.getUei().equals(EventConstants.INTERFACE_REPARENTED_EVENT_UEI)) {
            // If there is no interface then it cannot be processed
            if (event.getInterface() == null) {
                LOG.info("PollerEventProcessor: no interface found, discarding event");
            } else {
                interfaceReparentedHandler(event);
            }
        } else if (event.getUei().equals(EventConstants.NODE_LABEL_CHANGED_EVENT_UEI)) {
            if (event.getNodeid() < 0) {
                LOG.info("PollerEventProcessor: no node or interface found, discarding event");
            }
            nodeLabelChangedHandler(event);
        } else if (event.getUei().equals(EventConstants.NODE_DELETED_EVENT_UEI) || event.getUei().equals(EventConstants.DUP_NODE_DELETED_EVENT_UEI)) {
            if (event.getNodeid() < 0) {
                LOG.info("PollerEventProcessor: no node or interface found, discarding event");
            }
            // NEW NODE OUTAGE EVENTS
            nodeDeletedHandler(event);
        } else if (event.getUei().equals(EventConstants.INTERFACE_DELETED_EVENT_UEI)) {
            // If there is no interface then it cannot be processed
            if (event.getNodeid() < 0 || event.getInterface() == null) {
                LOG.info("PollerEventProcessor: invalid nodeid or no interface found, discarding event");
            } else {
                interfaceDeletedHandler(event);
            }
        } else if (event.getUei().equals(EventConstants.SERVICE_DELETED_EVENT_UEI)) {
            // If there is no interface then it cannot be processed
            if ((event.getNodeid() < 0) || (event.getInterface() == null) || (event.getService() == null)) {
                LOG.info("PollerEventProcessor: invalid nodeid or no nodeinterface or service found, discarding event");
            } else {
                serviceDeletedHandler(event);
            }
        } else if (event.getUei().equals(EventConstants.NODE_CATEGORY_MEMBERSHIP_CHANGED_EVENT_UEI)) {
            if (event.getNodeid() > 0) { 
                serviceReschedule(event, false);
            }
        } else if (event.getUei().equals(EventConstants.NODE_LOCATION_CHANGED_EVENT_UEI)) {
            if (event.getNodeid() > 0) {
                // Reschedule existing so that they can be polled at new location.
                serviceReschedule(event, true);
            }
        } else if (event.getUei().equals(EventConstants.ASSET_INFO_CHANGED_EVENT_UEI)) {
            if (event.getNodeid() > 0) {
                serviceReschedule(event, false);
            }
        } // end single event process

    } // end onEvent()

    private void serviceReschedule(Event event, boolean rescheduleExisting)   {
        final Long nodeId = event.getNodeid();

        if (nodeId == null || nodeId <= 0) {
            LOG.warn("Invalid node ID for event, skipping service reschedule: {}", event);
            return;
        }
        String nodeLabel = EventUtils.getParm(event, EventConstants.PARM_NODE_LABEL);
        try {
            nodeLabel = getPoller().getQueryManager().getNodeLabel(nodeId.intValue());
        } catch (final Exception e) {
            LOG.error("Unable to retrieve nodeLabel for node {}", nodeId, e);
        }

        String nodeLocation = null;
        try {
            nodeLocation = getPoller().getQueryManager().getNodeLocation(nodeId.intValue());
        } catch (final Exception e) {
            LOG.error("Unable to retrieve nodeLocation for node {}", nodeId, e);
        }

        getPollerConfig().rebuildPackageIpListMap();
        serviceReschedule(nodeId, nodeLabel, nodeLocation, event, rescheduleExisting);
    }

    private void rescheduleAllServices(Event event) {
        LOG.info("Poller configuration has been changed, rescheduling services.");
        getPollerConfig().rebuildPackageIpListMap();
        for (Long nodeId : getNetwork().getNodeIds()) {
            String nodeLabel = null;
            try {
                nodeLabel = getPoller().getQueryManager().getNodeLabel(nodeId.intValue());
            } catch (final Exception e) {
                LOG.error("Unable to retrieve nodeLabel for node {}", nodeId, e);
            }

            String nodeLocation = null;
            try {
                nodeLocation = getPoller().getQueryManager().getNodeLocation(nodeId.intValue());
            } catch (final Exception e) {
                LOG.error("Unable to retrieve nodeLocation for node {}", nodeId, e);
            }

            serviceReschedule(nodeId, nodeLabel, nodeLocation, event, true);
        }
    }

    private void serviceReschedule(Long nodeId, String nodeLabel, String nodeLocation, Event sourceEvent, boolean rescheduleExisting) {
        if (nodeId == null || nodeId <= 0) {
            LOG.warn("Invalid node ID for event, skipping service reschedule: {}", sourceEvent);
            return;
        }

        Date closeDate = sourceEvent.getTime();

        final Set<Service> databaseServices = new HashSet<>();

        for (final String[] s : getPoller().getQueryManager().getNodeServices(nodeId.intValue())) {
            databaseServices.add(new Service(s));
        }
        LOG.debug("# of Services in Database: {}", databaseServices.size());
        LOG.trace("Database Services: {}", databaseServices);

        final Set<Service> polledServices = new HashSet<>();

        final PollableNode pnode = getNetwork().getNode(nodeId.intValue());
        if (pnode == null) {
            LOG.debug("Node {} is not already being polled.", nodeId);
        } else {
            if (pnode.getNodeLabel() != null) {
                nodeLabel = pnode.getNodeLabel();
            }

            for (final PollableInterface iface : pnode.getInterfaces()) {
                for (final PollableService s : iface.getServices()) {
                    polledServices.add(new Service(s.getIpAddr(), s.getSvcName()));
                }
            }
            LOG.debug("# of Polled Services: {}", polledServices.size());
            LOG.trace("Polled Services: {}", polledServices);
        }

        // polledServices contains the list of services that are currently being polled
        // if any of these are no longer in the database, then remove them
        for (final Iterator<Service> iter = polledServices.iterator(); iter.hasNext(); ) {
            final Service polledService = iter.next();

            if (!databaseServices.contains(polledService)) {
                // We are polling the service, but it no longer exists.  Stop polling.
                if (pnode != null) {
                    final PollableService service = pnode.getService(polledService.getInetAddress(), polledService.getServiceName());
                    // Delete the service
                    service.delete();

                    while (!service.isDeleted()) {
                        try {
                            Thread.sleep(20);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                // Close outages.
                LOG.debug("{} should no longer be polled.  Resolving outages.", polledService);
                closeOutagesForService(sourceEvent, nodeId, closeDate, polledService);
                iter.remove();
            }
        }

        // Delete the remaining services if we which to reschedule those that are already active
        if (rescheduleExisting && pnode != null) {
            for (final Iterator<Service> iter = polledServices.iterator(); iter.hasNext(); ) {
                final Service polledService = iter.next();
                final PollableService service = pnode.getService(polledService.getInetAddress(), polledService.getServiceName());
                // Delete the service
                service.delete();

                while (!service.isDeleted()) {
                    try {
                        Thread.sleep(20);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                iter.remove();
            }
        }

        // Schedule all of the services, if they are not already
        for (final Service databaseService : databaseServices) {
            if (polledServices.contains(databaseService)) {
                LOG.debug("{} is being skipped. Already scheduled.", databaseService);
                continue;
            }

            LOG.debug("{} is being scheduled (or rescheduled) for polling.", databaseService);
            getPoller().scheduleService(nodeId.intValue(), nodeLabel, nodeLocation, databaseService.getAddress(), databaseService.getServiceName(), pnode);
            if (!getPollerConfig().isPolled(databaseService.getAddress(), databaseService.getServiceName())) {
                LOG.debug("{} is no longer polled.  Closing any pending outages.", databaseService);
                closeOutagesForService(sourceEvent, nodeId, closeDate, databaseService);
            }
        }
    }

    protected void closeOutagesForService(final Event event, final Long nodeId, final Date closeDate, final Service polledService) {
        getPoller().getQueryManager().closeOutagesForService(closeDate, event.getDbid(), nodeId.intValue(), polledService.getAddress(), polledService.getServiceName());
    }

    private void scheduledOutagesChangeHandler() {
        try {
            getPollerConfig().update();
            getPoller().getPollOutagesDao().reload();
        } catch (Throwable e) {
            LOG.error("Failed to reload PollerConfigFactory", e);
        }
        getPoller().refreshServicePackages();
    }

    /**
     * Return an id for this event listener
     */
    @Override
    public String getName() {
        return "Poller:PollerEventProcessor";
    }

    private Poller getPoller() {
        return m_poller;
    }

    private PollerConfig getPollerConfig() {
        return getPoller().getPollerConfig();
    }

    private PollableNetwork getNetwork() {
        return getPoller().getNetwork();
    }

    public static class Service implements Comparable<Service> {
        private final String m_addr;
        private final String m_serviceName;

        public Service(final String addr, final String serviceName) {
            m_addr = addr;
            m_serviceName = serviceName;
        }

        public Service(final String[] service) {
            m_addr = service[0];
            m_serviceName = service[1];
        }

        public InetAddress getInetAddress() {
            return addr(m_addr);
        }

        public String getAddress() {
            return m_addr;
        }
        public String getServiceName() {
            return m_serviceName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((m_addr == null) ? 0 : m_addr.hashCode());
            result = prime * result + ((m_serviceName == null) ? 0 : m_serviceName.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) { return true; }
            if (obj == null) { return false; }
            if (!(obj instanceof Service)) { return false; }
            final Service other = (Service) obj;
            if (m_addr == null) {
                if (other.m_addr != null) { return false; }
            } else if (!m_addr.equals(other.m_addr)) {
                return false;
            }
            if (m_serviceName == null) {
                if (other.m_serviceName != null) { return false; }
            } else if (!m_serviceName.equals(other.m_serviceName)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "Service [" + m_addr + ":" + m_serviceName + "]";
        }

        @Override
        public int compareTo(final Service o) {
            int ret = m_addr.compareTo(o.m_addr);
            if (ret == 0) {
                ret = m_serviceName.compareTo(o.m_serviceName);
            }
            return ret;
        }
    }
}
