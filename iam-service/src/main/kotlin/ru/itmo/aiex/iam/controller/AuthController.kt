package ru.itmo.aiex.iam.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.itmo.aiex.iam.service.AuthService
import ru.itmo.aiex.iam.service.TokenResponse

@RestController
class AuthController(private val auth: AuthService) {
    @Operation(summary = "Вход по email и паролю", security = [])
    @PostMapping("/api/v1/auth/login")
    fun login(@Valid @RequestBody request: LoginRequest): Mono<ResponseEntity<TokenResponse>> = Mono.fromCallable {
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
            .body(auth.login(request.email, request.password))
    }.subscribeOn(Schedulers.boundedElastic())
}

class LoginRequest(
    @field:NotBlank @field:Email @field:Size(max = 254)
    val email: String,
    @field:NotBlank @field:Size(max = 72)
    @field:Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
    val password: String,
)
