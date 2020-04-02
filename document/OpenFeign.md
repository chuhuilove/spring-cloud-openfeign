# 1.声明式REST客户端:Feign

[Feign](https://github.com/OpenFeign/feign)是一个声明式web服务客户端.

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

**StoreClient.java. **

```java
@FeignClient("stores")
public interface StoreClient {
    @RequestMapping(method = RequestMethod.GET, value = "/stores")
    List<Store> getStores();

    @RequestMapping(method = RequestMethod.POST, value = "/stores/{storeId}", consumes = "application/json")
    Store update(@PathVariable("storeId") Long storeId, Store store);
}
```

# [Feign文档](https://cloud.spring.io/spring-cloud-openfeign/2.1.x/single/spring-cloud-openfeign.html)
