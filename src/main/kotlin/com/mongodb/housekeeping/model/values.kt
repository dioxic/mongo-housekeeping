package com.mongodb.housekeeping.model

@JvmInline
value class Window(val value: Boolean) {
    override fun toString() =
        when (value) {
            true -> "open"
            false -> "closed"
        }
}

@JvmInline
value class Enabled(val value: Boolean) {
    override fun toString() =
        when (value) {
            true -> "enabled"
            false -> "disabled"
        }
}

@JvmInline
value class Rate(val value: Int)