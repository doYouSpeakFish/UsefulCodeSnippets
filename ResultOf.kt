
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
    fun <R> map(transform: (T) -> R): ResultOf<R, F> = when (this) {
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
    fun <R> flatMap(transform: (T) -> ResultOf<R, F>): ResultOf<R, F> = when (this) {
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

    /**
     * Combines the success result of this [ResultOf] with another [ResultOf] with the same
     * failure type.
     *
     * If either of the input [ResultOf] instances are failures, the return value will be a failure
     * wrapping the first failed result.
     *
     * To combine [ResultOf] instances with different error type, first call
     * [ResultOf.mapFailure] on one or both of them to convert them to the same failure type.
     */
    fun <T1, R> combine(other: ResultOf<T1, F>, transform: (T, T1) -> R) =
            combine(this, other, transform)
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

/**
 * Combines the success results of a variable number of [ResultOf] instances that all have the same
 * failure type.
 *
 * If any of the input [ResultOf] instances are failures, the return value will be a failure wrapping
 * the first failed result.
 *
 * To combine [ResultOf] instances with different error type, first call
 * [ResultOf.mapFailure] on one or both of them to convert them to the same failure type.
 */
fun <T, F, R> combine(
        vararg results: ResultOf<T, F>,
        transform: (List<T>) -> R
): ResultOf<R, F> {
    for (result in results) {
        if (result is ResultOf.Failure) return ResultOf.Failure(result.value)
    }
    val values = results.map { (it as ResultOf.Success).value }
    return ResultOf.Success(transform(values))
}

/**
 * Combines the success results of two [ResultOf] instances that have the same failure type.
 *
 * If either of the input [ResultOf] instances are failures, the return value will be a failure
 * wrapping the first failed result.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
@Suppress("UNCHECKED_CAST")
fun <T1, T2, R, F> combine(
        result1: ResultOf<T1, F>,
        result2: ResultOf<T2, F>,
        transform: (T1, T2) -> R
): ResultOf<R, F> = combine(
        result1.map { it as Any },
        result2.map { it as Any }
) { values ->
    transform(
            values[0] as T1,
            values[1] as T2,
    )
}

/**
 * Combines the success result of three [ResultOf] instances that all have the same failure type.
 *
 * If any of the input [ResultOf] instances are failures, the return value will be a failure wrapping
 * the first failed result.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, R, F> combine(
        result1: ResultOf<T1, F>,
        result2: ResultOf<T2, F>,
        result3: ResultOf<T3, F>,
        transform: (T1, T2, T3) -> R
): ResultOf<R, F> = combine(
        result1.map { it as Any },
        result2.map { it as Any },
        result3.map { it as Any }
) { values ->
    transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3
    )
}

/**
 * Combines the success result of four [ResultOf] instances that all have the same failure type.
 *
 * If any of the input [ResultOf] instances are failures, the return value will be a failure wrapping
 * the first failed result.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, T4, R, F> combine(
        result1: ResultOf<T1, F>,
        result2: ResultOf<T2, F>,
        result3: ResultOf<T3, F>,
        result4: ResultOf<T4, F>,
        transform: (T1, T2, T3, T4) -> R
): ResultOf<R, F> = combine(
        result1.map { it as Any },
        result2.map { it as Any },
        result3.map { it as Any },
        result4.map { it as Any }
) { values ->
    transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3,
            values[3] as T4
    )
}

/**
 * Combines the success result of five [ResultOf] instances that all have the same failure type.
 *
 * If any of the input [ResultOf] instances are failures, the return value will be a failure wrapping
 * the first failed result.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, T4, T5, R, F> combine(
        result1: ResultOf<T1, F>,
        result2: ResultOf<T2, F>,
        result3: ResultOf<T3, F>,
        result4: ResultOf<T4, F>,
        result5: ResultOf<T5, F>,
        transform: (T1, T2, T3, T4, T5) -> R
): ResultOf<R, F> = combine(
        result1.map { it as Any },
        result2.map { it as Any },
        result3.map { it as Any },
        result4.map { it as Any },
        result5.map { it as Any }
) { values ->
    transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3,
            values[3] as T4,
            values[4] as T5
    )
}

/**
 * Combines the success result of a variable number of [ResultOf] instances that all have the same
 * failure type. Applies [transform] to the list of results to get another [ResultOf] that is then
 * returned.
 *
 * If any of the input [ResultOf] instances are failures, the return value will be a failure wrapping
 * the first failed result.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
fun <T, F, R> flatCombine(
        vararg results: ResultOf<T, F>,
        transform: (List<T>) -> ResultOf<R, F>
): ResultOf<R, F> {
    for (result in results) {
        if (result is ResultOf.Failure) return ResultOf.Failure(result.value)
    }
    val values = results.map { (it as ResultOf.Success).value }
    return transform(values)
}

/**
 * Combines the success results of two [ResultOf] instances that all have the same failure type.
 * Applies [transform] to the list of results to get another [ResultOf] that is then returned.
 *
 * If either of the input [ResultOf] instances are failures, the return value will be a [ResultOf]
 * wrapping the first failure value.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
@Suppress("UNCHECKED_CAST")
fun <T1, T2, R, F> flatCombine(
        result1: ResultOf<T1, F>,
        result2: ResultOf<T2, F>,
        transform: (T1, T2) -> ResultOf<R, F>
): ResultOf<R, F> = flatCombine(
        result1.map { it as Any },
        result2.map { it as Any }
) { values ->
    transform(
            values[0] as T1,
            values[1] as T2,
    )
}

/**
 * Combines the success results of three [ResultOf] instances that all have the same failure type.
 * Applies [transform] to the list of results to get another [ResultOf] that is then returned.
 *
 * If any of the input [ResultOf] instances are failures, the return value will be a [ResultOf]
 * wrapping the first failure value.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, R, F> flatCombine(
        result1: ResultOf<T1, F>,
        result2: ResultOf<T2, F>,
        result3: ResultOf<T3, F>,
        transform: (T1, T2, T3) -> ResultOf<R, F>
): ResultOf<R, F> = flatCombine(
        result1.map { it as Any },
        result2.map { it as Any },
        result3.map { it as Any }
) { values ->
    transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3
    )
}

/**
 * Combines the success results of four [ResultOf] instances that all have the same failure type.
 * Applies [transform] to the list of results to get another [ResultOf] that is then returned.
 *
 * If any of the input [ResultOf] instances are failures, the return value will be a [ResultOf]
 * wrapping the first failure value.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, T4, R, F> flatCombine(
        result1: ResultOf<T1, F>,
        result2: ResultOf<T2, F>,
        result3: ResultOf<T3, F>,
        result4: ResultOf<T4, F>,
        transform: (T1, T2, T3, T4) -> ResultOf<R, F>
): ResultOf<R, F> = flatCombine(
        result1.map { it as Any },
        result2.map { it as Any },
        result3.map { it as Any },
        result4.map { it as Any },
) { values ->
    transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3,
            values[3] as T4
    )
}

/**
 * Combines the success results of five [ResultOf] instances that all have the same failure type.
 * Applies [transform] to the list of results to get another [ResultOf] that is then returned.
 *
 * If any of the input [ResultOf] instances are failures, the return value will be a [ResultOf]
 * wrapping the first failure value.
 *
 * To combine [ResultOf] instances with different error type, first call [ResultOf.mapFailure] on
 * one or both of them to convert them to the same failure type.
 */
@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, T4, T5, R, F> flatCombine(
        result1: ResultOf<T1, F>,
        result2: ResultOf<T2, F>,
        result3: ResultOf<T3, F>,
        result4: ResultOf<T4, F>,
        result5: ResultOf<T5, F>,
        transform: (T1, T2, T3, T4, T5) -> ResultOf<R, F>
): ResultOf<R, F> = flatCombine(
        result1.map { it as Any },
        result2.map { it as Any },
        result3.map { it as Any },
        result4.map { it as Any },
        result5.map { it as Any }
) { values ->
    transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3,
            values[3] as T4,
            values[4] as T5
    )
}
