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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import static com.github.ncoe.javapoet.TestUtil.findFirst;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.junit.Assert.fail;

public final class MethodSpecTest {
  @Rule
  public final CompilationRule compilation = new CompilationRule();

  private Elements elements;
  private Types types;

  @Before
  public void setUp() {
    elements = compilation.getElements();
    types = compilation.getTypes();
  }

  private TypeElement getElement(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }

  @SuppressWarnings("DataFlowIssue")
  @Test
  public void nullAnnotationsAddition() {
    try {
      MethodSpec.methodBuilder("doSomething").addAnnotations(null);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("annotationSpecs == null");
    }
  }

  @SuppressWarnings("DataFlowIssue")
  @Test
  public void nullTypeVariablesAddition() {
    try {
      MethodSpec.methodBuilder("doSomething").addTypeVariables(null);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("typeVariables == null");
    }
  }

  @SuppressWarnings("DataFlowIssue")
  @Test
  public void nullParametersAddition() {
    try {
      MethodSpec.methodBuilder("doSomething").addParameters(null);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("parameterSpecs == null");
    }
  }

  @SuppressWarnings("DataFlowIssue")
  @Test
  public void nullExceptionsAddition() {
    try {
      MethodSpec.methodBuilder("doSomething").addExceptions(null);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("exceptions == null");
    }
  }

  @Target(ElementType.PARAMETER)
  @interface Nullable {
  }

  abstract static class Everything {
    @Deprecated
    protected abstract <T extends Runnable & Closeable> Runnable everything(
      @Nullable String thing, List<? extends T> things) throws IOException, SecurityException;
  }

  @SuppressWarnings({"RedundantThrows", "unused"})
  abstract static class Generics {
    <T, R, V extends Throwable> T run(R param) throws V {
      return null;
    }
  }

  abstract static class HasAnnotation {
    @Override
    public abstract String toString();
  }

  @SuppressWarnings("unused")
  interface Throws<R extends RuntimeException> {
    void fail() throws R;
  }

  interface ExtendsOthers extends Callable<Integer>, Comparable<ExtendsOthers>,
    Throws<IllegalStateException> {
  }

  interface ExtendsIterableWithDefaultMethods extends Iterable<Object> {
  }

  @SuppressWarnings("unused")
  static final class FinalClass {
    void method() {
    }
  }

  @SuppressWarnings("unused")
  abstract static class InvalidOverrideMethods {
    final void finalMethod() {
    }

    private void privateMethod() {
    }

    static void staticMethod() {
    }
  }

  @Test
  public void overrideEverything() {
    TypeElement classElement = getElement(Everything.class);
    ExecutableElement methodElement = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
    MethodSpec method = MethodSpec.overriding(methodElement).build();
    assertThat(method.toString()).isEqualTo("""
      @java.lang.Override
      protected <T extends java.lang.Runnable & java.io.Closeable> java.lang.Runnable everything(
          java.lang.String arg0, java.util.List<? extends T> arg1) throws java.io.IOException,
          java.lang.SecurityException {
      }
      """);
  }

  @Test
  public void overrideGenerics() {
    TypeElement classElement = getElement(Generics.class);
    ExecutableElement methodElement = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
    MethodSpec method = MethodSpec.overriding(methodElement)
      .addStatement("return null")
      .build();
    assertThat(method.toString()).isEqualTo("""
      @java.lang.Override
      <T, R, V extends java.lang.Throwable> T run(R param) throws V {
        return null;
      }
      """);
  }

  @Test
  public void overrideDoesNotCopyOverrideAnnotation() {
    TypeElement classElement = getElement(HasAnnotation.class);
    ExecutableElement exec = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
    MethodSpec method = MethodSpec.overriding(exec).build();
    assertThat(method.toString()).isEqualTo("""
      @java.lang.Override
      public java.lang.String toString() {
      }
      """);
  }

