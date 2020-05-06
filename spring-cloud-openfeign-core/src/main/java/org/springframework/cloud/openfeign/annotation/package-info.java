/**
 * spring-cloud-openfeign.org.springframework.cloud.openfeign.annotation.package-info
 *
 * 接口内的方法参数上注解的处理器.
 *
 * 这个包里面的类,在{@link org.springframework.cloud.openfeign.support.SpringMvcContract}中有体现.
 * 在初始化{@link org.springframework.cloud.openfeign.support.SpringMvcContract}的时候,
 * 需要传进来一个{@code List<AnnotatedParameterProcessor>}对象,以作为参数上注解的解析器.
 * {@link org.springframework.cloud.openfeign.support.SpringMvcContract}的初始化
 * 在{@link org.springframework.cloud.openfeign.FeignClientsConfiguration#feignContract(org.springframework.core.convert.ConversionService)}
 * 函数中,将{@link org.springframework.cloud.openfeign.FeignClientsConfiguration#parameterProcessors}
 * 作为参数传给了{@code SpringMvcContract}.
 * 但是,请注意,{@link org.springframework.cloud.openfeign.FeignClientsConfiguration#parameterProcessors}上使用的
 * 是{@code @Autowired(required = false)},这也就意味着,开发者不是必须要创建{@link org.springframework.cloud.openfeign.AnnotatedParameterProcessor}
 * Bean.
 * 但是,如果开发者自己创建了{@link org.springframework.cloud.openfeign.AnnotatedParameterProcessor} Bean,
 * 那么就意味着,{@code SpringMvcContract}在使用自定义{@code AnnotatedParameterProcessor}Bean的情况下,
 * 不再使用此包内的处理器.
 * 除非,手动将此包内的处理器注册成Bean,由{@code SpringMvcContract}的构造函数来进行处理.
 *
 *
 *
 *
 *
 *
 *
 *
 * @author: cyzi
 * @Date: 2020-05-01  17:07:56
 * @Description: TODO
 */
package org.springframework.cloud.openfeign.annotation;
