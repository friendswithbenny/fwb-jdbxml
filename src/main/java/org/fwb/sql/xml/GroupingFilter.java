package org.fwb.sql.xml;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;

import org.fwb.xml.sax.AttributesList;
import org.fwb.xml.sax.SaxUtil;
import org.fwb.xml.sax.SubAttributes;
import org.fwb.xml.sax.snax.SimpleContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.XMLFilterImpl;

import com.google.common.base.Function;

/**
 * This class converts from the "flat record" xml format produced by
 * {@link Sql2Xml#toXmlFlat(ResultSet, ContentHandler, Function)}
 * into a further-segmented (in some ways "compressed") equivalent format.
 * 
 * In particular, instead of a flat set of single records,
 * a "grouping" is provided giving fixed depth to the result tree,
 * nested {@link #TAG_GROUP} tags are generated for each element 'N' in {@link #GROUPING},
 * each with the N next data values (in field-order) as attributes.
 * Whenever possible, these "grouping" tags are shared by children,
 * i.e. if consecutive records have the same values for that grouping's fields.
 * 
 * see {@link test.fwb.sql.xml.TestGroupingFilter} for examples.
 * 
 * TODO add namespace support, probably should initialize (constructor) with desired URI and maybe even prefix?
 * TODO nevermind XMLFilterImpl. use ForwardingContentHandler? it'd be a loss of "standard-ness"
 */
public class GroupingFilter extends XMLFilterImpl {
	/**
	 * sugar for
	 * {@link SaxUtil#createContentHandler(File)} and
	 * {@link #toGrouping(ResultSet, ContentHandler, Function, List)}
	 */
	public static void toGrouping(
			ResultSet rs, File f,
			Function<Object, String> serializer,
			List<Integer> grouping)
			throws SQLException, SAXException {
		ContentHandler ch = SaxUtil.createContentHandler(f);
		ch.startDocument();
		toGrouping(rs, ch, serializer, grouping);
		ch.endDocument();
	}
	
	public static void toGrouping(
			ResultSet rs, ContentHandler ch, Function<Object, String> serializer, List<Integer> grouping)
			throws SQLException, SAXException {
		GroupingFilter gf = new GroupingFilter(grouping);
		gf.setContentHandler(ch);
		Sql2Xml.toXmlFlat(rs, new SimpleContentHandler(gf), serializer);
	}
	
	/** xml-to-xml version */
	public static void toGrouping(
			InputSource source, ContentHandler target, List<Integer> grouping)
			throws IOException, SAXException {
		GroupingFilter gf = new GroupingFilter(grouping);
		gf.setParent(SaxUtil.newXMLReader());
		gf.setContentHandler(target);
		gf.parse(source);
	}
	
	static final String
		TAG_GROUP = "g";
	
	final List<Integer> GROUPING;
	final List<List<String>> CACHE;
	
	public GroupingFilter(List<Integer> grouping) {
		GROUPING = grouping;
		CACHE = new ArrayList<List<String>>(grouping.size());
	}
	
	@Override
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		if (Sql2Xml.TAG_RECORD.equalsIgnoreCase(qName))
			atts = startRecord(atts, 0);
		
		super.startElement(uri, localName, qName, atts);
		
		if (Sql2Xml.TAG_RESULTSET.equalsIgnoreCase(qName))
			comment("Grouping" + GROUPING);
	}
	
	/** add comment (if supported) */
	void comment(String comment) throws SAXException {
		ContentHandler ch = getContentHandler();
		if (ch instanceof LexicalHandler)
			((LexicalHandler) ch).comment(comment.toCharArray(), 0, comment.length());
	}
	
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (Sql2Xml.TAG_RESULTSET.equalsIgnoreCase(qName))
			endRecord(0);
		
		super.endElement(uri, localName, qName);
	}
	
	// recursive
	private Attributes startRecord(Attributes atts, int index) throws SAXException {
		if (index < GROUPING.size()) {
			int size = GROUPING.get(index);
			Attributes subAtts = new SubAttributes(atts, 0, size);
			List<String> curr = new AttributesList(subAtts);
			
			if (index < CACHE.size() && ! curr.equals(CACHE.get(index)))
				endRecord(index);
			
			if (index >= CACHE.size()) {
				CACHE.add(new ArrayList<String>(curr));	// snapshot in case atts is modified between calls (which it is, for ResultSets!)
				startElement(XMLConstants.NULL_NS_URI, TAG_GROUP, TAG_GROUP, subAtts);
			}
			
			return startRecord(new SubAttributes(atts, size, atts.getLength()-size), index+1);	// recurse
		} else
			return atts;
	}
	// recursive
	private void endRecord(int index) throws SAXException {
		// to be literal, note the following is "backward"
		// it doesn't matter, because all elements are the same: g (ending, with no attributes)
		if (index < CACHE.size()) {
			CACHE.remove(index);
			endElement(XMLConstants.NULL_NS_URI, TAG_GROUP, TAG_GROUP);
			endRecord(index);	// recurse
		}
	}
}
