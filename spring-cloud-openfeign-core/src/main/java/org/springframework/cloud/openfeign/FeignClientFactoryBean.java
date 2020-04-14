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

import java.util.Map;
import java.util.Objects;

import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 这个类实现了{@link FactoryBean}接口,也就是说,通过{@link FactoryBean#getObject()}返回的Bean,
 * 才是真正的bean.
 * @see FeignClientsRegistrar#registerFeignClient(BeanDefinitionRegistry, AnnotationMetadata, Map)
 * @author Spencer Gibb
 * @author Venil Noronha
 * @author Eko Kurniawan Khannedy
 * @author Gregor Zurowski
 */
class FeignClientFactoryBean
	implements FactoryBean<Object>, InitializingBean, ApplicationContextAware {
	public FeignClientFactoryBean(){
		System.err.println("has init FeignClientFactoryBean");
	}

	/***********************************
	 * 警告!这个类中没有任何东西属性应该添加@Autowired的.它会因为一些生命周期竞争条件而导致NPE.
	 ***********************************/

	/**
	 * 对应被添加了@FeignClient注解的那个类的type
	 */
	private Class<?> type;

	/**
	 * 对应FeignClient注解中serviceId,name,value
	 * 这三者之一.
	 */
	private String name;

	/**
	 * 对应FeignClient注解中url
	 */
	private String url;
	/**
	 * 对应FeignClient注解中的contextId,
	 * 如果在FeignClient注解上设置了contextId,则取值contextId,
	 * 否则,和{@linkplain #name}取值一致
	 */
	private String contextId;

	/**
	 * 对应FeignClient注解中的path
	 */
	private String path;

	/**
	 * 对应FeignClient注解中的decode404
	 */
	private boolean decode404;

	/**
	 * 对应FeignClient注解中的fallback
	 */
	private Class<?> fallback = void.class;

	/**
	 * 对应FeignClient注解中的fallbackFactory
	 */
	private Class<?> fallbackFactory = void.class;

	private ApplicationContext applicationContext;


	//////////start------>FactoryBean接口的实现//////////////////////
	@Override
	public Object getObject() throws Exception {
		return getTarget();
	}

	@Override
	public Class<?> getObjectType() {
		return this.type;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	//////////end------>FactoryBean接口的实现//////////////////////
	public Class<?> getType() {
		return this.type;
	}

	///////ApplicationContextAware接口/////////////////////////
	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		this.applicationContext = context;
	}

	///////InitializingBean接口/////////////////
	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.hasText(this.contextId, "Context id must be set");
		Assert.hasText(this.name, "Name must be set");
	}


	/**
	 * 获取Feign.Builder对象
	 * @param context
	 * @return
	 */
	protected Feign.Builder feign(FeignContext context) {
		FeignLoggerFactory loggerFactory = get(context, FeignLoggerFactory.class);
		Logger logger = loggerFactory.create(this.type);

		// @formatter:off
		Feign.Builder builder = get(context, Feign.Builder.class)
			// required values
			.logger(logger)
			// 设置编码器
			.encoder(get(context, Encoder.class))
			// 设置解码器
			.decoder(get(context, Decoder.class))
			.contract(get(context, Contract.class));
		// @formatter:on

		configureFeign(context, builder);

		return builder;
	}

	protected void configureFeign(FeignContext context, Feign.Builder builder) {
		// 获取用户配置的feign客户端属性
		// 也就是在feign.client中配置的值
		FeignClientProperties properties = this.applicationContext
			.getBean(FeignClientProperties.class);

		if (properties != null) {
			if (properties.isDefaultToProperties()) {
				configureUsingConfiguration(context, builder);
				configureUsingProperties(
					properties.getConfig().get(properties.getDefaultConfig()),
					builder);
				configureUsingProperties(properties.getConfig().get(this.contextId),
					builder);
			} else {
				configureUsingProperties(
					properties.getConfig().get(properties.getDefaultConfig()),
					builder);
				configureUsingProperties(properties.getConfig().get(this.contextId),
					builder);
				configureUsingConfiguration(context, builder);
			}
		} else {
			configureUsingConfiguration(context, builder);
		}
	}

	protected void configureUsingConfiguration(FeignContext context,
											   Feign.Builder builder) {
		Logger.Level level = getOptional(context, Logger.Level.class);
		if (level != null) {
			builder.logLevel(level);
		}
		Retryer retryer = getOptional(context, Retryer.class);
		if (retryer != null) {
			builder.retryer(retryer);
		}
		ErrorDecoder errorDecoder = getOptional(context, ErrorDecoder.class);
		if (errorDecoder != null) {
			builder.errorDecoder(errorDecoder);
		}
		Request.Options options = getOptional(context, Request.Options.class);
		if (options != null) {
			builder.options(options);
		}
		Map<String, RequestInterceptor> requestInterceptors = context
			.getInstances(this.contextId, RequestInterceptor.class);
		if (requestInterceptors != null) {
			builder.requestInterceptors(requestInterceptors.values());
		}

		if (this.decode404) {
			builder.decode404();
		}
	}

	protected void configureUsingProperties(
		FeignClientProperties.FeignClientConfiguration config,
		Feign.Builder builder) {
		if (config == null) {
			return;
		}

		if (config.getLoggerLevel() != null) {
			builder.logLevel(config.getLoggerLevel());
		}

		if (config.getConnectTimeout() != null && config.getReadTimeout() != null) {
			builder.options(new Request.Options(config.getConnectTimeout(),
				config.getReadTimeout()));
		}

		if (config.getRetryer() != null) {
			Retryer retryer = getOrInstantiate(config.getRetryer());
			builder.retryer(retryer);
		}

		if (config.getErrorDecoder() != null) {
			ErrorDecoder errorDecoder = getOrInstantiate(config.getErrorDecoder());
			builder.errorDecoder(errorDecoder);
		}

		if (config.getRequestInterceptors() != null
			&& !config.getRequestInterceptors().isEmpty()) {
			// this will add request interceptor to builder, not replace existing
			for (Class<RequestInterceptor> bean : config.getRequestInterceptors()) {
				RequestInterceptor interceptor = getOrInstantiate(bean);
				builder.requestInterceptor(interceptor);
			}
		}

		if (config.getDecode404() != null) {
			if (config.getDecode404()) {
				builder.decode404();
			}
		}

		if (Objects.nonNull(config.getEncoder())) {
			builder.encoder(getOrInstantiate(config.getEncoder()));
		}

		if (Objects.nonNull(config.getDecoder())) {
			builder.decoder(getOrInstantiate(config.getDecoder()));
		}

		if (Objects.nonNull(config.getContract())) {
			builder.contract(getOrInstantiate(config.getContract()));
		}
	}

	private <T> T getOrInstantiate(Class<T> tClass) {
		try {
			return this.applicationContext.getBean(tClass);
		} catch (NoSuchBeanDefinitionException e) {
			return BeanUtils.instantiateClass(tClass);
		}
	}

	protected <T> T get(FeignContext context, Class<T> type) {
		T instance = context.getInstance(this.contextId, type);
		if (instance == null) {
			throw new IllegalStateException(
				"No bean found of type " + type + " for " + this.contextId);
		}
		return instance;
	}

	protected <T> T getOptional(FeignContext context, Class<T> type) {
		return context.getInstance(this.contextId, type);
	}

	/**
	 * 设置负载均衡
	 * @param builder
	 * @param context
	 * @param target
	 * @param <T>
	 * @return
	 */
	protected <T> T loadBalance(Feign.Builder builder, FeignContext context,
								HardCodedTarget<T> target) {
		Client client = getOptional(context, Client.class);
		if (client != null) {
			builder.client(client);
			Targeter targeter = get(context, Targeter.class);
			return targeter.target(this, builder, context, target);
		}

		throw new IllegalStateException(
			"No Feign Client for loadBalancing defined. Did you forget to include spring-cloud-starter-netflix-ribbon?");
	}


	/**
	 * 为添加了@FeignClient注解的接口,创建代理对象.
	 * @param <T> the target type of the Feign client
	 * @return a {@link Feign} client created with the specified data and the context
	 * information
	 */
	<T> T getTarget() {

		//
		FeignContext context = this.applicationContext.getBean(FeignContext.class);

		Feign.Builder builder = feign(context);

		if (!StringUtils.hasText(this.url)) {
			if (!this.name.startsWith("http")) {
				this.url = "http://" + this.name;
			} else {
				this.url = this.name;
			}
			this.url += cleanPath();
			return (T) loadBalance(builder, context,
				new HardCodedTarget<>(this.type, this.name, this.url));
		}
		if (StringUtils.hasText(this.url) && !this.url.startsWith("http")) {
			this.url = "http://" + this.url;
		}
		String url = this.url + cleanPath();
		Client client = getOptional(context, Client.class);
		if (client != null) {
			if (client instanceof LoadBalancerFeignClient) {
				// not load balancing because we have a url,
				// but ribbon is on the classpath, so unwrap
				client = ((LoadBalancerFeignClient) client).getDelegate();
			}
			builder.client(client);
		}
		Targeter targeter = get(context, Targeter.class);

		return (T) targeter.target(this, builder, context,
			new HardCodedTarget<>(this.type, this.name, url));
	}

	private String cleanPath() {
		String path = this.path.trim();
		if (StringUtils.hasLength(path)) {
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
		}
		return path;
	}


	public void setType(Class<?> type) {
		this.type = type;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContextId() {
		return this.contextId;
	}

	public void setContextId(String contextId) {
		this.contextId = contextId;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getPath() {
		return this.path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isDecode404() {
		return this.decode404;
	}

	public void setDecode404(boolean decode404) {
		this.decode404 = decode404;
	}

	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}


	public Class<?> getFallback() {
		return this.fallback;
	}

	public void setFallback(Class<?> fallback) {
		this.fallback = fallback;
	}

	public Class<?> getFallbackFactory() {
		return this.fallbackFactory;
	}

	public void setFallbackFactory(Class<?> fallbackFactory) {
		this.fallbackFactory = fallbackFactory;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FeignClientFactoryBean that = (FeignClientFactoryBean) o;
		return Objects.equals(this.applicationContext, that.applicationContext)
			&& this.decode404 == that.decode404
			&& Objects.equals(this.fallback, that.fallback)
			&& Objects.equals(this.fallbackFactory, that.fallbackFactory)
			&& Objects.equals(this.name, that.name)
			&& Objects.equals(this.path, that.path)
			&& Objects.equals(this.type, that.type)
			&& Objects.equals(this.url, that.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.applicationContext, this.decode404, this.fallback,
			this.fallbackFactory, this.name, this.path, this.type, this.url);
	}

	@Override
	public String toString() {
		return new StringBuilder("FeignClientFactoryBean{").append("type=")
			.append(this.type).append(", ").append("name='").append(this.name)
			.append("', ").append("url='").append(this.url).append("', ")
			.append("path='").append(this.path).append("', ").append("decode404=")
			.append(this.decode404).append(", ").append("applicationContext=")
			.append(this.applicationContext).append(", ").append("fallback=")
			.append(this.fallback).append(", ").append("fallbackFactory=")
			.append(this.fallbackFactory).append("}").toString();
	}

}
