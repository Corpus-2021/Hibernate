/* 
 * Hibernate, Relational Persistence for Idiomatic Java
 * 
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.hibernate.osgi;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.metamodel.spi.TypeContributor;
import org.hibernate.osgi.util.OsgiServiceUtil;
import org.hibernate.service.BootstrapServiceRegistryBuilder;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * Hibernate 4.2 and 4.3 still heavily rely on TCCL for ClassLoading.  Although
 * our ClassLoaderService removed some of the reliance, the service is
 * unfortunately not available during Configuration.  An OSGi
 * bundle manually creating a SessionFactory would require numerous ClassLoader
 * tricks (or may be impossible altogether).
 * 
 * In order to fully control the TCCL issues and shield users from the
 * knowledge, we're requiring that bundles use this OSGi ServiceFactory.  It
 * configures and provides a SessionFactory as an OSGi service.
 * 
 * Note that an OSGi ServiceFactory differs from a Service.  The ServiceFactory
 * allows individual instances of Services to be created and provided to
 * multiple client Bundles.
 * 
 * @author Brett Meyer
 * @author Tim Ward
 */
public class OsgiSessionFactoryService implements ServiceFactory {
	
	private OsgiClassLoader osgiClassLoader;

	private OsgiJtaPlatform osgiJtaPlatform;

	private BundleContext context;

	public OsgiSessionFactoryService( OsgiClassLoader osgiClassLoader, OsgiJtaPlatform osgiJtaPlatform,
			BundleContext context ) {
		this.osgiClassLoader = osgiClassLoader;
		this.osgiJtaPlatform = osgiJtaPlatform;
		this.context = context;
	}

	@Override
	public Object getService(Bundle requestingBundle, ServiceRegistration registration) {
		osgiClassLoader.addBundle( requestingBundle );
		
		Configuration configuration = new Configuration();
		configuration.getProperties().put( AvailableSettings.JTA_PLATFORM, osgiJtaPlatform );
        configuration.configure();
        
        BootstrapServiceRegistryBuilder builder = new BootstrapServiceRegistryBuilder();
        builder.with( osgiClassLoader );
        
        List<Integrator> integrators = OsgiServiceUtil.getServiceImpls( Integrator.class, context );
        for (Integrator integrator : integrators) {
        	builder.with( integrator );
        }
        
        List<TypeContributor> typeContributors = OsgiServiceUtil.getServiceImpls( TypeContributor.class, context );
        for (TypeContributor typeContributor : typeContributors) {
        	configuration.registerTypeContributor( typeContributor );
        }
        
        ServiceRegistry serviceRegistry = new ServiceRegistryBuilder( builder.build() )
				.applySettings(configuration.getProperties()).buildServiceRegistry();
        return configuration.buildSessionFactory(serviceRegistry);
	}

	@Override
	public void ungetService(Bundle requestingBundle, ServiceRegistration registration, Object service) {
		( (SessionFactory) service).close();
	}

}
