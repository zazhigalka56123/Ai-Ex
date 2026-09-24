package ru.itmo.aiex.app.web

import jakarta.persistence.EntityNotFoundException
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import jakarta.validation.constraints.Min
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MaxUploadSizeExceededException
import ru.itmo.aiex.common.error.LlmUnavailableException
import ru.itmo.aiex.common.error.ValidationException
import java.sql.SQLException
import org.hibernate.exception.ConstraintViolationException as HibernateConstraintViolation

@RestController
@RequestMapping("/api/v1/__test")
class FailingTestController(private val validator: Validator) {
    @GetMapping("/integrity")
    fun integrity(): Nothing =
        throw DataIntegrityViolationException("duplicate", HibernateConstraintViolation("duplicate", SQLException("23505"), "uq_sample"))

    @GetMapping("/optimistic")
    fun optimistic(): Nothing = throw ObjectOptimisticLockingFailureException(SamplePayload::class.java, "42")

    @GetMapping("/entity")
    fun entity(): Nothing = throw EntityNotFoundException("gone")

    @GetMapping("/constraint")
    fun constraint(): Nothing = throw ConstraintViolationException(validator.validate(SamplePayload(name = " ", count = 1)))

    @GetMapping("/validation")
    fun validation(): Nothing = throw ValidationException("cursor", "cursor.invalid", "битый курсор")

    @GetMapping("/boom")
    fun boom(): Nothing = error("секретный стектрейс")

    @GetMapping("/llm")
    fun llm(): Nothing = throw LlmUnavailableException("провайдер лежит")

    @GetMapping("/upload")
    fun upload(): Nothing = throw MaxUploadSizeExceededException(1024)

    @GetMapping("/typed")
    fun typed(@RequestParam n: Int): Int = n

    @GetMapping("/method-validation")
    fun methodValidation(@RequestParam @Min(1) n: Int): Int = n

    @PostMapping("/body")
    fun body(@jakarta.validation.Valid @RequestBody payload: SamplePayload): SamplePayload = payload
}