  @Test
  public void overrideDoesNotCopyDefaultModifier() {
    TypeElement classElement = getElement(ExtendsIterableWithDefaultMethods.class);
    DeclaredType classType = (DeclaredType) classElement.asType();
    List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
    ExecutableElement exec = findFirst(methods, "spliterator");
    MethodSpec method = MethodSpec.overriding(exec, classType, types).build();
    assertThat(method.toString()).isEqualTo("""
      @java.lang.Override
      public java.util.Spliterator<java.lang.Object> spliterator() {
      }
      """);
  }

  @Test
  public void overrideExtendsOthersWorksWithActualTypeParameters() {
    TypeElement classElement = getElement(ExtendsOthers.class);
    DeclaredType classType = (DeclaredType) classElement.asType();
    List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
    ExecutableElement exec = findFirst(methods, "call");
    MethodSpec method = MethodSpec.overriding(exec, classType, types).build();
    assertThat(method.toString()).isEqualTo("""
      @java.lang.Override
      public java.lang.Integer call() throws java.lang.Exception {
      }
      """);
    exec = findFirst(methods, "compareTo");
    method = MethodSpec.overriding(exec, classType, types).build();
    assertThat(method.toString()).isEqualTo("""
      @java.lang.Override
      public int compareTo(%s arg0) {
      }
      """.formatted(ExtendsOthers.class.getCanonicalName()));
    exec = findFirst(methods, "fail");
    method = MethodSpec.overriding(exec, classType, types).build();
    assertThat(method.toString()).isEqualTo("""
      @java.lang.Override
      public void fail() throws java.lang.IllegalStateException {
      }
      """);
  }

  @Test
  public void overrideFinalClassMethod() {
    TypeElement classElement = getElement(FinalClass.class);
    List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
    try {
      MethodSpec.overriding(findFirst(methods, "method"));
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo(
        "Cannot override method on final class com.github.ncoe.javapoet.MethodSpecTest.FinalClass");
    }
  }

  @Test
  public void overrideInvalidModifiers() {
    TypeElement classElement = getElement(InvalidOverrideMethods.class);
    List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
    try {
      MethodSpec.overriding(findFirst(methods, "finalMethod"));
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("cannot override method with modifiers: [final]");
    }
    try {
      MethodSpec.overriding(findFirst(methods, "privateMethod"));
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("cannot override method with modifiers: [private]");
    }
    try {
      MethodSpec.overriding(findFirst(methods, "staticMethod"));
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("cannot override method with modifiers: [static]");
    }
  }

  @SuppressWarnings("unused")
  abstract static class AbstractClassWithPrivateAnnotation {

    private @interface PrivateAnnotation {
    }

    abstract void foo(@PrivateAnnotation final String bar);
  }

  @Test
  public void overrideDoesNotCopyParameterAnnotations() {
    TypeElement abstractTypeElement = getElement(AbstractClassWithPrivateAnnotation.class);
    ExecutableElement fooElement = ElementFilter.methodsIn(abstractTypeElement.getEnclosedElements()).get(0);
    ClassName implClassName = ClassName.get("com.github.ncoe.javapoet", "Impl");
    TypeSpec type = TypeSpec.classBuilder(implClassName)
      .superclass(abstractTypeElement.asType())
      .addMethod(MethodSpec.overriding(fooElement).build())
      .build();
    JavaFileObject jfo = JavaFile.builder(implClassName.packageName(), type).build().toJavaFileObject();
    Compilation compilation = javac().compile(jfo);
    assertThat(compilation).succeeded();
  }

