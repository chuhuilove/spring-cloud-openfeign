package com.chuhui.spring.feign.controller;

import com.chuhui.spring.feign.interfaces.RemoteInvokeInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @AUTHOR: cyzi
 * @DATE: 2020/4/9
 * @DESCRIPTION: todo
 */
@RestController
@RequestMapping("test")
public class TestController {

	@Autowired
	private RemoteInvokeInterface invokeService;

	@GetMapping("/message")
	public String getMessage() {
		String s = UUID.randomUUID().toString();
		return s;
	}

	@GetMapping("/remote/invoke")

	public String remoteInvoke() {
		String s = invokeService.remoteService();
		String s1 = UUID.randomUUID().toString();
		return s1 + "   " + s;

	}


}
