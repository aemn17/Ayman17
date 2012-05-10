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
package net.sf.hajdbc.sql;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseClusterConfiguration;
import net.sf.hajdbc.Dialect;
import net.sf.hajdbc.ExecutorServiceProvider;
import net.sf.hajdbc.Messages;
import net.sf.hajdbc.SynchronizationStrategy;
import net.sf.hajdbc.TransactionMode;
import net.sf.hajdbc.balancer.BalancerFactory;
import net.sf.hajdbc.balancer.BalancerFactoryEnum;
import net.sf.hajdbc.cache.DatabaseMetaDataCacheFactory;
import net.sf.hajdbc.cache.DatabaseMetaDataCacheFactoryEnum;
import net.sf.hajdbc.codec.CodecFactory;
import net.sf.hajdbc.codec.simple.SimpleCodecFactory;
import net.sf.hajdbc.dialect.CustomDialectFactory;
import net.sf.hajdbc.dialect.DialectFactory;
import net.sf.hajdbc.dialect.DialectFactoryEnum;
import net.sf.hajdbc.distributed.CommandDispatcherFactory;
import net.sf.hajdbc.distributed.jgroups.DefaultChannelProvider;
import net.sf.hajdbc.durability.DurabilityFactory;
import net.sf.hajdbc.durability.DurabilityFactoryEnum;
import net.sf.hajdbc.management.DefaultMBeanRegistrar;
import net.sf.hajdbc.management.MBeanRegistrar;
import net.sf.hajdbc.state.StateManagerFactory;
import net.sf.hajdbc.state.sql.SQLStateManagerFactory;
import net.sf.hajdbc.tx.SimpleTransactionIdentifierFactory;
import net.sf.hajdbc.tx.TransactionIdentifierFactory;
import net.sf.hajdbc.tx.UUIDTransactionIdentifierFactory;

import org.quartz.CronExpression;

/**
 * @author paul
 *
 */
@XmlType(propOrder = { "dispatcherFactory", "synchronizationStrategyDescriptors", "stateManagerFactoryDescriptor" })
public abstract class AbstractDatabaseClusterConfiguration<Z, D extends Database<Z>> implements DatabaseClusterConfiguration<Z, D>
{
	private static final long serialVersionUID = -2808296483725374829L;

	@XmlElement(name = "distributable", type = DefaultChannelProvider.class)
	private CommandDispatcherFactory dispatcherFactory;
	
	private Map<String, SynchronizationStrategy> synchronizationStrategies = new HashMap<String, SynchronizationStrategy>();
	private StateManagerFactory stateManagerFactory = new SQLStateManagerFactory();
	protected abstract NestedConfiguration<Z, D> getNestedConfiguration();
	
	@SuppressWarnings("unused")
	@XmlElement(name = "sync")
	private SynchronizationStrategyDescriptor[] getSynchronizationStrategyDescriptors() throws Exception
	{
		List<SynchronizationStrategyDescriptor> results = new ArrayList<SynchronizationStrategyDescriptor>(this.synchronizationStrategies.size());
		SynchronizationStrategyDescriptorAdapter adapter = new SynchronizationStrategyDescriptorAdapter();

		for (Map.Entry<String, SynchronizationStrategy> entry: this.synchronizationStrategies.entrySet())
		{
			SynchronizationStrategyDescriptor result = adapter.marshal(entry.getValue());
			
			result.setId(entry.getKey());
			
			results.add(result);
		}
		
		return results.toArray(new SynchronizationStrategyDescriptor[results.size()]);
	}
	
	@SuppressWarnings("unused")
	private void setSynchronizationStrategyDescriptors(SynchronizationStrategyDescriptor[] entries) throws Exception
	{
		SynchronizationStrategyDescriptorAdapter adapter = new SynchronizationStrategyDescriptorAdapter();
		
		for (SynchronizationStrategyDescriptor entry: entries)
		{
			SynchronizationStrategy strategy = adapter.unmarshal(entry);
			
			this.synchronizationStrategies.put(entry.getId(), strategy);
		}
	}
	
	@SuppressWarnings("unused")
	@XmlElement(name = "state")
	private StateManagerFactoryDescriptor getStateManagerFactoryDescriptor() throws Exception
	{
		return new StateManagerFactoryDescriptorAdapter().marshal(this.stateManagerFactory);
	}
	
