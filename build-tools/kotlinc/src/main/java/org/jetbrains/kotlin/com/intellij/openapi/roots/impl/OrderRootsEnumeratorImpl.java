package org.jetbrains.kotlin.com.intellij.openapi.roots.impl;

import androidx.annotation.NonNull;

import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.kotlin.com.intellij.openapi.module.Module;
import org.jetbrains.kotlin.com.intellij.openapi.roots.ContentEntry;
import org.jetbrains.kotlin.com.intellij.openapi.roots.JdkOrderEntry;
import org.jetbrains.kotlin.com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import org.jetbrains.kotlin.com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.kotlin.com.intellij.openapi.roots.ModuleRootModel;
import org.jetbrains.kotlin.com.intellij.openapi.roots.ModuleSourceOrderEntry;
import org.jetbrains.kotlin.com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.kotlin.com.intellij.openapi.roots.OrderEnumerationHandler;
import org.jetbrains.kotlin.com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.kotlin.com.intellij.openapi.roots.OrderRootsEnumerator;
import org.jetbrains.kotlin.com.intellij.openapi.roots.SourceFolder;
import org.jetbrains.kotlin.com.intellij.openapi.util.PathsList;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.kotlin.com.intellij.util.ArrayUtilRt;
import org.jetbrains.kotlin.com.intellij.util.NotNullFunction;
import org.jetbrains.kotlin.com.intellij.util.containers.ContainerUtil;

import java.util.*;

class OrderRootsEnumeratorImpl implements OrderRootsEnumerator {
  private static final Logger LOG = Logger.getInstance(OrderRootsEnumeratorImpl.class);
  private final OrderEnumeratorBase myOrderEnumerator;
  private final OrderRootType myRootType;
  private final NotNullFunction<? super OrderEntry, ? extends OrderRootType> myRootTypeProvider;
  private boolean myUsingCache;
  private NotNullFunction<? super JdkOrderEntry, VirtualFile[]> myCustomSdkRootProvider;
  private boolean myWithoutSelfModuleOutput;

  OrderRootsEnumeratorImpl(@NonNull OrderEnumeratorBase orderEnumerator, @NonNull OrderRootType rootType) {
    myOrderEnumerator = orderEnumerator;
    myRootType = rootType;
    myRootTypeProvider = null;
  }

  OrderRootsEnumeratorImpl(@NonNull OrderEnumeratorBase orderEnumerator,
                           @NonNull NotNullFunction<? super OrderEntry, ? extends OrderRootType> rootTypeProvider) {
    myOrderEnumerator = orderEnumerator;
    myRootType = null;
    myRootTypeProvider = rootTypeProvider;
  }

  @Override
  public VirtualFile[] getRoots() {
    if (myUsingCache) {
      checkCanUseCache();
      final OrderRootsCache cache = myOrderEnumerator.getCache();
      final int flags = myOrderEnumerator.getFlags();
      return cache.getOrComputeRoots(myRootType, flags, this::computeRootsUrls);
    }

    return VfsUtilCore.toVirtualFileArray(computeRoots());
  }

  @Override
  public String[] getUrls() {
    if (myUsingCache) {
      checkCanUseCache();
      final OrderRootsCache cache = myOrderEnumerator.getCache();
      final int flags = myOrderEnumerator.getFlags();
      return cache.getOrComputeUrls(myRootType, flags, this::computeRootsUrls);
    }
    return ArrayUtilRt.toStringArray(computeRootsUrls());
  }

  private void checkCanUseCache() {
    LOG.assertTrue(myRootTypeProvider == null, "Caching not supported for OrderRootsEnumerator with root type provider");
    LOG.assertTrue(myCustomSdkRootProvider == null, "Caching not supported for OrderRootsEnumerator with 'usingCustomRootProvider' option");
    LOG.assertTrue(!myWithoutSelfModuleOutput, "Caching not supported for OrderRootsEnumerator with 'withoutSelfModuleOutput' option");
  }

