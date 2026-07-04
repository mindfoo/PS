package org.workflow.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Configures the executor used to run workflow and task executions asynchronously.
 */
@Configuration
class ExecutorConfig {

    @Bean(name = ["executionExecutor"])
    fun executionExecutor(): Executor = Executors.newVirtualThreadPerTaskExecutor()

    /** HTTP client for HTTP-type task execution in [org.workflow.service.ExecutionService]. */
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
