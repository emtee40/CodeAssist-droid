package org.jetbrains.kotlin.com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

final class PersistentFSPaths {
  @NonNls private static final String DEPENDENT_PERSISTENT_LIST_START_PREFIX = "vfs_enum_";
  @NonNls private static final String ROOTS_START_PREFIX = "roots_";
  static final String VFS_FILES_EXTENSION = System.getProperty("idea.vfs.files.extension", ".dat");

  @NotNull
  private final String myCachesDir;

  PersistentFSPaths(@NotNull String dir) {
    myCachesDir = dir;
  }

  @NotNull File getCorruptionMarkerFile() {
    return new File(new File(myCachesDir), "corruption.marker");
  }

  @NotNull File getVfsEnumBaseFile() {
    return new File(new File(myCachesDir), DEPENDENT_PERSISTENT_LIST_START_PREFIX);
  }

  @NotNull Path getVfsEnumFile(@NotNull String enumName) {
    return Paths.get(myCachesDir).resolve(DEPENDENT_PERSISTENT_LIST_START_PREFIX + enumName + VFS_FILES_EXTENSION);
  }

  @NotNull File getRootsBaseFile() {
    return new File(new File(myCachesDir), ROOTS_START_PREFIX);
  }

  @NotNull Path getRootsStorage(@NotNull String storageName) {
    return Paths.get(myCachesDir).resolve(ROOTS_START_PREFIX + storageName + VFS_FILES_EXTENSION);
  }
}