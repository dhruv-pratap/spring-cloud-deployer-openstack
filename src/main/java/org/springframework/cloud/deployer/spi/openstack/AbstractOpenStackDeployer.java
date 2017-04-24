/*
 * Copyright 2015-2017 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.Server;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.util.RuntimeVersionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.collectionToCommaDelimitedString;

/**
 * Abstract base class for a deployer that targets OpenStack.
 */
public class AbstractOpenStackDeployer {

	protected static final String SPRING_DEPLOYMENT_KEY = "spring-deployment-id";
	protected static final String SPRING_GROUP_KEY = "spring-group-id";
	protected static final String SPRING_APP_KEY = "spring-app-id";
	protected static final String SPRING_MARKER_KEY = "role";
	protected static final String SPRING_MARKER_VALUE = "spring-app";

	protected static final Log logger = LogFactory.getLog(AbstractOpenStackDeployer.class);

	protected OSClient client;

	protected OpenStackDeployerProperties properties = new OpenStackDeployerProperties();

	/**
	 * Create the RuntimeEnvironmentInfo.
	 *
	 * @return the OpenStack runtime environment info
	 */
	protected RuntimeEnvironmentInfo createRuntimeEnvironmentInfo(Class spiClass, Class implementationClass) {
		return new RuntimeEnvironmentInfo.Builder()
				.spiClass(spiClass)
				.implementationName(implementationClass.getSimpleName())
				.implementationVersion(RuntimeVersionUtils.getVersion(implementationClass))
				.platformType("OpenStack")
				.platformApiVersion("v2.1")
				.platformClientVersion(RuntimeVersionUtils.getVersion(client.getClass()))
				.platformHostVersion("unknown")
				.addPlatformSpecificInfo("endpoint", client.getEndpoint())
				.addPlatformSpecificInfo("supported-services", collectionToCommaDelimitedString(client.getSupportedServices()))
				.build();
	}

	/**
	 * Creates a map of labels for a given ID. This will allow OpenStack services
	 * to "select" the right ReplicationControllers.
	 */
	protected Map<String, String> createIdMap(String appId, AppDeploymentRequest request, Integer instanceIndex) {
		//TODO: handling of app and group ids
		Map<String, String> map = new HashMap<>();
		map.put(SPRING_APP_KEY, appId);
		String groupId = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);
		if (groupId != null) {
			map.put(SPRING_GROUP_KEY, groupId);
		}
		String appInstanceId = instanceIndex == null ? appId : appId + "-" + instanceIndex;
		map.put(SPRING_DEPLOYMENT_KEY, appInstanceId);
		return map;
	}

	protected AppStatus buildAppStatus(String id, List<? extends Server> servers) {
		AppStatus.Builder statusBuilder = AppStatus.of(id);
		if (!isEmpty(servers)) {
			for (Server server : servers) {
				statusBuilder.with(new OpenStackAppInstanceStatus(id, server, properties));
			}
		}
		return statusBuilder.build();
	}

}
