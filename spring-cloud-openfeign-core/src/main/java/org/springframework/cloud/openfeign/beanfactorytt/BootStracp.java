package org.springframework.cloud.openfeign.beanfactorytt;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @AUTHOR: cyzi
 * @DATE: 2020/4/7
 * @DESCRIPTION: todo
 */
public class BootStracp {

	public static void main(String[] args) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				AppConfig.class);
		ThisIsInterface bean = context.getBean(ThisIsInterface.class);
		bean.printClassName();

	}

}
