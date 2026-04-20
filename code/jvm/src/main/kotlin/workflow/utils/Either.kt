package org.workflow.utils

/**
 * Represents a value of one of two possible types (a disjoint union).
 * Used to express service-layer results without throwing exceptions.
 *
 * Convention: [Left] is the *failure* case, [Right] is the *success* case.
 */
sealed class Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>()
    data class Right<out R>(val value: R) : Either<Nothing, R>()
}

/** Wraps a success value on the [Either.Right] side. */
fun <R> success(value: R): Either<Nothing, R> = Either.Right(value)

/** Wraps a failure value on the [Either.Left] side. */
fun <L> failure(error: L): Either<L, Nothing> = Either.Left(error)

typealias Success<S> = Either.Right<S>
typealias Failure<F> = Either.Left<F>

