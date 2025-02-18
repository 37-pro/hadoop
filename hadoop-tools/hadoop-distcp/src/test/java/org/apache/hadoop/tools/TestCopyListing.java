/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.apache.hadoop.hdfs.protocol.SnapshotDiffReport;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.tools.util.TestDistCpUtils;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.Text;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TestCopyListing extends SimpleCopyListing {
  private static final Logger LOG = LoggerFactory.getLogger(TestCopyListing.class);

  private static final Credentials CREDENTIALS = new Credentials();

  private static final Configuration config = new Configuration();
  private static MiniDFSCluster cluster;

  @BeforeAll
  public static void create() throws IOException {
    cluster = new MiniDFSCluster.Builder(config).numDataNodes(1).format(true)
                                                .build();
  }

  @AfterAll
  public static void destroy() {
    if (cluster != null) {
      cluster.shutdown();
    }
  }

  public static Collection<Object[]> data() {
    Object[][] data = new Object[][]{{1}, {2}, {10}, {20}};
    return Arrays.asList(data);
  }

  public TestCopyListing() {
    super(config, CREDENTIALS, 1, 0, false);
  }

  public void initTestCopyListing(int numListstatusThreads) {
    initSimpleCopyListing(config, CREDENTIALS,
        numListstatusThreads, 0, false);
  }

  @Override
  protected long getBytesToCopy() {
    return 0;
  }

  @Override
  protected long getNumberOfPaths() {
    return 0;
  }


  @Timeout(value = 10)
  @ParameterizedTest
  @MethodSource("data")
  public void testMultipleSrcToFile(int pNumListstatusThreads) {
    initTestCopyListing(pNumListstatusThreads);
    FileSystem fs = null;
    try {
      fs = FileSystem.get(getConf());
      List<Path> srcPaths = new ArrayList<Path>();
      srcPaths.add(new Path("/tmp/in/1"));
      srcPaths.add(new Path("/tmp/in/2"));
      final Path target = new Path("/tmp/out/1");
      TestDistCpUtils.createFile(fs, "/tmp/in/1");
      TestDistCpUtils.createFile(fs, "/tmp/in/2");
      fs.mkdirs(target);
      final DistCpOptions options = new DistCpOptions.Builder(srcPaths, target)
          .build();
      validatePaths(new DistCpContext(options));
      TestDistCpUtils.delete(fs, "/tmp");
      //No errors

      fs.create(target).close();
      try {
        validatePaths(new DistCpContext(options));
        fail("Invalid inputs accepted");
      } catch (InvalidInputException ignore) { }
      TestDistCpUtils.delete(fs, "/tmp");

      srcPaths.clear();
      srcPaths.add(new Path("/tmp/in/1"));
      fs.mkdirs(new Path("/tmp/in/1"));
      fs.create(target).close();
      try {
        validatePaths(new DistCpContext(options));
        fail("Invalid inputs accepted");
      } catch (InvalidInputException ignore) { }
      TestDistCpUtils.delete(fs, "/tmp");
    } catch (IOException e) {
      LOG.error("Exception encountered ", e);
      fail("Test input validation failed");
    } finally {
      TestDistCpUtils.delete(fs, "/tmp");
    }
  }

  @ParameterizedTest
  @Timeout(value = 10)
  @MethodSource("data")
  public void testDuplicates(int pNumListstatusThreads) {
    initTestCopyListing(pNumListstatusThreads);
    FileSystem fs = null;
    try {
      fs = FileSystem.get(getConf());
      List<Path> srcPaths = new ArrayList<Path>();
      srcPaths.add(new Path("/tmp/in/*/*"));
      TestDistCpUtils.createFile(fs, "/tmp/in/src1/1.txt");
      TestDistCpUtils.createFile(fs, "/tmp/in/src2/1.txt");
      Path target = new Path("/tmp/out");
      Path listingFile = new Path("/tmp/list");
      final DistCpOptions options = new DistCpOptions.Builder(srcPaths, target)
          .build();
      final DistCpContext context = new DistCpContext(options);
      CopyListing listing = CopyListing.getCopyListing(getConf(), CREDENTIALS,
          context);
      try {
        listing.buildListing(listingFile, context);
        fail("Duplicates not detected");
      } catch (DuplicateFileException ignore) {
      }
    } catch (IOException e) {
      LOG.error("Exception encountered in test", e);
      fail("Test failed " + e.getMessage());
    } finally {
      TestDistCpUtils.delete(fs, "/tmp");
    }
  }

  @ParameterizedTest
  @Timeout(value = 10)
  @MethodSource("data")
  public void testDiffBasedSimpleCopyListing(int pNumListstatusThreads)
      throws IOException {
    initTestCopyListing(pNumListstatusThreads);
    assertThrows(DuplicateFileException.class, () -> {
      FileSystem fs = null;
      Configuration configuration = getConf();
      DistCpSync distCpSync = Mockito.mock(DistCpSync.class);
      Path listingFile = new Path("/tmp/list");
      // Throws DuplicateFileException as it recursively traverses src3 directory
      // and also adds 3.txt,4.txt twice
      configuration.setBoolean(
          DistCpConstants.CONF_LABEL_DIFF_COPY_LISTING_TRAVERSE_DIRECTORY, true);
      try {
        fs = FileSystem.get(getConf());
        buildListingUsingSnapshotDiff(fs, configuration, distCpSync, listingFile);
      } finally {
        TestDistCpUtils.delete(fs, "/tmp");
      }
    });
  }

  @ParameterizedTest
  @Timeout(value = 10)
  @MethodSource("data")
  public void testDiffBasedSimpleCopyListingWithoutTraverseDirectory(
      int pNumListstatusThreads) throws IOException {
    initTestCopyListing(pNumListstatusThreads);
    FileSystem fs = null;
    Configuration configuration = getConf();
    DistCpSync distCpSync = Mockito.mock(DistCpSync.class);
    Path listingFile = new Path("/tmp/list");
    // no exception expected in this case
    configuration.setBoolean(
        DistCpConstants.CONF_LABEL_DIFF_COPY_LISTING_TRAVERSE_DIRECTORY, false);
    try {
      fs = FileSystem.get(getConf());
      buildListingUsingSnapshotDiff(fs, configuration, distCpSync, listingFile);
    } finally {
      TestDistCpUtils.delete(fs, "/tmp");
    }
  }

  private void buildListingUsingSnapshotDiff(FileSystem fs,
      Configuration configuration, DistCpSync distCpSync, Path listingFile)
      throws IOException {
    List<Path> srcPaths = new ArrayList<>();
    srcPaths.add(new Path("/tmp/in"));
    TestDistCpUtils.createFile(fs, "/tmp/in/src1/1.txt");
    TestDistCpUtils.createFile(fs, "/tmp/in/src2/1.txt");
    TestDistCpUtils.createFile(fs, "/tmp/in/src3/3.txt");
    TestDistCpUtils.createFile(fs, "/tmp/in/src3/4.txt");
    Path target = new Path("/tmp/out");
    // adding below flags useDiff & sync only to enable context.shouldUseSnapshotDiff()
    final DistCpOptions options =
        new DistCpOptions.Builder(srcPaths, target).withUseDiff("snap1",
            "snap2").withSyncFolder(true).build();
    // Create a dummy DiffInfo List that contains a directory + paths inside
    // that directory as part of the diff.
    ArrayList<DiffInfo> diffs = new ArrayList<>();
    diffs.add(new DiffInfo(new Path("/tmp/in/src3/"), new Path("/tmp/in/src3/"),
        SnapshotDiffReport.DiffType.CREATE));
    diffs.add(new DiffInfo(new Path("/tmp/in/src3/3.txt"),
        new Path("/tmp/in/src3/3.txt"), SnapshotDiffReport.DiffType.CREATE));
    diffs.add(new DiffInfo(new Path("/tmp/in/src3/4.txt"),
        new Path("/tmp/in/src3/4.txt"), SnapshotDiffReport.DiffType.CREATE));
    Mockito.when(distCpSync.prepareDiffListForCopyListing()).thenReturn(diffs);
    final DistCpContext context = new DistCpContext(options);
    CopyListing listing =
        new SimpleCopyListing(configuration, CREDENTIALS, distCpSync);
    listing.buildListing(listingFile, context);
  }

  @ParameterizedTest
  @MethodSource("data")
  public void testDuplicateSourcePaths(int pNumListstatusThreads) throws Exception {
    initTestCopyListing(pNumListstatusThreads);
    FileSystem fs = FileSystem.get(getConf());
    List<Path> srcPaths = new ArrayList<Path>();
    try {
      srcPaths.add(new Path("/tmp/in"));
      srcPaths.add(new Path("/tmp/in"));
      TestDistCpUtils.createFile(fs, "/tmp/in/src1/1.txt");
      TestDistCpUtils.createFile(fs, "/tmp/in/src2/1.txt");
      Path target = new Path("/tmp/out");
      Path listingFile = new Path("/tmp/list");
      final DistCpOptions options =
          new DistCpOptions.Builder(srcPaths, target).build();
      final DistCpContext context = new DistCpContext(options);
      CopyListing listing =
          CopyListing.getCopyListing(getConf(), CREDENTIALS, context);
      listing.buildListing(listingFile, context);
      assertTrue(fs.exists(listingFile));
    } finally {
      TestDistCpUtils.delete(fs, "/tmp");
    }
  }

  @ParameterizedTest
  @Timeout(value = 10)
  @MethodSource("data")
  public void testBuildListing(int pNumListstatusThreads) {
    initTestCopyListing(pNumListstatusThreads);
    FileSystem fs = null;
    try {
      fs = FileSystem.get(getConf());
      List<Path> srcPaths = new ArrayList<Path>();
      Path p1 = new Path("/tmp/in/1");
      Path p2 = new Path("/tmp/in/2");
      Path p3 = new Path("/tmp/in2/2");
      Path target = new Path("/tmp/out/1");
      srcPaths.add(p1.getParent());
      srcPaths.add(p3.getParent());
      TestDistCpUtils.createFile(fs, "/tmp/in/1");
      TestDistCpUtils.createFile(fs, "/tmp/in/2");
      TestDistCpUtils.createFile(fs, "/tmp/in2/2");
      fs.mkdirs(target);
      OutputStream out = fs.create(p1);
      out.write("ABC".getBytes());
      out.close();

      out = fs.create(p2);
      out.write("DEF".getBytes());
      out.close();

      out = fs.create(p3);
      out.write("GHIJ".getBytes());
      out.close();

      Path listingFile = new Path("/tmp/file");

      final DistCpOptions options = new DistCpOptions.Builder(srcPaths, target)
          .withSyncFolder(true)
          .build();
      CopyListing listing = new SimpleCopyListing(getConf(), CREDENTIALS);
      try {
        listing.buildListing(listingFile, new DistCpContext(options));
        fail("Duplicates not detected");
      } catch (DuplicateFileException ignore) {
      }
      assertThat(listing.getBytesToCopy()).isEqualTo(10);
      assertThat(listing.getNumberOfPaths()).isEqualTo(3);
      TestDistCpUtils.delete(fs, "/tmp");

      try {
        listing.buildListing(listingFile, new DistCpContext(options));
        fail("Invalid input not detected");
      } catch (InvalidInputException ignore) {
      }
      TestDistCpUtils.delete(fs, "/tmp");
    } catch (IOException e) {
      LOG.error("Exception encountered ", e);
      fail("Test build listing failed");
    } finally {
      TestDistCpUtils.delete(fs, "/tmp");
    }
  }

  @ParameterizedTest
  @Timeout(value = 60)
  @MethodSource("data")
  public void testWithRandomFileListing(int pNumListstatusThreads)
      throws IOException {
    initTestCopyListing(pNumListstatusThreads);
    FileSystem fs = null;
    try {
      fs = FileSystem.get(getConf());
      List<Path> srcPaths = new ArrayList<>();
      List<Path> srcFiles = new ArrayList<>();
      Path target = new Path("/tmp/out/1");
      final int pathCount = 25;
      for (int i = 0; i < pathCount; i++) {
        Path p = new Path("/tmp", String.valueOf(i));
        srcPaths.add(p);
        fs.mkdirs(p);

        Path fileName = new Path(p, i + ".txt");
        srcFiles.add(fileName);
        try (OutputStream out = fs.create(fileName)) {
          out.write(i);
        }
      }

      Path listingFile = new Path("/tmp/file");
      final DistCpOptions options = new DistCpOptions.Builder(srcPaths, target)
          .withSyncFolder(true).build();

      // Check without randomizing files
      getConf().setBoolean(
          DistCpConstants.CONF_LABEL_SIMPLE_LISTING_RANDOMIZE_FILES, false);
      SimpleCopyListing listing = new SimpleCopyListing(getConf(), CREDENTIALS);
      listing.buildListing(listingFile, new DistCpContext(options));

      assertEquals(listing.getNumberOfPaths(), pathCount);
      validateFinalListing(listingFile, srcFiles);
      fs.delete(listingFile, true);

      // Check with randomized file listing
      getConf().setBoolean(
          DistCpConstants.CONF_LABEL_SIMPLE_LISTING_RANDOMIZE_FILES, true);
      listing = new SimpleCopyListing(getConf(), CREDENTIALS);

      // Set the seed for randomness, so that it can be verified later
      long seed = System.nanoTime();
      listing.setSeedForRandomListing(seed);
      listing.buildListing(listingFile, new DistCpContext(options));
      assertEquals(listing.getNumberOfPaths(), pathCount);

      // validate randomness
      Collections.shuffle(srcFiles, new Random(seed));
      validateFinalListing(listingFile, srcFiles);
    } finally {
      TestDistCpUtils.delete(fs, "/tmp");
    }
  }

  private void validateFinalListing(Path pathToListFile, List<Path> srcFiles)
      throws IOException {
    FileSystem fs = pathToListFile.getFileSystem(config);

    try (SequenceFile.Reader reader = new SequenceFile.Reader(
        config, SequenceFile.Reader.file(pathToListFile))) {
      CopyListingFileStatus currentVal = new CopyListingFileStatus();

      Text currentKey = new Text();
      int idx = 0;
      while (reader.next(currentKey)) {
        reader.getCurrentValue(currentVal);
        assertEquals(fs.makeQualified(srcFiles.get(idx)),
            currentVal.getPath(), "srcFiles.size=" + srcFiles.size() +
            ", idx=" + idx);
        if (LOG.isDebugEnabled()) {
          LOG.debug("val=" + fs.makeQualified(srcFiles.get(idx)));
        }
        idx++;
      }
    }
  }


  @ParameterizedTest
  @Timeout(value = 10)
  @MethodSource("data")
  public void testBuildListingForSingleFile(int pNumListstatusThreads) {
    initTestCopyListing(pNumListstatusThreads);
    FileSystem fs = null;
    String testRootString = "/singleFileListing";
    Path testRoot = new Path(testRootString);
    SequenceFile.Reader reader = null;
    try {
      fs = FileSystem.get(getConf());
      if (fs.exists(testRoot))
        TestDistCpUtils.delete(fs, testRootString);

      Path sourceFile = new Path(testRoot, "/source/foo/bar/source.txt");
      Path decoyFile  = new Path(testRoot, "/target/moo/source.txt");
      Path targetFile = new Path(testRoot, "/target/moo/target.txt");

      TestDistCpUtils.createFile(fs, sourceFile.toString());
      TestDistCpUtils.createFile(fs, decoyFile.toString());
      TestDistCpUtils.createFile(fs, targetFile.toString());

      List<Path> srcPaths = new ArrayList<Path>();
      srcPaths.add(sourceFile);

      DistCpOptions options = new DistCpOptions.Builder(srcPaths, targetFile)
          .build();
      CopyListing listing = new SimpleCopyListing(getConf(), CREDENTIALS);

      final Path listFile = new Path(testRoot, "/tmp/fileList.seq");
      listing.buildListing(listFile, new DistCpContext(options));

      reader = new SequenceFile.Reader(getConf(), SequenceFile.Reader.file(listFile));

      CopyListingFileStatus fileStatus = new CopyListingFileStatus();
      Text relativePath = new Text();
      assertTrue(reader.next(relativePath, fileStatus));
      assertTrue(relativePath.toString().equals(""));
    }
    catch (Exception e) {
      fail("Unexpected exception encountered.");
      LOG.error("Unexpected exception: ", e);
    }
    finally {
      TestDistCpUtils.delete(fs, testRootString);
      IOUtils.closeStream(reader);
    }
  }

  @ParameterizedTest
  @MethodSource("data")
  public void testFailOnCloseError(int pNumListstatusThreads) throws IOException {
    initTestCopyListing(pNumListstatusThreads);
    File inFile = File.createTempFile("TestCopyListingIn", null);
    inFile.deleteOnExit();
    File outFile = File.createTempFile("TestCopyListingOut", null);
    outFile.deleteOnExit();
    List<Path> srcs = new ArrayList<Path>();
    srcs.add(new Path(inFile.toURI()));
    
    Exception expectedEx = new IOException("boom");
    SequenceFile.Writer writer = mock(SequenceFile.Writer.class);
    doThrow(expectedEx).when(writer).close();
    
    SimpleCopyListing listing = new SimpleCopyListing(getConf(), CREDENTIALS);
    final DistCpOptions options = new DistCpOptions.Builder(srcs,
        new Path(outFile.toURI())).build();
    Exception actualEx = null;
    try {
      listing.doBuildListing(writer, new DistCpContext(options));
    } catch (Exception e) {
      actualEx = e;
    }
    assertNotNull(actualEx, "close writer didn't fail");
    assertEquals(expectedEx, actualEx);
  }
}
