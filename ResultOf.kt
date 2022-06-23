
/**
 * A class for encapsulating success and failure results. This differs from the built in [Result]
 * class, as it allows for the failure to be any type, not just [Throwable]. This allows for sealed
 * classes to be used to represent expected failure modes that must be explicitly handled, and that
 * are not necessarily exceptions.
 */
sealed class ResultOf<T, F> {
    val isSuccess get() = this is Success
    val isFailure get() = this is Failure

    /**
     * Returns the success value if available, or null otherwise.
     */
    fun getOrNull() = (this as? Success)?.value

    /**
     * Returns the failure value is available, or null otherwise.
     */
    fun failureOrNull() = (this as? Failure)?.value

    /**
     * A successful result with the resulting [value].
     */
    data class Success<T, F>(val value: T): ResultOf<T, F>()

    /**
     * An unsuccessful result with a failure [value].
     */
    data class Failure<T, F>(val value: F): ResultOf<T, F>()

    /**
     * Executes the provided [action] if this is [ResultOf.Success]. Returns this [ResultOf].
     */
    fun onSuccess(action: (T) -> Unit): ResultOf<T, F> {
        if (this is Success) action(value)
        return this
    }

    /**
     * Executes the provided [action] if this is [ResultOf.Failure]. Returns this [ResultOf].
     */
    fun onFailure(action: (F) -> Unit): ResultOf<T, F> {
        if (this is Failure) action(value)
        return this
    }

    /**
     * If this is a [ResultOf.Success], maps the success value to a new value.
     * If this is a [ResultOf.Failure], the failure value remains unchanged.
     */
    fun <R> mapSuccess(transform: (T) -> R): ResultOf<R, F> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> Failure(value)
    }

    /**
     * If this is a [ResultOf.Failure], maps the failure value to a new value.
     * If this is a [ResultOf.Success], the success value remains unchanged.
     */
    fun <R> mapFailure(transform: (F) -> R): ResultOf<T, R> = when (this) {
        is Failure -> Failure(transform(value))
        is Success -> Success(value)
    }

    /**
     * Maps success values to a new value with a [transform] that itself returns a [ResultOf].
     * If this is a [ResultOf.Success], the result of [transform] is returned directly.
     * If this is a [ResultOf.Failure], the failure value remains unchanged.
     */
    fun <R> flatMapSuccess(transform: (T) -> ResultOf<R, F>): ResultOf<R, F> = when (this) {
        is Success -> transform(value)
        is Failure -> Failure(value)
    }

    /**
     * Maps failure values to a new value with a [transform] that itself returns a [ResultOf].
     * If this is a [ResultOf.Failure], the result of [transform] is returned directly.
     * If this is a [ResultOf.Success], the success value remains unchanged.
     */
    fun <R> flatMapFailure(transform: (F) -> ResultOf<T, R>): ResultOf<T, R> = when (this) {
        is Failure -> transform(value)
        is Success -> Success(value)
    }

    /**
     * Recovers from failure cases by applying the given [transform].
     */
    fun recover(transform: (F) -> T): ResultOf<T, F> = when (this) {
        is Failure -> Success(transform(value))
        is Success -> this
    }

    /**
     * Recovers from failure cases by applying a [transform] that can also return a [ResultOf].
     * If this is a [ResultOf.Failure], the result of [transform] is returned directly.
     * If this is a [ResultOf.Success], this [ResultOf] is returned unchanged.
     */
    fun flatRecover(transform: (F) -> ResultOf<T, F>): ResultOf<T, F> = when (this) {
        is Failure -> transform(value)
        is Success -> this
    }
}

/**
 * Runs the provided [block] and catches any exceptions, returning a [ResultOf] object to wrap the
 * result of [block] or the thrown exception as appropriate.
 */
fun <T, R> T.runOrCatch(block: T.() -> R): ResultOf<R, Throwable> {
    return try {
        ResultOf.Success(block())
    } catch (e: Throwable) {
        ResultOf.Failure(e)
    }
}
