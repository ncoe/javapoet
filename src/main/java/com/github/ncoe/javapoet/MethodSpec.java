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

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.ncoe.javapoet.Util.checkArgument;
import static com.github.ncoe.javapoet.Util.checkArgumentFalse;
import static com.github.ncoe.javapoet.Util.checkArgumentNotNull;
import static com.github.ncoe.javapoet.Util.checkNotNull;
import static com.github.ncoe.javapoet.Util.checkStateIsNull;
import static com.github.ncoe.javapoet.Util.checkStateNotEqual;

/**
 * A generated constructor or method declaration.
 */
public final class MethodSpec {
  static final String CONSTRUCTOR = "<init>";

  private final String name;
  private final CodeBlock javadoc;
  private final List<AnnotationSpec> annotations;
  private final Set<Modifier> modifiers;
  private final List<TypeVariableName> typeVariables;
  private final ReturnSpec returnSpec;
  private final List<ParameterSpec> parameters;
  private final boolean varargs;
  private final List<ThrowSpec> exceptions;
  private final CodeBlock code;
  private final CodeBlock defaultValue;
  private final boolean compactConstructor;

  private MethodSpec(Builder builder) {
    CodeBlock code = builder.code.build();
    checkArgument(
      code.isEmpty() || !builder.modifiers.contains(Modifier.ABSTRACT),
      "abstract method %s cannot have code",
      builder.name
    );
    checkArgument(
      !builder.varargs || lastParameterIsArray(builder.parameters),
      "last parameter of varargs method %s must be an array",
      builder.name
    );

    this.name = checkNotNull(builder.name, "name == null");
    this.javadoc = builder.javadoc.build();
    this.annotations = Util.immutableList(builder.annotations);
    this.modifiers = Util.immutableSet(builder.modifiers);
    this.typeVariables = Util.immutableList(builder.typeVariables);
    this.returnSpec = builder.returnSpec;
    this.parameters = Util.immutableList(builder.parameters);
    this.varargs = builder.varargs;
    this.exceptions = Util.immutableList(builder.exceptions);
    this.defaultValue = builder.defaultValue;
    this.code = code;
    this.compactConstructor = builder.compactConstructor;
  }

  public String getName() {
    return name;
  }

  public List<AnnotationSpec> getAnnotations() {
    return annotations;
  }

  public Set<Modifier> getModifiers() {
    return modifiers;
  }

  public List<TypeVariableName> getTypeVariables() {
    return typeVariables;
  }

  public List<ParameterSpec> getParameters() {
    return parameters;
  }

  public boolean isVarargs() {
    return varargs;
  }

  public List<ThrowSpec> getExceptions() {
    return exceptions;
  }

  public CodeBlock getDefaultValue() {
    return defaultValue;
  }

  private boolean lastParameterIsArray(List<ParameterSpec> parameters) {
    return !parameters.isEmpty()
      && TypeName.asArray((parameters.get(parameters.size() - 1).getType())) != null;
  }

  void emit(
    CodeWriter codeWriter, String enclosingName, Set<Modifier> implicitModifiers
  ) throws IOException {
    codeWriter.emitJavadocWithContext(javadoc, parameters, returnSpec, exceptions);
    codeWriter.emitAnnotations(annotations, false);
    codeWriter.emitModifiers(modifiers, implicitModifiers);

    if (!typeVariables.isEmpty()) {
      codeWriter.emitTypeVariables(typeVariables);
      codeWriter.emit(" ");
    }

    if (compactConstructor) {
      codeWriter.emit("$L", enclosingName);
    } else if (isConstructor()) {
      codeWriter.emit("$L", enclosingName);
      codeWriter.emitParameters(parameters, varargs);
    } else {
      codeWriter.emit("$T $L", returnSpec.getType(), name);
      codeWriter.emitParameters(parameters, varargs);
    }

    if (defaultValue != null && !defaultValue.isEmpty()) {
      codeWriter.emit(" default ");
      codeWriter.emit(defaultValue);
    }

    if (!exceptions.isEmpty()) {
      codeWriter.emitWrappingSpace().emit("throws");
      boolean firstException = true;
      for (ThrowSpec exception : exceptions) {
        if (!firstException) {
          codeWriter.emit(",");
        }
        codeWriter.emitWrappingSpace().emit("$T", exception.getType());
        firstException = false;
      }
    }

    if (hasModifier(Modifier.ABSTRACT)) {
      codeWriter.emit(";\n");
    } else if (hasModifier(Modifier.NATIVE)) {
      // Code is allowed to support stuff like GWT JSNI.
      codeWriter.emit(code);
      codeWriter.emit(";\n");
    } else {
      codeWriter
        .emit(" {\n")
        .indent()
        .emit(code, true)
        .unindent()
        .emit("}\n");
    }
    codeWriter.popTypeVariables(typeVariables);
  }

