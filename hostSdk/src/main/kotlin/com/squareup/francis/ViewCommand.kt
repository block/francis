package com.squareup.francis

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.squareup.francis.log.log
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

class ViewCommand(
  baseOptions: BaseOptions = BaseOptions(),
) : CliktCommand(name = "view") {
  override fun help(context: Context) = """
    Open a Perfetto trace file in ui.perfetto.dev.

    Starts a temporary local HTTP server that serves an HTML page which uses
    postMessage to send the trace to Perfetto UI. Press Ctrl+C to stop.
  """.trimIndent()

  private val baseOpts by baseOptions

  private val traceFile: File by argument(help = "Path to the Perfetto trace file (.perfetto-trace)")
    .file(mustExist = true, canBeDir = false, mustBeReadable = true)

  private val port: Int by option("-p", "--port", help = "Port for the local HTTP server")
    .int()
    .default(9001)

  override fun run() {
    baseOpts.setup()
    openTraceInPerfetto(traceFile, port)
  }

  companion object {
    fun openTraceInPerfetto(traceFile: File, port: Int = 9001) {
      val traceBytes = traceFile.readBytes()
      val fileName = traceFile.name

      val html = generateHtml(fileName)
      val shutdownLatch = CountDownLatch(1)

      val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
      val actualPort = server.address.port

      server.createContext("/") { exchange ->
        log { "${exchange.requestMethod} ${exchange.requestURI}" }
        val response = html.toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, response.size.toLong())
        exchange.responseBody.use { it.write(response) }
      }

      server.createContext("/trace") { exchange ->
        log { "${exchange.requestMethod} ${exchange.requestURI}" }
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, traceBytes.size.toLong())
        exchange.responseBody.use { it.write(traceBytes) }
      }

      server.createContext("/shutdown") { exchange ->
        log { "${exchange.requestMethod} ${exchange.requestURI}" }
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
        shutdownLatch.countDown()
      }

      server.executor = null
      server.start()

      val localUrl = "http://127.0.0.1:$actualPort/"

      log { "Serving loader page at $localUrl" }
      log { "Click the button in the browser to open the trace in Perfetto UI." }

      openInBrowser(localUrl)

      shutdownLatch.await()
      log { "Trace sent. Shutting down server..." }
      server.stop(0)
    }

    private fun openInBrowser(url: String) {
      val os = System.getProperty("os.name").lowercase()
      val command = when {
        os.contains("mac") -> arrayOf("open", url)
        os.contains("linux") -> arrayOf("xdg-open", url)
        else -> {
          log { "Open manually: $url" }
          return
        }
      }
      ProcessBuilder(*command).start()
    }

    private fun generateHtml(fileName: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
  <title>Open in Perfetto: $fileName</title>
  <style>
    body { 
      font-family: system-ui, sans-serif; 
      padding: 40px; 
      background: #1a1a2e; 
      color: #eee;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 80vh;
    }
    h1 { margin-bottom: 10px; }
    .filename { color: #888; margin-bottom: 30px; font-family: monospace; }
    button {
      background: #4a90d9;
      color: white;
      border: none;
      padding: 16px 32px;
      font-size: 18px;
      border-radius: 8px;
      cursor: pointer;
    }
    button:hover { background: #3a7bc8; }
    button:disabled { background: #555; cursor: default; }
    .status { margin-top: 20px; font-size: 16px; }
    .error { color: #ff6b6b; }
  </style>
</head>
<body>
  <h1>Perfetto Trace Viewer</h1>
  <div class="filename">$fileName</div>
  <button id="openBtn" disabled>Loading trace...</button>
  <div class="status" id="status"></div>
  <script>
    const FILE_NAME = "$fileName";
    let traceBuffer = null;

    function updateStatus(msg, isError) {
      const el = document.getElementById('status');
      el.textContent = msg;
      if (isError) el.classList.add('error');
    }

    async function loadTrace() {
      try {
        const response = await fetch('/trace');
        traceBuffer = await response.arrayBuffer();

        // Shutdown the server so `francis view` exits
        fetch('/shutdown');

        openTrace();
      } catch (e) {
        updateStatus('Failed to load trace: ' + e.message, true);
      }
    }

    function openTrace() {
      if (!traceBuffer) return;
      
      document.getElementById('openBtn').disabled = true;
      document.getElementById('openBtn').textContent = 'Opening...';
      
      const perfettoWindow = window.open('https://ui.perfetto.dev');
      if (!perfettoWindow) {
        updateStatus('Click the button to open Perfetto UI. (Tip: allow popups to skip this step)');
        document.getElementById('openBtn').disabled = false;
        document.getElementById('openBtn').textContent = 'Open in Perfetto UI';
        return;
      }

      updateStatus('Waiting for Perfetto UI...');

      const sendPing = () => {
        perfettoWindow.postMessage('PING', 'https://ui.perfetto.dev');
      };

      const pingInterval = setInterval(sendPing, 100);

      window.addEventListener('message', function handler(event) {
        if (event.origin !== 'https://ui.perfetto.dev') return;
        if (event.data !== 'PONG') return;

        clearInterval(pingInterval);
        window.removeEventListener('message', handler);

        perfettoWindow.postMessage({
          perfetto: {
            buffer: traceBuffer,
            title: FILE_NAME,
            fileName: FILE_NAME,
          }
        }, 'https://ui.perfetto.dev', [traceBuffer]);

        window.close();
      });

      sendPing();
    }

    document.getElementById('openBtn').onclick = openTrace;
    loadTrace();
  </script>
</body>
</html>
    """.trimIndent()
    }
  }
}
