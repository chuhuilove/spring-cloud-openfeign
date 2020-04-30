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

package org.springframework.cloud.openfeign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import feign.Client;
import feign.Feign;
import feign.httpclient.ApacheHttpClient;
import feign.okhttp.OkHttpClient;
import okhttp3.ConnectionPool;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.actuator.HasFeatures;
import org.springframework.cloud.commons.httpclient.ApacheHttpClientConnectionManagerFactory;
import org.springframework.cloud.commons.httpclient.ApacheHttpClientFactory;
import org.springframework.cloud.commons.httpclient.OkHttpClientConnectionPoolFactory;
import org.springframework.cloud.commons.httpclient.OkHttpClientFactory;
import org.springframework.cloud.openfeign.support.FeignHttpClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author Spencer Gibb
 * @author Julien Roy
 */
@Configuration
@ConditionalOnClass(Feign.class)
@EnableConfigurationProperties({FeignClientProperties.class,
	FeignHttpClientProperties.class})
public class FeignAutoConfiguration {

	@Autowired(required = false)
	private List<FeignClientSpecification> configurations = new ArrayList<>();

	@Bean
	public HasFeatures feignFeature() {
		return HasFeatures.namedFeature("Feign", Feign.class);
	}

	/**
	 * 将{@code FeignContext}注入到容器中.
	 * 以供后续使用.
	 *
	 * @return
	 */
	@Bean
	public FeignContext feignContext() {
		/**
		 * <dt>FeignContext存在的意义是什么呢?为什么要将其注入到容器中.</dt>
		 * 在{@link #feignContext()}函数中,调用了{@link FeignContext#setConfigurations(List)}.
		 * 这一点就比较值得玩味了.
		 * 再来看,其实际上传入的是{@linkplain configurations}.而{@linkplain configurations}上面
		 * 还添加了一个{@code @Autowired}注解.由此说明,{@linkplain configurations}里面的值是由容器注入的.
		 * 那么问题来了,容器中的{@code FeignClientSpecification}类型的Bean,是从哪里来的呢?
		 * 还记得,在阅读{@link FeignClientsRegistrar}源码的时候.{@link FeignClientsRegistrar#registerBeanDefinitions(AnnotationMetadata, BeanDefinitionRegistry)}
		 * 里面的两个函数调用:
		 * {@link FeignClientsRegistrar#registerDefaultConfiguration(AnnotationMetadata, BeanDefinitionRegistry) }和
		 * {@link FeignClientsRegistrar#registerFeignClients(AnnotationMetadata, BeanDefinitionRegistry)} .
		 * 这两者都有一个共同点,最终都是调用{@link FeignClientsRegistrar#registerClientConfiguration(BeanDefinitionRegistry, Object, Object)}
		 * 函数,来生成一个{@code FeignClientSpecification}类型的{@code BeanDefinition},并将其添加进BeanFactory中.
		 *
		 * 在前者,解析的是添加了{@code @EnableFeignClients}的类,将添加了此注解的类名获取到之后,
		 * 然后在通过组装,组装成default.xxx.FeignClientSpecification的形式,再获取到该类上{@code @EnableFeignClients}注解上的{@code defaultConfiguration}
		 * 属性,将这两个参数作为构造函数值,设置到生成的{@code BeanDefinition}中.
		 *
		 * 后者,解析的添加了{@code FeignClient}注解的类,获取{@code FeignClient}注解的{@link FeignClientsRegistrar#getClientName(Map)}属性,
		 * 同样是通过组装,组装成xxxx.FeignClientSpecification的形式,再获取到该类上{@code @FeignClient}注解上的{@code configuration}
		 * 属性,将这两个参数作为构造函数的值,设置到生成的{@code BeanDefinition}中.
		 *
		 * 以上,就是{@linkplain configurations}中值的由来.
		 *
		 * 但是,现在为止,还是没有闹明白,注入{@code FeignContext}的作用.
		 *
		 *
		 */
		FeignContext context = new FeignContext();
		context.setConfigurations(this.configurations);
		return context;
	}


	@Configuration
	@ConditionalOnClass(name = "feign.hystrix.HystrixFeign")
	protected static class HystrixFeignTargeterConfiguration {

		/**
		 * 注入Target,如果在类路径中发现了{@link feign.hystrix.HystrixFeign},
		 * 则判断是否注入{@link HystrixTargeter},如果用户没有注入自定义的{@link Targeter},
		 * 则注入{@linkplain HystrixFeignTargeterConfiguration#feignTargeter()} 的返回值.
		 */
		@Bean
		@ConditionalOnMissingBean
		public Targeter feignTargeter() {
			return new HystrixTargeter();
		}

	}


	@Configuration
	@ConditionalOnMissingClass("feign.hystrix.HystrixFeign")
	protected static class DefaultFeignTargeterConfiguration {
		/**
		 * 注入Target,如果在类路径中没有发现{@link feign.hystrix.HystrixFeign},
		 * 则判断是否注入{@link DefaultTargeter},如果用户没有注入自定义的{@link Targeter},
		 * 则注入{@linkplain DefaultFeignTargeterConfiguration#feignTargeter()} 的返回值.
		 */
		@Bean
		@ConditionalOnMissingBean
		public Targeter feignTargeter() {
			return new DefaultTargeter();
		}

