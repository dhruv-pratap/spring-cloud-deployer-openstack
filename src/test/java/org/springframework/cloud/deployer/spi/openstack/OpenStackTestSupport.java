/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.openstack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.spi.test.junit.AbstractExternalResourceTestSupport;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * JUnit {@link org.junit.Rule} that detects the fact that a Kubernetes installation is available.
 *
 * @author Thomas Risberg
 */
public class OpenStackTestSupport extends AbstractExternalResourceTestSupport<KubernetesClient> {

	private ConfigurableApplicationContext context;


	protected OpenStackTestSupport() {
		super("KUBERNETES");
	}

	@Override
	protected void cleanupResource() throws Exception {
		context.close();
	}

	@Override
	protected void obtainResource() throws Exception {
		context = new SpringApplicationBuilder(Config.class).web(false).run();
		resource = context.getBean(KubernetesClient.class);
		resource.namespaces().list();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableConfigurationProperties(OpenStackDeployerProperties.class)
	public static class Config {

		@Autowired
		private OpenStackDeployerProperties properties;

		@Bean
		public KubernetesClient kubernetesClient() {
			return new DefaultKubernetesClient().inNamespace(properties.getNamespace());
		}
	}
}
