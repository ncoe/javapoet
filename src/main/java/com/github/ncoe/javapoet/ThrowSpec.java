/*
 * Copyright (C) 2025 Square, Inc.
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

import java.io.IOException;
import java.lang.reflect.Type;

import static com.github.ncoe.javapoet.Util.checkNotNull;

/**
 * Combine a throws specification with documentation.
 */
public final class ThrowSpec {
  private final TypeName type;
  private final CodeBlock javadoc;

  private ThrowSpec(Builder builder) {
    this.type = checkNotNull(builder.type, "type == null");
    this.javadoc = builder.javadoc.build();
  }

  public TypeName getType() {
    return type;
  }

  public CodeBlock getJavadoc() {
    return javadoc;
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

  /**
   * Return a string representation of object.
   *
   * @param out the output
   * @return a string representation of the object
   */
  public String toString(Appendable out) {
    try {
      CodeWriter codeWriter = new CodeWriter(out);
      type.emit(codeWriter);
      if (!javadoc.isEmpty()) {
        out.append(" ");
        out.append(javadoc.toString());
      }
      return out.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  @Override
  public String toString() {
    StringBuilder out = new StringBuilder();
    return toString(out);
  }

  /**
   * Create a builder.
   *
   * @param type the type
   * @return the builder
   */
  public static Builder builder(TypeName type) {
    checkNotNull(type, "type == null");
    return new Builder(type);
  }

  /**
   * Create a builder.
   *
   * @param type the type
   * @return the builder
   */
  public static Builder builder(Type type) {
    return builder(TypeName.get(type));
  }

  /**
   * Throw Specification Builder.
   */
  public static final class Builder {
    private final TypeName type;
    private final CodeBlock.Builder javadoc = CodeBlock.builder();

    private Builder(TypeName type) {
      this.type = type;
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
     * Finish and return the throw specification.
     *
     * @return the throw specification
     */
    public ThrowSpec build() {
      return new ThrowSpec(this);
    }
  }
}
