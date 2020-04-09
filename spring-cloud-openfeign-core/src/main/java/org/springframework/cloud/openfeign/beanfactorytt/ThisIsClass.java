package org.springframework.cloud.openfeign.beanfactorytt;

import org.springframework.stereotype.Service;

/**
 * @AUTHOR: cyzi
 * @DATE: 2020/4/7
 * @DESCRIPTION: todo
 */
@Service
public class ThisIsClass {

	static {
		System.err.println("ThisIsClass");
	}

	public void printClassName() {
		System.err.println(getClass().getCanonicalName());
	}

}
