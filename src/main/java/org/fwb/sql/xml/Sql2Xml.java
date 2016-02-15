package org.fwb.sql.xml;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedList;

import org.fwb.sql.HeaderList;
import org.fwb.sql.HeaderList.HeaderTypeList;
import org.fwb.sql.RecordList;
import org.fwb.sql.RecordList.StringRecordList;
import org.fwb.xml.sax.ListAttributes;
import org.fwb.xml.sax.SubAttributes;
import org.fwb.xml.sax.snax.SimpleContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Converts jdbc {@link ResultSet} objects into various XML representations.
 * The three representations supported are 'flat', 'by-level', and 'connect-by-prior'.
 * All representations produce a root element named {@link #TAG_RESULTSET}.
 * All representations produce a uniform element for each record,
 * having tag-name {@link #TAG_RECORD} and an attribute for each column name and value.
 * The 'flat' approach merely lists those within the root.
 * The 'by-level' approach uses (record-order and)  an integer column
 * to determine nested depth of any record (within previous, lower-depth records).
 * 
 * The 'connect-by-prior' approach borrows terminology from Oracle's DB,
 * and is likely to be most useful on results of such (Oracle c-b-p) queries.
 * It uses record (order and) identifier column and parent-identifier column
 * to nest records as children of their parents.
 * 
 * @see GroupingFilter for another approach 
 * 
 * TODO improve/formalize documentation for the by-level and connect-by-prior
 * 
 * TODO this class' xml-structuring capabilities should apply to more general constructs than RS
 * (i.e. as GroupingFilter works at the pure XML-level too, this class could serialize sequences of non-rs tuples too)
 */
public class Sql2Xml {
	/** @deprecated static utilities only */
	@Deprecated
	private Sql2Xml() { }
	
	static final Logger LOG = LoggerFactory.getLogger(Sql2Xml.class);
	
	static final String
		TAG_RESULTSET = "rs",
		TAG_RECORD = "r";
	
	/** Attributes whose names are the field names and whose values are the field type-names */
	public static Attributes getRsmdAttributes(ResultSetMetaData rsmd) {
		return new ListAttributes(
				new HeaderList(rsmd),
				new HeaderTypeList(rsmd));
	}
	/** Attributes whose names are the field names and whose values are the field string-values */
	public static Attributes getStringRecordAttributes(ResultSet rs) {
		return new ListAttributes(
				new HeaderList(rs),
				new StringRecordList(rs));
	}
	/** Attributes whose names are the field names and whose values are the transformed field values */
	public static Attributes getTransformedAttributes(ResultSet rs, Function<Object, String> serializer) {
		return new ListAttributes(
				new HeaderList(rs),
				Lists.transform(
						new RecordList<Object>(rs),
						serializer));
	}
	
	public static final void toXmlFlat(
			ResultSet rs, ContentHandler ch, Function<Object, String> serializer)
			throws SQLException, SAXException {
		SimpleContentHandler sch = SimpleContentHandler.of(ch);
		Attributes a = getRsmdAttributes(rs.getMetaData());
		sch.startElement(TAG_RESULTSET, a);
			a = serializer == null
					? getStringRecordAttributes(rs)
					: getTransformedAttributes(rs, serializer);
			while (rs.next())
				sch.emptyElement(TAG_RECORD, a);
		sch.endElement(TAG_RESULTSET);
	}
	
	/**
	 * uses the first column to determine record depth-level,
	 * and suppresses said column from the output.
	 * 
	 * @see #toXmlByLevel(ResultSet, SimpleContentHandler, int)
	 */
	public static final void toXmlByLevel(
			ResultSet rs, ContentHandler ch)
			throws SAXException, SQLException {
		toXmlByLevel(rs, ch, -1);
	}
	/**
	 * SQL to XML "By Level."
	 * Generates uniform elements
	 * whose (arbitrary) tree structure is determined by (record-order, along with)
	 * column-defined record depth-level.
	 * 
	 * @param levelColumn	the ResultSet column index (1-based) which holds "depth level".
	 * 		if this number is negative, it and all columns left of it are suppressed from the XML output (attributes).
	 */
	public static final void toXmlByLevel(
			ResultSet rs, ContentHandler ch, int levelColumn)
			throws SAXException, SQLException {
		SimpleContentHandler sch = SimpleContentHandler.of(ch);
		
		Preconditions.checkArgument(0 != levelColumn,
				"levelColumn must be non-zero");
		
		Attributes atts = getRsmdAttributes(rs.getMetaData());
		if (0 > levelColumn)
			atts = new SubAttributes(atts, - levelColumn);
		sch.startElement(TAG_RESULTSET, atts);
			
			atts = getStringRecordAttributes(rs);
			if (0 > levelColumn)
				atts = new SubAttributes(atts, - levelColumn);
			
			int depth = 0;
			while (rs.next()) {
				int newDepth = rs.getInt(Math.abs(levelColumn));
				
				for (; depth >= newDepth; -- depth)
					sch.endElement(TAG_RECORD);
				
				// add 1 depth for this record
				++ depth;
				
				// failsafe: add empty records to target depth higher than current+1
				// rather than force-fail as the disabled line below would do
				for (; depth < newDepth; ++ depth)
					sch.startElement(TAG_RECORD);
//				Preconditions.checkArgument(depth == newDepth)
				
				sch.startElement(TAG_RECORD, atts);
			}
			while (0 < depth --)
				sch.endElement(TAG_RECORD);
		sch.endElement(TAG_RESULTSET);
	}
	
	/**
	 * uses the first column to determine parent id, suppressing it,
	 * and uses the second column to determine record id (not suppressed).
	 */
	public static final void toXmlConnectByPrior(
			ResultSet rs, ContentHandler ch)
			throws SAXException, SQLException {
		toXmlConnectByPrior(rs, ch, -1, 2);
	}
	/**
	 * SQL to XML Tree "Connect-by-Prior:"
	 * Generates uniform elements
	 * whose (arbitrary) tree structure is determined by (record order, along with)
	 * each record's "id" column, and "parent" column.
	 * 
	 * 
	 * if either column index provided is negative, that many columns (absolute value) from the left will be suppressed from the XML output (attributes).
	 * @param idColumn		column index (1-based) holding the "id" for a given record, this will be matched against any child record's "parent" field
	 * @param parentColumn	column index (1-based) holding the "parent id" for a given record, this will be matched against any parent record's "id" field
	 */
	public static final void toXmlConnectByPrior(
			ResultSet rs, ContentHandler ch, int parentColumn, int idColumn)
			throws SAXException, SQLException {
		SimpleContentHandler sch = SimpleContentHandler.of(ch);
		
		Preconditions.checkArgument(0 != parentColumn,
				"parentColumn must be non-zero");
		Preconditions.checkArgument(0 < idColumn,
				"idColumn (%s) must be positive", idColumn);
		
		Attributes atts = getRsmdAttributes(rs.getMetaData());
		if (0 > parentColumn)
			atts = new SubAttributes(atts, - parentColumn);
		sch.startElement(TAG_RESULTSET, atts);
			atts = getStringRecordAttributes(rs);
			LinkedList<String> stack = new LinkedList<String>();
			while (rs.next()) {
				while ((! stack.isEmpty())
						&& (! rs.getString(parentColumn).equals(stack.getLast()))) {
					stack.removeLast();
					sch.endElement(TAG_RECORD);
				}
				
				sch.startElement(TAG_RECORD, atts);
				stack.add(rs.getString(idColumn));
			}
			for (int i = 0; i < stack.size(); ++i)
				sch.endElement(TAG_RECORD);
		sch.endElement(TAG_RESULTSET);
	}
}