		/**
		 * 不得不说,SpringBoot的精髓<b>自动注入</b>,就是依赖于如此多的{@code @Conditional*}
		 */
	}

	// the following configuration is for alternate feign clients if
	// ribbon is not on the class path.
	// see corresponding configurations in FeignRibbonClientAutoConfiguration
	// for load balanced ribbon clients.
	@Configuration
	@ConditionalOnClass(ApacheHttpClient.class)
	@ConditionalOnMissingClass("com.netflix.loadbalancer.ILoadBalancer")
	@ConditionalOnMissingBean(CloseableHttpClient.class)
	@ConditionalOnProperty(value = "feign.httpclient.enabled", matchIfMissing = true)
	protected static class HttpClientFeignConfiguration {

		private final Timer connectionManagerTimer = new Timer(
			"FeignApacheHttpClientConfiguration.connectionManagerTimer", true);

		@Autowired(required = false)
		private RegistryBuilder registryBuilder;

		private CloseableHttpClient httpClient;

		@Bean
		@ConditionalOnMissingBean(HttpClientConnectionManager.class)
		public HttpClientConnectionManager connectionManager(
			ApacheHttpClientConnectionManagerFactory connectionManagerFactory,
			FeignHttpClientProperties httpClientProperties) {
			final HttpClientConnectionManager connectionManager = connectionManagerFactory
				.newConnectionManager(httpClientProperties.isDisableSslValidation(),
					httpClientProperties.getMaxConnections(),
					httpClientProperties.getMaxConnectionsPerRoute(),
					httpClientProperties.getTimeToLive(),
					httpClientProperties.getTimeToLiveUnit(),
					this.registryBuilder);
			this.connectionManagerTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					connectionManager.closeExpiredConnections();
				}
			}, 30000, httpClientProperties.getConnectionTimerRepeat());
			return connectionManager;
		}

		@Bean
		public CloseableHttpClient httpClient(ApacheHttpClientFactory httpClientFactory,
											  HttpClientConnectionManager httpClientConnectionManager,
											  FeignHttpClientProperties httpClientProperties) {
			RequestConfig defaultRequestConfig = RequestConfig.custom()
				.setConnectTimeout(httpClientProperties.getConnectionTimeout())
				.setRedirectsEnabled(httpClientProperties.isFollowRedirects())
				.build();
			this.httpClient = httpClientFactory.createBuilder()
				.setConnectionManager(httpClientConnectionManager)
				.setDefaultRequestConfig(defaultRequestConfig).build();
			return this.httpClient;
		}

		@Bean
		@ConditionalOnMissingBean(Client.class)
		public Client feignClient(HttpClient httpClient) {
			return new ApacheHttpClient(httpClient);
		}

		@PreDestroy
		public void destroy() throws Exception {
			this.connectionManagerTimer.cancel();
			if (this.httpClient != null) {
				this.httpClient.close();
			}
		}

	}

	@Configuration
	@ConditionalOnClass(OkHttpClient.class)
	@ConditionalOnMissingClass("com.netflix.loadbalancer.ILoadBalancer")
	@ConditionalOnMissingBean(okhttp3.OkHttpClient.class)
	@ConditionalOnProperty("feign.okhttp.enabled")
	protected static class OkHttpFeignConfiguration {

		private okhttp3.OkHttpClient okHttpClient;

		@Bean
		@ConditionalOnMissingBean(ConnectionPool.class)
		public ConnectionPool httpClientConnectionPool(
			FeignHttpClientProperties httpClientProperties,
			OkHttpClientConnectionPoolFactory connectionPoolFactory) {
			Integer maxTotalConnections = httpClientProperties.getMaxConnections();
			Long timeToLive = httpClientProperties.getTimeToLive();
			TimeUnit ttlUnit = httpClientProperties.getTimeToLiveUnit();
			return connectionPoolFactory.create(maxTotalConnections, timeToLive, ttlUnit);
		}

		@Bean
		public okhttp3.OkHttpClient client(OkHttpClientFactory httpClientFactory,
										   ConnectionPool connectionPool,
										   FeignHttpClientProperties httpClientProperties) {
			Boolean followRedirects = httpClientProperties.isFollowRedirects();
			Integer connectTimeout = httpClientProperties.getConnectionTimeout();
			Boolean disableSslValidation = httpClientProperties.isDisableSslValidation();
			this.okHttpClient = httpClientFactory.createBuilder(disableSslValidation)
				.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
				.followRedirects(followRedirects).connectionPool(connectionPool)
				.build();
			return this.okHttpClient;
		}

		@PreDestroy
		public void destroy() {
			if (this.okHttpClient != null) {
				this.okHttpClient.dispatcher().executorService().shutdown();
				this.okHttpClient.connectionPool().evictAll();
			}
		}

		@Bean
		@ConditionalOnMissingBean(Client.class)
		public Client feignClient(okhttp3.OkHttpClient client) {
			return new OkHttpClient(client);
		}

	}

}