  @Test
  public void equalsAndHashCode() {
    MethodSpec a = MethodSpec.constructorBuilder().build();
    MethodSpec b = MethodSpec.constructorBuilder().build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    a = MethodSpec.methodBuilder("taco").build();
    b = MethodSpec.methodBuilder("taco").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    TypeElement classElement = getElement(Everything.class);
    ExecutableElement methodElement = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
    a = MethodSpec.overriding(methodElement).build();
    b = MethodSpec.overriding(methodElement).build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  public void withoutParameterJavaDoc() {
    MethodSpec methodSpec = MethodSpec.methodBuilder("getTaco")
      .addModifiers(Modifier.PRIVATE)
      .addParameter(TypeName.DOUBLE, "money")
      .addJavadoc("Gets the best Taco\n")
      .build();
    assertThat(methodSpec.toString()).isEqualTo("""
      /**
       * Gets the best Taco
       */
      private void getTaco(double money) {
      }
      """);
  }

  @Test
  public void withParameterJavaDoc() {
    MethodSpec methodSpec = MethodSpec.methodBuilder("getTaco")
      .addParameter(ParameterSpec.builder(TypeName.DOUBLE, "money")
        .addJavadoc("the amount required to buy the taco.")
        .build())
      .addParameter(ParameterSpec.builder(TypeName.INT, "count")
        .addJavadoc("the number of Tacos to buy.")
        .build())
      .addJavadoc("Gets the best Taco money can buy.")
      .build();
    assertThat(methodSpec.toString()).isEqualTo("""
      /**
       * Gets the best Taco money can buy.
       *
       * @param money the amount required to buy the taco.
       * @param count the number of Tacos to buy.
       */
      void getTaco(double money, int count) {
      }
      """);
  }

  @Test
  public void withParameterJavaDocAndWithoutMethodJavadoc() {
    MethodSpec methodSpec = MethodSpec.methodBuilder("getTaco")
      .addParameter(ParameterSpec.builder(TypeName.DOUBLE, "money")
        .addJavadoc("the amount required to buy the taco.")
        .build())
      .addParameter(ParameterSpec.builder(TypeName.INT, "count")
        .addJavadoc("the number of Tacos to buy.")
        .build())
      .build();
    assertThat(methodSpec.toString()).isEqualTo("""
      /**
       * @param money the amount required to buy the taco.
       * @param count the number of Tacos to buy.
       */
      void getTaco(double money, int count) {
      }
      """);
  }

  @Test
  public void withParameterJavaDocBlocks() {
    MethodSpec methodSpec = MethodSpec.methodBuilder("getTaco")
      .addParameter(ParameterSpec.builder(TypeName.DOUBLE, "money")
        .addJavadoc(CodeBlock.of("the amount required to buy the taco."))
        .build())
      .addParameter(ParameterSpec.builder(TypeName.INT, "count")
        .addJavadoc(CodeBlock.of("the number of Tacos to buy."))
        .build())
      .build();
    assertThat(methodSpec.toString()).isEqualTo("""
      /**
       * @param money the amount required to buy the taco.
       * @param count the number of Tacos to buy.
       */
      void getTaco(double money, int count) {
      }
      """);
  }

  @Test
  public void duplicateExceptionsIgnored() {
    ClassName ioException = ClassName.get(IOException.class);
    ClassName timeoutException = ClassName.get(TimeoutException.class);
    MethodSpec methodSpec = MethodSpec.methodBuilder("duplicateExceptions")
      .addException(ioException)
      .addException(timeoutException)
      .addException(timeoutException)
      .addException(ioException)
      .build();
    List<ThrowSpec> throwsList = Arrays.asList(
      ThrowSpec.builder(ioException).build(),
      ThrowSpec.builder(timeoutException).build()
    );
    assertThat(methodSpec.getExceptions())
      .isEqualTo(throwsList);
    assertThat(methodSpec.toBuilder().addException(ioException).build().getExceptions())
      .isEqualTo(throwsList);
  }

  @Test
  public void nullIsNotAValidMethodName() {
    try {
      MethodSpec.methodBuilder(null);
      fail("NullPointerException expected");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isEqualTo("name == null");
    }
  }

  @Test
  public void addModifiersVarargsShouldNotBeNull() {
    try {
      MethodSpec.methodBuilder("taco")
        .addModifiers((Modifier[]) null);
      fail("NullPointerException expected");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isEqualTo("modifiers == null");
    }
  }

  @Test
  public void modifyMethodName() {
    MethodSpec methodSpec = MethodSpec.methodBuilder("initialMethod")
      .build()
      .toBuilder()
      .setName("revisedMethod")
      .build();

    assertThat(methodSpec.toString()).isEqualTo("""
      void revisedMethod() {
      }
      """);
  }

  @Test
  public void modifyAnnotations() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("foo")
      .addAnnotation(Override.class)
      .addAnnotation(SuppressWarnings.class);

    builder.getAnnotations().remove(1);
    assertThat(builder.build().getAnnotations()).hasSize(1);
  }

