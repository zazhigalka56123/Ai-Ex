plugins {
    base
    // Подключает build-logic к корневому classpath, иначе app не найдёт плагин Spring Boot.
    id("aiex.spring-conventions") apply false
}
