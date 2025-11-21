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
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.github.ncoe.javapoet.Util.checkArgument;
import static com.github.ncoe.javapoet.Util.checkNotNull;
import static com.github.ncoe.javapoet.Util.checkState;

/**
 * A generated field declaration.
 */
public final class FieldSpec {
  private final TypeName type;
  private final String name;
  private final CodeBlock javadoc;
  private final List<AnnotationSpec> annotations;
  private final Set<Modifier> modifiers;
  private final CodeBlock initializer;

  private FieldSpec(Builder builder) {
    this.type = checkNotNull(builder.type, "type == null");
    this.name = checkNotNull(builder.name, "name == null");
    this.javadoc = builder.javadoc.build();
    this.annotations = Util.immutableList(builder.annotations);
    this.modifiers = Util.immutableSet(builder.modifiers);
    this.initializer = (builder.initializer == null)
      ? CodeBlock.builder().build()
      : builder.initializer;
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

  /**
   * Check if a modifier is present.
   *
   * @param modifier the modifier
   * @return true if the modifier is present
   */
  public boolean hasModifier(Modifier modifier) {
    return modifiers.contains(modifier);
  }

  void emit(CodeWriter codeWriter, Set<Modifier> implicitModifiers) throws IOException {
    codeWriter.emitJavadoc(javadoc);
    codeWriter.emitAnnotations(annotations, false);
    codeWriter.emitModifiers(modifiers, implicitModifiers);
    codeWriter.emit("$T $L", type, name);
    if (!initializer.isEmpty()) {
      codeWriter.emit(" = ");
      codeWriter.emit(initializer);
    }
    codeWriter.emit(";\n");
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
      emit(codeWriter, Collections.emptySet());
      return out.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  /**
   * Create a new field builder.
   *
   * @param type      the type
   * @param name      the name
   * @param modifiers the modifiers
   * @return this
   */
  public static Builder builder(TypeName type, String name, Modifier... modifiers) {
    checkNotNull(type, "type == null");
    checkArgument(SourceVersion.isName(name), "not a valid name: %s", name);
    return new Builder(type, name)
      .addModifiers(modifiers);
  }

  /**
   * Create a new field builder.
   *
   * @param type      the type
   * @param name      the name
   * @param modifiers the modifiers
   * @return this
   */
  public static Builder builder(Type type, String name, Modifier... modifiers) {
    return builder(TypeName.get(type), name, modifiers);
  }

  /**
   * Create a builder with a copy of this data.
   *
   * @return the new builder
   */
  public Builder toBuilder() {
    Builder builder = new Builder(type, name);
    builder.javadoc.add(javadoc);
    builder.annotations.addAll(annotations);
    builder.modifiers.addAll(modifiers);
    builder.initializer = initializer.isEmpty() ? null : initializer;
    return builder;
  }

  /**
   * Field Specification Builder.
   */
  public static final class Builder {
    private final TypeName type;
    private final String name;

    private final CodeBlock.Builder javadoc = CodeBlock.builder();
    private CodeBlock initializer = null;

    private final List<AnnotationSpec> annotations = new ArrayList<>();
    private final List<Modifier> modifiers = new ArrayList<>();

    private Builder(TypeName type, String name) {
      this.type = type;
      this.name = name;
    }

    public List<AnnotationSpec> getAnnotations() {
      return annotations;
    }

    public List<Modifier> getModifiers() {
      return modifiers;
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
     * Add a collection of annotation specifications.
     *
     * @param annotationSpecs the annotation specifications
     * @return this
     */
    public Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
      checkArgument(annotationSpecs != null, "annotationSpecs == null");
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
     * Add modifiers.
     *
     * @param modifiers the modifiers
     * @return this
     */
    public Builder addModifiers(Modifier... modifiers) {
      Collections.addAll(this.modifiers, modifiers);
      return this;
    }

    /**
     * Add to the field initializer.
     *
     * @param format the format
     * @param args   the arguments
     * @return this
     */
    public Builder initializer(String format, Object... args) {
      return initializer(CodeBlock.of(format, args));
    }

    /**
     * Add to the field initializer.
     *
     * @param codeBlock the code block
     * @return this
     */
    public Builder initializer(CodeBlock codeBlock) {
      checkState(this.initializer == null, "initializer was already set");
      this.initializer = checkNotNull(codeBlock, "codeBlock == null");
      return this;
    }

    /**
     * Finish and return the field specification.
     *
     * @return the field specification
     */
    public FieldSpec build() {
      return new FieldSpec(this);
    }
  }
}
