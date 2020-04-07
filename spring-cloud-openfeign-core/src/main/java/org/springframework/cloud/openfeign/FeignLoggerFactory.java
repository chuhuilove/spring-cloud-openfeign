/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.openfeign;

import feign.Logger;

/**
 * Allows an application to use a custom Feign {@link Logger}.
 * 允许应用程序来使用Feign {@link Logger}.
 * 日志这个东西,真的需要好好研究一下下.
 * 下一步,需要把netflix的源码全部搞下来...
 *
 * @author Venil Noronha
 */
public interface FeignLoggerFactory {

	/**
	 * Factory method to provide a {@link Logger} for a given {@link Class}.
	 * @param type the {@link Class} for which a {@link Logger} instance is to be created
	 * @return a {@link Logger} instance
	 */
	Logger create(Class<?> type);

}
