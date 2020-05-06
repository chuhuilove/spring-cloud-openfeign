package com.chuhui.spring.feign.interfaces;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @AUTHOR: cyzi
 * @DATE: 2020/4/9
 * @DESCRIPTION: todo
 */
@FeignClient("${services.remoteServer}")
public interface RemoteInvokeInterface {

	@GetMapping("/feign-server/example/uuid")
	 String remoteService();
}
