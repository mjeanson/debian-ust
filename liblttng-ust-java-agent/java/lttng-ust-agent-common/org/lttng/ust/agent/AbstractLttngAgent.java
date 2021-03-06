/*
 * Copyright (C) 2015 - EfficiOS Inc., Alexandre Montplaisir <alexmonthy@efficios.com>
 * Copyright (C) 2013 - David Goulet <dgoulet@efficios.com>
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License, version 2.1 only,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package org.lttng.ust.agent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.lttng.ust.agent.client.ILttngTcpClientListener;
import org.lttng.ust.agent.client.LttngTcpSessiondClient;
import org.lttng.ust.agent.filter.FilterChangeNotifier;
import org.lttng.ust.agent.session.EventRule;
import org.lttng.ust.agent.utils.LttngUstAgentLogger;

/**
 * Base implementation of a {@link ILttngAgent}.
 *
 * @author Alexandre Montplaisir
 * @param <T>
 *            The type of logging handler that should register to this agent
 */
public abstract class AbstractLttngAgent<T extends ILttngHandler>
		implements ILttngAgent<T>, ILttngTcpClientListener {

	private static final String WILDCARD = "*";
	private static final int INIT_TIMEOUT = 3; /* Seconds */

	/** The handlers registered to this agent */
	private final Set<T> registeredHandlers = new HashSet<T>();

	/**
	 * The trace events currently enabled in the sessions.
	 *
	 * The key represents the event name, the value is the ref count (how many
	 * different sessions currently have this event enabled). Once the ref count
	 * falls to 0, this means we can avoid sending log events through JNI
	 * because nobody wants them.
	 *
	 * It uses a concurrent hash map, so that the {@link #isEventEnabled} and
	 * read methods do not need to take a synchronization lock.
	 */
	private final Map<String, Integer> enabledEvents = new ConcurrentHashMap<String, Integer>();

	/**
	 * The trace events prefixes currently enabled in the sessions, which means
	 * the event names finishing in *, like "abcd*". We track them separately
	 * from the standard event names, so that we can use {@link String#equals}
	 * and {@link String#startsWith} appropriately.
	 *
	 * We track the lone wildcard "*" separately, in {@link #enabledWildcards}.
	 */
	private final NavigableMap<String, Integer> enabledEventPrefixes =
			new ConcurrentSkipListMap<String, Integer>();

	/** Number of sessions currently enabling the wildcard "*" event */
	private final AtomicInteger enabledWildcards = new AtomicInteger(0);

	/**
	 * The application contexts currently enabled in the tracing sessions.
	 *
	 * It is first indexed by context retriever, then by context name. This
	 * allows to efficiently query all the contexts for a given retriever.
	 *
	 * Works similarly as {@link #enabledEvents}, but for app contexts (and with
	 * an extra degree of indexing).
	 *
	 * TODO Could be changed to a Guava Table once/if we start using it.
	 */
	private final Map<String, Map<String, Integer>> enabledAppContexts = new ConcurrentHashMap<String, Map<String, Integer>>();

	/** Tracing domain. Defined by the sub-classes via the constructor. */
	private final Domain domain;

	/* Lazy-loaded sessiond clients and their thread objects */
	private LttngTcpSessiondClient rootSessiondClient = null;
	private LttngTcpSessiondClient userSessiondClient = null;
	private Thread rootSessiondClientThread = null;
	private Thread userSessiondClientThread = null;

	/** Indicates if this agent has been initialized. */
	private boolean initialized = false;

	/**
	 * Constructor. Should only be called by sub-classes via super(...);
	 *
	 * @param domain
	 *            The tracing domain of this agent.
	 */
	protected AbstractLttngAgent(Domain domain) {
		this.domain = domain;
	}

	@Override
	public Domain getDomain() {
		return domain;
	}

	@Override
	public void registerHandler(T handler) {
		synchronized (registeredHandlers) {
			if (registeredHandlers.isEmpty()) {
				/*
				 * This is the first handler that registers, we will initialize
				 * the agent.
				 */
				init();
			}
			registeredHandlers.add(handler);
		}
	}

	@Override
	public void unregisterHandler(T handler) {
		synchronized (registeredHandlers) {
			registeredHandlers.remove(handler);
			if (registeredHandlers.isEmpty()) {
				/* There are no more registered handlers, close the connection. */
				dispose();
			}
		}
	}

	private void init() {
		/*
		 * Only called from a synchronized (registeredHandlers) block, should
		 * not need additional synchronization.
		 */
		if (initialized) {
			return;
		}

		LttngUstAgentLogger.log(AbstractLttngAgent.class, "Initializing Agent for domain: " + domain.name());

		String rootClientThreadName = "Root sessiond client started by agent: " + this.getClass().getSimpleName();

		rootSessiondClient = new LttngTcpSessiondClient(this, getDomain().value(), true);
		rootSessiondClientThread = new Thread(rootSessiondClient, rootClientThreadName);
		rootSessiondClientThread.setDaemon(true);
		rootSessiondClientThread.start();

		String userClientThreadName = "User sessiond client started by agent: " + this.getClass().getSimpleName();

		userSessiondClient = new LttngTcpSessiondClient(this, getDomain().value(), false);
		userSessiondClientThread = new Thread(userSessiondClient, userClientThreadName);
		userSessiondClientThread.setDaemon(true);
		userSessiondClientThread.start();

		/* Give the threads' registration a chance to end. */
		if (!rootSessiondClient.waitForConnection(INIT_TIMEOUT)) {
			userSessiondClient.waitForConnection(INIT_TIMEOUT);
		}

		initialized = true;
	}

	/**
	 * Dispose the agent
	 */
	private void dispose() {
		LttngUstAgentLogger.log(AbstractLttngAgent.class, "Disposing Agent for domain: " + domain.name());

		/*
		 * Only called from a synchronized (registeredHandlers) block, should
		 * not need additional synchronization.
		 */
		rootSessiondClient.close();
		userSessiondClient.close();

		try {
			rootSessiondClientThread.join();
			userSessiondClientThread.join();

		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		rootSessiondClient = null;
		rootSessiondClientThread = null;
		userSessiondClient = null;
		userSessiondClientThread = null;

		/*
		 * Send filter change notifications for all event rules currently
		 * active, then clear them.
		 */
		FilterChangeNotifier fcn = FilterChangeNotifier.getInstance();

		for (Map.Entry<String, Integer> entry : enabledEvents.entrySet()) {
			String eventName = entry.getKey();
			Integer nb = entry.getValue();
			for (int i = 0; i < nb.intValue(); i++) {
				fcn.removeEventRules(eventName);
			}
		}
		enabledEvents.clear();

		for (Map.Entry<String, Integer> entry : enabledEventPrefixes.entrySet()) {
			/* Re-add the * at the end, the FCN tracks the rules that way */
			String eventName = (entry.getKey() + "*");
			Integer nb = entry.getValue();
			for (int i = 0; i < nb.intValue(); i++) {
				fcn.removeEventRules(eventName);
			}
		}
		enabledEventPrefixes.clear();

		int wildcardRules = enabledWildcards.getAndSet(0);
		for (int i = 0; i < wildcardRules; i++) {
			fcn.removeEventRules(WILDCARD);
		}

		/*
		 * Also clear tracked app contexts (no filter notifications sent for
		 * those currently).
		 */
		enabledAppContexts.clear();

		initialized = false;
	}

	@Override
	public boolean eventEnabled(EventRule eventRule) {
		/* Notify the filter change manager of the command */
		FilterChangeNotifier.getInstance().addEventRule(eventRule);

		String eventName = eventRule.getEventName();

		if (eventName.equals(WILDCARD)) {
			enabledWildcards.incrementAndGet();
			return true;
		}
		if (eventName.endsWith(WILDCARD)) {
			/* Strip the "*" from the name. */
			String prefix = eventName.substring(0, eventName.length() - 1);
			return incrementRefCount(prefix, enabledEventPrefixes);
		}

		return incrementRefCount(eventName, enabledEvents);
	}

	@Override
	public boolean eventDisabled(String eventName) {
		/* Notify the filter change manager of the command */
		FilterChangeNotifier.getInstance().removeEventRules(eventName);

		if (eventName.equals(WILDCARD)) {
			int newCount = enabledWildcards.decrementAndGet();
			if (newCount < 0) {
				/* Event was not enabled, bring the count back to 0 */
				enabledWildcards.incrementAndGet();
				return false;
			}
			return true;
		}

		if (eventName.endsWith(WILDCARD)) {
			/* Strip the "*" from the name. */
			String prefix = eventName.substring(0, eventName.length() - 1);
			return decrementRefCount(prefix, enabledEventPrefixes);
		}

		return decrementRefCount(eventName, enabledEvents);
	}

	@Override
	public boolean appContextEnabled(String contextRetrieverName, String contextName) {
		synchronized (enabledAppContexts) {
			Map<String, Integer> retrieverMap = enabledAppContexts.get(contextRetrieverName);
			if (retrieverMap == null) {
				/* There is no submap for this retriever, let's create one. */
				retrieverMap = new ConcurrentHashMap<String, Integer>();
				enabledAppContexts.put(contextRetrieverName, retrieverMap);
			}

			return incrementRefCount(contextName, retrieverMap);
		}
	}

	@Override
	public boolean appContextDisabled(String contextRetrieverName, String contextName) {
		synchronized (enabledAppContexts) {
			Map<String, Integer> retrieverMap = enabledAppContexts.get(contextRetrieverName);
			if (retrieverMap == null) {
				/* There was no submap for this retriever, invalid command? */
				return false;
			}

			boolean ret = decrementRefCount(contextName, retrieverMap);

			/* If the submap is now empty we can remove it from the main map. */
			if (retrieverMap.isEmpty()) {
				enabledAppContexts.remove(contextRetrieverName);
			}

			return ret;
		}
	}

	/*
	 * Implementation of this method is domain-specific.
	 */
	@Override
	public abstract Collection<String> listAvailableEvents();

	@Override
	public boolean isEventEnabled(String eventName) {
		/* If at least one session enabled the "*" wildcard, send the event */
		if (enabledWildcards.get() > 0) {
			return true;
		}

		/* Check if at least one session wants this exact event name */
		if (enabledEvents.containsKey(eventName)) {
			return true;
		}

		/* Look in the enabled prefixes if one of them matches the event */
		String potentialMatch = enabledEventPrefixes.floorKey(eventName);
		if (potentialMatch != null && eventName.startsWith(potentialMatch)) {
			return true;
		}

		return false;
	}

	@Override
	public Collection<Map.Entry<String, Map<String, Integer>>> getEnabledAppContexts() {
		return enabledAppContexts.entrySet();
	}

	private static boolean incrementRefCount(String key, Map<String, Integer> refCountMap) {
		synchronized (refCountMap) {
			Integer count = refCountMap.get(key);
			if (count == null) {
				/* This is the first instance of this event being enabled */
				refCountMap.put(key, Integer.valueOf(1));
				return true;
			}
			if (count.intValue() <= 0) {
				/* It should not have been in the map in the first place! */
				throw new IllegalStateException();
			}
			/* The event was already enabled, increment its refcount */
			refCountMap.put(key, Integer.valueOf(count.intValue() + 1));
			return true;
		}
	}

	private static boolean decrementRefCount(String key, Map<String, Integer> refCountMap) {
		synchronized (refCountMap) {
			Integer count = refCountMap.get(key);
			if (count == null || count.intValue() <= 0) {
				/*
				 * The sessiond asked us to disable an event that was not
				 * enabled previously. Command error?
				 */
				return false;
			}
			if (count.intValue() == 1) {
				/*
				 * This is the last instance of this event being disabled,
				 * remove it from the map so that we stop sending it.
				 */
				refCountMap.remove(key);
				return true;
			}
			/*
			 * Other sessions are still looking for this event, simply decrement
			 * its refcount.
			 */
			refCountMap.put(key, Integer.valueOf(count.intValue() - 1));
			return true;
		}
	}
}

