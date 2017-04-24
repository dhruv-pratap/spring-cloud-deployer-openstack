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

import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ServerCreate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.openstack4j.api.Builders.server;
import static org.openstack4j.model.compute.Action.SUSPEND;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * A deployer that targets OpenStack.
 */
public class OpenStackAppDeployer extends AbstractOpenStackDeployer implements AppDeployer {

	private static final String SERVER_PORT_KEY = "server.port";

	@Autowired
	public OpenStackAppDeployer(OpenStackDeployerProperties properties, OSClient client) {
		this.properties = properties;
		this.client = client;
	}

	@Override
	public String deploy(AppDeploymentRequest request) {

		String appId = createDeploymentId(request);
		logger.debug(String.format("Deploying app: %s", appId));

		try {
			AppStatus status = status(appId);
			if (!status.getState().equals(DeploymentState.unknown)) {
				throw new IllegalStateException(String.format("App '%s' is already deployed", appId));
			}

			int externalPort = configureExternalPort(request);

			String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
			int count = (countProperty != null) ? Integer.parseInt(countProperty) : 1;

			String indexedProperty = request.getDeploymentProperties().get(INDEXED_PROPERTY_KEY);
			boolean indexed = (indexedProperty != null) && Boolean.valueOf(indexedProperty);

			if (indexed) {
				for (int index=0 ; index < count ; index++) {
					String indexedId = appId + "-" + index;
					Map<String, String> idMap = createIdMap(appId, request, index);
					logger.debug(String.format("Creating service: %s on %d with index %d", appId, externalPort, index));
					createApplication(indexedId, request, idMap, externalPort);
				}
			}
			else {
				Map<String, String> idMap = createIdMap(appId, request, null);
				logger.debug(String.format("Creating service: %s on {}", appId, externalPort));
				createApplication(appId, request, idMap, externalPort);
			}

			return appId;
		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public void undeploy(String appId) {
		logger.debug(String.format("Undeploying app: %s", appId));
		AppStatus status = status(appId);
		if (status.getState().equals(DeploymentState.unknown)) {
			throw new IllegalStateException(String.format("App '%s' is not deployed", appId));
		}

		try {
			deleteApplication(appId);
		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}


	@Override
	public AppStatus status(String appId) {
		Map<String, String> selector = new HashMap<>();
		selector.put(SPRING_APP_KEY, appId);
		List<? extends Server> servers = client.compute().servers().list(selector);
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Building AppStatus for app: %s", appId));
			if (!isEmpty(servers)) {
				logger.debug(String.format("Servers for appId %s: %d", appId, servers.size()));
				for (Server server : servers) {
					logger.debug(String.format("Server: %s", server.getName()));
				}
			}
		}
		AppStatus status = buildAppStatus(appId, servers);
		logger.debug(String.format("Status for app: %s is %s", appId, status));

		return status;
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return super.createRuntimeEnvironmentInfo(AppDeployer.class, this.getClass());
	}

	protected int configureExternalPort(final AppDeploymentRequest request) {
		int externalPort = 8080;
		Map<String, String> parameters = request.getDefinition().getProperties();
		if (parameters.containsKey(SERVER_PORT_KEY)) {
			externalPort = Integer.valueOf(parameters.get(SERVER_PORT_KEY));
		}

		return externalPort;
	}

	protected String createDeploymentId(AppDeploymentRequest request) {
		String groupId = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);
		String deploymentId;
		if (groupId == null) {
			deploymentId = String.format("%s", request.getDefinition().getName());
		}
		else {
			deploymentId = String.format("%s-%s", groupId, request.getDefinition().getName());
		}
		// OpenStack does not allow . in the name and does not allow uppercase in the name
		return deploymentId.replace('.', '-').toLowerCase();
	}

	private void createApplication(String appId, AppDeploymentRequest request, Map<String, String> idMap, int externalPort) {

		// Create a Server Model Object
		ServerCreate sc = server()
							.name(appId)
							.flavor("flavorId")
							.image("imageId")
							.addMetadata(idMap)
							.addMetadataItem(SPRING_MARKER_KEY, SPRING_MARKER_VALUE)
//							.addNetworkPort(externalPort)
							.build();

		// Boot the Server
		Server server = client
							.compute()
							.servers()
							.boot(sc);

	}


	private void deleteApplication(String appId) {
		// Suspend Server
		logger.debug(String.format("Suspending service: %s", appId));
		ActionResponse suspensionResponse = client.compute().servers().action(appId, SUSPEND);
		logger.debug(String.format("Suspension status: %s", suspensionResponse));

		// Delete Server
		logger.debug(String.format("Deleting service: %s", appId));
		ActionResponse deletionResponse = client.compute().servers().delete(appId);
		logger.debug(String.format("Deletion status: %s", deletionResponse));
	}

}
