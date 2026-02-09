package com.squareup.francis.demoapp

import android.os.Bundle
import android.widget.TextView
import android.app.Activity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No XML, just a TextView created in code
        val tv = TextView(this).apply {
            text = "Hello, minimal Android app!"
            textSize = 20f
            setPadding(40, 80, 40, 80)
        }

        setContentView(tv)
    }
}
