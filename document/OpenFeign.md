
# [Feign文档](https://cloud.spring.io/spring-cloud-openfeign/2.1.x/single/spring-cloud-openfeign.html)

# 1.声明式REST客户端:Feign

[Feign](https://github.com/OpenFeign/feign)是一个声明式web服务客户端.它使编写Web服务客户端更加容易.要使用Feign,需要创建一个接口并对其添加注解.它具有可插入注解支持,包括Feign注解和JAX-RS注解.Feign还支持可插拔编码器和解码器.Spring Cloud添加了对Spring MVC注解的支持,并支持使用Spring Web中默认使用的相同`HttpMessageConverters`.当使用Feign时,Spring Cloud集成了Ribbon和Eureka以提供负载均衡的http客户端.

## 1.1 如何包含Feign

要在项目中包含Feign,需要引入如下的组件:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```
下面是Spring Boot示例

```java
@SpringBootApplication
@EnableFeignClients
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
```

**StoreClient.java.**

```java
@FeignClient("stores")
public interface StoreClient {
    @RequestMapping(method = RequestMethod.GET, value = "/stores")
    List<Store> getStores();

    @RequestMapping(method = RequestMethod.POST, value = "/stores/{storeId}", consumes = "application/json")
    Store update(@PathVariable("storeId") Long storeId, Store store);
}
```
在`@FeignClient`注解中,字符串值(上面的"stores")是一个任意的客户端名称,用于创建一个Ribbon负载均衡器(有关Ribbon支持的详细信息,请参阅下面).还可以使用`url`属性(绝对值或主机名)指定URL.应用程序上下文中bean的名称是接口的完全限定名.要指定自己的别名值,可以使用`@FeignClient`注解的`qualifier`值.

上面的Ribbon客户端希望发现"stores"服务的物理地址.如果您的应用程序是一个Eureka客户端,那么它将在Eureka服务注册表中解析服务.如果你不想使用Eureka,你可以简单地在外部配置中配置一个服务器列表(参见上面的示例).

## 1.2 Overriding Feign Defaults

```java
@FeignClient(name = "stores", configuration = FooConfiguration.class)
public interface StoreClient {
    //..
}
```

## 1.3 手动创建Feign客户端

## 1.4 Feign Hystrix Support

## 1.5 Feign Hystrix Fallbacks

## 1.6 Feign and @Primary

## 1.7 Feign Inheritance Support

## 1.8 Feign request/response compression

## 1.9 Feign logging

## 1.10 Feign @QueryMap support

OpenFeign @QueryMap注解支持将POJO用作GET参数映射.不幸的是,默认的OpenFeign QueryMap注解与Spring不兼容,因为它缺少`value`属性.
Spring Cloud OpenFeign提供了一个等价的`@SpringQueryMap`注解,用于将POJO或Map参数注解为查询参数映射.

例如,Params类定义两个属性`param1`和`param2`:

```java
// Params.java
public class Params {
    private String param1;
    private String param2;

    // [Getters and setters omitted for brevity]
}
```

以下Feign客户端通过使用`@SpringQueryMap`注解来使用`Params`类：

```java
@FeignClient("demo")
public class DemoTemplate {

    @GetMapping(path = "/demo")
    String demoEndpoint(@SpringQueryMap Params params);
}
```

## 1.11 Troubleshooting

### 1.11.1 Early Initialization Errors
