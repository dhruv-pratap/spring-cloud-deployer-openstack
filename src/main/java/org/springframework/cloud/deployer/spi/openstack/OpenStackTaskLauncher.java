/*
 * Copyright 2016-2017 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import org.hashids.Hashids;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ServerCreate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.openstack4j.api.Builders.server;
import static org.openstack4j.model.compute.Action.SUSPEND;
import static org.openstack4j.model.compute.Server.Status.*;

/**
 * A task launcher that targets OpenStack.
 */
public class OpenStackTaskLauncher extends AbstractOpenStackDeployer implements TaskLauncher {

	@Autowired
	public OpenStackTaskLauncher(OpenStackDeployerProperties properties, OSClient client) {
		this.properties = properties;
		this.client = client;
	}

	@Override
	public String launch(AppDeploymentRequest request) {
		String appId = createDeploymentId(request);
		TaskStatus status = status(appId);
		if (!status.getState().equals(LaunchState.unknown)) {
			throw new IllegalStateException("Task " + appId + " already exists with a state of " + status);
		}
		Map<String, String> idMap = createIdMap(appId, request, null);

		logger.debug(String.format("Launching pod for task: %s", appId));
		try {
			createTask(appId, request, idMap);
			return appId;
		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public void cancel(String id) {
		logger.debug(String.format("Cancelling task: %s", id));
		cleanup(id);
	}

	@Override
	public void cleanup(String id) {
		logger.debug(String.format("Deleting pod for task: %s", id));
		deletePod(id);
	}

	@Override
	public void destroy(String appName) {
		for (String id : getPodIdsForTaskName(appName)) {
			cleanup(id);
		}
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		return super.createRuntimeEnvironmentInfo(TaskLauncher.class, this.getClass());
	}

	@Override
	public TaskStatus status(String id) {
		TaskStatus status = buildTaskStatus(id);
		logger.debug(String.format("Status for task: %s is %s", id, status));

		return status;
	}

	protected String createDeploymentId(AppDeploymentRequest request) {
		String name = request.getDefinition().getName();
		Hashids hashids = new Hashids(name, 0, "abcdefghijklmnopqrstuvwxyz1234567890");
		String hashid = hashids.encode(System.currentTimeMillis());
		String deploymentId = name + "-" + hashid;
		// OpenStack does not allow . in the name and does not allow uppercase in the name
		return deploymentId.replace('.', '-').toLowerCase();
	}

	private void createTask(String appId, AppDeploymentRequest request, Map<String, String> idMap) {
		Map<String, String> labelMap = new HashMap<>();
		labelMap.put("task-name", request.getDefinition().getName());
		labelMap.put(SPRING_MARKER_KEY, SPRING_MARKER_VALUE);

		// Create a Server Model Object
		ServerCreate sc = server()
				.name(appId)
				.flavor("flavorId")
				.image("imageId")
				.addMetadata(idMap)
				.addMetadata(labelMap)
				.build();

		// Boot the Server
		Server server = client
				.compute()
				.servers()
				.boot(sc);

	}

	private List<String> getPodIdsForTaskName(String taskName) {
		List<String> ids = new ArrayList<>();
		List<? extends Server> servers = client.compute().servers().list(ImmutableMap.of("task-name", taskName));
		for (Server server : servers) {
			ids.add(server.getName());
		}
		return ids;
	}


	private void deletePod(String appId) {
		try {
			// Suspend Server
			logger.debug(String.format("Suspending service: %s", appId));
			ActionResponse suspensionResponse = client.compute().servers().action(appId, SUSPEND);
			logger.debug(String.format("Suspension status: %s", suspensionResponse));

			// Delete Server
			logger.debug(String.format("Deleting service: %s", appId));
			ActionResponse deletionResponse = client.compute().servers().delete(appId);
			logger.debug(String.format("Deletion status: %s", deletionResponse));

		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	TaskStatus buildTaskStatus(String id) {
		Server server = client.compute().servers().get(id);
		if (server == null) {
			return new TaskStatus(id, LaunchState.unknown, new HashMap<>());
		}
		Server.Status serverStatus = server.getStatus();
		if (serverStatus == null) {
			return new TaskStatus(id, LaunchState.unknown, new HashMap<>());
		}
		else {
			if (serverStatus == BUILD) {
				return new TaskStatus(id, LaunchState.launching, new HashMap<>());
			}
			else if (serverStatus == ERROR) {
				return new TaskStatus(id, LaunchState.failed, new HashMap<>());
			}
			else if (serverStatus == ACTIVE) {
				return new TaskStatus(id, LaunchState.complete, new HashMap<>());
			}
			else {
				return new TaskStatus(id, LaunchState.running, new HashMap<>());
			}
		}
	}

}
