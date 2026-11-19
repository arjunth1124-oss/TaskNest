package com.arjun.tasknest.util

/** Abstraction over the system clock so time-dependent logic is unit-testable. */
fun interface Clock {
    fun now(): Long
}
