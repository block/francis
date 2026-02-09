package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AbArgsTest {
  @Test
  fun noMarkers_allArgsAreShared() {
    val result = preprocessAbArgs(listOf("--app", "foo.apk", "--iterations", "5"))

    assertThat(result.shared).containsExactly("--app", "foo.apk", "--iterations", "5").inOrder()
    assertThat(result.baselineOnly).isEmpty()
    assertThat(result.treatmentOnly).isEmpty()
  }

  @Test
  fun baselineOnly_splitsCorrectly() {
    val result = preprocessAbArgs(listOf("--app", "foo.apk", "--baseline", "--bar", "123"))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.baselineOnly).containsExactly("--bar", "123").inOrder()
    assertThat(result.treatmentOnly).isEmpty()
  }

  @Test
  fun treatmentOnly_splitsCorrectly() {
    val result = preprocessAbArgs(listOf("--app", "foo.apk", "--treatment", "--bar", "456"))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.baselineOnly).isEmpty()
    assertThat(result.treatmentOnly).containsExactly("--bar", "456").inOrder()
  }

  @Test
  fun baselineThenTreatment_splitsCorrectly() {
    val result = preprocessAbArgs(listOf(
      "--app", "foo.apk",
      "--baseline", "--bar", "123",
      "--treatment", "--bar", "456"
    ))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.baselineOnly).containsExactly("--bar", "123").inOrder()
    assertThat(result.treatmentOnly).containsExactly("--bar", "456").inOrder()
  }

  @Test
  fun treatmentThenBaseline_splitsCorrectly() {
    val result = preprocessAbArgs(listOf(
      "--app", "foo.apk",
      "--treatment", "--bar", "456",
      "--baseline", "--bar", "123"
    ))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.baselineOnly).containsExactly("--bar", "123").inOrder()
    assertThat(result.treatmentOnly).containsExactly("--bar", "456").inOrder()
  }

  @Test
  fun baselineArgs_combinesSharedAndBaseline() {
    val result = preprocessAbArgs(listOf(
      "--app", "foo.apk",
      "--baseline", "--bar", "123",
      "--treatment", "--bar", "456"
    ))

    assertThat(result.baselineArgs()).containsExactly("--app", "foo.apk", "--bar", "123").inOrder()
  }

  @Test
  fun treatmentArgs_combinesSharedAndTreatment() {
    val result = preprocessAbArgs(listOf(
      "--app", "foo.apk",
      "--baseline", "--bar", "123",
      "--treatment", "--bar", "456"
    ))

    assertThat(result.treatmentArgs()).containsExactly("--app", "foo.apk", "--bar", "456").inOrder()
  }

  @Test
  fun emptyArgs_returnsEmptyLists() {
    val result = preprocessAbArgs(emptyList())

    assertThat(result.shared).isEmpty()
    assertThat(result.baselineOnly).isEmpty()
    assertThat(result.treatmentOnly).isEmpty()
  }

  @Test
  fun markersAtStart_noSharedArgs() {
    val result = preprocessAbArgs(listOf("--baseline", "--bar", "123", "--treatment", "--bar", "456"))

    assertThat(result.shared).isEmpty()
    assertThat(result.baselineOnly).containsExactly("--bar", "123").inOrder()
    assertThat(result.treatmentOnly).containsExactly("--bar", "456").inOrder()
  }

  @Test
  fun baselineOptions_alias() {
    val result = preprocessAbArgs(listOf("--app", "foo.apk", "--baseline-options", "--bar", "123"))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.baselineOnly).containsExactly("--bar", "123").inOrder()
  }

  @Test
  fun baselineOpts_alias() {
    val result = preprocessAbArgs(listOf("--app", "foo.apk", "--baseline-opts", "--bar", "123"))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.baselineOnly).containsExactly("--bar", "123").inOrder()
  }

  @Test
  fun treatmentOptions_alias() {
    val result = preprocessAbArgs(listOf("--app", "foo.apk", "--treatment-options", "--bar", "456"))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.treatmentOnly).containsExactly("--bar", "456").inOrder()
  }

  @Test
  fun treatmentOpts_alias() {
    val result = preprocessAbArgs(listOf("--app", "foo.apk", "--treatment-opts", "--bar", "456"))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.treatmentOnly).containsExactly("--bar", "456").inOrder()
  }

  @Test
  fun mixedAliases_workTogether() {
    val result = preprocessAbArgs(listOf(
      "--app", "foo.apk",
      "--baseline-options", "--bar", "123",
      "--treatment-opts", "--bar", "456"
    ))

    assertThat(result.shared).containsExactly("--app", "foo.apk").inOrder()
    assertThat(result.baselineOnly).containsExactly("--bar", "123").inOrder()
    assertThat(result.treatmentOnly).containsExactly("--bar", "456").inOrder()
  }

  @Test
  fun duplicateBaselineMarkers_throwsError() {
    val exception = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
      preprocessAbArgs(listOf("--baseline", "--bar", "123", "--baseline-options", "--baz", "456"))
    }
    assertThat(exception).hasMessageThat().contains("Cannot specify baseline options more than once")
  }

  @Test
  fun duplicateTreatmentMarkers_throwsError() {
    val exception = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
      preprocessAbArgs(listOf("--treatment", "--bar", "123", "--treatment-opts", "--baz", "456"))
    }
    assertThat(exception).hasMessageThat().contains("Cannot specify treatment options more than once")
  }
}
