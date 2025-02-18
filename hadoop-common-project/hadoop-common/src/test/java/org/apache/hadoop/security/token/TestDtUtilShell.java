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
package org.apache.hadoop.security.token;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.test.GenericTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;

public class TestDtUtilShell {
  private static byte[] IDENTIFIER = {
      0x69, 0x64, 0x65, 0x6e, 0x74, 0x69, 0x66, 0x69, 0x65, 0x72};
  private static byte[] PASSWORD = {
      0x70, 0x61, 0x73, 0x73, 0x77, 0x6f, 0x72, 0x64};
  private static Text KIND = new Text("testTokenKind");
  private static Text SERVICE = new Text("testTokenService");
  private static Text SERVICE2 = new Text("ecivreSnekoTtset");
  private static Configuration defaultConf = new Configuration();
  private static FileSystem localFs = null;
  private final String alias = "proxy_ip:1234";
  private final String getUrl = SERVICE_GET.toString() + "://localhost:9000/";
  private final String getUrl2 = "http://localhost:9000/";
  public static Text SERVICE_GET = new Text("testTokenServiceGet");
  public static Text KIND_GET = new Text("testTokenKindGet");
  public static Token<?> MOCK_TOKEN =
      new Token(IDENTIFIER, PASSWORD, KIND_GET, SERVICE_GET);

  private static final Text SERVICE_IMPORT =
      new Text("testTokenServiceImport");
  private static final Text KIND_IMPORT = new Text("testTokenKindImport");
  private static final Token<?> IMPORT_TOKEN =
      new Token(IDENTIFIER, PASSWORD, KIND_IMPORT, SERVICE_IMPORT);

  static {
    try {
      defaultConf.set("fs.defaultFS", "file:///");
      localFs = FileSystem.getLocal(defaultConf);
    } catch (IOException e) {
      throw new RuntimeException("init failure", e);
    }
  }

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final Path workDir = new Path(
      GenericTestUtils.getTestDir("TestDtUtilShell").getAbsolutePath());
  private final Path tokenFile = new Path(workDir, "testPrintTokenFile");
  private final Path tokenFile2 = new Path(workDir, "testPrintTokenFile2");
  private final Path tokenLegacyFile = new Path(workDir, "testPrintTokenFile3");
  private final Path tokenFileGet = new Path(workDir, "testGetTokenFile");
  private final Path tokenFileImport = new Path(workDir, "testImportTokenFile");
  private final String tokenFilename = tokenFile.toString();
  private final String tokenFilename2 = tokenFile2.toString();
  private final String tokenFilenameGet = tokenFileGet.toString();
  private final String tokenFilenameImport = tokenFileImport.toString();
  private String[] args = null;
  private DtUtilShell dt = null;
  private int rc = 0;

  @BeforeEach
  public void setup() throws Exception {
    localFs.mkdirs(localFs.makeQualified(workDir));
    makeTokenFile(tokenFile, false, null);
    makeTokenFile(tokenFile2, false, SERVICE2);
    makeTokenFile(tokenLegacyFile, true, null);
    dt = new DtUtilShell();
    dt.setConf(new Configuration());
    dt.setOut(new PrintStream(outContent));
    outContent.reset();
    rc = 0;
  }

  @AfterEach
  public void teardown() throws Exception {
    localFs.delete(localFs.makeQualified(workDir), true);
  }

  public void makeTokenFile(Path tokenPath, boolean legacy, Text service)
        throws IOException {
    if (service == null) {
      service = SERVICE;
    }
    Credentials creds = new Credentials();
    Token<? extends TokenIdentifier> tok = (Token<? extends TokenIdentifier>)
        new Token(IDENTIFIER, PASSWORD, KIND, service);
    creds.addToken(tok.getService(), tok);
    Credentials.SerializedFormat format =
        Credentials.SerializedFormat.PROTOBUF;
    if (legacy) {
      format = Credentials.SerializedFormat.WRITABLE;
    }
    creds.writeTokenStorageFile(tokenPath, defaultConf, format);
  }

