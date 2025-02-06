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

package org.apache.hadoop.mapreduce.v2.app.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.http.JettyUtils;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.v2.api.records.JobId;
import org.apache.hadoop.mapreduce.v2.app.AppContext;
import org.apache.hadoop.mapreduce.v2.app.MockAppContext;
import org.apache.hadoop.mapreduce.v2.app.job.Job;
import org.apache.hadoop.mapreduce.v2.util.MRApps;
import org.apache.hadoop.util.XMLUtils;
import org.apache.hadoop.yarn.webapp.GenericExceptionHandler;
import org.apache.hadoop.yarn.webapp.JerseyTestBase;
import org.apache.hadoop.yarn.webapp.WebServicesTestUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.jettison.JettisonFeature;
import org.glassfish.jersey.server.ResourceConfig;
/**
 * Test the app master web service Rest API for getting the job conf. This
 * requires created a temporary configuration file.
 *
 *   /ws/v1/mapreduce/job/{jobid}/conf
 */
public class TestAMWebServicesJobConf extends JerseyTestBase {

  private static Configuration conf = new Configuration();
  private static AppContext appContext;

  private static File testConfDir = new File("target",
      TestAMWebServicesJobConf.class.getSimpleName() + "confDir");

  @Override
  protected Application configure() {
    ResourceConfig config = new ResourceConfig();
    config.register(new JerseyBinder());
    config.register(AMWebServices.class);
    config.register(GenericExceptionHandler.class);
    config.register(JAXBContextResolver.class);
    config.register(new JettisonFeature());
    return config;
  }

  private static class JerseyBinder extends AbstractBinder {
    @Override
    protected void configure() {

      Path confPath = new Path(testConfDir.toString(), MRJobConfig.JOB_CONF_FILE);
      Configuration config = new Configuration();

      FileSystem localFs;
      try {
        localFs = FileSystem.getLocal(config);
        confPath = localFs.makeQualified(confPath);

        OutputStream out = localFs.create(confPath);
        try {
          conf.writeXml(out);
        } finally {
          out.close();
        }
        if (!localFs.exists(confPath)) {
          fail("error creating config file: " + confPath);
        }

      } catch (IOException e) {
        fail("error creating config file: " + e.getMessage());
      }

      appContext = new MockAppContext(0, 2, 1, confPath);
      App app = new App(appContext);
      bind(appContext).to(AppContext.class).named("am");
      bind(app).to(App.class).named("app");
      bind(conf).to(Configuration.class).named("conf");
      final HttpServletResponse response = mock(HttpServletResponse.class);
      final HttpServletRequest request = mock(HttpServletRequest.class);
      bind(response).to(HttpServletResponse.class);
      bind(request).to(HttpServletRequest.class);
    }
  }


  @BeforeAll
  public static void setTestConfDir() throws Exception {
    testConfDir.mkdir();
  }

  @AfterAll
  static public void stop() {
    FileUtil.fullyDelete(testConfDir);
  }

  @Test
  public void testJobConf() throws Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);

      Response response = r.path("ws").path("v1").path("mapreduce")
          .path("jobs").path(jobId).path("conf")
          .request(MediaType.APPLICATION_JSON).get(Response.class);
      assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
          response.getMediaType().toString());
      JSONObject json = response.readEntity(JSONObject.class);
      assertEquals(1, json.length(), "incorrect number of elements");
      JSONObject info = json.getJSONObject("conf");
      verifyAMJobConf(info, jobsMap.get(id));
    }
  }

  @Test
  public void testJobConfSlash() throws Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);
      Response response = r.path("ws").path("v1").path("mapreduce")
          .path("jobs").path(jobId).path("conf/")
          .request(MediaType.APPLICATION_JSON).get(Response.class);
      assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
          response.getMediaType().toString());
      JSONObject json = response.readEntity(JSONObject.class);
      assertEquals(1, json.length(), "incorrect number of elements");
      JSONObject info = json.getJSONObject("conf");
      verifyAMJobConf(info, jobsMap.get(id));
    }
  }

  @Test
  public void testJobConfDefault() throws Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);

      Response response = r.path("ws").path("v1").path("mapreduce")
          .path("jobs").path(jobId).path("conf").request().get(Response.class);
      assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
          response.getMediaType().toString());
      JSONObject json = response.readEntity(JSONObject.class);
      assertEquals(1, json.length(), "incorrect number of elements");
      JSONObject info = json.getJSONObject("conf");
      verifyAMJobConf(info, jobsMap.get(id));
    }
  }

  @Test
  public void testJobConfXML() throws Exception {
    WebTarget r = target();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);

      Response response = r.path("ws").path("v1").path("mapreduce")
          .path("jobs").path(jobId).path("conf")
          .request(MediaType.APPLICATION_XML).get(Response.class);
      assertEquals(MediaType.APPLICATION_XML_TYPE + ";" + JettyUtils.UTF_8,
          response.getMediaType().toString());
      String xml = response.readEntity(String.class);
      DocumentBuilderFactory dbf = XMLUtils.newSecureDocumentBuilderFactory();
      DocumentBuilder db = dbf.newDocumentBuilder();
      InputSource is = new InputSource();
      is.setCharacterStream(new StringReader(xml));
      Document dom = db.parse(is);
      NodeList info = dom.getElementsByTagName("conf");
      verifyAMJobConfXML(info, jobsMap.get(id));
    }
  }

  public void verifyAMJobConf(JSONObject info, Job job) throws JSONException {

    assertEquals(2, info.length(), "incorrect number of elements");

    WebServicesTestUtils.checkStringMatch("path", job.getConfFile().toString(),
        info.getString("path"));
    // just do simple verification of fields - not data is correct
    // in the fields
    JSONArray properties = info.getJSONArray("property");
    for (int i = 0; i < properties.length(); i++) {
      JSONObject prop = properties.getJSONObject(i);
      String name = prop.getString("name");
      String value = prop.getString("value");
      assertTrue((name != null && !name.isEmpty()), "name not set");
      assertTrue((value != null && !value.isEmpty()), "value not set");
    }
  }

  public void verifyAMJobConfXML(NodeList nodes, Job job) {

    assertEquals(1, nodes.getLength(), "incorrect number of elements");

    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      WebServicesTestUtils.checkStringMatch("path", job.getConfFile()
          .toString(), WebServicesTestUtils.getXmlString(element, "path"));

      // just do simple verification of fields - not data is correct
      // in the fields
      NodeList properties = element.getElementsByTagName("property");

      for (int j = 0; j < properties.getLength(); j++) {
        Element property = (Element) properties.item(j);
        assertNotNull(property, "should have counters in the web service info");
        String name = WebServicesTestUtils.getXmlString(property, "name");
        String value = WebServicesTestUtils.getXmlString(property, "value");
        assertTrue((name != null && !name.isEmpty()), "name not set");
        assertTrue((value != null && !value.isEmpty()), "name not set");
      }
    }
  }

}
