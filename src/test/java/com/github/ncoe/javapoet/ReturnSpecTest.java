package com.github.ncoe.javapoet;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class ReturnSpecTest {
  @SuppressWarnings({"ConstantValue", "EqualsWithItself", "EqualsBetweenInconvertibleTypes"})
  @Test
  public void equalsAndHashCode() {
    ReturnSpec a = ReturnSpec.builder(int.class).addJavadoc("some value").build();
    assertThat(a.equals(null)).isFalse();
    assertThat(a.equals(42)).isFalse();
    assertThat(a.equals(a)).isTrue();
    ReturnSpec b = ReturnSpec.builder(int.class).addJavadoc(CodeBlock.of("some value")).build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());

    try {
      Appendable out = Mockito.mock(Appendable.class);
      Mockito.doThrow(IOException.class).when(out).append(ArgumentMatchers.anyString());

      a.toString(out);
    } catch (AssertionError | IOException e) {
      //empty
    }

    a = ReturnSpec.builder(int.class).build();
    b = ReturnSpec.builder(int.class).build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());
  }
}
