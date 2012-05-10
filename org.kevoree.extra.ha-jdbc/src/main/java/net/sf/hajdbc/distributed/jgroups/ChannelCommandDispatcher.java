/*
 * HA-JDBC: High-Availability JDBC
 * Copyright 2004-2009 Paul Ferraro
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.hajdbc.distributed.jgroups;

import net.sf.hajdbc.Messages;
import net.sf.hajdbc.distributed.*;
import net.sf.hajdbc.distributed.MembershipListener;
import net.sf.hajdbc.util.Objects;
import org.jgroups.*;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.util.Rsp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A JGroups-based command dispatcher.
 * 
 * @author Paul Ferraro
 * @see org.jgroups.blocks.MessageDispatcher
 * @param <C> the execution context type
 */
public class ChannelCommandDispatcher<C> implements RequestHandler, CommandDispatcher<C>, org.jgroups.MembershipListener, MessageListener
{
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private final String id;
	private final long timeout;
	private final MessageDispatcher dispatcher;
	private final C context;
	private final AtomicReference<View> viewReference = new AtomicReference<View>();
	private final MembershipListener membershipListener;
	private final Stateful stateful;
	
	/**
	 * Constructs a new ChannelCommandDispatcher.
	 * @param id the channel name
	 * @param provider the channel provider
	 * @param context the execution context
	 * @param stateful the state transfer handler
	 * @param membershipListener notified of membership changes
	 * @throws Exception if channel cannot be created
	 */
	public ChannelCommandDispatcher(String id, ChannelProvider provider, C context, Stateful stateful, MembershipListener membershipListener) throws Exception
	{
		this.id = id;
		this.context = context;
		this.stateful = stateful;
		this.membershipListener = membershipListener;
		
		this.dispatcher = new MessageDispatcher(provider.getChannel(), this, this, this);
		this.timeout = provider.getTimeout();
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.Lifecycle#start()
	 */
	@Override
	public void start() throws Exception
	{
		Channel channel = this.dispatcher.getChannel();
		
		channel.setDiscardOwnMessages(true);
		
		// Connect and fetch state
		channel.connect(this.id, null, 0);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.Lifecycle#stop()
	 */
	@Override
	public void stop()
	{
		Channel channel = this.dispatcher.getChannel();
		
		if (channel.isOpen())
		{
			if (channel.isConnected())
			{
				channel.disconnect();
			}
			
			channel.close();
		}
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.distributed.CommandDispatcher#executeAll(net.sf.hajdbc.distributed.Command)
	 */
	@Override
	public <R> Map<Member, R> executeAll(Command<R, C> command)
	{
		Message message = new Message(null, this.getLocalAddress(), command);
		
		try
		{
			Map<Address, Rsp<R>> responses = this.dispatcher.castMessage(null, message, new RequestOptions(ResponseMode.GET_ALL, this.timeout));
			
			if (responses == null) return Collections.emptyMap();
			
			Map<Member, R> results = new TreeMap<Member, R>();
			
			for (Map.Entry<Address, Rsp<R>> entry: responses.entrySet())
			{
				Rsp<R> response = entry.getValue();
	
				results.put(new AddressMember(entry.getKey()), response.wasReceived() ? response.getValue() : null);
			}
			
			return results;
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.distributed.CommandDispatcher#executeCoordinator(net.sf.hajdbc.distributed.Command)
	 */
	@Override
	public <R> R executeCoordinator(Command<R, C> command)
	{
		while (true)
		{
			Message message = new Message(this.getCoordinatorAddress(), this.getLocalAddress(), command);

			try
			{
				return this.dispatcher.sendMessage(message, new RequestOptions(ResponseMode.GET_ALL, this.timeout));
			}
			catch (Exception e)
			{
				return null;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.distributed.CommandDispatcher#isCoordinator()
	 */
	@Override
	public boolean isCoordinator()
	{
		return this.getLocalAddress().equals(this.getCoordinatorAddress());
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.distributed.CommandDispatcher#getLocal()
	 */
	@Override
	public Member getLocal()
	{
		return new AddressMember(this.getLocalAddress());
	}
	
	private Address getLocalAddress()
	{
		return this.dispatcher.getChannel().getAddress();
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.distributed.CommandDispatcher#getCoordinator()
	 */
	@Override
	public Member getCoordinator()
	{
		return new AddressMember(this.getCoordinatorAddress());
	}

	private Address getCoordinatorAddress()
	{
		return this.dispatcher.getChannel().getView().getMembers().get(0);
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.blocks.RequestHandler#handle(org.jgroups.Message)
	 */
	@Override
	public Object handle(Message message)
	{
		Command<Object, C> command = Objects.deserialize(message.getRawBuffer());

		this.logger.debug(Messages.COMMAND_RECEIVED.getMessage(command, message.getSrc()));
		
		return command.execute(this.context);
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MembershipListener#viewAccepted(org.jgroups.View)
	 */
	@Override
	public void viewAccepted(View view)
	{
		if (this.membershipListener != null)
		{
			View oldView = this.viewReference.getAndSet(view);
			
			for (Address address: view.getMembers())
			{
				if ((oldView == null) || !oldView.containsMember(address))
				{
					this.membershipListener.added(new AddressMember(address));
				}
			}
			
			if (oldView != null)
			{
				for (Address address: oldView.getMembers())
				{
					if (!view.containsMember(address))
					{
						this.membershipListener.removed(new AddressMember(address));
					}
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MessageListener#getState(java.io.OutputStream)
	 */
	@Override
	public void getState(OutputStream output) throws Exception
	{
		this.stateful.writeState(new ObjectOutputStream(output));
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MessageListener#setState(java.io.InputStream)
	 */
	@Override
	public void setState(InputStream input) throws Exception
	{
		this.stateful.readState(new ObjectInputStream(input));
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MembershipListener#suspect(org.jgroups.Address)
	 */
	@Override
	public void suspect(Address member)
	{
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MembershipListener#block()
	 */
	@Override
	public void block()
	{
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MembershipListener#unblock()
	 */
	@Override
	public void unblock()
	{
	}

	/**
	 * {@inheritDoc}
	 * @see org.jgroups.MessageListener#receive(org.jgroups.Message)
	 */
	@Override
	public void receive(Message message)
	{
	}
}
