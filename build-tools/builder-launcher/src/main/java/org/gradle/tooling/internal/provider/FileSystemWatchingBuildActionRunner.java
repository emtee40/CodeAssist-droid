package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.changedetection.state.FileHasherStatistics;
//import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.file.StatStatistics;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.VirtualFileSystemServices;
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics;
import org.gradle.internal.watch.options.FileSystemWatchingSettingsFinalizedProgressDetails;
import org.gradle.internal.watch.registry.WatchMode;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.VfsLogging;
import org.gradle.internal.watch.vfs.WatchLogging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemWatchingBuildActionRunner implements BuildActionRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemWatchingBuildActionRunner.class);

    private final BuildOperationProgressEventEmitter eventEmitter;
    private final BuildLifecycleAwareVirtualFileSystem virtualFileSystem;
    private final StatStatistics.Collector statStatisticsCollector;
    private final FileHasherStatistics.Collector fileHasherStatisticsCollector;
    private final DirectorySnapshotterStatistics.Collector directorySnapshotterStatisticsCollector;
    private final BuildOperationRunner buildOperationRunner;
    private final BuildActionRunner delegate;

    public FileSystemWatchingBuildActionRunner(
        BuildOperationProgressEventEmitter eventEmitter,
        BuildLifecycleAwareVirtualFileSystem virtualFileSystem,
        StatStatistics.Collector statStatisticsCollector,
        FileHasherStatistics.Collector fileHasherStatisticsCollector,
        DirectorySnapshotterStatistics.Collector directorySnapshotterStatisticsCollector,
        BuildOperationRunner buildOperationRunner,
        BuildActionRunner delegate
    ) {
        this.eventEmitter = eventEmitter;
        this.virtualFileSystem = virtualFileSystem;
        this.statStatisticsCollector = statStatisticsCollector;
        this.fileHasherStatisticsCollector = fileHasherStatisticsCollector;
        this.directorySnapshotterStatisticsCollector = directorySnapshotterStatisticsCollector;
        this.buildOperationRunner = buildOperationRunner;
        this.delegate = delegate;
    }

    @Override
    public Result run(BuildAction action, BuildTreeLifecycleController buildController) {
        StartParameterInternal startParameter = action.getStartParameter();

        WatchMode watchFileSystemMode = startParameter.getWatchFileSystemMode();
        VfsLogging verboseVfsLogging = startParameter.isVfsVerboseLogging()
            ? VfsLogging.VERBOSE
            : VfsLogging.NORMAL;
        WatchLogging debugWatchLogging = startParameter.isWatchFileSystemDebugLogging()
            ? WatchLogging.DEBUG
            : WatchLogging.NORMAL;

        LOGGER.info("Watching the file system is configured to be {}", watchFileSystemMode.getDescription());
        if (watchFileSystemMode.isEnabled()) {
            dropVirtualFileSystemIfRequested(startParameter, virtualFileSystem);
        }
        if (verboseVfsLogging == VfsLogging.VERBOSE) {
            logVfsStatistics("since last build", statStatisticsCollector, fileHasherStatisticsCollector, directorySnapshotterStatisticsCollector);
        }

        if (action.getStartParameter().getProjectCacheDir() != null) {
            // We'd like to create the probe in the `.gradle` directory under the build root,
            // but if project cache is somewhere else, then we don't want to put trash in there
            // See https://github.com/gradle/gradle/issues/17262
            switch (watchFileSystemMode) {
                case ENABLED:
                    throw new IllegalStateException("Enabling file system watching via --watch-fs (or via the " + "WatchOptions" + " property) with --project-cache-dir also specified is not supported; remove either option to fix this problem");
                case DEFAULT:
                    LOGGER.info("File system watching is disabled because --project-cache-dir is specified");
                    watchFileSystemMode = WatchMode.DISABLED;
                    break;
                default:
                    break;
            }
        }

        boolean actuallyWatching = virtualFileSystem.afterBuildStarted(watchFileSystemMode, verboseVfsLogging, debugWatchLogging, buildOperationRunner);
        LOGGER.info("File system watching is {}", actuallyWatching ? "active" : "inactive");
        //noinspection Convert2Lambda
        eventEmitter.emitNowForCurrent(new FileSystemWatchingSettingsFinalizedProgressDetails() {
            @Override
            public boolean isEnabled() {
                return actuallyWatching;
            }
        });

        try {
            return delegate.run(action, buildController);
        } finally {
            int maximumNumberOfWatchedHierarchies = VirtualFileSystemServices.getMaximumNumberOfWatchedHierarchies(startParameter);
            virtualFileSystem.beforeBuildFinished(watchFileSystemMode, verboseVfsLogging, debugWatchLogging, buildOperationRunner, maximumNumberOfWatchedHierarchies);
            if (verboseVfsLogging == VfsLogging.VERBOSE) {
                logVfsStatistics("during current build", statStatisticsCollector, fileHasherStatisticsCollector, directorySnapshotterStatisticsCollector);
            }
        }
    }

    private static void logVfsStatistics(
        String title,
        StatStatistics.Collector statStatisticsCollector,
        FileHasherStatistics.Collector fileHasherStatisticsCollector,
        DirectorySnapshotterStatistics.Collector directorySnapshotterStatisticsCollector
    ) {
        LOGGER.warn("VFS> Statistics {}:", title);
        LOGGER.warn("VFS> > Stat: {}", statStatisticsCollector.collect());
        LOGGER.warn("VFS> > FileHasher: {}", fileHasherStatisticsCollector.collect());
        LOGGER.warn("VFS> > DirectorySnapshotter: {}", directorySnapshotterStatisticsCollector.collect());
    }

    private static void dropVirtualFileSystemIfRequested(StartParameterInternal startParameter, BuildLifecycleAwareVirtualFileSystem virtualFileSystem) {
        if (VirtualFileSystemServices.isDropVfs(startParameter)) {
            virtualFileSystem.invalidateAll();
        }
    }
}