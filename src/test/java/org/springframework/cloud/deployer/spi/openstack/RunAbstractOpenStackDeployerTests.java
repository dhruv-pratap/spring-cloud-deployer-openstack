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

import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.FileSystemResource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import io.fabric8.kubernetes.api.model.Quantity;

/**
 * Unit test for {@link AbstractOpenStackDeployer}.
 *
 * @author Moritz Schulze
 */
public class RunAbstractOpenStackDeployerTests {

	private AbstractOpenStackDeployer kubernetesDeployer;
	private AppDeploymentRequest deploymentRequest;
	private Map<String, String> deploymentProperties;
	private OpenStackDeployerProperties serverProperties;

	@Before
	public void setUp() throws Exception {
		deploymentProperties = new HashMap<>();
		deploymentRequest = new AppDeploymentRequest(new AppDefinition("foo", Collections.emptyMap()), new FileSystemResource(""), deploymentProperties);
		serverProperties = new OpenStackDeployerProperties();
		kubernetesDeployer = new OpenStackAppDeployer(serverProperties, null);
	}

	@Test
	public void deduceImagePullPolicy_fallsBackToAlwaysIfOverrideNotParseable() throws Exception {
		deploymentProperties.put("spring.cloud.deployer.openstack.imagePullPolicy", "not-a-real-value");
		ImagePullPolicy pullPolicy = kubernetesDeployer.deduceImagePullPolicy(deploymentRequest);
		assertThat(pullPolicy, is(ImagePullPolicy.Always));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void limitCpu_noDeploymentProperty_deprecatedServerProperty_usesServerProperty() throws Exception {
		serverProperties.setCpu("500m");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("500m")));
	}

	@Test
	public void limitCpu_noDeploymentProperty_serverProperty_usesServerProperty() throws Exception {
		serverProperties.getLimits().setCpu("400m");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("400m")));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void limitMemory_noDeploymentProperty_deprecatedServerProperty_usesServerProperty() throws Exception {
		serverProperties.setMemory("640Mi");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("640Mi")));
	}

	@Test
	public void limitMemory_noDeploymentProperty_serverProperty_usesServerProperty() throws Exception {
		serverProperties.getLimits().setMemory("540Mi");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("540Mi")));
	}

	@Test
	public void limitCpu_deploymentProperty_deprecatedKey_usesDeploymentProperty() throws Exception {
		serverProperties.getLimits().setCpu("100m");
		deploymentProperties.put("spring.cloud.deployer.openstack.cpu", "300m");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("300m")));
	}

	@Test
	public void limitCpu_deploymentProperty_usesDeploymentProperty() throws Exception {
		serverProperties.getLimits().setCpu("100m");
		deploymentProperties.put("spring.cloud.deployer.openstack.limits.cpu", "400m");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("400m")));
	}

	@Test
	public void limitMemory_deploymentProperty_deprecatedKey_usesDeploymentProperty() throws Exception {
		serverProperties.getLimits().setMemory("640Mi");
		deploymentProperties.put("spring.cloud.deployer.openstack.memory", "1024Mi");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("1024Mi")));
	}

	@Test
	public void limitMemory_deploymentProperty_usesDeploymentProperty() throws Exception {
		serverProperties.getLimits().setMemory("640Mi");
		deploymentProperties.put("spring.cloud.deployer.openstack.limits.memory", "256Mi");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("256Mi")));
	}

	@Test
	public void requestCpu_noDeploymentProperty_serverProperty_usesServerProperty() throws Exception {
		serverProperties.getRequests().setCpu("400m");
		Map<String, Quantity> requests = kubernetesDeployer.deduceResourceRequests(deploymentRequest);
		MatcherAssert.assertThat(requests.get("cpu"), is(new Quantity("400m")));
	}

	@Test
	public void requestMemory_noDeploymentProperty_serverProperty_usesServerProperty() throws Exception {
		serverProperties.getRequests().setMemory("120Mi");
		Map<String, Quantity> requests = kubernetesDeployer.deduceResourceRequests(deploymentRequest);
		MatcherAssert.assertThat(requests.get("memory"), is(new Quantity("120Mi")));
	}

	@Test
	public void requestCpu_deploymentProperty_usesDeploymentProperty() throws Exception {
		serverProperties.getRequests().setCpu("1000m");
		deploymentProperties.put("spring.cloud.deployer.openstack.requests.cpu", "461m");
		Map<String, Quantity> requests = kubernetesDeployer.deduceResourceRequests(deploymentRequest);
		MatcherAssert.assertThat(requests.get("cpu"), is(new Quantity("461m")));
	}

	@Test
	public void requestMemory_deploymentProperty_usesDeploymentProperty() throws Exception {
		serverProperties.getRequests().setMemory("640Mi");
		deploymentProperties.put("spring.cloud.deployer.openstack.requests.memory", "256Mi");
		Map<String, Quantity> requests = kubernetesDeployer.deduceResourceRequests(deploymentRequest);
		MatcherAssert.assertThat(requests.get("memory"), is(new Quantity("256Mi")));
	}

	@Test
	public void limitMemory_commonDeploymentProperty_noUnit() throws Exception {
		deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "512");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("512Mi")));
	}

	@Test
	public void limitMemory_commonDeploymentProperty_mSuffix() throws Exception {
		deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "512m");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("512Mi")));
	}

	@Test
	public void limitMemory_commonDeploymentProperty_gSuffix() throws Exception {
		deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "1g");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("1024Mi")));
	}

	@Test
	public void limitCpu_commonDeploymentProperty_wholeNumber() throws Exception {
		deploymentProperties.put(AppDeployer.CPU_PROPERTY_KEY, "1");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("1")));
	}

	@Test
	public void limitCpu_commonDeploymentProperty_fraction() throws Exception {
		deploymentProperties.put(AppDeployer.CPU_PROPERTY_KEY, "0.5");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("0.5")));
	}

	@Test
	public void limitMemory_commonDeploymentProperty_getsOverridden() throws Exception {
		deploymentProperties.put(AppDeployer.MEMORY_PROPERTY_KEY, "512m");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("512Mi")));
		deploymentProperties.put("spring.cloud.deployer.openstack.memory", "640Mi");
		limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("640Mi")));
		deploymentProperties.put("spring.cloud.deployer.openstack.limits.memory", "768Mi");
		limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("memory"), is(new Quantity("768Mi")));
	}

	@Test
	public void limitCpu_commonDeploymentProperty_getsOverridden() throws Exception {
		deploymentProperties.put(AppDeployer.CPU_PROPERTY_KEY, "1");
		Map<String, Quantity> limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("1")));
		deploymentProperties.put("spring.cloud.deployer.openstack.cpu", "500m");
		limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("500m")));
		deploymentProperties.put("spring.cloud.deployer.openstack.limits.cpu", "750m");
		limits = kubernetesDeployer.deduceResourceLimits(deploymentRequest);
		MatcherAssert.assertThat(limits.get("cpu"), is(new Quantity("750m")));
	}
}
