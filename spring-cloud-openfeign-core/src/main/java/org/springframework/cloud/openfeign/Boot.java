package org.springframework.cloud.openfeign;

import org.jetbrains.annotations.NotNull;

/**
 * @AUTHOR: cyzi
 * @DATE: 2020/4/3
 * @DESCRIPTION: 四个name, 总结
 */
public class Boot {
	public static void main(String[] args) {
		/**
		 * 浅谈Class中的四个Name
		 */

		System.err.println(int.class.getName());
		System.err.println(int.class.getTypeName());
		System.err.println(int.class.getSimpleName());
		System.err.println(int.class.getCanonicalName());
		System.err.println("==================================");
		System.err.println(String.class.getName());
		System.err.println(String.class.getTypeName());
		System.err.println(String.class.getSimpleName());
		System.err.println(String.class.getCanonicalName());
		System.err.println("==================================");
		System.err.println(Teste.class.getName());
		System.err.println(Teste.class.getTypeName());
		System.err.println(Teste.class.getSimpleName());
		System.err.println(Teste.class.getCanonicalName());

		Teste[] testes = new Teste[10];
		System.err.println("==================================");

		System.err.println(testes.getClass().getName());
		System.err.println(testes.getClass().getTypeName());
		System.err.println(testes.getClass().getSimpleName());
		System.err.println(testes.getClass().getCanonicalName());

		System.err.println("==================================");

		System.err.println(ThisIsInterface.class.getName());
		System.err.println(ThisIsInterface.class.getTypeName());
		System.err.println(ThisIsInterface.class.getSimpleName());
		System.err.println(ThisIsInterface.class.getCanonicalName());

		System.err.println("==================================");

		System.err.println(Teste.ThisIsInterface.class.getName());
		System.err.println(Teste.ThisIsInterface.class.getTypeName());
		System.err.println(Teste.ThisIsInterface.class.getSimpleName());
		System.err.println(Teste.ThisIsInterface.class.getCanonicalName());

		Comparable comparable = new Comparable<Object>() {
			@Override
			public int compareTo(@NotNull Object o) {
				System.err.println("==================================");

				System.err.println(this.getClass().getName());
				System.err.println(this.getClass().getTypeName());
				System.err.println(this.getClass().getSimpleName());
				System.err.println(this.getClass().getCanonicalName());
				return 0;
			}
		};
		comparable.compareTo(new Object());
	}

	private static class Teste {
		private interface ThisIsInterface {


		}
	}

	private interface ThisIsInterface {

	}
}