  /**
   * Check if a modifier is already present.
   *
   * @param modifier the modifier
   * @return true if the modifier is present
   */
  public boolean hasModifier(Modifier modifier) {
    return modifiers.contains(modifier);
  }

  public boolean isConstructor() {
    return name.equals(CONSTRUCTOR);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    if (getClass() != o.getClass()) {
      return false;
    }
    return toString().equals(o.toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public String toString() {
    StringBuilder out = new StringBuilder();
    try {
      CodeWriter codeWriter = new CodeWriter(out);
      emit(codeWriter, "Constructor", Collections.emptySet());
      return out.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  /**
   * Create builder for a method.
   *
   * @param name the name
   * @return the builder
   */
  public static Builder methodBuilder(String name) {
    return new Builder(name, false);
  }

  /**
   * Create builder for a method.
   *
   * @param name      the name
   * @param modifiers the modifiers
   * @return the builder
   */
  public static Builder methodBuilder(String name, Modifier... modifiers) {
    return methodBuilder(name).addModifiers(modifiers);
  }

  /**
   * Create builder for a constructor.
   *
   * @return the builder
   */
  public static Builder constructorBuilder() {
    return new Builder(CONSTRUCTOR, false);
  }

  /**
   * Create builder for a constructor.
   *
   * @param modifiers the modifiers
   * @return the builder
   */
  public static Builder constructorBuilder(Modifier... modifiers) {
    return constructorBuilder().addModifiers(modifiers);
  }

  /**
   * Create builder for a compact record constructor.
   *
   * @return the builder
   */
  public static Builder compactConstructorBuilder() {
    return new Builder(CONSTRUCTOR, true);
  }

  /**
   * Create builder for a compact record constructor.
   *
   * @param modifiers the modifiers
   * @return the builder
   */
  public static Builder compactConstructorBuilder(Modifier... modifiers) {
    return compactConstructorBuilder().addModifiers(modifiers);
  }

  /**
   * Returns a new method spec builder that overrides {@code method}.
   *
   * <p>This will copy its visibility modifiers, type parameters, return type, name, parameters,
   * and throws declarations. An {@link Override} annotation will be added.
   *
   * <p>Note that in JavaPoet 1.2 through 1.7 this method retained annotations from the method and
   * parameters of the overridden method. Since JavaPoet 1.8 annotations must be added separately.
   *
   * @param method the method
   * @return the new builder
   */
  public static Builder overriding(ExecutableElement method) {
    checkNotNull(method, "method == null");

    Element enclosingClass = method.getEnclosingElement();
    if (enclosingClass.getModifiers().contains(Modifier.FINAL)) {
      throw new IllegalArgumentException("Cannot override method on final class " + enclosingClass);
    }

    Set<Modifier> modifiers = method.getModifiers();
    if (modifiers.contains(Modifier.PRIVATE)
      || modifiers.contains(Modifier.FINAL)
      || modifiers.contains(Modifier.STATIC)) {
      throw new IllegalArgumentException("cannot override method with modifiers: " + modifiers);
    }

    String methodName = method.getSimpleName().toString();
    MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);

    methodBuilder.addAnnotation(Override.class);

    modifiers = new LinkedHashSet<>(modifiers);
    modifiers.remove(Modifier.ABSTRACT);
    modifiers.remove(Modifier.DEFAULT);
    methodBuilder.addModifiers(modifiers);

    for (TypeParameterElement typeParameterElement : method.getTypeParameters()) {
      TypeVariable var = (TypeVariable) typeParameterElement.asType();
      methodBuilder.addTypeVariable(TypeVariableName.get(var));
    }

    methodBuilder
      .returns(TypeName.get(method.getReturnType()))
      .addParameters(ParameterSpec.parametersOf(method))
      .varargs(method.isVarArgs());

    for (TypeMirror thrownType : method.getThrownTypes()) {
      methodBuilder.addException(TypeName.get(thrownType));
    }

    return methodBuilder;
  }

  /**
   * Returns a new method spec builder that overrides {@code method} as
   * a member of {@code enclosing}. This will resolve type parameters:
   * for example overriding {@link Comparable#compareTo} in a type that
   * implements {@code Comparable<Movie>}, the {@code T} parameter will
   * be resolved to {@code Movie}.
   *
   * <p>This will copy its visibility modifiers, type parameters, return type, name, parameters,
   * and throws declarations. An {@link Override} annotation will be added.
   *
   * <p>Note that in JavaPoet 1.2 through 1.7 this method retained annotations from
   * the method and parameters of the overridden method. Since JavaPoet 1.8 annotations
   * must be added separately.
   *
   * @param method    the method
   * @param enclosing the enclosing type
   * @param types     the types
   * @return the new builder
   */
  public static Builder overriding(ExecutableElement method, DeclaredType enclosing, Types types) {
    final ExecutableType executableType = (ExecutableType) types.asMemberOf(enclosing, method);
    final List<? extends TypeMirror> resolvedParameterTypes = executableType.getParameterTypes();
    final List<? extends TypeMirror> resolvedThrownTypes = executableType.getThrownTypes();
    final TypeMirror resolvedReturnType = executableType.getReturnType();

    Builder builder = overriding(method);
    builder.returns(TypeName.get(resolvedReturnType));
    for (int i = 0, size = builder.parameters.size(); i < size; i++) {
      ParameterSpec parameter = builder.parameters.get(i);
      TypeName type = TypeName.get(resolvedParameterTypes.get(i));
      builder.parameters.set(i, parameter.toBuilder(type, parameter.getName()).build());
    }
    builder.exceptions.clear();
    for (TypeMirror resolvedThrownType : resolvedThrownTypes) {
      builder.addException(TypeName.get(resolvedThrownType));
    }

    return builder;
  }

  /**
   * Create a builder using this method as a template.
   *
   * @return the builder
   */
  public Builder toBuilder() {
    Builder builder = new Builder(name, compactConstructor);
    builder.javadoc.add(javadoc);
    builder.annotations.addAll(annotations);
    builder.modifiers.addAll(modifiers);
    builder.typeVariables.addAll(typeVariables);
    builder.returnSpec = returnSpec;
    builder.parameters.addAll(parameters);
    builder.exceptions.addAll(exceptions);
    builder.code.add(code);
    builder.varargs = varargs;
    builder.defaultValue = defaultValue;
    return builder;
  }

  /**
   * Method Specification Builder.
   */
  public static final class Builder {
    private String name;

    private final CodeBlock.Builder javadoc = CodeBlock.builder();
    private ReturnSpec returnSpec;
    private final Set<ThrowSpec> exceptions = new LinkedHashSet<>();
    private final CodeBlock.Builder code = CodeBlock.builder();
    private boolean varargs;
    private CodeBlock defaultValue;

    private final List<TypeVariableName> typeVariables = new ArrayList<>();
    private final List<AnnotationSpec> annotations = new ArrayList<>();
    private final List<Modifier> modifiers = new ArrayList<>();
    private final List<ParameterSpec> parameters = new ArrayList<>();

    private final boolean compactConstructor;

    private Builder(String name, boolean compactConstructor) {
      setName(name);
      this.compactConstructor = compactConstructor;
    }

    public List<TypeVariableName> getTypeVariables() {
      return typeVariables;
    }

    public List<AnnotationSpec> getAnnotations() {
      return annotations;
    }

    public List<Modifier> getModifiers() {
      return modifiers;
    }

    public List<ParameterSpec> getParameters() {
      return parameters;
    }

    /**
     * Set the method name.
     *
     * @param name the name
     * @return this
     */
    public Builder setName(String name) {
      checkNotNull(name, "name == null");
      checkArgument(
        name.equals(CONSTRUCTOR) || SourceVersion.isName(name),
        "not a valid name: %s",
        name
      );
      this.name = name;
      this.returnSpec = name.equals(CONSTRUCTOR) ? null : ReturnSpec.builder(TypeName.VOID).build();
      return this;
    }

    /**
     * Add javadoc.
     *
     * @param format the format
     * @param args   the arguments
     * @return this
     */
    public Builder addJavadoc(String format, Object... args) {
      javadoc.add(format, args);
      return this;
    }

    /**
     * Add javadoc.
     *
     * @param block the block
     * @return this
     */
    public Builder addJavadoc(CodeBlock block) {
      javadoc.add(block);
      return this;
    }

    /**
     * Add a collection of annotations.
     *
     * @param annotationSpecs the annotation specifications
     * @return this
     */
    public Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
      checkArgumentNotNull(annotationSpecs, "annotationSpecs == null");
      for (AnnotationSpec annotationSpec : annotationSpecs) {
        this.annotations.add(annotationSpec);
      }
      return this;
    }

    /**
     * Add an annotation.
     *
     * @param annotationSpec the annotation specification
     * @return this
     */
    public Builder addAnnotation(AnnotationSpec annotationSpec) {
      this.annotations.add(annotationSpec);
      return this;
    }

    /**
     * Add an annotation.
     *
     * @param annotation the annotation
     * @return this
     */
    public Builder addAnnotation(ClassName annotation) {
      this.annotations.add(AnnotationSpec.builder(annotation).build());
      return this;
    }

    /**
     * Add an annotation.
     *
     * @param annotation the annotation
     * @return this
     */
    public Builder addAnnotation(Class<?> annotation) {
      return addAnnotation(ClassName.get(annotation));
    }

    /**
     * Add a collection of modifiers.
     *
     * @param modifiers the modifiers
     * @return this
     */
    public Builder addModifiers(Modifier... modifiers) {
      checkNotNull(modifiers, "modifiers == null");
      Collections.addAll(this.modifiers, modifiers);
      return this;
    }

    /**
     * Add a collection of modifiers.
     *
     * @param modifiers the modifiers
     * @return this
     */
    public Builder addModifiers(Iterable<Modifier> modifiers) {
      checkNotNull(modifiers, "modifiers == null");
      for (Modifier modifier : modifiers) {
        this.modifiers.add(modifier);
      }
      return this;
    }

    /**
     * Add a collection of type variables.
     *
     * @param typeVariables the type variables
     * @return this
     */
    public Builder addTypeVariables(Iterable<TypeVariableName> typeVariables) {
      checkArgumentNotNull(typeVariables, "typeVariables == null");
      for (TypeVariableName typeVariable : typeVariables) {
        this.typeVariables.add(typeVariable);
      }
      return this;
    }

    /**
     * Add a type variable.
     *
     * @param typeVariable the type variable
     * @return this
     */
    public Builder addTypeVariable(TypeVariableName typeVariable) {
      typeVariables.add(typeVariable);
      return this;
    }

    /**
     * Add the return type.
     *
     * @param returnSpec the return specification
     * @return this
     */
    public Builder returns(ReturnSpec returnSpec) {
      this.returnSpec = returnSpec;
      return this;
    }

    /**
     * Add the return type.
     *
     * @param returnType the return type
     * @return this
     */
    public Builder returns(TypeName returnType) {
      checkStateNotEqual(name, CONSTRUCTOR, "constructor cannot have return type.");
      return returns(ReturnSpec.builder(returnType).build());
    }

    /**
     * Add the return type.
     *
     * @param returnType the return type
     * @return this
     */
    public Builder returns(Type returnType) {
      return returns(TypeName.get(returnType));
    }

    /**
     * Add a collection of parameters.
     *
     * @param parameterSpecs the parameter specifications
     * @return this
     */
    public Builder addParameters(Iterable<ParameterSpec> parameterSpecs) {
      checkArgumentNotNull(parameterSpecs, "parameterSpecs == null");
      checkArgumentFalse(compactConstructor, "compact constructors do not have parameters");
      for (ParameterSpec parameterSpec : parameterSpecs) {
        this.parameters.add(parameterSpec);
      }
      return this;
    }

    /**
     * Add a parameter.
     *
     * @param parameterSpec the parameter specification
     * @return this
     */
    public Builder addParameter(ParameterSpec parameterSpec) {
      this.parameters.add(parameterSpec);
      return this;
    }

    /**
     * Add a parameter.
     *
     * @param type      the type
     * @param name      the name
     * @param modifiers the modifiers
     * @return this
     */
    public Builder addParameter(TypeName type, String name, Modifier... modifiers) {
      return addParameter(ParameterSpec.builder(type, name, modifiers).build());
    }

    /**
     * Add a parameter.
     *
     * @param type      the type
     * @param name      the name
     * @param modifiers the modifiers
     * @return this
     */
    public Builder addParameter(Type type, String name, Modifier... modifiers) {
      return addParameter(TypeName.get(type), name, modifiers);
    }

    /**
     * Set if variable arguments.
     *
     * @return this
     */
    public Builder varargs() {
      return varargs(true);
    }

    /**
     * Set if variable arguments.
     *
     * @param varargs is variable arguments
     * @return this
     */
    public Builder varargs(boolean varargs) {
      this.varargs = varargs;
      return this;
    }

    /**
     * Add a collection of exceptions.
     *
     * @param exceptions the exceptions
     * @return this
     */
    public Builder addExceptions(Iterable<? extends TypeName> exceptions) {
      checkArgumentNotNull(exceptions, "exceptions == null");
      for (TypeName exception : exceptions) {
        addException(exception);
      }
      return this;
    }

    /**
     * Add an exception.
     *
     * @param throwSpec the throw specification
     * @return this
     */
    public Builder addException(ThrowSpec throwSpec) {
      this.exceptions.add(throwSpec);
      return this;
    }

    /**
     * Add an exception.
     *
     * @param exception the exception
     * @return this
     */
    public Builder addException(TypeName exception) {
      return addException(ThrowSpec.builder(exception).build());
    }

    /**
     * Add an exception.
     *
     * @param exception the exception
     * @return this
     */
    public Builder addException(Type exception) {
      return addException(TypeName.get(exception));
    }

    /**
     * Add a code block.
     *
     * @param format the format
     * @param args   the arguments
     * @return this
     */
    public Builder addCode(String format, Object... args) {
      code.add(format, args);
      return this;
    }

    /**
     * Add a code block.
     *
     * @param codeBlock the code block
     * @return this
     */
    public Builder addCode(CodeBlock codeBlock) {
      code.add(codeBlock);
      return this;
    }

    /**
     * Add a named code block.
     *
     * @param format the format
     * @param args   the arguments
     * @return this
     */
    public Builder addNamedCode(String format, Map<String, ?> args) {
      code.addNamed(format, args);
      return this;
    }

    /**
     * Add a single-line comment.
     *
     * @param format the format
     * @param args   the arguments
     * @return this
     */
    public Builder addComment(String format, Object... args) {
      code.add("// " + format + "\n", args);
      return this;
    }

    /**
     * A default method block.
     *
     * @param format the format
     * @param args   the args
     * @return this
     */
    public Builder defaultValue(String format, Object... args) {
      return defaultValue(CodeBlock.of(format, args));
    }

    /**
     * A default method block.
     *
     * @param codeBlock the code block
     * @return this
     */
    public Builder defaultValue(CodeBlock codeBlock) {
      checkStateIsNull(this.defaultValue, "defaultValue was already set");
      this.defaultValue = checkNotNull(codeBlock, "codeBlock == null");
      return this;
    }

    /**
     * Start a control block.
     *
     * @param controlFlow the control flow construct and its code, such as "if (foo == 5)".
     *                    Shouldn't contain braces or newline characters.
     * @param args        the arguments
     * @return this
     */
    public Builder beginControlFlow(String controlFlow, Object... args) {
      code.beginControlFlow(controlFlow, args);
      return this;
    }

    /**
     * Start a control block.
     *
     * @param codeBlock the control flow construct and its code, such as "if (foo == 5)".
     *                  Shouldn't contain braces or newline characters.
     * @return this
     */
    public Builder beginControlFlow(CodeBlock codeBlock) {
      return beginControlFlow("$L", codeBlock);
    }

    /**
     * Start the next control block.
     *
     * @param controlFlow the control flow construct and its code, such as "else if (foo == 10)".
     *                    Shouldn't contain braces or newline characters.
     * @param args        the arguments
     * @return this
     */
    public Builder nextControlFlow(String controlFlow, Object... args) {
      code.nextControlFlow(controlFlow, args);
      return this;
    }

    /**
     * Start the next control block.
     *
     * @param codeBlock the control flow construct and its code, such as "else if (foo == 10)".
     *                  Shouldn't contain braces or newline characters.
     * @return this
     */
    public Builder nextControlFlow(CodeBlock codeBlock) {
      return nextControlFlow("$L", codeBlock);
    }

    /**
     * End a control flow block.
     *
     * @return this
     */
    public Builder endControlFlow() {
      code.endControlFlow();
      return this;
    }

    /**
     * End a control flow block.
     *
     * @param controlFlow the optional control flow construct and its code, such as
     *                    "while(foo == 20)". Only used for "do/while" control flows.
     * @param args        the arguments
     * @return this
     */
    public Builder endControlFlow(String controlFlow, Object... args) {
      code.endControlFlow(controlFlow, args);
      return this;
    }

    /**
     * End a control flow block.
     *
     * @param codeBlock the optional control flow construct and its code, such as
     *                  "while(foo == 20)". Only used for "do/while" control flows.
     * @return this
     */
    public Builder endControlFlow(CodeBlock codeBlock) {
      return endControlFlow("$L", codeBlock);
    }

    /**
     * Add a statement.
     *
     * @param format the format
     * @param args   the arguments
     * @return this
     */
    public Builder addStatement(String format, Object... args) {
      code.addStatement(format, args);
      return this;
    }

    /**
     * Add a statement from a code block.
     *
     * @param codeBlock the code block
     * @return this
     */
    public Builder addStatement(CodeBlock codeBlock) {
      code.addStatement(codeBlock);
      return this;
    }

    /**
     * Finish and return the method specification.
     *
     * @return the modifier specification
     */
    public MethodSpec build() {
      return new MethodSpec(this);
    }
  }
}
