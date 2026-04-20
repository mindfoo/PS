package org.workflow.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.WorkflowCreateRequest
import org.workflow.dto.WorkflowResponse
import org.workflow.dto.WorkflowUpdateRequest
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.service.utils.WorkflowError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

@Service
/** Implements workflow CRUD operations with ownership and admin visibility rules. */
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val userRepository: UserRepository
) {

    fun list(authenticationName: String): Either<WorkflowError, List<WorkflowResponse>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflows = if (isAdmin(currentUser)) {
            workflowRepository.findAll()
        } else {
            workflowRepository.findAllByOwnerId(currentUser.id!!)
        }

        return success(workflows.map { toResponse(it) })
    }

    fun getById(workflowId: UUID, authenticationName: String): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        return success(toResponse(workflow))
    }

    @Transactional
    fun create(request: WorkflowCreateRequest, authenticationName: String): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val saved = workflowRepository.save(
            Workflow(
                name = request.name,
                created_by = currentUser
            )
        )

        return success(toResponse(saved))
    }

    @Transactional
    fun update(
        workflowId: UUID,
        request: WorkflowUpdateRequest,
        authenticationName: String
    ): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        workflow.name = request.name
        return success(toResponse(workflowRepository.save(workflow)))
    }

    @Transactional
    fun delete(workflowId: UUID, authenticationName: String): Either<WorkflowError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        workflowRepository.delete(workflow)
        return success(Unit)
    }

    private fun findCurrentUser(username: String): User? =
        userRepository.findByUsername(username)

    private fun isAdmin(user: User): Boolean =
        user.role.name.equals("ADMIN", ignoreCase = true)

    private fun toResponse(workflow: Workflow): WorkflowResponse =
        WorkflowResponse(
            id = workflow.id,
            name = workflow.name,
            ownerId = workflow.created_by.id,
            ownerUsername = workflow.created_by.username
        )
}