  @Test
  public void testPrint() throws Exception {
    args = new String[] {"print", tokenFilename};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple print exit code");
    assertTrue(outContent.toString().contains(KIND.toString()),
        "test simple print output kind:\n" + outContent.toString());
    assertTrue(outContent.toString().contains(SERVICE.toString()),
        "test simple print output service:\n" + outContent.toString());

    outContent.reset();
    args = new String[] {"print", tokenLegacyFile.toString()};
    rc = dt.run(args);
    assertEquals(0, rc, "test legacy print exit code");
    assertTrue(outContent.toString().contains(KIND.toString()),
        "test simple print output kind:\n" + outContent.toString());
    assertTrue(outContent.toString().contains(SERVICE.toString()),
        "test simple print output service:\n" + outContent.toString());

    outContent.reset();
    args = new String[] {
        "print", "-alias", SERVICE.toString(), tokenFilename};
    rc = dt.run(args);
    assertEquals(0, rc, "test alias print exit code");
    assertTrue(outContent.toString().contains(KIND.toString()),
        "test simple print output kind:\n" + outContent.toString());
    assertTrue(outContent.toString().contains(SERVICE.toString()),
        "test simple print output service:\n" + outContent.toString());

    outContent.reset();
    args = new String[] {
        "print", "-alias", "not-a-serivce", tokenFilename};
    rc = dt.run(args);
    assertEquals(0, rc, "test no alias print exit code");
    assertFalse(outContent.toString().contains(KIND.toString()),
        "test no alias print output kind:\n" + outContent.toString());
    assertFalse(outContent.toString().contains(SERVICE.toString()),
        "test no alias print output service:\n" + outContent.toString());
  }

  @Test
  public void testEdit() throws Exception {
    String oldService = SERVICE2.toString();
    String newAlias = "newName:12345";
    args = new String[] {"edit",
        "-service", oldService, "-alias", newAlias, tokenFilename2};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple edit exit code");
    args = new String[] {"print", "-alias", oldService, tokenFilename2};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple edit print old exit code");
    assertTrue(outContent.toString().contains(KIND.toString()),
        "test simple edit output kind old:\n" + outContent.toString());
    assertTrue(outContent.toString().contains(oldService),
        "test simple edit output service old:\n" + outContent.toString());
    args = new String[] {"print", "-alias", newAlias, tokenFilename2};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple edit print new exit code");
    assertTrue(outContent.toString().contains(KIND.toString()),
        "test simple edit output kind new:\n" + outContent.toString());
    assertTrue(outContent.toString().contains(newAlias),
        "test simple edit output service new:\n" + outContent.toString());
  }

  @Test
  public void testAppend() throws Exception {
    args = new String[] {"append", tokenFilename, tokenFilename2};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple append exit code");
    args = new String[] {"print", tokenFilename2};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple append print exit code");
    assertTrue(outContent.toString().contains(KIND.toString()),
        "test simple append output kind:\n" + outContent.toString());
    assertTrue(outContent.toString().contains(SERVICE.toString()),
        "test simple append output service:\n" + outContent.toString());
    assertTrue(outContent.toString().contains(SERVICE2.toString()),
        "test simple append output service:\n" + outContent.toString());
  }

  @Test
  public void testRemove() throws Exception {
    args = new String[] {"remove", "-alias", SERVICE.toString(), tokenFilename};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple remove exit code");
    args = new String[] {"print", tokenFilename};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple remove print exit code");
    assertFalse(outContent.toString().contains(KIND.toString()),
        "test simple remove output kind:\n" + outContent.toString());
    assertFalse(outContent.toString().contains(SERVICE.toString()),
        "test simple remove output service:\n" + outContent.toString());
  }

  @Test
  public void testGet() throws Exception {
    args = new String[] {"get", getUrl, tokenFilenameGet};
    rc = dt.run(args);
    assertEquals(0, rc, "test mocked get exit code");
    args = new String[] {"print", tokenFilenameGet};
    rc = dt.run(args);
    String oc = outContent.toString();
    assertEquals(0, rc, "test print after get exit code");
    assertTrue(oc.contains(KIND_GET.toString()),
        "test print after get output kind:\n" + oc);
    assertTrue(oc.contains(SERVICE_GET.toString()),
        "test print after get output service:\n" + oc);
  }