  @NonNull
  private Collection<VirtualFile> computeRoots() {
    final Collection<VirtualFile> result = new LinkedHashSet<>();
    myOrderEnumerator.forEach((orderEntry, customHandlers) -> {
      OrderRootType type = getRootType(orderEntry);

      if (orderEntry instanceof ModuleSourceOrderEntry) {
        collectModuleRoots(type, ((ModuleSourceOrderEntry)orderEntry).getRootModel(), result, true, !myOrderEnumerator.isProductionOnly(),
                customHandlers);
      }
      else if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry) orderEntry;
        final Module module = moduleOrderEntry.getModule();
        if (module != null) {
          ModuleRootModel rootModel = myOrderEnumerator.getRootModel(module);
          boolean productionOnTests = moduleOrderEntry.isProductionOnTestDependency();
          boolean includeTests = !myOrderEnumerator.isProductionOnly()
                                 && OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers)
                                 || productionOnTests;
          collectModuleRoots(type, rootModel, result, !productionOnTests, includeTests, customHandlers);
        }
      }
      else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        if (myCustomSdkRootProvider != null && orderEntry instanceof JdkOrderEntry) {
          Collections.addAll(result, myCustomSdkRootProvider.fun((JdkOrderEntry)orderEntry));
          return true;
        }
        if (OrderEnumeratorBase.addCustomRootsForLibraryOrSdk((LibraryOrSdkOrderEntry)orderEntry, type, result, customHandlers)) {
          return true;
        }
        Collections.addAll(result, ((LibraryOrSdkOrderEntry)orderEntry).getRootFiles(type));
      }
      else {
        LOG.error("Unexpected implementation of OrderEntry: " + orderEntry.getClass().getName());
      }
      return true;
    });
    return result;
  }

  @NonNull
  private Collection<String> computeRootsUrls() {
    final Collection<String> result = new LinkedHashSet<>();
    myOrderEnumerator.forEach((orderEntry, customHandlers) -> {
      OrderRootType type = getRootType(orderEntry);

      if (orderEntry instanceof ModuleSourceOrderEntry) {
        boolean includeTests = !myOrderEnumerator.isProductionOnly();
        collectModuleRootsUrls(type, ((ModuleSourceOrderEntry)orderEntry).getRootModel(), result, true, includeTests, customHandlers);
      }
      else if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry) orderEntry;
        final Module module = moduleOrderEntry.getModule();
        if (module != null) {
          ModuleRootModel rootModel = myOrderEnumerator.getRootModel(module);
          boolean productionOnTests = moduleOrderEntry.isProductionOnTestDependency();
          boolean includeTests = !myOrderEnumerator.isProductionOnly() && OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers)
                                 || productionOnTests;
          collectModuleRootsUrls(type, rootModel, result, !productionOnTests, includeTests, customHandlers);
        }
      }
      else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        if (OrderEnumeratorBase.addCustomRootUrlsForLibraryOrSdk((LibraryOrSdkOrderEntry)orderEntry, type, result, customHandlers)) {
          return true;
        }
        Collections.addAll(result, ((LibraryOrSdkOrderEntry)orderEntry).getRootUrls(type));
      }
      else {
        LOG.error("Unexpected implementation of OrderEntry: " + orderEntry.getClass().getName());
      }
      return true;
    });
    return result;
  }

  @NonNull
  @Override
  public PathsList getPathsList() {
    final PathsList list = new PathsList();
    collectPaths(list);
    return list;
  }

  @Override
  public void collectPaths(@NonNull PathsList list) {
    list.addVirtualFiles(getRoots());
  }

  @NonNull
  @Override
  public OrderRootsEnumerator usingCache() {
    myUsingCache = true;
    return this;
  }

  @NonNull
  @Override
  public OrderRootsEnumerator withoutSelfModuleOutput() {
    myWithoutSelfModuleOutput = true;
    return this;
  }

  @NonNull
  @Override
  public OrderRootsEnumerator usingCustomRootProvider(@NonNull NotNullFunction<? super OrderEntry, VirtualFile[]> provider) {
    return usingCustomSdkRootProvider(provider);
  }

  @Override
  public @NonNull OrderRootsEnumerator usingCustomSdkRootProvider(@NonNull NotNullFunction<? super JdkOrderEntry, VirtualFile[]> provider) {
    myCustomSdkRootProvider = provider;
    return this;
  }

  private void collectModuleRoots(@NonNull OrderRootType type,
                                  ModuleRootModel rootModel,
                                  @NonNull Collection<? super VirtualFile> result,
                                  final boolean includeProduction,
                                  final boolean includeTests,
                                  @NonNull List<? extends OrderEnumerationHandler> customHandlers) {
    if (type.equals(OrderRootType.SOURCES)) {
      if (includeProduction) {
        Collections.addAll(result, rootModel.getSourceRoots(includeTests));
      }
      else {
        throw new UnsupportedOperationException();
//        result.addAll(rootModel.getSourceRoots(JavaModuleSourceRootTypes.TESTS));
      }
    }
    else if (type.equals(OrderRootType.CLASSES)) {
//      final CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
//      if (extension != null) {
//        if (myWithoutSelfModuleOutput && myOrderEnumerator.isRootModuleModel(rootModel)) {
//          if (includeTests && includeProduction) {
//            Collections.addAll(result, extension.getOutputRoots(false));
//          }
//        }
//        else {
//          if (includeProduction) {
//            Collections.addAll(result, extension.getOutputRoots(includeTests));
//          }
//          else {
//            ContainerUtil.addIfNotNull(result, extension.getCompilerOutputPathForTests());
//          }
//        }
//      }
    }
    OrderEnumeratorBase.addCustomRootsForModule(type, rootModel, result, includeProduction, includeTests, customHandlers);
  }

  private void collectModuleRootsUrls(@NonNull OrderRootType type,
                                      @NonNull ModuleRootModel rootModel,
                                      @NonNull Collection<String> result,
                                      boolean includeProduction, boolean includeTests,
                                      @NonNull List<? extends OrderEnumerationHandler> customHandlers) {
    if (type.equals(OrderRootType.SOURCES)) {
      if (includeProduction) {
        Collections.addAll(result, rootModel.getSourceRootUrls(includeTests));
      }
      else {
        for (ContentEntry entry : rootModel.getContentEntries()) {
//          for (SourceFolder folder : entry.getSourceFolders(JavaModuleSourceRootTypes.TESTS)) {
//            result.add(folder.getUrl());
//          }
        }
      }
    }
    else if (type.equals(OrderRootType.CLASSES)) {
      boolean hasTests = false;
      boolean hasProduction = false;
      for (ContentEntry contentEntry : rootModel.getContentEntries()) {
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          if (sourceFolder.isTestSource()) {
            hasTests = true;
          }
          else {
            hasProduction = true;
          }
        }
      }
      includeTests &= hasTests;
      includeProduction &= hasProduction;
//      final CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
//      if (extension != null) {
//        if (myWithoutSelfModuleOutput && myOrderEnumerator.isRootModuleModel(rootModel)) {
//          if (includeTests && includeProduction) {
//            Collections.addAll(result, extension.getOutputRootUrls(false));
//          }
//        }
//        else {
//          if (includeProduction) {
//            Collections.addAll(result, extension.getOutputRootUrls(includeTests));
//          }
//          else if (includeTests) {
//            ContainerUtil.addIfNotNull(result, extension.getCompilerOutputUrlForTests());
//          }
//        }
//      }
    }
    OrderEnumeratorBase.addCustomRootsUrlsForModule(type, rootModel, result, includeProduction, includeTests, customHandlers);
  }

  @NonNull
  private OrderRootType getRootType(@NonNull OrderEntry e) {
    return myRootType != null ? myRootType : myRootTypeProvider.fun(e);
  }
}