package com.chuhui.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * @AUTHOR: cyzi
 * @DATE: 2020/4/9
 * @DESCRIPTION: todo
 */
@SpringBootApplication
@EnableFeignClients
public class BootStrap {

	public static void main(String[] args) {
		  SpringApplication.run(BootStrap.class);
	}



}