  @Test
  public void testGetWithServiceFlag() throws Exception {
    args = new String[] {"get", getUrl2, "-service", SERVICE_GET.toString(),
                         tokenFilenameGet};
    rc = dt.run(args);
    assertEquals(0, rc, "test mocked get with service flag exit code");
    args = new String[] {"print", tokenFilenameGet};
    rc = dt.run(args);
    String oc = outContent.toString();
    assertEquals(0, rc, "test print after get with service flag exit code");
    assertTrue(oc.contains(KIND_GET.toString()),
        "test print after get with service flag output kind:\n" + oc);
    assertTrue(oc.contains(SERVICE_GET.toString()),
        "test print after get with service flag output service:\n" + oc);
  }

  @Test
  public void testGetWithAliasFlag() throws Exception {
    args = new String[] {"get", getUrl, "-alias", alias, tokenFilenameGet};
    rc = dt.run(args);
    assertEquals(0, rc, "test mocked get with alias flag exit code");
    args = new String[] {"print", tokenFilenameGet};
    rc = dt.run(args);
    String oc = outContent.toString();
    assertEquals(0, rc, "test print after get with alias flag exit code");
    assertTrue(oc.contains(KIND_GET.toString()),
        "test print after get with alias flag output kind:\n" + oc);
    assertTrue(oc.contains(alias),
        "test print after get with alias flag output alias:\n" + oc);
    assertFalse(oc.contains(SERVICE_GET.toString()),
        "test print after get with alias flag output old service:\n" + oc);
  }

  @Test
  public void testFormatJavaFlag() throws Exception {
    args = new String[] {"get", getUrl, "-format", "java", tokenFilenameGet};
    rc = dt.run(args);
    assertEquals(0, rc, "test mocked get with java format flag exit code");
    Credentials creds = new Credentials();
    Credentials spyCreds = Mockito.spy(creds);
    DataInputStream in = new DataInputStream(
        new FileInputStream(tokenFilenameGet));
    spyCreds.readTokenStorageStream(in);
    Mockito.verify(spyCreds).readFields(in);
  }

  @Test
  public void testFormatProtoFlag() throws Exception {
    args = new String[] {
        "get", getUrl, "-format", "protobuf", tokenFilenameGet};
    rc = dt.run(args);
    assertEquals(0, rc, "test mocked get with protobuf format flag exit code");
    Credentials creds = new Credentials();
    Credentials spyCreds = Mockito.spy(creds);
    DataInputStream in = new DataInputStream(
        new FileInputStream(tokenFilenameGet));
    spyCreds.readTokenStorageStream(in);
    Mockito.verify(spyCreds, Mockito.never()).readFields(in);
  }

  @Test
  public void testImport() throws Exception {
    String base64 = IMPORT_TOKEN.encodeToUrlString();
    args = new String[] {"import", base64, tokenFilenameImport};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple import print old exit code");

    args = new String[] {"print", tokenFilenameImport};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple import print old exit code");
    assertTrue(outContent.toString().contains(KIND_IMPORT.toString()),
        "test print after import output:\n" + outContent);
    assertTrue(outContent.toString().contains(SERVICE_IMPORT.toString()),
        "test print after import output:\n" + outContent);
    assertTrue(outContent.toString().contains(base64),
        "test print after simple import output:\n" + outContent);
  }

  @Test
  public void testImportWithAliasFlag() throws Exception {
    String base64 = IMPORT_TOKEN.encodeToUrlString();
    args = new String[] {"import", base64, "-alias", alias,
        tokenFilenameImport};
    rc = dt.run(args);
    assertEquals(0, rc, "test import with alias print old exit code");

    args = new String[] {"print", tokenFilenameImport};
    rc = dt.run(args);
    assertEquals(0, rc, "test simple import print old exit code");
    assertTrue(outContent.toString().contains(KIND_IMPORT.toString()),
        "test print after import output:\n" + outContent);
    assertTrue(outContent.toString().contains(alias),
        "test print after import with alias output:\n" + outContent);
  }
}
