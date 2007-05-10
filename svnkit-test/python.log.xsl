<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="html" indent="yes" standalone="no" omit-xml-declaration="yes" version="4.0" encoding="UTF-8" doctype-public="-//W3C//DTD XHTML 1.0 Transitional//EN" />

<xsl:template match="/">

	<xsl:apply-templates />

</xsl:template>


<xsl:template match="PythonTests">
<html>
<head><title>SVNKit Python Tests Results</title>
</head>
<body>
<table bgcolor="white" width="900" cellpadding="0" align="center" style="height: 100%;"  border="0" cellspacing="0" >
<tr align="center" valign="top" >
<td colspan="1" rowspan="1">

<div class="leftedge" >
<div class="rightedge">


<h1>SVNKit Python Tests Results</h1>
<p>
<xsl:text>Started on </xsl:text><xsl:value-of select="@start" />
<br />
<xsl:apply-templates select="server" mode="anchors" />

	<xsl:apply-templates select="server" mode="tables" />

<xsl:text>Total time elapsed: </xsl:text><xsl:value-of select="@elapsed" />
</p>	
<br />
</div>
</div>
</td>
</tr>
</table>
</body>
</html>
	
</xsl:template>

<xsl:template match="server" mode="tables">

<xsl:choose>
<xsl:when test="@name='svnserve'">
<a name="svnserve"></a>
</xsl:when>
<xsl:when test="@name='file'">
<a name="file"></a>
</xsl:when>
<xsl:otherwise>
<a name="apache"></a>
</xsl:otherwise>
</xsl:choose>

<h4>
<xsl:value-of select="@name" />
<xsl:text>[</xsl:text>
<xsl:value-of select="@url" />
<xsl:text>]</xsl:text>
</h4>
<p>
	<xsl:apply-templates select="suite"/>
</p>

</xsl:template>


<xsl:template match="server" mode="anchors">

<xsl:choose>
<xsl:when test="@name='svnserve'">
Click <a href="#svnserve">here</a> to view the results of the tests run against <b>svnserve</b>.
<br />
</xsl:when>
<xsl:when test="@name='file'">
Click <a href="#file">here</a> to view the results of the tests run against local <b>fsfs</b> repository.
<br />
</xsl:when>
<xsl:otherwise>
Click <a href="#apache">here</a> to view the results of the tests run against <b>apache</b> server.
<br />
</xsl:otherwise>
</xsl:choose>

</xsl:template>


<xsl:template match="suite">

<b style="text-transform: capitalize;">	
	<xsl:value-of select="@name" />
</b>	
<table cellpadding="3" cellspacing="1" border="0" width="100%" bgcolor="#999933">
<tr bgcolor="#E1E1E1" align="left">
<td>Test</td>
<td>Id</td>
<td>Result</td>
</tr>	
	<xsl:apply-templates select="test"/>
</table>
<xsl:text>Total: </xsl:text><xsl:value-of select="@total" />
<xsl:text>, Passed: </xsl:text><xsl:value-of select="@passed" />
<xsl:text>, Failed: </xsl:text><xsl:value-of select="@failed" />
<br />

</xsl:template>


<xsl:template match="test">

<tr bgcolor="white">
<td width="85%">

<xsl:value-of select="@name" />
</td>

<td width="6%">
<xsl:value-of select="@id" />
</td>

<xsl:choose>

<xsl:when test="@result='PASSED'">
<td bgcolor="#99FFAD" width="9%">
<xsl:value-of select="@result" />
</td>
</xsl:when>
<xsl:otherwise>
<td bgcolor="#FF9980" width="9%">
<xsl:value-of select="@result" />
</td>
</xsl:otherwise>
</xsl:choose>

</tr>

</xsl:template>

</xsl:stylesheet>
  