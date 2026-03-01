package com.squareup.francis.process

import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class TeeProcessBuilderTest {
  companion object {
    @JvmField
    @ClassRule
    val tempFolder = TemporaryFolder()
  }

  // --- stdout DISCARD ---

  @Test
  fun stdout_discard() {
    val process = TeeProcessBuilder("echo", "hello")
      .apply { stdoutRedirect = OutputRedirectSpec.DISCARD }
      .start()
    val exitCode = process.waitFor()

    assertThat(exitCode).isEqualTo(0)
  }

  // --- stdout PIPE ---

  @Test
  fun stdout_pipe_buffersOutput() {
    val process = TeeProcessBuilder("echo", "hello world")
      .apply { stdoutRedirect = OutputRedirectSpec.PIPE }
      .start()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText()
    assertThat(output).isEqualTo("hello world\n")
  }

  // --- stdout INHERIT ---

  @Test
  fun stdout_inherit() {
    val process = TeeProcessBuilder("echo", "hello")
      .apply { stdoutRedirect = OutputRedirectSpec.INHERIT }
      .start()
    val exitCode = process.waitFor()

    assertThat(exitCode).isEqualTo(0)
  }

  // --- stdout ToFile ---

  @Test
  fun stdout_toFile_writesToFile() {
    val output = File(tempFolder.root, "stdout_write.txt")
    val process = TeeProcessBuilder("echo", "file output")
      .apply { stdoutRedirect = OutputRedirectSpec(listOf(OutputTarget.ToFile(output))) }
      .start()
    process.waitFor()

    assertThat(output.readText()).isEqualTo("file output\n")
  }

  @Test
  fun stdout_toFile_append() {
    val output = File(tempFolder.root, "stdout_append.txt")
    output.writeText("existing\n")

    val process = TeeProcessBuilder("echo", "appended")
      .apply { stdoutRedirect = OutputRedirectSpec(listOf(OutputTarget.ToFile(output, append = true))) }
      .start()
    process.waitFor()

    assertThat(output.readText()).isEqualTo("existing\nappended\n")
  }

  // --- stdout ToStream ---

  @Test
  fun stdout_toStream() {
    val baos = ByteArrayOutputStream()
    val process = TeeProcessBuilder("echo", "to stream")
      .apply { stdoutRedirect = OutputRedirectSpec(listOf(OutputTarget.ToStream(baos))) }
      .start()
    process.waitFor()

    assertThat(baos.toString()).isEqualTo("to stream\n")
  }

  // --- stdout multiple targets (tee) ---

  @Test
  fun stdout_tee_pipeAndStream() {
    val baos = ByteArrayOutputStream()
    val process = TeeProcessBuilder("echo", "teed output")
      .apply {
        stdoutRedirect = OutputRedirectSpec(listOf(
          OutputTarget.Pipe,
          OutputTarget.ToStream(baos)
        ))
      }
      .start()
    process.waitFor()

    val pipeOutput = process.stdoutStream.bufferedReader().readText()
    assertThat(pipeOutput).isEqualTo("teed output\n")
    assertThat(baos.toString()).isEqualTo("teed output\n")
  }

  @Test
  fun stdout_tee_pipeAndFile() {
    val output = File(tempFolder.root, "stdout_tee.txt")
    val process = TeeProcessBuilder("echo", "teed to file")
      .apply {
        stdoutRedirect = OutputRedirectSpec(listOf(
          OutputTarget.Pipe,
          OutputTarget.ToFile(output)
        ))
      }
      .start()
    process.waitFor()

    val pipeOutput = process.stdoutStream.bufferedReader().readText()
    assertThat(pipeOutput).isEqualTo("teed to file\n")
    assertThat(output.readText()).isEqualTo("teed to file\n")
  }

  // --- stderr PIPE ---

  @Test
  fun stderr_pipe_buffersError() {
    val process = TeeProcessBuilder("sh", "-c", "echo error >&2")
      .apply { stderrRedirect = OutputRedirectSpec.PIPE }
      .start()
    process.waitFor()

    val error = process.stderrStream.bufferedReader().readText()
    assertThat(error).isEqualTo("error\n")
  }

  // --- stderr tee ---

  @Test
  fun stderr_tee_pipeAndStream() {
    val baos = ByteArrayOutputStream()
    val process = TeeProcessBuilder("sh", "-c", "echo error >&2")
      .apply {
        stderrRedirect = OutputRedirectSpec(listOf(
          OutputTarget.Pipe,
          OutputTarget.ToStream(baos)
        ))
      }
      .start()
    process.waitFor()

    val pipeError = process.stderrStream.bufferedReader().readText()
    assertThat(pipeError).isEqualTo("error\n")
    assertThat(baos.toString()).isEqualTo("error\n")
  }

  // --- stdin NULL ---

  @Test
  fun stdin_null() {
    val process = TeeProcessBuilder("cat")
      .apply {
        stdinRedirect = InputRedirectSpec.NULL
        stdoutRedirect = OutputRedirectSpec.PIPE
      }
      .start()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText()
    assertThat(output).isEmpty()
  }

  // --- stdin PIPE ---

  @Test
  fun stdin_pipe_sendsInput() {
    val process = TeeProcessBuilder("cat")
      .apply {
        stdinRedirect = InputRedirectSpec.PIPE
        stdoutRedirect = OutputRedirectSpec.PIPE
      }
      .start()

    process.stdinStream.write("test input\n".toByteArray())
    process.stdinStream.close()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText()
    assertThat(output).isEqualTo("test input\n")
  }

  // --- stdin FromFile ---

  @Test
  fun stdin_fromFile_readsFromFile() {
    val input = File(tempFolder.root, "stdin.txt")
    input.writeText("from file\n")

    val process = TeeProcessBuilder("cat")
      .apply {
        stdinRedirect = InputRedirectSpec(InputSource.FromFile(input))
        stdoutRedirect = OutputRedirectSpec.PIPE
      }
      .start()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText()
    assertThat(output).isEqualTo("from file\n")
  }

  // --- stdin with tee outputs ---

  @Test
  fun stdin_pipe_withTee() {
    val teeCapture = ByteArrayOutputStream()
    val process = TeeProcessBuilder("cat")
      .apply {
        stdinRedirect = InputRedirectSpec(
          source = InputSource.Pipe,
          teeOutputs = listOf(OutputTarget.ToStream(teeCapture))
        )
        stdoutRedirect = OutputRedirectSpec.PIPE
      }
      .start()

    process.stdinStream.write("teed input\n".toByteArray())
    process.stdinStream.close()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText()
    assertThat(output).isEqualTo("teed input\n")
    assertThat(teeCapture.toString()).isEqualTo("teed input\n")
  }

  @Test
  fun stdin_fromFile_withTee() {
    val input = File(tempFolder.root, "stdin_tee.txt")
    input.writeText("file input\n")
    val teeCapture = ByteArrayOutputStream()

    val process = TeeProcessBuilder("cat")
      .apply {
        stdinRedirect = InputRedirectSpec(
          source = InputSource.FromFile(input),
          teeOutputs = listOf(OutputTarget.ToStream(teeCapture))
        )
        stdoutRedirect = OutputRedirectSpec.PIPE
      }
      .start()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText()
    assertThat(output).isEqualTo("file input\n")
    assertThat(teeCapture.toString()).isEqualTo("file input\n")
  }

  // --- Incomplete lines ---

  @Test
  fun stdout_incompleteLinePreservesBytes() {
    val process = TeeProcessBuilder("printf", "no newline")
      .apply { stdoutRedirect = OutputRedirectSpec.PIPE }
      .start()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText()
    assertThat(output).isEqualTo("no newline")
  }

  // --- directory ---

  @Test
  fun directory_setsWorkingDirectory() {
    val dir = tempFolder.newFolder("workdir")
    val process = TeeProcessBuilder("pwd")
      .apply {
        directory = dir
        stdoutRedirect = OutputRedirectSpec.PIPE
      }
      .start()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText().trim()
    assertThat(output).isEqualTo(dir.canonicalPath)
  }

  // --- environment ---

  @Test
  fun environment_setsEnvVar() {
    val process = TeeProcessBuilder("sh", "-c", "echo \$TEST_VAR")
      .apply {
        environment = mapOf("TEST_VAR" to "hello_env")
        stdoutRedirect = OutputRedirectSpec.PIPE
      }
      .start()
    process.waitFor()

    val output = process.stdoutStream.bufferedReader().readText().trim()
    assertThat(output).isEqualTo("hello_env")
  }

  // --- INHERIT validation ---

  @Test(expected = IllegalArgumentException::class)
  fun stdout_inherit_withOtherTargets_throws() {
    TeeProcessBuilder("echo", "hello")
      .apply {
        stdoutRedirect = OutputRedirectSpec(listOf(
          OutputTarget.Inherit,
          OutputTarget.ToStream(ByteArrayOutputStream())
        ))
      }
      .start()
  }

  @Test(expected = IllegalArgumentException::class)
  fun stderr_inherit_withOtherTargets_throws() {
    TeeProcessBuilder("echo", "hello")
      .apply {
        stderrRedirect = OutputRedirectSpec(listOf(
          OutputTarget.Inherit,
          OutputTarget.ToStream(ByteArrayOutputStream())
        ))
      }
      .start()
  }

  @Test(expected = IllegalArgumentException::class)
  fun stdin_inherit_withTeeOutputs_throws() {
    TeeProcessBuilder("cat")
      .apply {
        stdinRedirect = InputRedirectSpec(
          source = InputSource.Inherit,
          teeOutputs = listOf(OutputTarget.ToStream(ByteArrayOutputStream()))
        )
      }
      .start()
  }
}
