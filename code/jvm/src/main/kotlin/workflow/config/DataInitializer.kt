package org.workflow.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.workflow.entity.Permission
import org.workflow.entity.Roles
import org.workflow.entity.User
import org.workflow.entity.enums.ActionType
import org.workflow.entity.enums.ResourceType
import org.workflow.repository.PermissionRepository
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository

/**
 * Seeds the database with default roles, permissions and a default admin account on first startup.
 * Idempotent — exits early if the ADMIN role already exists.
 */
@Component
class DataInitializer(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (roleRepository.findByName("ADMIN") != null) {
            log.info("DataInitializer: roles already seeded, skipping.")
            return
        }

        log.info("DataInitializer: seeding permissions, roles and default admin...")

        // ── 1. Persist all permission records first ──────────────────────────
        fun save(r: ResourceType, a: ActionType): Permission =
            permissionRepository.save(Permission(resource = r, action = a))

        val wRead    = save(ResourceType.WORKFLOW,  ActionType.READ)
        val wWrite   = save(ResourceType.WORKFLOW,  ActionType.WRITE)
        val wDelete  = save(ResourceType.WORKFLOW,  ActionType.DELETE)
        val wExecute = save(ResourceType.WORKFLOW,  ActionType.EXECUTE)

        val tRead    = save(ResourceType.TASK,      ActionType.READ)
        val tWrite   = save(ResourceType.TASK,      ActionType.WRITE)
        val tDelete  = save(ResourceType.TASK,      ActionType.DELETE)
        val tExecute = save(ResourceType.TASK,      ActionType.EXECUTE)

        val sRead    = save(ResourceType.SCHEDULE,  ActionType.READ)
        val sWrite   = save(ResourceType.SCHEDULE,  ActionType.WRITE)
        val sDelete  = save(ResourceType.SCHEDULE,  ActionType.DELETE)

        val eRead    = save(ResourceType.EXECUTION, ActionType.READ)

        val uManage  = save(ResourceType.USER,      ActionType.MANAGE)

        // ── 2. Build roles and link the managed permission entities ──────────
        val reader = Roles(name = "READER").also {
            it.permissions.addAll(setOf(wRead, tRead, sRead, eRead))
        }
        val writer = Roles(name = "WRITER").also {
            it.permissions.addAll(setOf(wRead, wWrite, wDelete, tRead, tWrite, tDelete, sRead, sWrite, sDelete, eRead))
        }
        val dev = Roles(name = "DEV").also {
            it.permissions.addAll(setOf(wRead, wExecute, tRead, tExecute, eRead))
        }
        val admin = Roles(name = "ADMIN").also {
            it.permissions.addAll(setOf(wRead, wWrite, wDelete, wExecute,
                                        tRead, tWrite, tDelete, tExecute,
                                        sRead, sWrite, sDelete,
                                        eRead, uManage))
        }

        roleRepository.saveAll(listOf(reader, writer, dev, admin))

        // ── 3. Default admin account (change password on first login!) ───────
        if (userRepository.findByUsername("admin") == null) {
            userRepository.save(
                User(
                    username = "admin",
                    passwordValidation = passwordEncoder.encode("admin123"),
                    displayName = "System Administrator",
                    role = admin
                )
            )
            log.warn("DataInitializer: default admin created — change the password immediately! (admin / admin123)")
        }

        log.info("DataInitializer: seeding complete — roles: READER, WRITER, DEV, ADMIN.")
    }
}
