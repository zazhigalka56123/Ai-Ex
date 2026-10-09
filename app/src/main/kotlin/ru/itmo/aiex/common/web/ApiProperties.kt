package ru.itmo.aiex.common.web

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aiex.api")
data class ApiProperties(val basePath: String) {
    init {
        require(basePath.startsWith("/") && !basePath.endsWith("/")) {
            "base-path должен начинаться с / и не заканчиваться на /"
        }
    }
}
