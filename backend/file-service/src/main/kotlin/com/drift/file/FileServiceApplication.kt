package com.drift.file

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** file-service entry point. */
@SpringBootApplication
class FileServiceApplication

fun main(args: Array<String>) {
    runApplication<FileServiceApplication>(*args)
}
