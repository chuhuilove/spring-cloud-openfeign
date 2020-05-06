/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.openfeign.ribbon;

import java.io.IOException;
import java.net.URI;

import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import feign.Client;
import feign.Request;
import feign.Response;

import org.springframework.cloud.netflix.ribbon.SpringClientFactory;

/**
 * @author Dave Syer
 * 实现了{@linkplain Client},代表着这个类是实际执行http请求并返回响应的类.
 */
public class LoadBalancerFeignClient implements Client {

	static final Request.Options DEFAULT_OPTIONS = new Request.Options();

	private final Client delegate;

	private CachingSpringLoadBalancerFactory lbClientFactory;

	private SpringClientFactory clientFactory;

	public LoadBalancerFeignClient(Client delegate,
			CachingSpringLoadBalancerFactory lbClientFactory,
			SpringClientFactory clientFactory) {
		this.delegate = delegate;
		this.lbClientFactory = lbClientFactory;
		this.clientFactory = clientFactory;
	}

	/**
	 *
	 * @param originalUrl 原始url https://chuhui.server.com/example/req
	 * @param host 主机名  chuhui.server.com
	 * @return
	 */
	static URI cleanUrl(String originalUrl, String host) {
		String newUrl = originalUrl;
		if (originalUrl.startsWith("https://")) {
			newUrl = originalUrl.substring(0, 8)
					+ originalUrl.substring(8 + host.length());
			// newurl: https://chuhui.server.com
		}
		else if (originalUrl.startsWith("http")) {
			newUrl = originalUrl.substring(0, 7)
					+ originalUrl.substring(7 + host.length());
			// newurl: http://chuhui.server.com
		}
		StringBuffer buffer = new StringBuffer(newUrl);

		if ((newUrl.startsWith("https://") && newUrl.length() == 8)
				|| (newUrl.startsWith("http://") && newUrl.length() == 7)) {
			buffer.append("/");
			// buffer:http://chuhui.server.com/ 或者 https://chuhui.server.com/
		}
		return URI.create(buffer.toString());
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		try {
			// 获取完整的url
			URI asUri = URI.create(request.url());
			// 从url中获取主机名
			String clientName = asUri.getHost();
			// 从url中获取主机名,然后进一步封装
			URI uriWithoutHost = cleanUrl(request.url(), clientName);

			FeignLoadBalancer.RibbonRequest ribbonRequest = new FeignLoadBalancer.RibbonRequest(
					this.delegate, request, uriWithoutHost);

			IClientConfig requestConfig = getClientConfig(options, clientName);

			return lbClient(clientName)
					.executeWithLoadBalancer(ribbonRequest, requestConfig).toResponse();
		}
		catch (ClientException e) {
			IOException io = findIOException(e);
			if (io != null) {
				throw io;
			}
			throw new RuntimeException(e);
		}
	}

	/**
	 * 获取客户端配置
	 * @param options 设置了连接超时,读取超时..
	 * @param clientName 客户端名称
	 * @return
	 */
	IClientConfig getClientConfig(Request.Options options, String clientName) {
		IClientConfig requestConfig;
		if (options == DEFAULT_OPTIONS) {
			requestConfig = this.clientFactory.getClientConfig(clientName);
		}
		else {
			requestConfig = new FeignOptionsClientConfig(options);
		}
		return requestConfig;
	}

	protected IOException findIOException(Throwable t) {
		if (t == null) {
			return null;
		}
		if (t instanceof IOException) {
			return (IOException) t;
		}
		return findIOException(t.getCause());
	}

	public Client getDelegate() {
		return this.delegate;
	}

	private FeignLoadBalancer lbClient(String clientName) {
		return this.lbClientFactory.create(clientName);
	}

	static class FeignOptionsClientConfig extends DefaultClientConfigImpl {

		FeignOptionsClientConfig(Request.Options options) {
			setProperty(CommonClientConfigKey.ConnectTimeout,
					options.connectTimeoutMillis());
			setProperty(CommonClientConfigKey.ReadTimeout, options.readTimeoutMillis());
		}

		@Override
		public void loadProperties(String clientName) {

		}

		@Override
		public void loadDefaultValues() {

		}

	}

}
