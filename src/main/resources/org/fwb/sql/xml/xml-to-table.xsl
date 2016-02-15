<?xml version="1.0" encoding="UTF-8"?>
<!--
an alternative to Sql2Html,
this uses templates to convert xml "resultset" into an HTML table.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- false to build a table with data only -->
	<xsl:param name="header">true</xsl:param>
	
	<!-- root -->
	<xsl:template match="/*">
		<table>
			<xsl:if test="$header">
				<thead>
					<xsl:apply-templates mode="header" select="*[1]" />
				</thead>
			</xsl:if>
			<tbody>
				<xsl:apply-templates />
			</tbody>
		</table>
	</xsl:template>
	
	<!-- records -->
	<xsl:template match="*">
		<tr>
			<xsl:apply-templates select="@*" />
		</tr>
	</xsl:template>
	<xsl:template match="@*">
		<td>
			<xsl:value-of select="." />
		</td>
	</xsl:template>
	
	<!-- header -->
	<xsl:template mode="header" match="*">
		<tr>
			<xsl:apply-templates mode="header" select="@*" />
		</tr>
	</xsl:template>
	<xsl:template mode="header" match="@*">
		<th>
			<xsl:value-of select="name()" />
		</th>
	</xsl:template>
</xsl:stylesheet>