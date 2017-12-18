package org.esciencecloud.auth.util

import java.net.URLDecoder
import java.net.URLEncoder

val String.urlEncoded: String get() = URLEncoder.encode(this, "UTF-8")
val String.urlDecoded: String get() = URLDecoder.decode(this, "UTF-8")
