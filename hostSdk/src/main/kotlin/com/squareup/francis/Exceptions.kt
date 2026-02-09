package com.squareup.francis

class PithyException(val exitCode: Int, msg: String?) : Exception(msg)
