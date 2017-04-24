/*
 * Copyright 2015-2016 the original author or authors.
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
import org.openstack4j.model.compute.Server;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.util.ObjectUtils.nullSafeToString;

/**
 * Represents the status of a module.
 */
public class OpenStackAppInstanceStatus implements AppInstanceStatus {

	private static Log logger = LogFactory.getLog(OpenStackAppInstanceStatus.class);
	private final Server server;
	private final String moduleId;
	private OpenStackDeployerProperties properties;

	public OpenStackAppInstanceStatus(String moduleId, Server server, OpenStackDeployerProperties properties) {
		this.moduleId = moduleId;
		this.server = server;
		this.properties = properties;
	}

	@Override
	public String getId() {
		return server == null ? "N/A" : server.getName();
	}

	@Override
	public DeploymentState getState() {
		return server != null ? mapState() : DeploymentState.unknown;
	}

	/**
	 * Maps OpenStack phases/states onto Spring Cloud Deployer states
	 */
	private DeploymentState mapState() {
		logger.debug(String.format("%s - Status [ %s ]", server.getName(), server.getStatus()));
		switch (server.getStatus()) {
			
			case BUILD:
				return DeploymentState.deploying;
				
			// We only report a module as running if the container is also ready to service requests.
			// We also implement the Readiness check as part of the container to ensure ready means
			// that the module is up and running and not only that the JVM has been created and the
			// Spring module is still starting up
			case ACTIVE:
				// we assume we only have one container
				return DeploymentState.deployed;

			case ERROR:
				return DeploymentState.failed;

			case UNKNOWN:
				return DeploymentState.unknown;

			default: 
				return DeploymentState.unknown;
		}
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> result = new HashMap<>();

		if (server != null) {
			result.put("server_starttime", nullSafeToString(server.getLaunchedAt()));
			result.put("server_ip", server.getAccessIPv4());
			result.put("status", server.getStatus().value());
		}
		return result;
	}
}
