//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2002-2003 Blast Internet Services, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of Blast Internet Services, Inc.
//
// Modifications:
//
// 2003 Jan 31: Cleaned up some unused imports.
// 
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
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
//      http://www.blast.com/
//
// Tab Size = 8
//

package org.opennms.netmgt.notifd;

import java.lang.*;
import java.sql.*;
import java.util.*;

import java.lang.reflect.UndeclaredThrowableException;

import org.opennms.core.utils.ThreadCategory;

import org.opennms.core.fiber.PausableFiber;
import org.opennms.core.resource.*;
import org.opennms.core.resource.db.*;

import org.opennms.netmgt.config.*;
import org.opennms.netmgt.config.notifd.*;

/**
 * This class is used to represent the notification execution
 * service. When an event is received by this service that has
 * one of either a notification, trouble ticket, or auto action
 * then a process is launched to execute the appropriate commands.
 *
 * @author <a href="mailto:mike@opennms.org">Mike Davidson</a>
 * @author <a href="mailto:weave@oculan.com">Brian Weaver</a>
 * @author <a href="http://www.opennms.org/">OpenNMS.org</a>
 *
 */
public final class Notifd
	implements PausableFiber
{
	/**
	 * Logging categoyr for log4j
	 */
	private static String LOG4J_CATEGORY = "OpenNMS.Notifd";
        
	/**
	 * The signlton instance.
	 */
	private static final Notifd m_singleton = new Notifd();
        
	/**
	 * The map for holding different notice queues
	 */
	private Map m_noticeQueues;
        
        /**
         *
         */
        private Map m_queueHandlers;
        
	/**
	 * The broadcast event receiver.
	 */
	private BroadcastEventProcessor	m_eventReader;
        
	/**
	 * The current status of this fiber
	 */
	private int			m_status;

	/**
	 * Constructs a new Notifd service daemon.
	 */
	private Notifd()
	{
                try {
                        NotifdConfigFactory.init();
                } catch(Throwable t)
		{
			ThreadCategory.getInstance(getClass()).warn("start: Failed to init NotifdConfigFactory.", t);
		}
	}
        
	public synchronized void init()
	{
                ThreadCategory.setPrefix(LOG4J_CATEGORY);
                
                m_noticeQueues   = new HashMap();
                m_queueHandlers  = new HashMap();
		m_eventReader    = null;
                try
                {
                        NotifdConfigFactory.reload();
                        
                        ThreadCategory.getInstance(getClass()).info("Notification status = " + NotifdConfigFactory.getInstance().getPrettyStatus());
                        
                        Queue queues[] = NotifdConfigFactory.getInstance().getConfiguration().getQueue();
                        for (int i = 0; i < queues.length; i++)
                        {
                                NoticeQueue curQueue = new NoticeQueue();
                                
                                Class handlerClass = Class.forName(queues[i].getHandlerClass().getName());
                                NotifdQueueHandler handlerQueue = (NotifdQueueHandler)handlerClass.newInstance();
                                
                                handlerQueue.setQueueID(queues[i].getQueueId());
                                handlerQueue.setNoticeQueue(curQueue);
                                handlerQueue.setInterval(queues[i].getInterval());
                                
                                m_noticeQueues.put(queues[i].getQueueId(), curQueue);
                                m_queueHandlers.put(queues[i].getQueueId(), handlerQueue);
                        }
                }
                catch(Throwable t)
		{
			ThreadCategory.getInstance(getClass()).warn("start: Failed to load notifd queue handlers.", t);
		}
                
		// start the event reader
		//
		try
		{
			NotifdConfigFactory.init();
			m_eventReader = new BroadcastEventProcessor(m_noticeQueues);
		}
		catch(Exception ex)
		{
			ThreadCategory.getInstance(getClass()).error("Failed to setup event receiver", ex);
			throw new UndeclaredThrowableException(ex);
		}
	}
	/**
	 * Starts the <em>Notifd</em> service. The process of starting
	 * the service involves starting the queue handlers and starting 
	 * an event receiver.
	 */
	public synchronized void start()
	{
                ThreadCategory.setPrefix(LOG4J_CATEGORY);
                
		Iterator i = m_queueHandlers.keySet().iterator();
                while(i.hasNext())
                {
                        NotifdQueueHandler curHandler = (NotifdQueueHandler)m_queueHandlers.get(i.next());
                        curHandler.start();
                }
	
		// create the control receiver
		m_status = RUNNING;
	}
        
	/**
	 * Stops the currently running service. If the service is
	 * not running then the command is silently discarded.
	 *
	 */
	public synchronized void stop()
	{
		m_status = STOP_PENDING;

		try
		{
			Iterator i = m_queueHandlers.keySet().iterator();
                        while(i.hasNext())
                        {
                                NotifdQueueHandler curHandler = (NotifdQueueHandler)m_queueHandlers.get(i.next());
                                curHandler.stop();
                        }
		}
		catch(Exception e) { }
                
		if(m_eventReader != null)
			m_eventReader.close();
                
		m_eventReader = null;

		m_status = STOPPED;

	}
        
	/**
	 * Returns the current status of the service.
	 *
	 * @return The service's status.
	 */
	public synchronized int getStatus()
	{
		return m_status;
	}
        
	/**
	 * Returns the name of the service.
	 *
	 * @return The service's name.
	 */
	public String getName()
	{
		return "OpenNMS.Notifd";
	}
        
	/**
	 * Pauses the service if its currently running
	 */
	public synchronized void pause()
	{
		if(m_status != RUNNING)
			return;
                
                m_status = PAUSE_PENDING;
                
                Iterator i = m_queueHandlers.keySet().iterator();
                while(i.hasNext())
                {
                        NotifdQueueHandler curHandler = (NotifdQueueHandler)m_queueHandlers.get(i.next());
                        curHandler.pause();
                }

		m_status = PAUSED;
	}

	/**
	 * Resumes the service if its currently paused
	 */
	public synchronized void resume()
	{
		if(m_status != PAUSED)
			return;
                
		m_status = RESUME_PENDING;
                
                Iterator i = m_queueHandlers.keySet().iterator();
                while(i.hasNext())
                {
                        NotifdQueueHandler curHandler = (NotifdQueueHandler)m_queueHandlers.get(i.next());
                        curHandler.resume();
                }

		m_status = RUNNING;
	}

	/**
	 * Returns the singular instance of the Notifd
	 * daemon. There can be only one instance of this
	 * service per virtual machine.
	 */
	public static Notifd getInstance()
	{
		return m_singleton;
	}
}
