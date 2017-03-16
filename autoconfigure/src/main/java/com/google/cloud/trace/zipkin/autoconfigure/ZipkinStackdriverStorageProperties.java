/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.trace.zipkin.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@ConfigurationProperties("zipkin.storage.stackdriver")
public class ZipkinStackdriverStorageProperties {
	private String projectId;
	private String apiHost = "cloudtrace.googleapis.com";
	private Executor executor = new Executor();

	public Executor getExecutor() {
		return executor;
	}

	public void setExecutor(Executor executor) {
		this.executor = executor;
	}

	public String getProjectId() {
		if (StringUtils.hasText(this.projectId)) {
			return this.projectId;
		}
		RestTemplate restTemplate = new RestTemplate();
		RequestEntity<Void> requestEntity = RequestEntity.get(URI.create("http://metadata.google.internal/computeMetadata/v1/project/project-id"))
				.header("Metadata-Flavor", "Google")
				.build();
		ResponseEntity<String> responseEntity = restTemplate.exchange(requestEntity, String.class);
		return responseEntity.getBody();
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getApiHost() {
		return apiHost;
	}

	public void setApiHost(String apiHost) {
		this.apiHost = apiHost;
	}

	public static class Executor {
		private int corePoolSize = 1;
		private int maxPoolSize = 5;
		private int queueCapacity = 200000;

		public int getCorePoolSize() {
			return corePoolSize;
		}

		public int getMaxPoolSize() {
			return maxPoolSize;
		}

		public int getQueueCapacity() {
			return queueCapacity;
		}

		public void setCorePoolSize(int corePoolSize) {
			this.corePoolSize = corePoolSize;
		}

		public void setMaxPoolSize(int maxPoolSize) {
			this.maxPoolSize = maxPoolSize;
		}

		public void setQueueCapacity(int queueCapacity) {
			this.queueCapacity = queueCapacity;
		}

		public String toString() {
			return "Executor(corePoolSize=" + this.getCorePoolSize()
					+ ", maxPoolSize=" + this.getMaxPoolSize() + ", queueCapacity=" + this.getQueueCapacity() + ")";
		}
	}
}
