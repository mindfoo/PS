package org.workflow.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Provides a virtual-thread executor for async workflow/task execution. */
@Configuration
class ExecutorConfig {

    @Bean(name = ["executionExecutor"])
    fun executionExecutor(): Executor = Executors.newVirtualThreadPerTaskExecutor()
}
