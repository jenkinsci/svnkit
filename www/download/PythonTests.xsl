<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="html" indent="yes" standalone="no" omit-xml-declaration="yes" version="4.0" encoding="UTF-8" doctype-public="-//W3C//DTD XHTML 1.0 Transitional//EN" />

<xsl:template match="/">

	<xsl:apply-templates />

</xsl:template>


<xsl:template match="PythonTests">
<html>
<head><title></title>
<meta name="keywords" content="Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development" />   
<meta name="description" content="Pure Java Subversion Library. Open Source, provided by TMate Software" />
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
<link rel="stylesheet" type="text/css" media="screen" href="/css/home.css"></link>
<link rel="shortcut icon" href="/img/favicon.ico"/>
</head>
<body>
<table bgcolor="white" width="900" cellpadding="0" align="center" style="height: 100%;"  border="0" cellspacing="0" >
<tr align="center" valign="top" >
<td colspan="1" rowspan="1">

<div class="leftedge" >
<div class="rightedge">

<table  cellpadding="0" align="center" width="870" border="0"  cellspacing="0">
<tr align="center" valign="top">
<td align="left" valign="top" style="padding: 3px 20px 20px 20px;">

<table border="0" cellpadding="0" cellspacing="0" style="width: 100%;">
<tr>
<td rowspan="2" colspan="1" width="50%">
<a style="border-bottom: 0px;" href="/"><img width="415" height="97" src="../img/svnkit_logo.jpg" border="0" /></a>
</td>
<td rowspan="1" colspan="1" width="50%" style="font-style: italic; font-size: 17px; padding-left: 15px" align="left" valign="center">
The only pure Java&#153; Subversion library in the world!
</td>
</tr>
<tr>
<td rowspan="1" colspan="1" align="left" valign="bottom" style="padding-right: 0px; padding-bottom: 12px; padding-left: 5px">
<table border="0" cellpadding="0" cellspacing="0" style="width: 100%;">
<tr style="height: 10px;">

<td valign="top" align="center" style="font: normal bold 17px/18px trebuchet ms, verdana, tahoma, arial ; border-right: 1px inset #336699;">
<a class="headitem" style="border-bottom: 0px;" href="../index.html">Home</a>
</td>

<td valign="top" align="center" style="font: normal bold 17px/18px trebuchet ms, verdana, tahoma, arial ; border-right: 1px inset #336699;">
<a class="headitem" style="border-bottom: 0px;" href="index.php">Get Library</a></td>

<td valign="top" align="center" style="font: normal bold 17px/17px trebuchet ms, verdana, tahoma, arial ; border-right: 1px inset #336699;">
<a class="headitem" style="border-bottom: 0px;" href="../kb/index.html">Knowledge Base</a> 
</td>

<td valign="top" align="center" style="font: normal bold 17px/18px trebuchet ms, verdana, tahoma, arial;">
<a class="headitem" style="border-bottom: 0px;" href="../licensing/index.html">Licensing</a>
</td>
</tr>
</table>
</td>
</tr>
</table>

<h1>Python tests</h1>
<p>
<xsl:text>Started on </xsl:text><xsl:value-of select="@start" />
<br />
<xsl:apply-templates select="server" mode="anchors" />

	<xsl:apply-templates select="server" mode="tables" />

<xsl:text>Total time elapsed: </xsl:text><xsl:value-of select="@elapsed" />
</p>	
<br />
<table style="margin-top: 1em;" width="100%" cellpadding="0" cellspacing="0"><tr><td id="footer" align="left">Copyright &#50;&#48;&#48;&#52;-&#50;&#48;&#48;&#53;, TMate Software</td><td align="right" id="footer">feedback is welcome at <a href="mailto:feedback%40svnkit.com">feedback@svnkit.com</a></td></tr></table>
</td>
</tr>
</table>
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
  