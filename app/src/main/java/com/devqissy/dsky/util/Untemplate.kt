package com.devqissy.dsky.util

/**
 * Utility wrapper for trivial resolution of URI templates to URLs.
 */
object Untemplate {
    /**
     * Performs variable expansion on a URI Template (RFC 6570) in the special case where all
     * variables are undefined.
     */
    @JvmStatic
    fun strip(template: String): String = template.replace(Regex("\\{[^}]*\\}"), "")
}