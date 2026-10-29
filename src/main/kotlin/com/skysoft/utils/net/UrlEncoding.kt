package com.skysoft.utils.net

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

fun urlEncodedForm(vararg parameters: Pair<String, String>): String =
    parameters.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
