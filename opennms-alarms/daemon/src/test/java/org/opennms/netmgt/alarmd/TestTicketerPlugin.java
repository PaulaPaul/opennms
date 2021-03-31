/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2021 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2021 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.alarmd;

import org.opennms.api.integration.ticketing.Plugin;
import org.opennms.api.integration.ticketing.PluginException;
import org.opennms.api.integration.ticketing.Ticket;

/*
 TestTicketerPlugin used in DefaultAlarmTicketerServiceIT
 */
public class TestTicketerPlugin implements Plugin {

    private Ticket m_ticket;

    @Override
    public Ticket get(String ticketId) throws PluginException {
        if (ticketId.equals("testId")) {
            return m_ticket;
        }
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        if(ticketId.equals("testId2")) {
            ticket.setState(Ticket.State.CANCELLED);
        }
        return ticket;
    }

    @Override
    public void saveOrUpdate(Ticket ticket) throws PluginException {
        m_ticket = ticket;
        m_ticket.setId("testId");
    }
}