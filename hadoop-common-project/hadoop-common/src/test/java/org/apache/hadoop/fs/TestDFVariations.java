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
package org.apache.hadoop.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Random;

import org.apache.hadoop.test.GenericTestUtils;
import org.apache.hadoop.util.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class TestDFVariations {
  private static final String TEST_ROOT_DIR =
      GenericTestUtils.getTestDir("testdfvariations").getAbsolutePath();
  private static File test_root = null;

  @BeforeEach
  public void setup() throws IOException {
    test_root = new File(TEST_ROOT_DIR);
    test_root.mkdirs();
  }
  
  @AfterEach
  public void after() throws IOException {
    FileUtil.setWritable(test_root, true);
    FileUtil.fullyDelete(test_root);
    assertTrue(!test_root.exists());
  }
  
  public static class XXDF extends DF {
    public XXDF() throws IOException {
      super(test_root, 0L);
    }

    @Override
    protected String[] getExecString() {
      return new String[] { "echo", "IGNORE\n", 
        "/dev/sda3", "453115160", "53037920", "400077240", "11%", "/foo/bar\n"};
    }
  }

  @Test
  @Timeout(value = 5)
  public void testMount() throws Exception {
    XXDF df = new XXDF();
    String expectedMount =
        Shell.WINDOWS ? df.getDirPath().substring(0, 2) : "/foo/bar";
    assertEquals(expectedMount, df.getMount(), "Invalid mount point");
  }

  @Test
  @Timeout(value = 5)
  public void testFileSystem() throws Exception {
    XXDF df = new XXDF();
    String expectedFileSystem =
        Shell.WINDOWS ? df.getDirPath().substring(0, 2) : "/dev/sda3";
    assertEquals(expectedFileSystem, df.getFilesystem(), "Invalid filesystem");
  }

  @Test
  @Timeout(value = 5)
  public void testDFInvalidPath() throws Exception {
    // Generate a path that doesn't exist
    Random random = new Random(0xDEADBEEFl);
    File file = null;
    byte[] bytes = new byte[64];
    while (file == null) {
      random.nextBytes(bytes);
      final String invalid = new String("/" + bytes);
      final File invalidFile = new File(invalid);
      if (!invalidFile.exists()) {
        file = invalidFile;
      }
    }
    DF df = new DF(file, 0l);
    try {
      df.getMount();
    } catch (FileNotFoundException e) {
      // expected, since path does not exist
      GenericTestUtils.assertExceptionContains(file.getName(), e);
    }
  }
  
  @Test
  @Timeout(value = 5)
  public void testDFMalformedOutput() throws Exception {
    DF df = new DF(new File("/"), 0l);
    BufferedReader reader = new BufferedReader(new StringReader(
        "Filesystem     1K-blocks     Used Available Use% Mounted on\n" +
        "/dev/sda5       19222656 10597036   7649060  59% /"));
    df.parseExecResult(reader);
    df.parseOutput();
    
    reader = new BufferedReader(new StringReader(
        "Filesystem     1K-blocks     Used Available Use% Mounted on"));
    df.parseExecResult(reader);
    try {
      df.parseOutput();
      fail("Expected exception with missing line!");
    } catch (IOException e) {
      GenericTestUtils.assertExceptionContains(
          "Fewer lines of output than expected", e);
      System.out.println(e.toString());
    }
    
    reader = new BufferedReader(new StringReader(
        "Filesystem     1K-blocks     Used Available Use% Mounted on\n" +
        " "));
    df.parseExecResult(reader);
    try {
      df.parseOutput();
      fail("Expected exception with empty line!");
    } catch (IOException e) {
      GenericTestUtils.assertExceptionContains("Unexpected empty line", e);
      System.out.println(e.toString());
    }
    
    reader = new BufferedReader(new StringReader(
        "Filesystem     1K-blocks     Used Available Use% Mounted on\n" +
        "       19222656 10597036   7649060  59% /"));
    df.parseExecResult(reader);
    try {
      df.parseOutput();
      fail("Expected exception with missing field!");
    } catch (IOException e) {
      GenericTestUtils.assertExceptionContains("Could not parse line: ", e);
      System.out.println(e.toString());
    }
  }

  @Test
  @Timeout(value = 5)
  public void testGetMountCurrentDirectory() throws Exception {
    File currentDirectory = new File(".");
    String workingDir = currentDirectory.getAbsoluteFile().getCanonicalPath();
    DF df = new DF(new File(workingDir), 0L);
    String mountPath = df.getMount();
    File mountDir = new File(mountPath);
    assertTrue(mountDir.exists(), "Mount dir ["+mountDir.getAbsolutePath()+"] should exist.");
    assertTrue(mountDir.isDirectory(),
        "Mount dir ["+mountDir.getAbsolutePath()+"] should be directory.");
    assertTrue(workingDir.startsWith(mountPath),
        "Working dir ["+workingDir+"] should start with ["+mountPath+"].");
  }
}

