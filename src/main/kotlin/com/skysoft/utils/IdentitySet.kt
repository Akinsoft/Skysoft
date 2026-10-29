package com.skysoft.utils

import java.util.Collections
import java.util.IdentityHashMap

fun <T> identitySet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
