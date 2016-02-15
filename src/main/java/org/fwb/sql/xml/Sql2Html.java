package org.fwb.sql.xml;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;

import javax.swing.text.html.HTML.Tag;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.fwb.sql.RecordList;
import org.fwb.sql.RecordList.StringRecordList;
import org.fwb.xml.sax.SaxUtil;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.SAXException;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

/**
 * utility to convert relational sql results (jdbc ResultSet) into html-style, strict XML data dump.
 * 
 * TODO use SNAX?
 * TODO rather than a boolean 'header' i think there should be two alternative methods: toTable and toTBody
 * TODO ditto for Sql2HtmlXsl
 * 
 * @see Sql2TableXsl
 */
public class Sql2Html {
	/** @deprecated static utilities only */
	@Deprecated
	private Sql2Html() { }
	
	/**
	 * unbelievable that these constants don't exist,
	 * and protected constructors require strict subclasses :(
	 */
	static final Tag
		THEAD = new Tag("thead") { },
		TBODY = new Tag("tbody") { };
	
	static final String
		ATT_TITLE = "title";
	
	/**
	 * @return	the number of data records written (not including header)
	 * @throws SQLException thrown by {@code rs}
	 * @throws SAXException thrown by {@code sax}
	 */
	public static final int toTable(
			ResultSet rs, ContentHandler sax, boolean header)
			throws SAXException, SQLException {
		return toTable(rs, sax, header,
				new StringRecordList(rs));
	}
	
	/**
	 * @return	the number of data records written (not including header)
	 * @throws SQLException thrown by {@code rs}
	 * @throws SAXException thrown by {@code sax}
	 */
	public static final int toTable(
			ResultSet rs, ContentHandler sax, boolean header, Function<Object, String> serializer)
			throws SAXException, SQLException {
		return toTable(rs, sax, header,
				Collections2.transform(new RecordList<Object>(rs), serializer));
	}
	
	/**
	 * @param rs merely used for iteration
	 * @param view the view of record values to be serialized
	 */
	private static final int toTable(
			ResultSet rs, ContentHandler sax, boolean header, Collection<String> view)
			throws SAXException, SQLException {
		
		start(sax, Tag.TABLE);
			if (header) {
				start(sax, THEAD);
					header(sax, rs.getMetaData());
				end(sax, THEAD);
			}
			
			start(sax, TBODY);
				int retVal;
				for (retVal = 0; rs.next(); ++retVal)
					record(sax, view);
			end(sax, TBODY);
		end(sax, Tag.TABLE);
		
		return retVal;
	}
	
	/**
	 * writes out a header row, including SQL types as "type" attribute
	 */
	private static void header(ContentHandler sax, ResultSetMetaData rsmd) throws SQLException, SAXException {
		start(sax, Tag.TR);
			char[] s;
			AttributesImpl a;
			for (int i = 0; i < rsmd.getColumnCount(); ++i) {
				a = new AttributesImpl();
				s = rsmd.getColumnName(i+1).toCharArray();
				a.addAttribute(XMLConstants.NULL_NS_URI, ATT_TITLE, ATT_TITLE, SaxUtil.CDATA, rsmd.getColumnTypeName(i+1));
				sax.startElement(XMLConstants.NULL_NS_URI, Tag.TH.toString(), Tag.TH.toString(), a);
				sax.characters(s, 0, s.length);
				end(sax, Tag.TD);
			}
		end(sax, Tag.TR);
	}
	/**
	 * writes out a record
	 */
	private static void record(ContentHandler sax, Collection<String> ls) throws SAXException {
		start(sax, Tag.TR);
			for (String s : ls) {
				start(sax, Tag.TD);
				if (s != null)
					sax.characters(s.toCharArray(), 0, s.length());
				end(sax, Tag.TD);
			}
		end(sax, Tag.TR);
	}
	
	/*
	 * shortcuts for empty namespace and attributes.
	 * n.b. for historical reasons this class maintains independence from SNAX.
	 * this section is a mini re-implementation of SimpleContentHandler.
	 */
	/** @see org.fwb.xml.sax.snax.SimpleContentHandler#startElement(String) */
	static void start(ContentHandler sax, Tag t) throws SAXException {
		sax.startElement(XMLConstants.NULL_NS_URI, t.toString(), t.toString(), SaxUtil.EMPTY_ATTS);
	}
	/** @see org.fwb.xml.sax.snax.SimpleContentHandler#endElement(String) */
	static void end(ContentHandler sax, Tag t) throws SAXException {
		sax.endElement(XMLConstants.NULL_NS_URI, t.toString(), t.toString());
	}
	
	/**
	 * consider this alternative XSL-based approach.
	 * 
	 * the direct approach ({@link Sql2Html}) should be preferred.
	 * 
	 * however, this is helpful for test-confirmation
	 * and use-cases which already employ a transformation pipeline.
	 */
	public static class Sql2HtmlXsl {
		/** @deprecated static utilities only */
		@Deprecated
		private Sql2HtmlXsl() { }
		
		static final SAXTransformerFactory STF = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
		
		public static final Templates XML2TABLE;
		static {
			try {
				XML2TABLE = STF.newTemplates(new StreamSource(Sql2Xml.class.getResource("xml-to-table.xsl").toExternalForm()));
			} catch (TransformerConfigurationException e) {
				throw new RuntimeException(e);
			}
		}
		
		public static final void toTableXsl(
				ResultSet rs, Result r, boolean header, Function<Object, String> serializer)
				throws TransformerConfigurationException, SQLException, SAXException {
			TransformerHandler th = STF.newTransformerHandler(XML2TABLE);	// TransformerConfigurationException
			th.setResult(r);
			th.getTransformer().setParameter("header", header);
			th.startDocument();
			Sql2Xml.toXmlFlat(rs, th, serializer);
			th.endDocument();
		}
	}
}
