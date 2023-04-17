package com.tyron.editor.event;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Allows receiving notifications when editors are created and released.
 * To subscribe, use the {@code com.intellij.editorFactoryListener} extension point or
 * {@link com.intellij.openapi.editor.EditorFactory#addEditorFactoryListener(EditorFactoryListener, com.intellij.openapi.Disposable)}.
 */
public interface EditorFactoryListener extends EventListener {
  /**
   * Called after {@link com.intellij.openapi.editor.Editor} instance has been created.
   */
  default void editorCreated(@NotNull EditorFactoryEvent event) {
  }

  /**
   * Called before {@link com.intellij.openapi.editor.Editor} instance will be released.
   */
  default void editorReleased(@NotNull EditorFactoryEvent event) {
  }
}