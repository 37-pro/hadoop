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

package org.apache.hadoop.mapred;

import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.hadoop.util.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestQueueConfigurationParser {
/**
 * test xml generation 
 * @throws ParserConfigurationException
 * @throws Exception 
 */
  @Test
  @Timeout(value = 5)
  public void testQueueConfigurationParser()
      throws ParserConfigurationException, Exception {
    JobQueueInfo info = new JobQueueInfo("root", "rootInfo");
    JobQueueInfo infoChild1 = new JobQueueInfo("child1", "child1Info");
    JobQueueInfo infoChild2 = new JobQueueInfo("child2", "child1Info");

    info.addChild(infoChild1);
    info.addChild(infoChild2);
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
        .newInstance();
    DocumentBuilder builder = docBuilderFactory.newDocumentBuilder();
    
    
    Document document = builder.newDocument();
    

    // test QueueConfigurationParser.getQueueElement 
    Element e = QueueConfigurationParser.getQueueElement(document, info);
    // transform result to string for check
    DOMSource domSource = new DOMSource(e);
    StringWriter writer = new StringWriter();
    StreamResult result = new StreamResult(writer);
    TransformerFactory tf = XMLUtils.newSecureTransformerFactory();
    Transformer transformer = tf.newTransformer();
    transformer.transform(domSource, result);
    String str= writer.toString();
    assertTrue(str
        .endsWith("<queue><name>root</name><properties/><state>running</state><queue><name>child1</name><properties/><state>running</state></queue><queue><name>child2</name><properties/><state>running</state></queue></queue>"));
  }
}
