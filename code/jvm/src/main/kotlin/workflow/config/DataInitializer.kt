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
import org.workflow.entity.enums.RoleType
import org.workflow.repository.PermissionRepository
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository
import org.workflow.service.ServiceHelpers

/**
 * Seeds the database with default roles, permissions and a default admin account on first startup.
 */
@Component
class DataInitializer(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val helpers: ServiceHelpers
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (roleRepository.findByName(RoleType.ADMIN) != null) {
            log.info("DataInitializer: roles already seeded, skipping.")
            return
        }

        log.info("DataInitializer: seeding permissions, roles and default admin...")

        // Build all resource–action permission pairs and persist them to the database
        fun save(r: ResourceType, a: ActionType): Permission =
            permissionRepository.save(Permission(resource = r, action = a))

        // w - workflows
        val wRead    = save(ResourceType.WORKFLOW,  ActionType.READ)
        val wWrite   = save(ResourceType.WORKFLOW,  ActionType.WRITE)
        val wDelete  = save(ResourceType.WORKFLOW,  ActionType.DELETE)
        val wExecute = save(ResourceType.WORKFLOW,  ActionType.EXECUTE)
        // t - tasks
        val tRead    = save(ResourceType.TASK,      ActionType.READ)
        val tWrite   = save(ResourceType.TASK,      ActionType.WRITE)
        val tDelete  = save(ResourceType.TASK,      ActionType.DELETE)
        val tExecute = save(ResourceType.TASK,      ActionType.EXECUTE)
        val tUpload  = save(ResourceType.TASK,      ActionType.UPLOAD)
        // s - schedules
        val sRead    = save(ResourceType.SCHEDULE,  ActionType.READ)
        val sWrite   = save(ResourceType.SCHEDULE,  ActionType.WRITE)
        val sDelete  = save(ResourceType.SCHEDULE,  ActionType.DELETE)
        // e - executions
        val eRead    = save(ResourceType.EXECUTION, ActionType.READ)
        // u - users
        val uManage  = save(ResourceType.USER,      ActionType.MANAGE)

        // Build role-permission association
        val reader = Roles(name = RoleType.READER).also {
            it.permissions.addAll(setOf(wRead, tRead, sRead, eRead))
        }
        val writer = Roles(name = RoleType.WRITER).also {
            it.permissions.addAll(setOf(wRead, wWrite, wDelete, tRead, tWrite, tDelete, sRead, sWrite, sDelete, eRead))
        }
        val dev = Roles(name = RoleType.DEV).also {
            it.permissions.addAll(setOf(wRead, wExecute, tRead, tExecute, tUpload, eRead))
        }
        val admin = Roles(name = RoleType.ADMIN).also {
            it.permissions.addAll(setOf(wRead, wWrite, wDelete, wExecute,
                                        tRead, tWrite, tDelete, tExecute, tUpload,
                                        sRead, sWrite, sDelete,
                                        eRead, uManage))
        }

        roleRepository.saveAll(listOf(reader, writer, dev, admin))

        // Default admin account as first user
        if (helpers.findUser("admin") == null) {
            userRepository.save(
                User(
                    username = "admin",
                    passwordValidation = passwordEncoder.encode("Admin@12345!"),
                    role = admin
                )
            )
            log.warn("DataInitializer: default admin created. Username: admin — change password immediately on first login.")
        }

        log.info("DataInitializer: seeding complete — roles and its permissions.")
    }
}
