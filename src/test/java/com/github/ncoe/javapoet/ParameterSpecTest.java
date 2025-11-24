/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.ncoe.javapoet;

import com.google.testing.compile.CompilationRule;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.List;

import static com.github.ncoe.javapoet.TestUtil.findFirst;
import static com.google.common.truth.Truth.assertThat;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.junit.Assert.fail;

public class ParameterSpecTest {
  @Rule
  public final CompilationRule compilation = new CompilationRule();

  private Elements elements;

  @Before
  public void setUp() {
    elements = compilation.getElements();
  }

  private TypeElement getElement(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }

  @Test
  public void equalsAndHashCode() {
    ParameterSpec a = ParameterSpec.builder(int.class, "foo").build();
    ParameterSpec b = ParameterSpec.builder(int.class, "foo").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());
    a = ParameterSpec.builder(int.class, "i").addModifiers(Modifier.STATIC).build();
    b = ParameterSpec.builder(int.class, "i").addModifiers(Modifier.STATIC).build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());
  }

  @Test
  public void receiverParameterInstanceMethod() {
    ParameterSpec.Builder builder = ParameterSpec.builder(int.class, "this");
    assertThat(builder.build().getName()).isEqualTo("this");
  }

  @Test
  public void receiverParameterNestedClass() {
    ParameterSpec.Builder builder = ParameterSpec.builder(int.class, "Foo.this");
    assertThat(builder.build().getName()).isEqualTo("Foo.this");
  }

  @Test
  public void keywordName() {
    try {
      ParameterSpec.builder(int.class, "super");
      fail();
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo("not a valid name: super");
    }
  }

  @Test
  public void nullAnnotationsAddition() {
    try {
      ParameterSpec.builder(int.class, "foo").addAnnotations(null);
      fail();
    } catch (Exception e) {
      assertThat(e.getMessage())
        .isEqualTo("annotationSpecs == null");
    }
  }

  @SuppressWarnings("unused")
  static final class VariableElementFieldClass {
    String name;
  }

  @Test
  public void fieldVariableElement() {
    TypeElement classElement = getElement(VariableElementFieldClass.class);
    List<VariableElement> methods = fieldsIn(elements.getAllMembers(classElement));
    VariableElement element = findFirst(methods, "name");

    try {
      ParameterSpec.get(element);
      fail();
    } catch (IllegalArgumentException exception) {
      assertThat(exception).hasMessageThat().isEqualTo("element is not a parameter");
    }
  }

  @SuppressWarnings("unused")
  static final class VariableElementParameterClass {
    public void foo(@Nullable final String bar) {
    }
  }

  @Test
  public void parameterVariableElement() {
    TypeElement classElement = getElement(VariableElementParameterClass.class);
    List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
    ExecutableElement element = findFirst(methods, "foo");
    VariableElement parameterElement = element.getParameters().get(0);

    assertThat(ParameterSpec.get(parameterElement).toString())
      .isEqualTo("java.lang.String bar");
  }

  @Test
  public void addNonFinalModifier() {
    List<Modifier> modifiers = new ArrayList<>();
    modifiers.add(Modifier.FINAL);
    modifiers.add(Modifier.PUBLIC);

    try {
      ParameterSpec.builder(int.class, "foo")
        .addModifiers(modifiers);
      fail();
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo("unexpected parameter modifier: public");
    }
  }

  @Test
  public void modifyAnnotations() {
    ParameterSpec.Builder builder = ParameterSpec.builder(int.class, "foo")
      .addAnnotation(Override.class)
      .addAnnotation(SuppressWarnings.class);

    builder.getAnnotations().remove(1);
    assertThat(builder.build().getAnnotations()).hasSize(1);
  }

  @Test
  public void modifyModifiers() {
    ParameterSpec.Builder builder = ParameterSpec.builder(int.class, "foo")
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    builder.getModifiers().remove(1);
    ParameterSpec parameterSpec = builder.build();
    assertThat(parameterSpec.hasModifier(Modifier.PUBLIC)).isTrue();
    assertThat(parameterSpec.getModifiers()).containsExactly(Modifier.PUBLIC);
  }

  @Test
  public void addAnnotations() {
    ParameterSpec parameterSpec = ParameterSpec
      .builder(String.class, "name")
      .addAnnotations(List.of(AnnotationSpec.builder(Nullable.class).build()))
      .build();
    assertThat(parameterSpec.toString()).isEqualTo("@org.jspecify.annotations.Nullable java.lang.String name");

    parameterSpec.toBuilder();
  }
}
