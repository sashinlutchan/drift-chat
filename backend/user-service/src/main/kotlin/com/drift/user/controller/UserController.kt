package com.drift.user.controller

import com.drift.user.common.SessionCookieSupport
import com.drift.user.config.AppProperties
import com.drift.user.dto.OwnUserDto
import com.drift.user.dto.UserDto
import com.drift.user.dto.UsernameCheckDto
import com.drift.user.interfaces.IUserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.security.Principal
import java.util.UUID

@RestController
@Validated
class UserController(
    private val userService: IUserService,
    private val properties: AppProperties,
) {
    @GetMapping("/users/csrf")
    fun csrf(request: HttpServletRequest): ResponseEntity<Unit> {
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
        return ResponseEntity.ok().build()
    }

    @PostMapping("/users", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun signUp(
        @RequestPart @NotBlank username: String,
        @RequestPart @NotBlank name: String,
        @RequestPart @NotBlank email: String,
        @RequestPart("avatar", required = true) avatar: MultipartFile,
        response: HttpServletResponse,
    ): ResponseEntity<OwnUserDto> {
        val result =
            userService.signUp(
                username = username,
                name = name,
                email = email,
                avatarFileName = avatar.originalFilename,
                avatarContentType = avatar.contentType,
                avatarContent = avatar.inputStream,
                avatarSize = avatar.size,
            )
        SessionCookieSupport.write(
            response = response,
            cookieName = properties.sessionCookieName,
            jwt = result.sessionJwt,
            ttl = properties.sessionTtl,
            secure = properties.cookieSecure,
        )
        return ResponseEntity.ok(OwnUserDto.from(result.user, userService.isOnline(result.user.id)))
    }

    @GetMapping("/users/me")
    fun me(principal: Principal): ResponseEntity<OwnUserDto> {
        val user = userService.getById(UUID.fromString(principal.name))
        return ResponseEntity.ok(OwnUserDto.from(user, userService.isOnline(user.id)))
    }

    @PatchMapping("/users/me", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateMe(
        principal: Principal,
        @RequestPart(required = false) name: String?,
        @RequestPart(required = false) email: String?,
        @RequestPart("avatar", required = false) avatar: MultipartFile?,
    ): ResponseEntity<OwnUserDto> {
        val updated =
            userService.updateProfile(
                id = UUID.fromString(principal.name),
                name = name,
                email = email,
                avatarFileName = avatar?.originalFilename,
                avatarContentType = avatar?.contentType,
                avatarContent = avatar?.inputStream,
                avatarSize = avatar?.size ?: 0L,
            )
        return ResponseEntity.ok(OwnUserDto.from(updated, userService.isOnline(updated.id)))
    }

    @GetMapping("/users/{id}")
    fun publicProfile(
        @PathVariable id: UUID,
    ): ResponseEntity<UserDto> {
        val user = userService.getById(id)
        return ResponseEntity.ok(UserDto.from(user, userService.isOnline(id)))
    }

    @GetMapping("/users/check-username")
    fun checkUsername(
        @RequestParam username: String,
    ): ResponseEntity<UsernameCheckDto> = ResponseEntity.ok(UsernameCheckDto(username, userService.usernameAvailable(username)))
}
