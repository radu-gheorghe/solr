/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.blob;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.store.*;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.IOUtils;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CachingDirectoryFactory;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.DirectoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobDirectoryFactory extends CachingDirectoryFactory {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Pattern INDEX_NAME_PATTERN = Pattern.compile("index(?:\\.[0-9]{17})?");

  private String localRootPath;
  private BlobStore blobStore;
  private BlobPusher blobPusher;
  private MMapParams mMapParams;

  @Override
  public void initCoreContainer(CoreContainer cc) {
    super.initCoreContainer(cc);
    localRootPath = (dataHomePath == null ? cc.getCoreRootDirectory() : dataHomePath).getParent().toString();
    // blobListingManager = BlobListingManager.getInstance(cc, "/blobDirListings");
  }

  @Override
  public void init(@SuppressWarnings("rawtypes") NamedList args) {
    super.init(args);
    SolrParams params = args.toSolrParams();

    // BlobStore where files are persisted.
    blobStore = initBlobStore(params);
    blobPusher = new BlobPusher(blobStore);

    // Filesystem MMapDirectory used as a local file cache.
    mMapParams = new MMapParams(params);
  }

  private BlobStore initBlobStore(SolrParams params) {
    String blobStoreClass = params.get("blobStore.class");
    if (blobStoreClass == null) {
      throw new IllegalArgumentException("blobStore.class is required");
    }
    BlobStore blobStore = coreContainer.getResourceLoader().newInstance(blobStoreClass, BlobStore.class);
    blobStore.init(params);
    return blobStore;
  }

  BlobStore getBlobStore() {
    return blobStore;
  }

  MMapParams getMMapParams() {
    return mMapParams;
  }

  @Override
  public void doneWithDirectory(Directory directory) throws IOException {
    log.debug("doneWithDirectory {}", directory);
    ((BlobDirectory) directory).release();
    super.doneWithDirectory(directory);
  }

  @Override
  public void close() throws IOException {
    log.debug("close");
    IOUtils.closeQuietly(blobStore);
    IOUtils.closeQuietly(blobPusher);
    super.close();
  }

  @Override
  protected LockFactory createLockFactory(String rawLockType) {
    log.debug("createLockFactory {}", rawLockType);
    if (rawLockType == null) {
      rawLockType = DirectoryFactory.LOCK_TYPE_NATIVE;
      log.warn("No lockType configured, assuming '{}'.", rawLockType);
    }
    String lockType = rawLockType.toLowerCase(Locale.ROOT).trim();
    switch (lockType) {
      case DirectoryFactory.LOCK_TYPE_SIMPLE:
        return SimpleFSLockFactory.INSTANCE;
      case DirectoryFactory.LOCK_TYPE_NATIVE:
        return NativeFSLockFactory.INSTANCE;
      case DirectoryFactory.LOCK_TYPE_SINGLE:
        return new SingleInstanceLockFactory();
      case DirectoryFactory.LOCK_TYPE_NONE:
        return NoLockFactory.INSTANCE;
      default:
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                "Unrecognized lockType: " + rawLockType);
    }
  }

  @Override
  protected Directory create(String path, LockFactory lockFactory, DirContext dirContext)
      throws IOException {
    log.debug("Create Directory {}", path);
    // Create directly a MMapDirectory without calling MMapDirectoryFactory because this BlobDirectoryFactory
    // is already a CachingDirectoryFactory, so we don't want another CachingDirectoryFactory.
    MMapDirectory mapDirectory = createMMapDirectory(path, lockFactory);
    String blobDirPath = getLocalRelativePath(path);
    return new BlobDirectory(mapDirectory, blobDirPath, blobPusher);
  }

  private MMapDirectory createMMapDirectory(String path, LockFactory lockFactory) throws IOException {
    MMapDirectory mapDirectory = new MMapDirectory(new File(path).toPath(), lockFactory, mMapParams.maxChunk);
    try {
      mapDirectory.setUseUnmap(mMapParams.unmap);
    } catch (IllegalArgumentException e) {
      log.warn("Unmap not supported on this JVM, continuing on without setting unmap", e);
    }
    mapDirectory.setPreload(mMapParams.preload);
    return mapDirectory;
  }

  String getLocalRelativePath(String path) {
    return getRelativePath(path, localRootPath);
  }

  private String getRelativePath(String path, String referencePath) {
    if (!path.startsWith(referencePath)) {
      throw new IllegalArgumentException("Path=" + path + " is expected to start with referencePath="
              + referencePath + " otherwise we have to adapt the code");
    }
    String relativePath = path.substring(referencePath.length());
    if (relativePath.startsWith("/")) {
      relativePath = relativePath.substring(1);
    }
    return relativePath;
  }

  @Override
  public boolean exists(String path) throws IOException {
    boolean exists = super.exists(path);
    log.debug("exists {} = {}", path, exists);
    return exists;
  }

  @Override
  protected void removeDirectory(CacheValue cacheValue) throws IOException {
    log.debug("removeDirectory {}", cacheValue);
    File dirFile = new File(cacheValue.path);
    FileUtils.deleteDirectory(dirFile);
    String blobDirPath = getLocalRelativePath(cacheValue.path);
    blobStore.deleteDirectory(blobDirPath);
  }

  @Override
  public void move(Directory fromDir, Directory toDir, String fileName, IOContext ioContext)
      throws IOException {
    // TODO: override for efficiency?
    log.debug("move {} {} to {}", fromDir, fileName, toDir);
    super.move(fromDir, toDir, fileName, ioContext);
  }

  @Override
  public void renameWithOverwrite(Directory dir, String fileName, String toName)
      throws IOException {
    // TODO: override to perform an atomic rename if possible?
    log.debug("renameWithOverwrite {} {} to {}", dir, fileName, toName);
    super.renameWithOverwrite(dir, fileName, toName);
  }

  @Override
  public boolean isPersistent() {
    return true;
  }

  @Override
  public boolean isSharedStorage() {
    return true;
  }

  @Override
  public void release(Directory directory) throws IOException {
    log.debug("release {}", directory);
    ((BlobDirectory) directory).release();
    super.release(directory);
  }

  @Override
  public boolean isAbsolute(String path) {
    boolean isAbsolute = new File(path).isAbsolute();
    log.debug("isAbsolute {} = {}", path, isAbsolute);
    return isAbsolute;
  }

  @Override
  public boolean searchersReserveCommitPoints() {
    return false; // TODO: double check
  }

  @Override
  public String getDataHome(CoreDescriptor cd) throws IOException {
    String dataHome = super.getDataHome(cd);
    log.debug("getDataHome {}", dataHome);
    return dataHome;
  }

  @Override
  public void cleanupOldIndexDirectories(String dataDirPath, String currentIndexDirPath, boolean afterCoreReload) {
    log.debug("cleanupOldIndexDirectories {} {}", dataDirPath, currentIndexDirPath);

    super.cleanupOldIndexDirectories(dataDirPath, currentIndexDirPath, afterCoreReload);

    try {
      dataDirPath = normalize(dataDirPath);
      currentIndexDirPath = normalize(currentIndexDirPath);
    } catch (IOException e) {
      log.error("Failed to delete old index directories in {} due to: ", dataDirPath, e);
    }
    String blobDirPath = getLocalRelativePath(dataDirPath);
    String currentIndexDirName = getRelativePath(currentIndexDirPath, dataDirPath);
    List<String> oldIndexDirs;
    try {
      oldIndexDirs = blobStore.listInDirectory(blobDirPath,
              (name) -> !name.equals(currentIndexDirName)
                      && INDEX_NAME_PATTERN.matcher(name).matches());
    } catch (IOException e) {
      log.error("Failed to delete old index directories in {} due to: ", blobDirPath, e);
      return;
    }
    if (oldIndexDirs.isEmpty()) {
      return;
    }
    if (afterCoreReload) {
      // Do not remove the most recent old directory after a core reload.
      if (oldIndexDirs.size() == 1) {
        return;
      }
      oldIndexDirs.sort(null);
      oldIndexDirs = oldIndexDirs.subList(0, oldIndexDirs.size() - 1);
    }
    try {
      for (String oldIndexDir : oldIndexDirs) {
        blobStore.deleteDirectory(blobDirPath + '/' + oldIndexDir);
      }
    } catch (IOException e) {
      log.error("Failed to delete old index directories {} in {} due to: ", oldIndexDirs, blobDirPath, e);
    }
  }

  /**
   * Parameters to create {@link MMapDirectory}.
   */
  static class MMapParams {

    final boolean unmap;
    final boolean preload;
    final int maxChunk;

    private MMapParams(SolrParams params) {
      maxChunk = params.getInt("mmap.maxChunkSize", MMapDirectory.DEFAULT_MAX_CHUNK_SIZE);
      if (maxChunk <= 0) {
        throw new IllegalArgumentException("mmap.maxChunkSize must be greater than 0");
      }
      unmap = params.getBool("mmap.unmap", true);
      preload = params.getBool("mmap.preload", false); // default turn-off
    }
  }
}
