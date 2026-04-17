package org.workflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
/** Entry point for the Workflow Platform API application. */
class WorkflowMainApplication

fun main(args: Array<String>) {
    runApplication<WorkflowMainApplication>(*args)
}