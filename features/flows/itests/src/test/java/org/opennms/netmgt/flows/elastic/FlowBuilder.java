/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.flows.elastic;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class FlowBuilder {

    private final List<FlowDocument> flows = new ArrayList<>();

    private NodeDocument exporterNode;
    private Integer snmpInterfaceId;
    private String application = null;
    private Direction direction = Direction.INGRESS;
    private String srcHostname = null;
    private String dstHostname = null;
    private Integer tos = null;

    public FlowBuilder withExporter(String fs, String fid, int nodeId) {
        exporterNode = new NodeDocument();
        exporterNode.setForeignSource(fs);
        exporterNode.setForeignId(fid);
        exporterNode.setNodeId(nodeId);
        return this;
    }

    public FlowBuilder withSnmpInterfaceId(Integer snmpInterfaceId) {
        this.snmpInterfaceId = snmpInterfaceId;
        return this;
    }

    public FlowBuilder withApplication(String application) {
        this.application = application;
        return this;
    }

    public FlowBuilder withDirection(Direction direction) {
        this.direction = Objects.requireNonNull(direction);
        return this;
    }

    public FlowBuilder withHostnames(final String srcHostname, final String dstHostname) {
        this.srcHostname = srcHostname;
        this.dstHostname = dstHostname;
        return this;
    }

    public FlowBuilder withTos(final Integer tos) {
        this.tos = tos;
        return this;
    }

    public FlowBuilder withFlow(Date date, String sourceIp, int sourcePort, String destIp, int destPort, long numBytes) {
        return withFlow(date, date, date, sourceIp, sourcePort, destIp, destPort, numBytes);
    }

    public FlowBuilder withFlow(Date firstSwitched, Date lastSwitched, String sourceIp, int sourcePort, String destIp, int destPort, long numBytes) {
        return withFlow(firstSwitched, firstSwitched, lastSwitched, sourceIp, sourcePort, destIp, destPort, numBytes);
    }

    public FlowBuilder withFlow(Date firstSwitched, Date deltaSwitched, Date lastSwitched, String sourceIp, int sourcePort, String destIp, int destPort, long numBytes) {
        final FlowDocument flow = new FlowDocument();
        flow.setTimestamp(lastSwitched.getTime());
        flow.setFirstSwitched(firstSwitched.getTime());
        flow.setDeltaSwitched(deltaSwitched.getTime());
        flow.setLastSwitched(lastSwitched.getTime());
        flow.setSrcAddr(sourceIp);
        flow.setSrcPort(sourcePort);
        if (this.srcHostname != null) {
            flow.setSrcAddrHostname(this.srcHostname);
        }
        flow.setDstAddr(destIp);
        flow.setDstPort(destPort);
        if (this.dstHostname != null) {
            flow.setDstAddrHostname(this.dstHostname);
        };
        flow.setBytes(numBytes);
        flow.setProtocol(6); // TCP
        if (exporterNode !=  null) {
            flow.setNodeExporter(exporterNode);
        }
        if (direction == Direction.INGRESS) {
            flow.setInputSnmp(snmpInterfaceId);
        } else if (direction == Direction.EGRESS) {
            flow.setOutputSnmp(snmpInterfaceId);
        }
        flow.setApplication(application);
        flow.setDirection(direction);
        flow.setSrcLocality(Locality.PRIVATE);
        flow.setDstLocality(Locality.PRIVATE);
        flow.setFlowLocality(Locality.PRIVATE);
        flow.setTos(tos);
        flows.add(flow);
        return this;
    }

    public List<FlowDocument> build() {
        return flows;
    }
}
