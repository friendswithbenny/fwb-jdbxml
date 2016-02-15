package test.fwb.sql.xml;

import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.fwb.sql.xml.GroupingFilter;
import org.fwb.xml.sax.SaxUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;

/**
 * unit test only exercises the transform xml-to-xml
 */
public class TestGroupingFilter {
	static final Logger LOG = LoggerFactory.getLogger(TestGroupingFilter.class);
	static final String RESOURCE_PATTERN = "TestGroupingFilter.%s.xml";
	static final InputSource FLAT = new InputSource(resource("flat").toExternalForm());
	
	static Boolean wsStack;
	@BeforeClass
	public static void setUpClass() {
		wsStack = XMLUnit.getIgnoreWhitespace();
		XMLUnit.setIgnoreWhitespace(true);
	}
	@AfterClass
	public static void tearDownClass() {
		XMLUnit.setIgnoreAttributeOrder(wsStack);
		wsStack = null;
	}
	
	@Test
	public void test_1() throws Exception {
		StringWriter sw = new StringWriter();
		GroupingFilter.toGrouping(
				FLAT,
				SaxUtil.createContentHandler(sw),
				Collections.singletonList(1));
		assertResourceEqualsXml("group.1", sw.toString());
	}
	
	@Test
	public void test_1_1() throws Exception {
		StringWriter sw = new StringWriter();
		GroupingFilter.toGrouping(
				FLAT,
				SaxUtil.createContentHandler(sw),
				Arrays.asList(1, 1));
		assertResourceEqualsXml("group.1.1", sw.toString());
	}
	
	@Test
	public void test_2() throws Exception {
		StringWriter sw = new StringWriter();
		GroupingFilter.toGrouping(
				FLAT,
				SaxUtil.createContentHandler(sw),
				Collections.singletonList(2));
		assertResourceEqualsXml("group.2", sw.toString());
	}
	
	static void assertResourceEqualsXml(String resourceName, String xml) throws SAXException, IOException {
		LOG.debug("checking: {}", xml);
		XMLAssert.assertXMLEqual(
				Resources.toString(
						resource(resourceName),
						Charsets.UTF_8),
				xml);
	}
	static URL resource(String name) {
		URL retVal = TestGroupingFilter.class.getResource(
				String.format(RESOURCE_PATTERN, name));
		Preconditions.checkArgument(null != retVal,
				"no such resource: %s", name);
		return retVal;
	}
}