	@SuppressWarnings("unused")
	private void setStateManagerFactoryDescriptor(StateManagerFactoryDescriptor descriptor) throws Exception
	{
		this.stateManagerFactory = new StateManagerFactoryDescriptorAdapter().unmarshal(descriptor);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getAutoActivationExpression()
	 */
	@Override
	public CronExpression getAutoActivationExpression()
	{
		return this.getNestedConfiguration().getAutoActivationExpression();
	}

	public void setAutoActivationExpression(CronExpression expression)
	{
		this.getNestedConfiguration().setAutoActivationExpression(expression);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getBalancerFactory()
	 */
	@Override
	public BalancerFactory getBalancerFactory()
	{
		return this.getNestedConfiguration().getBalancerFactory();
	}

	public void setBalancerFactory(BalancerFactory factory)
	{
		this.getNestedConfiguration().setBalancerFactory(factory);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getDispatcherFactory()
	 */
	@Override
	public CommandDispatcherFactory getDispatcherFactory()
	{
		return this.dispatcherFactory;
	}

	public void setDispatcherFactory(CommandDispatcherFactory factory)
	{
		this.dispatcherFactory = factory;
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getDatabaseMap()
	 */
	@Override
	public Map<String, D> getDatabaseMap()
	{
		return this.getNestedConfiguration().getDatabaseMap();
	}

	public void setDatabases(Collection<D> databases)
	{
		Map<String, D> map = this.getDatabaseMap();
		
		for (D database: databases)
		{
			map.put(database.getId(), database);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getDatabaseMetaDataCacheFactory()
	 */
	@Override
	public DatabaseMetaDataCacheFactory getDatabaseMetaDataCacheFactory()
	{
		return this.getNestedConfiguration().getDatabaseMetaDataCacheFactory();
	}

	public void setDatabaseMetaDataCacheFactory(DatabaseMetaDataCacheFactory factory)
	{
		this.getNestedConfiguration().setDatabaseMetaDataCacheFactory(factory);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getDefaultSynchronizationStrategy()
	 */
	@Override
	public String getDefaultSynchronizationStrategy()
	{
		return this.getNestedConfiguration().getDefaultSynchronizationStrategy();
	}

	public void setDefaultSynchronizationStrategy(String strategy)
	{
		this.getNestedConfiguration().setDefaultSynchronizationStrategy(strategy);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getDialectFactory()
	 */
	@Override
	public DialectFactory getDialectFactory()
	{
		return this.getNestedConfiguration().getDialectFactory();
	}

	public void setDialectFactory(DialectFactory factory)
	{
		this.getNestedConfiguration().setDialectFactory(factory);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getDurabilityFactory()
	 */
	@Override
	public DurabilityFactory getDurabilityFactory()
	{
		return this.getNestedConfiguration().getDurabilityFactory();
	}

	public void setDurabilityFactory(DurabilityFactory factory)
	{
		this.getNestedConfiguration().setDurabilityFactory(factory);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getExecutorProvider()
	 */
	@Override
	public ExecutorServiceProvider getExecutorProvider()
	{
		return this.getNestedConfiguration().getExecutorProvider();
	}
	
	public void setExecutorProvider(ExecutorServiceProvider provider)
	{
		this.getNestedConfiguration().setExecutorProvider(provider);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getThreadFactory()
	 */
	@Override
	public ThreadFactory getThreadFactory()
	{
		return this.getNestedConfiguration().getThreadFactory();
	}

	public void setThreadFactory(ThreadFactory factory)
	{
		this.getNestedConfiguration().setThreadFactory(factory);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getCodecFactory()
	 */
	@Override
	public CodecFactory getCodecFactory()
	{
		return this.getNestedConfiguration().getCodecFactory();
	}

	public void setCodecFactory(CodecFactory factory)
	{
		this.getNestedConfiguration().setCodecFactory(factory);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getFailureDetectionExpression()
	 */
	@Override
	public CronExpression getFailureDetectionExpression()
	{
		return this.getNestedConfiguration().getFailureDetectionExpression();
	}

	public void setFailureDetectionExpression(CronExpression expression)
	{
		this.getNestedConfiguration().setFailureDetectionExpression(expression);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getStateManagerFactory()
	 */
	@Override
	public StateManagerFactory getStateManagerFactory()
	{
		return this.stateManagerFactory;
	}

	public void setStateManagerFactory(StateManagerFactory factory)
	{
		this.stateManagerFactory = factory;
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getSynchronizationStrategyMap()
	 */
	@Override
	public Map<String, SynchronizationStrategy> getSynchronizationStrategyMap()
	{
		return this.synchronizationStrategies;
	}

	public void setSynchronizationStrategyMap(Map<String, SynchronizationStrategy> strategies)
	{
		this.synchronizationStrategies = strategies;
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getTransactionMode()
	 */
	@Override
	public TransactionMode getTransactionMode()
	{
		return this.getNestedConfiguration().getTransactionMode();
	}

	public void setTransactionMode(TransactionMode mode)
	{
		this.getNestedConfiguration().setTransactionMode(mode);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#isCurrentDateEvaluationEnabled()
	 */
	@Override
	public boolean isCurrentDateEvaluationEnabled()
	{
		return this.getNestedConfiguration().isCurrentDateEvaluationEnabled();
	}

	public void setCurrentDateEvaluationEnabled(boolean enabled)
	{
		this.getNestedConfiguration().setCurrentDateEvaluationEnabled(enabled);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#isCurrentTimeEvaluationEnabled()
	 */
	@Override
	public boolean isCurrentTimeEvaluationEnabled()
	{
		return this.getNestedConfiguration().isCurrentTimeEvaluationEnabled();
	}

	public void setCurrentTimeEvaluationEnabled(boolean enabled)
	{
		this.getNestedConfiguration().setCurrentTimeEvaluationEnabled(enabled);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#isCurrentTimestampEvaluationEnabled()
	 */
	@Override
	public boolean isCurrentTimestampEvaluationEnabled()
	{
		return this.getNestedConfiguration().isCurrentTimestampEvaluationEnabled();
	}

	public void setCurrentTimestampEvaluationEnabled(boolean enabled)
	{
		this.getNestedConfiguration().setCurrentTimestampEvaluationEnabled(enabled);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#isIdentityColumnDetectionEnabled()
	 */
	@Override
	public boolean isIdentityColumnDetectionEnabled()
	{
		return this.getNestedConfiguration().isIdentityColumnDetectionEnabled();
	}

	public void setIdentityColumnDetectionEnabled(boolean enabled)
	{
		this.getNestedConfiguration().setIdentityColumnDetectionEnabled(enabled);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#isRandEvaluationEnabled()
	 */
	@Override
	public boolean isRandEvaluationEnabled()
	{
		return this.getNestedConfiguration().isRandEvaluationEnabled();
	}

	public void setRandEvaluationEnabled(boolean enabled)
	{
		this.getNestedConfiguration().setRandEvaluationEnabled(enabled);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#isSequenceDetectionEnabled()
	 */
	@Override
	public boolean isSequenceDetectionEnabled()
	{
		return this.getNestedConfiguration().isSequenceDetectionEnabled();
	}
	
	public void setSequenceDetectionEnabled(boolean enabled)
	{
		this.getNestedConfiguration().setSequenceDetectionEnabled(enabled);
	}
	
	static Map<String, Map.Entry<PropertyDescriptor, PropertyEditor>> findDescriptors(Class<?> targetClass) throws Exception
	{
		Map<String, Map.Entry<PropertyDescriptor, PropertyEditor>> map = new HashMap<String, Map.Entry<PropertyDescriptor, PropertyEditor>>();
		
		for (PropertyDescriptor descriptor: Introspector.getBeanInfo(targetClass).getPropertyDescriptors())
		{
			if ((descriptor.getReadMethod() != null) && (descriptor.getWriteMethod() != null))
			{
				PropertyEditor editor = PropertyEditorManager.findEditor(descriptor.getPropertyType());
				
				if (editor != null)
				{
					map.put(descriptor.getName(), new AbstractMap.SimpleImmutableEntry<PropertyDescriptor, PropertyEditor>(descriptor, editor));
				}
			}
		}
		
		return map;
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getMBeanRegistrar()
	 */
	@Override
	public MBeanRegistrar<Z, D> getMBeanRegistrar()
	{
		return this.getNestedConfiguration().getMBeanRegistrar();
	}

	public void setMBeanRegistrar(MBeanRegistrar<Z, D> registrar)
	{
		this.getNestedConfiguration().setMBeanRegistrar(registrar);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#isFairLocking()
	 */
	@Override
	public boolean isFairLocking()
	{
		return this.getNestedConfiguration().isFairLocking();
	}

	public void setFairLocking(boolean fair)
	{
		this.getNestedConfiguration().setFairLocking(fair);
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfiguration#getTransactionIdentifierFactory()
	 */
	@Override
	public TransactionIdentifierFactory<? extends Object> getTransactionIdentifierFactory()
	{
		return (this.dispatcherFactory != null) ? new UUIDTransactionIdentifierFactory() : new SimpleTransactionIdentifierFactory();
	}

	@XmlType(name = "abstractNestedConfiguration")
	protected static abstract class NestedConfiguration<Z, D extends Database<Z>> implements DatabaseClusterConfiguration<Z, D>
	{
		private static final long serialVersionUID = -5674156614205147546L;

		@XmlJavaTypeAdapter(BalancerFactoryAdapter.class)
		@XmlAttribute(name = "balancer")
		private BalancerFactory balancerFactory = BalancerFactoryEnum.ROUND_ROBIN;
		
		@XmlJavaTypeAdapter(DatabaseMetaDataCacheFactoryAdapter.class)
		@XmlAttribute(name = "meta-data-cache")
		private DatabaseMetaDataCacheFactory databaseMetaDataCacheFactory = DatabaseMetaDataCacheFactoryEnum.EAGER;
		
		@XmlJavaTypeAdapter(DialectFactoryAdapter.class)
		@XmlAttribute(name = "dialect")
		private DialectFactory dialectFactory = DialectFactoryEnum.STANDARD;
		
		@XmlJavaTypeAdapter(DurabilityFactoryAdapter.class)
		@XmlAttribute(name = "durability")
		private DurabilityFactory durabilityFactory = DurabilityFactoryEnum.FINE;

		private ExecutorServiceProvider executorProvider = new DefaultExecutorServiceProvider();
		private ThreadFactory threadFactory = Executors.defaultThreadFactory();
		private CodecFactory codecFactory = new SimpleCodecFactory();
		private MBeanRegistrar<Z, D> registrar = new DefaultMBeanRegistrar<Z, D>();
		
		@XmlJavaTypeAdapter(TransactionModeAdapter.class)
		@XmlAttribute(name = "transaction-mode")
		private TransactionMode transactionMode = TransactionModeEnum.SERIAL;

		@XmlJavaTypeAdapter(CronExpressionAdapter.class)
		@XmlAttribute(name = "auto-activate-schedule")
		private CronExpression autoActivationExpression;
		@XmlJavaTypeAdapter(CronExpressionAdapter.class)
		@XmlAttribute(name = "failure-detect-schedule")
		private CronExpression failureDetectionExpression;
		
		@XmlAttribute(name = "eval-current-date")
		private Boolean currentDateEvaluationEnabled = false;
		@XmlAttribute(name = "eval-current-time")
		private Boolean currentTimeEvaluationEnabled = false;
		@XmlAttribute(name = "eval-current-timestamp")
		private Boolean currentTimestampEvaluationEnabled = false;
		@XmlAttribute(name = "eval-rand")
		private Boolean randEvaluationEnabled = false;
		
		@XmlAttribute(name = "detect-identity-columns")
		private Boolean identityColumnDetectionEnabled = false;
		@XmlAttribute(name = "detect-sequences")
		private Boolean sequenceDetectionEnabled = false;

		@XmlAttribute(name = "fair-locking")
		private Boolean fairLocking = true;
		
		private String defaultSynchronizationStrategy;
		
		private Map<String, D> databases = new HashMap<String, D>();
		
		@SuppressWarnings("unused")
		@XmlIDREF
		@XmlAttribute(name = "default-sync", required = true)
		private SynchronizationStrategyDescriptor getDefaultSynchronizationStrategyDescriptor()
		{
			SynchronizationStrategyDescriptor descriptor = new SynchronizationStrategyDescriptor();
			descriptor.setId(this.defaultSynchronizationStrategy);
			return descriptor;
		}
		
		@SuppressWarnings("unused")
		private void setDefaultSynchronizationStrategyDescriptor(SynchronizationStrategyDescriptor descriptor)
		{
			this.defaultSynchronizationStrategy = descriptor.getId();
		}
		
		@Override
		public Map<String, D> getDatabaseMap()
		{
			return this.databases;
		}
		
		@Override
		public CronExpression getAutoActivationExpression()
		{
			return this.autoActivationExpression;
		}

		void setAutoActivationExpression(CronExpression expression)
		{
			this.autoActivationExpression = expression;
		}
		
		@Override
		public BalancerFactory getBalancerFactory()
		{
			return this.balancerFactory;
		}

		void setBalancerFactory(BalancerFactory factory)
		{
			this.balancerFactory = factory;
		}
		
		@Override
		public CommandDispatcherFactory getDispatcherFactory()
		{
			throw new IllegalStateException();
		}

		@Override
		public DatabaseMetaDataCacheFactory getDatabaseMetaDataCacheFactory()
		{
			return this.databaseMetaDataCacheFactory;
		}

		void setDatabaseMetaDataCacheFactory(DatabaseMetaDataCacheFactory factory)
		{
			this.databaseMetaDataCacheFactory = factory;
		}
		
		@Override
		public String getDefaultSynchronizationStrategy()
		{
			return this.defaultSynchronizationStrategy;
		}

		void setDefaultSynchronizationStrategy(String strategy)
		{
			this.defaultSynchronizationStrategy = strategy;
		}
		
		@Override
		public DialectFactory getDialectFactory()
		{
			return this.dialectFactory;
		}

		void setDialectFactory(DialectFactory factory)
		{
			this.dialectFactory = factory;
		}
		
		@Override
		public DurabilityFactory getDurabilityFactory()
		{
			return this.durabilityFactory;
		}

		void setDurabilityFactory(DurabilityFactory factory)
		{
			this.durabilityFactory = factory;
		}
		
		@Override
		public ExecutorServiceProvider getExecutorProvider()
		{
			return this.executorProvider;
		}

		void setExecutorProvider(ExecutorServiceProvider provider)
		{
			this.executorProvider = provider;
		}
		
		@Override
		public ThreadFactory getThreadFactory()
		{
			return this.threadFactory;
		}

		void setThreadFactory(ThreadFactory factory)
		{
			this.threadFactory = factory;
		}
		
		@Override
		public CodecFactory getCodecFactory()
		{
			return this.codecFactory;
		}

		void setCodecFactory(CodecFactory factory)
		{
			this.codecFactory = factory;
		}
		
		@Override
		public MBeanRegistrar<Z, D> getMBeanRegistrar()
		{
			return this.registrar;
		}
		
		void setMBeanRegistrar(MBeanRegistrar<Z, D> registrar)
		{
			this.registrar = registrar;
		}
		
		@Override
		public CronExpression getFailureDetectionExpression()
		{
			return this.failureDetectionExpression;
		}

		void setFailureDetectionExpression(CronExpression expression)
		{
			this.failureDetectionExpression = expression;
		}
		
		@Override
		public StateManagerFactory getStateManagerFactory()
		{
			throw new IllegalStateException();
		}
		
		@Override
		public Map<String, SynchronizationStrategy> getSynchronizationStrategyMap()
		{
			throw new IllegalStateException();
		}
		
		@Override
		public TransactionMode getTransactionMode()
		{
			return this.transactionMode;
		}

		void setTransactionMode(TransactionMode mode)
		{
			this.transactionMode = mode;
		}
		
		@Override
		public boolean isCurrentDateEvaluationEnabled()
		{
			return this.currentDateEvaluationEnabled;
		}
		
		void setCurrentDateEvaluationEnabled(boolean enabled)
		{
			this.currentDateEvaluationEnabled = enabled;
		}
		
		@Override
		public boolean isCurrentTimeEvaluationEnabled()
		{
			return this.currentTimeEvaluationEnabled;
		}
		
		void setCurrentTimeEvaluationEnabled(boolean enabled)
		{
			this.currentTimeEvaluationEnabled = enabled;
		}
		
		@Override
		public boolean isCurrentTimestampEvaluationEnabled()
		{
			return this.currentTimestampEvaluationEnabled;
		}
		
		void setCurrentTimestampEvaluationEnabled(boolean enabled)
		{
			this.currentTimestampEvaluationEnabled = enabled;
		}
		
		@Override
		public boolean isIdentityColumnDetectionEnabled()
		{
			return this.identityColumnDetectionEnabled;
		}
		
		void setIdentityColumnDetectionEnabled(boolean enabled)
		{
			this.identityColumnDetectionEnabled = enabled;
		}
		
		@Override
		public boolean isRandEvaluationEnabled()
		{
			return this.randEvaluationEnabled;
		}

		void setRandEvaluationEnabled(boolean enabled)
		{
			this.randEvaluationEnabled = enabled;
		}
		
		@Override
		public boolean isSequenceDetectionEnabled()
		{
			return this.sequenceDetectionEnabled;
		}
		
		void setSequenceDetectionEnabled(boolean enabled)
		{
			this.sequenceDetectionEnabled = enabled;
		}
		
		@Override
		public boolean isFairLocking()
		{
			return this.fairLocking;
		}
		
		void setFairLocking(boolean fair)
		{
			this.fairLocking = fair;
		}

		@Override
		public TransactionIdentifierFactory<? extends Object> getTransactionIdentifierFactory()
		{
			throw new IllegalStateException();
		}
	}

	static class BalancerFactoryAdapter extends EnumAdapter<BalancerFactory, BalancerFactoryEnum>
	{
		@Override
		protected Class<BalancerFactoryEnum> getTargetClass()
		{
			return BalancerFactoryEnum.class;
		}
	}

	static class DatabaseMetaDataCacheFactoryAdapter extends EnumAdapter<DatabaseMetaDataCacheFactory, DatabaseMetaDataCacheFactoryEnum>
	{
		@Override
		protected Class<DatabaseMetaDataCacheFactoryEnum> getTargetClass()
		{
			return DatabaseMetaDataCacheFactoryEnum.class;
		}
	}

	static class DurabilityFactoryAdapter extends EnumAdapter<DurabilityFactory, DurabilityFactoryEnum>
	{
		@Override
		protected Class<DurabilityFactoryEnum> getTargetClass()
		{
			return DurabilityFactoryEnum.class;
		}
	}

	static class TransactionModeAdapter extends EnumAdapter<TransactionMode, TransactionModeEnum>
	{
		@Override
		protected Class<TransactionModeEnum> getTargetClass()
		{
			return TransactionModeEnum.class;
		}
	}

	static abstract class EnumAdapter<I, E extends I> extends XmlAdapter<E, I>
	{
		@Override
		public I unmarshal(E enumerated) throws Exception
		{
			return enumerated;
		}
		
		@Override
		public E marshal(I object) throws Exception
		{
			return this.getTargetClass().cast(object);
		}

		protected abstract Class<E> getTargetClass();		
	}

	static class DialectFactoryAdapter extends XmlAdapter<String, DialectFactory>
	{
		@Override
		public String marshal(DialectFactory factory)
		{
			return factory.toString();
		}

		@Override
		public DialectFactory unmarshal(String value) throws Exception
		{
			try
			{
				return DialectFactoryEnum.valueOf(value.toUpperCase());
			}
			catch (IllegalArgumentException e)
			{
				return new CustomDialectFactory(DialectFactory.class.getClassLoader().loadClass(value).asSubclass(Dialect.class));
			}
		}
	}

	static class CronExpressionAdapter extends XmlAdapter<String, CronExpression>
	{
		@Override
		public String marshal(CronExpression expression)
		{
			return (expression != null) ? expression.getCronExpression() : null;
		}

		@Override
		public CronExpression unmarshal(String value) throws Exception
		{
			return (value != null) ? new CronExpression(value) : null;
		}
	}
	
	@XmlType
	static class SynchronizationStrategyDescriptor extends Descriptor<SynchronizationStrategy>
	{
		@XmlID
		@XmlAttribute(name = "id", required = true)
		private String id;
		
		public String getId()
		{
			return this.id;
		}
		
		public void setId(String id)
		{
			this.id = id;
		}
	}
	
	static class SynchronizationStrategyDescriptorAdapter extends DescriptorAdapter<SynchronizationStrategy, SynchronizationStrategyDescriptor>
	{
		SynchronizationStrategyDescriptorAdapter()
		{
			super(SynchronizationStrategyDescriptor.class);
		}
	}
	
	@XmlType
	static class StateManagerFactoryDescriptor extends Descriptor<StateManagerFactory>
	{
	}

	static class StateManagerFactoryDescriptorAdapter extends DescriptorAdapter<StateManagerFactory, StateManagerFactoryDescriptor>
	{
		StateManagerFactoryDescriptorAdapter()
		{
			super(StateManagerFactoryDescriptor.class);
		}
	}
	
	static abstract class Descriptor<T>
	{
		@XmlAttribute(name = "class", required = true)
		private Class<T> targetClass;
		
		@XmlElement(name = "property")
		private List<Property> properties;
		
		public Class<T> getTargetClass()
		{
			return this.targetClass;
		}
		
		public void setTargetClass(Class<T> targetClass)
		{
			this.targetClass = targetClass;
		}
		
		public List<Property> getProperties()
		{
			return this.properties;
		}

		public void setProperties(List<Property> properties)
		{
			this.properties = properties;
		}
	}

	static class DescriptorAdapter<T, D extends Descriptor<T>> extends XmlAdapter<D, T>
	{
		private final Class<D> descriptorClass;
		
		DescriptorAdapter(Class<D> descriptorClass)
		{
			this.descriptorClass = descriptorClass;
		}
		
		@Override
		public D marshal(T object) throws Exception
		{
			D result = this.descriptorClass.newInstance();
			@SuppressWarnings("unchecked")
			Class<T> targetClass = (Class<T>) object.getClass();
			List<Property> properties = new LinkedList<Property>();
			
			result.setTargetClass(targetClass);
			result.setProperties(properties);
			
			for (Map.Entry<PropertyDescriptor, PropertyEditor> entry: findDescriptors(targetClass).values())
			{
				PropertyDescriptor descriptor = entry.getKey();
				PropertyEditor editor = entry.getValue();
				
				Object value = descriptor.getReadMethod().invoke(object);
				if (value != null)
				{
					editor.setValue(value);
					
					Property property = new Property();					
					property.setName(descriptor.getName());
					property.setValue(editor.getAsText());
					
					properties.add(property);
				}
			}
			
			return result;
		}

		@Override
		public T unmarshal(D target) throws Exception
		{
			Class<T> targetClass = target.getTargetClass();
			T result = targetClass.newInstance();
			List<Property> properties = target.getProperties();
			
			if (properties != null)
			{
				Map<String, Map.Entry<PropertyDescriptor, PropertyEditor>> descriptors = findDescriptors(targetClass);
				
				for (Property property: properties)
				{
					String name = property.getName();
					Map.Entry<PropertyDescriptor, PropertyEditor> entry = descriptors.get(name);
					
					if (entry == null)
					{
						throw new IllegalArgumentException(Messages.INVALID_PROPERTY.getMessage(name, targetClass.getName()));
					}
					
					PropertyDescriptor descriptor = entry.getKey();
					PropertyEditor editor = entry.getValue();

					String textValue = property.getValue();
					
					try
					{
						editor.setAsText(textValue);
					}
					catch (Exception e)
					{
						throw new IllegalArgumentException(Messages.INVALID_PROPERTY_VALUE.getMessage(textValue, name, targetClass.getName()));
					}
					descriptor.getWriteMethod().invoke(result, editor.getValue());
				}
			}
			return result;
		}
	}
	
	@XmlType
	protected static class Property
	{
		@XmlAttribute(required = true)
		private String name;
		@XmlValue
		private String value;
		
		public String getName()
		{
			return this.name;
		}
		
		public void setName(String name)
		{
			this.name = name;
		}
		
		public String getValue()
		{
			return this.value;
		}
		
		public void setValue(String value)
		{
			this.value = value;
		}
	}
}
