package org.springframework.cloud.openfeign.beanfactorytt;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Service;

/**
 * @AUTHOR: cyzi
 * @DATE: 2020/4/7
 * @DESCRIPTION: todo
 */
@Service
public class InterfacesFactoryBean implements FactoryBean<ThisIsInterface> {

	@Override
	public ThisIsInterface getObject() throws Exception {
		return new ThisIsInterface() {
			@Override
			public void printClassName() {
				System.err.println(
						InterfacesFactoryBean.class + " " + getClass().getName());
			}
		};
	}

	@Override
	public Class<?> getObjectType() {
		return ThisIsInterface.class;
	}

}
