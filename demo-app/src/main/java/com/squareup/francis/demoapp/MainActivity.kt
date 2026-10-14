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
    setContentView(screen("Hello, minimal Android app!", "Browse items", ::showItems))
  }

  private fun showItems() {
    setContentView(
      screen("Choose an item", "Coffee") { showItem("Coffee") }
        .apply { addView(button("Tea") { showItem("Tea") }) }
    )
  }

  private fun showItem(item: String) {
    setContentView(screen(item, "Show details") { showDetails(item) })
  }

  private fun showDetails(item: String) {
    Trace.beginSection("Demo.showDetails")
    try {
      setContentView(screen("$item details", "Back to items", ::showItems))
    } finally {
      Trace.endSection()
    }
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
      addView(button(buttonLabel, onClick))
    }

  private fun button(label: String, onClick: () -> Unit): Button =
    Button(this).apply {
      text = label
      isAllCaps = false
      setOnClickListener { onClick() }
    }
}
