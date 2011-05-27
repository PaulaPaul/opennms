/**
 * 
 */
package org.opennms.netmgt.provision.service;

import static org.opennms.core.utils.LogUtils.debugf;

import java.net.InetAddress;
import java.util.List;

import org.opennms.core.tasks.BatchTask;
import org.opennms.core.tasks.RunInBatch;
import org.opennms.netmgt.dao.SnmpAgentConfigFactory;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.updates.NodeUpdate;
import org.opennms.netmgt.provision.NodePolicy;
import org.opennms.netmgt.provision.service.snmp.SystemGroup;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.SnmpUtils;
import org.opennms.netmgt.snmp.SnmpWalker;
import org.springframework.util.Assert;

final class NodeInfoScan implements RunInBatch {

    private final SnmpAgentConfigFactory m_agentConfigFactory;
    private final InetAddress m_agentAddress;
    private final String m_foreignSource;
    private OnmsNode m_node;
    private NodeUpdate m_nodeUpdate;
    private Integer m_nodeId;
    private boolean restoreCategories = false;
    private final ProvisionService m_provisionService;
    private final ScanProgress m_scanProgress;
    

    NodeInfoScan(OnmsNode node, InetAddress agentAddress, String foreignSource, ScanProgress scanProgress, SnmpAgentConfigFactory agentConfigFactory, ProvisionService provisionService, Integer nodeId){
        m_node = node;
        m_nodeUpdate = new NodeUpdate(nodeId, foreignSource, node.getForeignId());
        m_agentAddress = agentAddress;
        m_foreignSource = foreignSource;
        m_scanProgress = scanProgress;
        m_agentConfigFactory = agentConfigFactory;
        m_provisionService = provisionService;
        m_nodeId = nodeId;
    }

    /** {@inheritDoc} */
    public void run(BatchTask phase) {
        
        phase.getBuilder().addSequence(
                new RunInBatch() {
                    public void run(BatchTask batch) {
                        collectNodeInfo();
                    }
                },
                new RunInBatch() {
                    public void run(BatchTask phase) {
                        doPersistNodeInfo();
                    }
                });
    }

    private InetAddress getAgentAddress() {
        return m_agentAddress;
    }

    private SnmpAgentConfig getAgentConfig(InetAddress primaryAddress) {
        return getAgentConfigFactory().getAgentConfig(primaryAddress);
    }

    private SnmpAgentConfigFactory getAgentConfigFactory() {
        return m_agentConfigFactory;
    }

    private String getForeignSource() {
        return m_foreignSource;
    }

    private ProvisionService getProvisionService() {
        return m_provisionService;
    }

    private void abort(String reason) {
        m_scanProgress.abort(reason);
    }

    private OnmsNode getNode() {
        return m_node;
    }
    
    private NodeUpdate getNodeUpdate() {
    	return m_nodeUpdate;
    }
    
    private Integer getNodeId() {
        return m_nodeId;
    }

    private void setNode(OnmsNode node) {
        m_node = node;
    }

    private void collectNodeInfo() {
        Assert.notNull(getAgentConfigFactory(), "agentConfigFactory was not injected");
        InetAddress primaryAddress = getAgentAddress();
        SnmpAgentConfig agentConfig = getAgentConfig(primaryAddress);
        
        SystemGroup systemGroup = new SystemGroup(primaryAddress);
        
        SnmpWalker walker = SnmpUtils.createWalker(agentConfig, "systemGroup", systemGroup);
        walker.start();
        
        try {
        
            walker.waitFor();
        
            if (walker.timedOut()) {
                abort("Aborting node scan : Agent timed out while scanning the system table");
            }
            else if (walker.failed()) {
                abort("Aborting node scan : Agent failed while scanning the system table: " + walker.getErrorMessage());
            } else {
        
                systemGroup.updateSnmpDataForNode(getNode());
            }
        
            List<NodePolicy> nodePolicies = getProvisionService().getNodePoliciesForForeignSource(getEffectiveForeignSource());
            
            OnmsNode node = null;
            if (isAborted()) {
                if (getNodeId() != null && nodePolicies.size() > 0) {
                    restoreCategories = true;
                    node = m_provisionService.getDbNodeInitCat(getNodeId());
                    debugf(this, "collectNodeInfo: checking %d node policies for restoration of categories", nodePolicies.size());
                }
            } else {
                node = getNode();
            }
            for(NodePolicy policy : nodePolicies) {
                if (node != null) {
                    node = policy.apply(node);
                }
            }
        
            if (node == null) {
                restoreCategories = false;
                if (!isAborted()) {
                    String reason = "Aborted scan of node due to configured policy";
                    abort(reason);
                }
            } else {
                setNode(node);
            }
        
        } catch (InterruptedException e) {
            abort("Aborting node scan : Scan thread interrupted!");
        }
    }

    private String getEffectiveForeignSource() {
        return getForeignSource()  == null ? "default" : getForeignSource();
    }

    private void doPersistNodeInfo() {
        if (restoreCategories) {
            debugf(this, "doPersistNodeInfo: Restoring %d categories to DB", getNode().getCategories().size());
        }
        if (!isAborted() || restoreCategories) {
            getProvisionService().updateNodeAttributes(getNode(), getNodeUpdate());
        }
    }

    private boolean isAborted() {
        return m_scanProgress.isAborted();
    }
}
