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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import feign.ReflectiveFeign;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AbstractClassTestingTypeFilter;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * @author Spencer Gibb
 * @author Jakub Narloch
 * @author Venil Noronha
 * @author Gang Li
 */
class FeignClientsRegistrar
	implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {

	// patterned after Spring Integration IntegrationComponentScanRegistrar
	// and RibbonClientsConfigurationRegistgrar

	private ResourceLoader resourceLoader;

	private Environment environment;

	FeignClientsRegistrar() {
	}

	static void validateFallback(final Class clazz) {
		Assert.isTrue(!clazz.isInterface(),
			"Fallback class must implement the interface annotated by @FeignClient");
	}

	static void validateFallbackFactory(final Class clazz) {
		Assert.isTrue(!clazz.isInterface(), "Fallback factory must produce instances "
			+ "of fallback classes that implement the interface annotated by @FeignClient");
	}

	static String getName(String name) {
		if (!StringUtils.hasText(name)) {
			return "";
		}

		String host = null;
		try {
			String url;
			if (!name.startsWith("http://") && !name.startsWith("https://")) {
				url = "http://" + name;
			} else {
				url = name;
			}
			host = new URI(url).getHost();

		} catch (URISyntaxException e) {
		}
		Assert.state(host != null, "Service id not legal hostname (" + name + ")");
		return name;
	}

	static String getUrl(String url) {
		if (StringUtils.hasText(url) && !(url.startsWith("#{") && url.contains("}"))) {
			if (!url.contains("://")) {
				url = "http://" + url;
			}
			try {
				new URL(url);
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException(url + " is malformed", e);
			}
		}
		return url;
	}

	static String getPath(String path) {
		if (StringUtils.hasText(path)) {
			path = path.trim();
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
		}
		return path;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata metadata,
										BeanDefinitionRegistry registry) {
		/**
		 * 提取出{@link EnableFeignClients}注解的defaultConfiguration属性,
		 * 和添加了{@link EnableFeignClients}注解的类的全类名,
		 * 将这个两个参数作为{@link FeignClientSpecification}的构造参数,
		 * 然后将{@link FeignClientSpecification}转化为BeanDefinition,注册到BeanFactory中.
		 * 这里,为什么要将{@link FeignClientSpecification}注册到BeanFactory中?
		 * {@link FeignClientSpecification}有什么特殊的意义存在吗?
		 * 所以,就需要根据其中的代码,来进行判断了.
		 * 直接去看{@link FeignClientSpecification}的源码吧.
		 * @see FeignClientSpecification
		 */
		registerDefaultConfiguration(metadata, registry);
		/**
		 *
		 */
		registerFeignClients(metadata, registry);
	}

	private void registerDefaultConfiguration(AnnotationMetadata metadata,
											  BeanDefinitionRegistry registry) {
		/**
		 * 先获取EnableFeignClients注解上的属性和属性值,
		 * 在目前的情况下,EnableFeignClients注解上有
		 * value,basePackages,basePackageClasses,
		 * defaultConfiguration,clients这个五个属性.
		 * 不过,这个五个属性有没有设置值,是另外的事情.
		 */
		Map<String, Object> defaultAttrs = metadata
			.getAnnotationAttributes(EnableFeignClients.class.getName(), true);

		if (defaultAttrs != null && defaultAttrs.containsKey("defaultConfiguration")) {
			String name;
			// 判断添加了EnableFeignClients注解的类是否是嵌套类,内部类,和在方法中定义的匿名内部类.
			// 如果是那样,则该类的全类名和直接用public class 修饰的全类名有出入\
			// 所以这里需要先判断一下.
			if (metadata.hasEnclosingClass()) {
				name = "default." + metadata.getEnclosingClassName();
			} else {
				name = "default." + metadata.getClassName();
			}
			registerClientConfiguration(registry, name,
				defaultAttrs.get("defaultConfiguration"));
		}
	}

	public void registerFeignClients(AnnotationMetadata metadata,
									 BeanDefinitionRegistry registry) {
		// 获取类扫描器
		ClassPathScanningCandidateComponentProvider scanner = getScanner();
		scanner.setResourceLoader(this.resourceLoader);

		Set<String> basePackages;

		// 获取EnableFeignClients注解的属性
		Map<String, Object> attrs = metadata
			.getAnnotationAttributes(EnableFeignClients.class.getName());

		// 设置类型过滤器,只过滤添加了@FeignClient注解的类
		AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(
			FeignClient.class);

		// 获取注解的clients属性值
		final Class<?>[] clients = attrs == null ? null
			: (Class<?>[]) attrs.get("clients");

		if (clients == null || clients.length == 0) {
			/**
			 * 如果没有配置clients属性,或者属性就是默认值,即,是一个空数组
			 * 则扫描整个包,从加了注解的那个类中,提取出其所在的包名,
			 * 然后进行扫描
			 */
			scanner.addIncludeFilter(annotationTypeFilter);
			basePackages = getBasePackages(metadata);
		} else {
			/**
			 * 如果已经配置了一些必须要扫描的类,则会连带着配置类所在的包,也一块扫描.
			 * 比如说,在client属性中,指定了一个内部接口,其全限定名为com.chuhui.aaa.bbb$ccc$ddd
			 * 那么在获取其包名的时候,只能获取到com.chuhui.aaa
			 * 而其类名,通过clazz.getCanonicalName()方法,则能获取到com.chuhui.aaa.bbb.ccc.ddd
			 * 这种形式
			 */
			final Set<String> clientClasses = new HashSet<>();
			basePackages = new HashSet<>();
			for (Class<?> clazz : clients) {
				basePackages.add(ClassUtils.getPackageName(clazz));
				//clazz.getCanonicalName()这个方法在这里使用,真精彩.
				// 不需要自己再手动去处理内部类,嵌套类中的$符号的问题了.
				clientClasses.add(clazz.getCanonicalName());
			}
			AbstractClassTestingTypeFilter filter = new AbstractClassTestingTypeFilter() {
				@Override
				protected boolean match(ClassMetadata metadata) {
					String cleaned = metadata.getClassName().replaceAll("\\$", ".");
					return clientClasses.contains(cleaned);
				}
			};
			scanner.addIncludeFilter(
				new AllTypeFilter(Arrays.asList(filter, annotationTypeFilter)));
		}

		/**
		 * 扫描包中,带有@FeignClient注解的类
		 */
		for (String basePackage : basePackages) {

			// 获取包里面,带有FeignClient注解的BeanDefinition
			// findCandidateComponents这个函数,会自动将.class文件转换成BeanDefinition对象
			Set<BeanDefinition> candidateComponents = scanner
				.findCandidateComponents(basePackage);

			for (BeanDefinition candidateComponent : candidateComponents) {
				if (candidateComponent instanceof AnnotatedBeanDefinition) {
					// 验证,带有@FeignClient注解的类,是接口,而不是其他形式的类型
					// fixme 注意,这里有一个连环炮,为什么一定要接口呢?
					/**
					 * 2020年4月7日11:03:00
					 * 这里解释一下,为什么一定要使用接口呢?抽象类不行吗?
					 * {@link ReflectiveFeign#newInstance(feign.Target)}函数是创建接口的实例的地方,
					 * 在这个里面,调用了 <pre>{@code
					 *  InvocationHandler handler = factory.create(target, methodToHandler);
					 *  T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(),
					 *         new Class<?>[] {target.type()}, handler);
					 *  //其他事情.......
					 *  return proxy;
					 * }</pre>
					 * 换句话说,生成代理对象的技术是JDK动态代理...而不是采用的,类似cglib之类的东西.
					 */
					AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
					AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
					Assert.isTrue(annotationMetadata.isInterface(),
						"@FeignClient can only be specified on an interface");

					Map<String, Object> attributes = annotationMetadata
						.getAnnotationAttributes(
							FeignClient.class.getCanonicalName());


					/**
					 * 为这个带有@FeignClient的类,注册一个
					 * {@link FeignClientSpecification}
					 */
					String name = getClientName(attributes);
					registerClientConfiguration(registry, name,
						attributes.get("configuration"));
					/**
					 * 获取到FeignClient的name以后,
					 * 将这个带有@FeignClient注解的类注册到BeanFactory中,
					 * 即,添加到beanDefinitionMap
					 */
					registerFeignClient(registry, annotationMetadata, attributes);
				}
			}
		}
	}

	/**
	 * 将被@FeignClient注解的类,注册到BeanFactory中
	 * @param registry  注册BeanDefinition的借口
	 * @param annotationMetadata 被@FeignClient注册过的类的元数据
	 * @param attributes 从@FeignClient提取出来的属性,map
	 */
	private void registerFeignClient(BeanDefinitionRegistry registry,
									 AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
		/**
		 *  先假设一下,添加@FeignClient注解的类名叫做AddFeignClass,
		 *  现在来分析一下,是如何将AddFeignClass添加到容器中的.
		 *
		 *  首先,如果我们直接调用{@code BeanDefinitionBuilder.genericBeanDefinition(AddFeignClass.class)},
		 *  这么做是否合适?
		 *
		 *  答案是,不合适.为什么呢?
		 *  只因为,AddFeignClass是一个接口,虽然将其转换成一个{@link BeanDefinition},是允许的.
		 *  但是在容器里面的,必须是AddFeignClass的一个实例化对象.
		 *  并且,在扫描阶段,spring-framework会直接过滤掉直接添加了@Service注解的接口.
		 *
		 *
		 *  根据FactoryBean的特性,只有在{@link FactoryBean#getObject()} 的时候,返回的才是真正的实例.
		 *  所以,在外部调用{@link FactoryBean#getObject()}的时候,就可以在{@link FactoryBean#getObject()}
		 *  内部做很多文章,比如,给AddFeignClass接口动态生成一个实例对象.
		 *
		 *  所以,这里将{@link FeignClientFactoryBean}转化为BeanDefinition.
		 *
		 *  不要去从参数数量的角度去考虑这个问题,
		 *  因为{@code BeanDefinitionBuilder.addConstructorArgValue}
		 *  和{@code BeanDefinitionBuilder.addConstructorArgReference}
		 *  可以无限制的添加参数.
		 *
		 */

		String className = annotationMetadata.getClassName();

		// 使用FeignClientFactoryBean来创建一个BeanDefinition
		BeanDefinitionBuilder definition = BeanDefinitionBuilder
			.genericBeanDefinition(FeignClientFactoryBean.class);
		validate(attributes);


		definition.addPropertyValue("url", getUrl(attributes));
		definition.addPropertyValue("path", getPath(attributes));
		String name = getName(attributes);
		definition.addPropertyValue("name", name);
		String contextId = getContextId(attributes);
		definition.addPropertyValue("contextId", contextId);
		definition.addPropertyValue("type", className);
		definition.addPropertyValue("decode404", attributes.get("decode404"));
		definition.addPropertyValue("fallback", attributes.get("fallback"));
		definition.addPropertyValue("fallbackFactory", attributes.get("fallbackFactory"));
		// 注入模式,设置为按类型注入
		definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

		String alias = contextId + "FeignClient";
		AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();

		boolean primary = (Boolean) attributes.get("primary"); // has a default, won't be
		// null

		beanDefinition.setPrimary(primary);

		String qualifier = getQualifier(attributes);
		if (StringUtils.hasText(qualifier)) {
			alias = qualifier;
		}

		BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className,
			new String[]{alias});
		BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
	}

	private void validate(Map<String, Object> attributes) {
		AnnotationAttributes annotation = AnnotationAttributes.fromMap(attributes);
		// This blows up if an aliased property is overspecified
		// FIXME annotation.getAliasedString("name", FeignClient.class, null);
		validateFallback(annotation.getClass("fallback"));
		validateFallbackFactory(annotation.getClass("fallbackFactory"));
	}

	/* for testing */ String getName(Map<String, Object> attributes) {
		String name = (String) attributes.get("serviceId");
		if (!StringUtils.hasText(name)) {
			name = (String) attributes.get("name");
		}
		if (!StringUtils.hasText(name)) {
			name = (String) attributes.get("value");
		}
		name = resolve(name);
		return getName(name);
	}

	private String getContextId(Map<String, Object> attributes) {
		String contextId = (String) attributes.get("contextId");
		if (!StringUtils.hasText(contextId)) {
			return getName(attributes);
		}

		contextId = resolve(contextId);
		return getName(contextId);
	}

	private String resolve(String value) {
		if (StringUtils.hasText(value)) {
			return this.environment.resolvePlaceholders(value);
		}
		return value;
	}

	private String getUrl(Map<String, Object> attributes) {
		String url = resolve((String) attributes.get("url"));
		return getUrl(url);
	}

	private String getPath(Map<String, Object> attributes) {
		String path = resolve((String) attributes.get("path"));
		return getPath(path);
	}

	protected ClassPathScanningCandidateComponentProvider getScanner() {

		return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
			@Override
			protected boolean isCandidateComponent(
				AnnotatedBeanDefinition beanDefinition) {
				boolean isCandidate = false;
				if (beanDefinition.getMetadata().isIndependent()) {
					if (!beanDefinition.getMetadata().isAnnotation()) {
						isCandidate = true;
					}
				}
				return isCandidate;
			}
		};
	}

	/**
	 * 从EnableFeignClients的属性中,最大程度的获取所有包名
	 * @param importingClassMetadata 添加了EnableFeignClients注解的基础类,所代表的AnnotationMetadata对象
	 * @return
	 */
	protected Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
		Map<String, Object> attributes = importingClassMetadata
			.getAnnotationAttributes(EnableFeignClients.class.getCanonicalName());

		Set<String> basePackages = new HashSet<>();
		for (String pkg : (String[]) attributes.get("value")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		for (String pkg : (String[]) attributes.get("basePackages")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		for (Class<?> clazz : (Class[]) attributes.get("basePackageClasses")) {
			basePackages.add(ClassUtils.getPackageName(clazz));
		}

		if (basePackages.isEmpty()) {
			basePackages.add(
				ClassUtils.getPackageName(importingClassMetadata.getClassName()));
		}
		return basePackages;
	}

	private String getQualifier(Map<String, Object> client) {
		if (client == null) {
			return null;
		}
		String qualifier = (String) client.get("qualifier");
		if (StringUtils.hasText(qualifier)) {
			return qualifier;
		}
		return null;
	}

	/**
	 * 获取一个值,作为客户端的名字.
	 * 如果优先从{@link FeignClient#contextId()}获取值.
	 * 如果没有获取到,则从{@link FeignClient#value()},
	 * {@link FeignClient#name()},
	 * {@link FeignClient#toString()}
	 * 一次获取,直至获取到.
	 *
	 * @param client @FeignClient的属性
	 * @return
	 */
	private String getClientName(Map<String, Object> client) {
		if (client == null) {
			return null;
		}
		String value = (String) client.get("contextId");
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("value");
		}
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("name");
		}
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("serviceId");
		}
		if (StringUtils.hasText(value)) {
			return value;
		}

		throw new IllegalStateException("Either 'name' or 'value' must be provided in @"
			+ FeignClient.class.getSimpleName());
	}

	private void registerClientConfiguration(BeanDefinitionRegistry registry, Object name,
											 Object configuration) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(FeignClientSpecification.class);

		builder.addConstructorArgValue(name);
		builder.addConstructorArgValue(configuration);
		/**
		 * 这里动态的注册到BeanFactory中一个BeanDefinition,也就是,那个{@code beanDefinitionMap}
		 * beanName:name.FeignClientSpecification
		 * beanDefinition:由FeignClientSpecification组成的Bean定义
		 */
		registry.registerBeanDefinition(
			name + "." + FeignClientSpecification.class.getSimpleName(),
			builder.getBeanDefinition());
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	/**
	 * Helper class to create a {@link TypeFilter} that matches if all the delegates
	 * match.
	 *
	 * @author Oliver Gierke
	 */
	private static class AllTypeFilter implements TypeFilter {

		private final List<TypeFilter> delegates;

		/**
		 * Creates a new {@link AllTypeFilter} to match if all the given delegates match.
		 *
		 * @param delegates must not be {@literal null}.
		 */
		AllTypeFilter(List<TypeFilter> delegates) {
			Assert.notNull(delegates, "This argument is required, it must not be null");
			this.delegates = delegates;
		}

		@Override
		public boolean match(MetadataReader metadataReader,
							 MetadataReaderFactory metadataReaderFactory) throws IOException {

			for (TypeFilter filter : this.delegates) {
				if (!filter.match(metadataReader, metadataReaderFactory)) {
					return false;
				}
			}

			return true;
		}

	}

}
