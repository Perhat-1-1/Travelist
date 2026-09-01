package com.travelist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务线程池:承载 AI 聊天 SSE 流式任务,避免占用 Servlet 线程。
 */
@Configuration
public class AsyncConfig
{
	@Bean(name = "chatStreamExecutor")
	public ThreadPoolTaskExecutor chatStreamExecutor()
	{
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("chat-stream-");
		executor.initialize();
		return executor;
	}
}
