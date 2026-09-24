plugins {
    java
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.dzgylxt"
version = "1.0.0"
description = "Supply Chain Procurement Collaboration Platform - Backend"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.0.0-SNAPSHOT"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.5")
        mavenBom("com.baomidou:mybatis-plus-bom:3.5.7")
    }
}

dependencies {
    // ---- Spring Boot 核心 ----
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // ---- ORM: MyBatis-Plus (Spring Boot 3) ----
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.7")
    implementation("com.mysql:mysql-connector-j")
    // jsqlparser 由 mybatis-plus-extension 传递引入（数据权限拦截器依赖）

    // ---- JWT ----
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ---- OpenAPI 3.1 (Springdoc) ----
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // ---- 工具 ----
    implementation("cn.hutool:hutool-all:5.8.32")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("io.minio:minio:8.5.12")
    implementation("com.alibaba:easyexcel:3.3.4")
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ---- Lombok ----
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    // ---- 测试 ----
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // ---- B7 集成测试：Testcontainers（真实 MySQL8 + Redis，禁 H2 假绿源）----
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
    testImplementation("org.testcontainers:mysql:1.20.6")
}

// MapStruct + Lombok 注解处理器协同
// 说明：Gradle 默认使用进程内 Java Compiler API 编译，须显式绑定注解处理器路径，
// 否则 Lombok / MapStruct 处理器不会被执行（生成的 getter/setter 缺失）。
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters"))
    options.annotationProcessorPath = configurations["annotationProcessor"]
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("supply-chain-backend.jar")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // B7：本机 Docker Desktop 服务端 API=1.56 已弃用旧版本（v1.32 /info → HTTP 400），
    // docker-java 默认协商 1.32 会被拒。显式指定新版 API（client 1.44 兼容服务端 1.56）。
    systemProperty("api.version", "1.44")
    environment("DOCKER_API_VERSION", "1.44")
    // B7：Docker Hub 拉取 ryuk 受网络阻塞——禁用 ryuk（容器清理由 JVM shutdown hook 承担；
    // mysql:8.0 / redis:7.0 本机已缓存，无需拉取）
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
