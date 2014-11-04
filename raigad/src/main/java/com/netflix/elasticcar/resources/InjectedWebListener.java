/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.elasticcar.resources;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.netflix.elasticcar.aws.IAMCredential;
import com.netflix.elasticcar.backup.AbstractRepository;
import com.netflix.elasticcar.backup.AbstractRepositorySettingsParams;
import com.netflix.elasticcar.backup.S3Repository;
import com.netflix.elasticcar.backup.S3RepositorySettingsParams;
import com.netflix.elasticcar.configuration.ElasticCarConfiguration;
import com.netflix.elasticcar.configuration.IConfiguration;
import com.netflix.elasticcar.identity.CassandraInstanceFactory;
import com.netflix.elasticcar.identity.EurekaHostsSupplier;
import com.netflix.elasticcar.identity.HostSupplier;
import com.netflix.elasticcar.identity.IElasticCarInstanceFactory;
import com.netflix.elasticcar.scheduler.GuiceJobFactory;
import com.netflix.elasticcar.startup.ElasticCarServer;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import org.elasticsearch.common.collect.Lists;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InjectedWebListener extends GuiceServletContextListener
{
    protected static final Logger logger = LoggerFactory.getLogger(InjectedWebListener.class);

    @Override
    protected Injector getInjector()
    {
        List<Module> moduleList = Lists.newArrayList();
        moduleList.add(new JaxServletModule());
        moduleList.add(new ElasticCarGuiceModule());
        Injector injector;
        try
        {
        		injector  = Guice.createInjector(moduleList);
        		startJobs(injector);
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(),e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return injector;
    }

    private void startJobs(Injector injector) throws Exception
    {
    		ElasticCarServer escarServer = injector.getInstance(ElasticCarServer.class);

    		logger.info("**Now starting to initialize escarserver from OSS");
    		escarServer.initialize();

    }

    public static class JaxServletModule extends ServletModule
    {
        @Override
        protected void configureServlets()
        {
            Map<String, String> params = new HashMap<String, String>();
            params.put(PackagesResourceConfig.PROPERTY_PACKAGES
                    , "unbound");
            params.put("com.sun.jersey.config.property.packages", "com.netflix.escar.resources");
            params.put(ServletContainer.PROPERTY_FILTER_CONTEXT_PATH, "/REST");
            serve("/REST/*").with(GuiceContainer.class, params);
        }
    }


    public static class ElasticCarGuiceModule extends AbstractModule
    {
        @Override
        protected void configure()
        {
    		logger.info("**Binding OSS Config classes.");
            // fix bug in Jersey-Guice integration exposed by child injectors
            binder().bind(GuiceContainer.class).asEagerSingleton();

            binder().bind(GuiceJobFactory.class).asEagerSingleton();

            logger.info("**Binding Config classes.......");
            bind(IConfiguration.class).to(ElasticCarConfiguration.class);
            binder().bind(IElasticCarInstanceFactory.class).to(CassandraInstanceFactory.class);
            //TODO: use config.getCredentialProvider() instead of IAMCredential
            binder().bind(com.netflix.elasticcar.aws.ICredential.class).to(IAMCredential.class);
            binder().bind(AbstractRepository.class).annotatedWith(Names.named("s3")).to(S3Repository.class);
            binder().bind(AbstractRepositorySettingsParams.class).annotatedWith(Names.named("s3")).to(S3RepositorySettingsParams.class);

            bind(SchedulerFactory.class).to(StdSchedulerFactory.class).asEagerSingleton();
            bind(HostSupplier.class).to(EurekaHostsSupplier.class).asEagerSingleton();
//            bind(IElasticCarInstanceFactory.class).to(CassandraInstanceFactory.class);
//            bind(ICredential.class).to(IAMCredential.class);
        }
    }
}
