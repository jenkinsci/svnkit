<?xml version="1.0" encoding="UTF-8"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="html" indent="yes" version="4.0" encoding="UTF-8" doctype-public="-//W3C//DTD XHTML 1.0 Transitional//EN" />

<xsl:template match="/">

	<xsl:apply-templates />

</xsl:template>


<xsl:template match="PythonTests">
<html xmlns="http://www.w3.org/1999/xhtml" >
<head><title>JavaSVN</title>
<meta name="keywords" content="Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development" />   
<meta name="description" content="Pure Java Subversion Library. Open Source, provided by TMate Software" />
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
<link rel="stylesheet" type="text/css" media="screen" href="../css/home.css"></link>
</head>
<body>
<table bgcolor="white" width="900" cellpadding="0" align="center" style="height: 100%;"  border="0" cellspacing="0" >
<tr align="center" valign="top" >
<td colspan="1" rowspan="1">

<div class="leftedge" >
<div class="rightedge">

<table  cellpadding="0" align="center" width="870" border="0"  cellspacing="0">
<tr align="center" valign="top">
<td align="left" valign="top" style="padding: 20px 20px 20px 20px;">

<p>
<div style="padding-bottom: 10px; border-bottom: 1px solid #ccc;">
<span class="javasvn"><a style="border-bottom: 0px; color: rgb(51, 102, 153);" href="http://tmate.org/svn/">JavaSVN</a>&#160;</span><span style="font-size: 140%; font-weight: bold;">The only pure Java Subversion client library in the world!</span>
</div>
<small><a href="../index.html" style="border-bottom: 0px;">Home</a> :: Python Tests</small>
</p>

<xsl:text>Python tests started on&#160;</xsl:text><xsl:value-of select="@start" />

	<xsl:apply-templates select="server" />

<xsl:text>Total time elapsed:&#160;</xsl:text><xsl:value-of select="@elapsed" />
	
<br />
<table style="margin-top: 1em;" width="100%" cellpadding="0" cellspacing="0"><tr><td id="footer" align="left">Copyright &#169; 
&#50;&#48;&#48;&#52;-&#50;&#48;&#48;&#53;, TMate Software</td><td align="right" id="footer">feedback is welcome at <a href="mailto:feedback%40tmatesoft.com">feedback@tmatesoft.com</a></td></tr></table>
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




<xsl:template match="server">

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



<xsl:template match="suite">

<xsl:text>Suite Name:&#160;</xsl:text>
<b>	
	<xsl:value-of select="@name" />
</b>	
<table cellpadding="3" cellspacing="1" border="0" width="100%" bgcolor="#999933">
<tr bgcolor="#E1E1E1" align="left">
<td>Test Name</td>
<td>Id</td>
<td>Result</td>
</tr>	
	<xsl:apply-templates select="test"/>
</table>
<xsl:text>Total:&#160;</xsl:text><xsl:value-of select="@total" />
<xsl:text>, Passed:&#160;</xsl:text><xsl:value-of select="@passed" />
<xsl:text>, Failed:&#160;</xsl:text><xsl:value-of select="@failed" />
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
  