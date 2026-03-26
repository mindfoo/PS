package org.workflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WorkflowMainApplication

fun main(args: Array<String>) {
    runApplication<WorkflowMainApplication>(*args)
}