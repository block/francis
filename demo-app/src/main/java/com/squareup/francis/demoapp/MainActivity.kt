package com.squareup.francis.demoapp

import android.app.Activity
import android.os.Bundle
import android.os.Trace
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    showHome()
  }

  private fun showHome() {
    setContentView(
      screen("Hello, minimal Android app!", "Show details") {
        Trace.beginSection("Demo.showDetails")
        try {
          setContentView(screen("Details screen", "Close details", ::showHome))
        } finally {
          Trace.endSection()
        }
      }
    )
  }

  private fun screen(title: String, buttonLabel: String, onClick: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(40, 80, 40, 80)
      addView(
        TextView(this@MainActivity).apply {
          text = title
          textSize = 20f
        }
      )
      addView(
        Button(this@MainActivity).apply {
          text = buttonLabel
          isAllCaps = false
          setOnClickListener { onClick() }
        }
      )
    }
}
