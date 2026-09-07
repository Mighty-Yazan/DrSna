package com.example.demo.controller

import com.example.demo.dto.UserManagementDto
import com.example.demo.model.Role
import com.example.demo.service.SuperAdminUserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize

@RestController
@RequestMapping("/api/v1/super-admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class SuperAdminUserController(
    private val userService: SuperAdminUserService
) {

    // عرض ورصد قائمة الحسابات مع فلترة اختيارية حسب الدور والبحث
    @GetMapping
    fun getUsers(
        @RequestParam(required = false) role: Role?,
        @RequestParam(required = false) search: String?
    ): ResponseEntity<List<UserManagementDto>> {
        val users = userService.getUsers(role, search)
        return ResponseEntity.ok(users)
    }

    // حظر أو إعادة تفعيل حساب مستخدم
    @PutMapping("/{userId}/status")
    fun updateUserStatus(
        @PathVariable userId: UUID,
        @RequestParam active: Boolean
    ): ResponseEntity<UserManagementDto> {
        val updatedUser = userService.toggleUserActiveStatus(userId, active)
        return ResponseEntity.ok(updatedUser)
    }
}