  @Test
  public void modifyModifiers() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("foo")
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    builder.getModifiers().remove(1);
    assertThat(builder.build().getModifiers()).containsExactly(Modifier.PUBLIC);
  }

  @Test
  public void modifyParameters() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("foo")
      .addParameter(int.class, "source");

    builder.getParameters().remove(0);
    assertThat(builder.build().getParameters()).isEmpty();
  }

  @Test
  public void modifyTypeVariables() {
    TypeVariableName t = TypeVariableName.get("T");
    MethodSpec.Builder builder = MethodSpec.methodBuilder("foo")
      .addTypeVariable(t)
      .addTypeVariable(TypeVariableName.get("V"));

    builder.getTypeVariables().remove(1);
    assertThat(builder.build().getTypeVariables()).containsExactly(t);
  }

  @Test
  public void ensureTrailingNewline() {
    MethodSpec methodSpec = MethodSpec.methodBuilder("method")
      .addCode("codeWithNoNewline();")
      .build();

    assertThat(methodSpec.toString()).isEqualTo("""
      void method() {
        codeWithNoNewline();
      }
      """);
  }

  /**
   * Ensures that we don't add a duplicate newline if one is already present.
   */
  @Test
  public void ensureTrailingNewlineWithExistingNewline() {
    MethodSpec methodSpec = MethodSpec.methodBuilder("method")
      .addCode("codeWithNoNewline();\n") // Have a newline already, so ensure we're not adding one
      .build();

    assertThat(methodSpec.toString()).isEqualTo("""
      void method() {
        codeWithNoNewline();
      }
      """);
  }

  @Test
  public void controlFlowWithNamedCodeBlocks() {
    Map<String, Object> m = new HashMap<>();
    m.put("field", "valueField");
    m.put("threshold", "5");

    MethodSpec methodSpec = MethodSpec.methodBuilder("method")
      .beginControlFlow(named("if ($field:N > $threshold:L)", m))
      .nextControlFlow(named("else if ($field:N == $threshold:L)", m))
      .endControlFlow()
      .build();

    assertThat(methodSpec.toString()).isEqualTo("""
      void method() {
        if (valueField > 5) {
        } else if (valueField == 5) {
        }
      }
      """);
  }

  @Test
  public void doWhileWithNamedCodeBlocks() {
    Map<String, Object> m = new HashMap<>();
    m.put("field", "valueField");
    m.put("threshold", "5");

    MethodSpec methodSpec = MethodSpec.methodBuilder("method")
      .beginControlFlow("do")
      .addStatement(named("$field:N--", m))
      .endControlFlow(named("while ($field:N > $threshold:L)", m))
      .build();

    assertThat(methodSpec.toString()).isEqualTo("""
      void method() {
        do {
          valueField--;
        } while (valueField > 5);
      }
      """);
  }

  private static CodeBlock named(String format, Map<String, ?> args) {
    return CodeBlock.builder().addNamed(format, args).build();
  }

  @Test
  public void coverage() {
    MethodSpec
      .methodBuilder("name")
      .addTypeVariables(List.of(TypeVariableName.get("T")))
      .addAnnotations(List.of(AnnotationSpec.builder(Override.class).build()))
      .addJavadoc(CodeBlock.of("Huge Success!!"))
      .addExceptions(List.of(ClassName.get(IOException.class)))
      .addComment("Check this out")
      .addNamedCode("$text:S", Map.of("text", "oops"))
      .build();
  }
}
