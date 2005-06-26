<html><head><title>Pure Java Subversion (SVN) Client Library</title>

<meta name="keywords" content="Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development">   
<meta name="description" content="Pure Java Subversion Library. Open Source, provided by TMate Software">
<link rel="alternate" type="application/rss+xml" title="RSS 2.0" href="http://tmate.org/svn/rss2.php" />
<style>
html body {
    margin: 0px;
    padding: 0px;
    margin-left: 1em;
    margin-right: 1em;
    font-family: trebuchet ms, verdana, tahoma, arial;
    color: #333;
    text-align: left;
}
table tr td {
    font-size: 82%;
}
table tr td a {
    padding-left: 1em;
}
#normal {
    padding-left: 0;
}

small, h5 {
    color: #669999;
    font-size: 100%;
    margin: 0px;
    padding: 0px;
    margin-top: 0.5em;
    font-weight: normal;
}
p {
    margin: 0px;
    padding: 0px;
    padding-top: 0.5em;
}
ul {
    margin-top: 0.5em;
}
</style><!-- base href="http://tmate.org/svn/" --></head>
<body>

<? 
include ("../stats/stats.php"); 
?>

<table style="font-size: 120%;" width="800">
<tbody><tr><td align="center"><h3 style="padding:0px; margin:0px;">Pure Java Subversion (SVN) Client Library</h3></td></tr>
<tr><td align="center"><small><i>The only pure java subversion client library in the world!</i></small></td></tr>
</tbody></table>
<div style="font-size: 82%; max-width: 800px; width: 800px;">
<h5>About</h5>
<p>JavaSVN is a pure java Subversion (SVN) client library. This means that users 
of the library, i.e. java applications do not have to include svn native binaries 
or <i>javahl</i> bindings to work with subversion repository. JavaSVN library is not 
only a 100% java replacement for javahl bindings, but also a library that provides 
high level of control over subversion repository operations. 
</p>
<table style="margin: 0px; padding: 0px;">
<tr style="margin: 0px; padding: 0px;">
<td  style="margin: 0px; padding: 0px;" align="left" valign="top" width="500px">
<h5><b>Professional Support</b></h5>
<p style="padding-right: 0.5em;">If you're using JavaSVN in business-critical applications, you may get a professional 
support for JavaSVN library. This support includes bug fixes, development prioritization 
and problem support. If you're interested in professional JavaSVN support, please contact
<a id="normal" href="mailto:support@tmatesoft.com">support@tmatesoft.com</a> for more details.</p>
</td>
<td valign="top"  align="left"  style="margin: 0px; padding: 0px;">
<center><nobr><h5>JavaSVN Users</h5></nobr></center>
<ul style="padding: 0px; margin: 0px; padding-left: 1em; list-style-type:none; border-width: 0 0 0 1px; border-style: dashed; border-color:#669999;">
<li><b><a id="normal" href="http://tmatesoft.com/">TMate</a></b><br />Subversion tracking and reporting tool</li>
<li><a id="normal" href="http://smartcvs.com/smartsvn/index.html">SmartSVN</a><br />Standalone Pure Java Subversion GUI Client</li>
<li><a id="normal" href="http://intellij.net/index.jsp">IntelliJ IDEA 5.0 (EAP)</a><br />Java IDE developed by <a id="normal" href="http://jetbrains.com/">JetBrains</a></li>
<li><a id="normal" href="http://subclipse.tigris.org/">Subclipse</a><br /> Eclipse Subversion plugin (<a id="normal" href="subclipse.html">details</a>)</li>
</ul>

</td>
</tr></table>
<h5>Major Features</h5>
<ul>
<li>No external binaries or libraries are needed</li>
<li>Supports http, https, svn and svn+ssh connection protocols</li>
<li>Default implementation provides support for default subversion working copy files</li>
<li>Low level API allows to work directly with repository, without filesystem overhead</li>
<li>Extensible design - every part of implementation could be extened or replaced</li>
<li>May be used as a transparent javahl replacement</li>
</ul>
Check the <a href="status.html">JavaSVN status</a> page to see
what features are planned, but not yet implemented.
<h5>License</h5>
<a id="normal" href="license.html">Licence&nbsp;Agreement</a>&nbsp;(BSD-like)
<h5>Documentation</h5>
<b>New:</b>&nbsp;<a id="normal" href="list.html">Mailing&nbsp;List&nbsp;(ask&nbsp;your&nbsp;questions&nbsp;there)</a><br/>
<a id="normal" href="usage.html">How&nbsp;to&nbsp;Use&nbsp;(for&nbsp;developers)</a><br/>
<a id="normal" href="build.html">Build&nbsp;Instructions</a><br/>
<a id="normal" href="subclipse.html">For&nbsp;Subclipse&nbsp;(and&nbsp;svnant)&nbsp;Users</a><br/>
<a id="normal" href="ant.html">Using&nbsp;JavaSVN&nbsp;with&nbsp;Ant</a><br/>
<a id="normal" href="javadoc/index.html">JavaDoc</a><br/>
<a id="normal" href="feed/rss_util.php?function=publish_examples&url=http://www.tmate.org/svn">Example programs</a>
<h5>Issue Tracker</h5>
Use the following link to view JavaSVN issues or submit a new one:
<a id="normal" href="http://tmate.org/tracker/">JavaSVN Issue Tracker</a>.<br/> 
Please read <a href="logging.html">documentation chapter</a> on how to get JavaSVN log before submitting a bug report. 
<h5>Repository</h5>
To browse source code visit <a id="normal" href="http://72.9.228.230:8080/svn/jsvn/">http://72.9.228.230:8080/svn/jsvn/</a>
<br />To get the latest source code use the following command:
<pre style="margin: 4px; padding: 0.5em; font-size: 100%; width: 40em; background-color: #eee;">$ svn co http://72.9.228.230:8080/svn/jsvn/trunk/</pre>
</div>
<table width="800">
<tbody>

<tr><td width="30%"><h5>Download</h5></td>
<td align="right" width="80%"><a style="text-decoration: none" href="rss.xml"><span style="font-family:Arial;font-size:9px;background: #ff6000;color: #fff;padding: 0 4px">rss&nbsp;1.0</span>
<a style="text-decoration: none" href="rss2.xml"><span style="font-family:Arial;font-size:9px;background: #ff6000;color: #fff;padding: 0 4px">rss&nbsp;2.0</span></td>
</tr>
<tr><td style="padding-bottom: 5px;" colspan=2><b>Note:</b> to get an Eclipse plugin version use <b>http://tmate.org/svn/</b> as an update site location in Eclipse Update Manager.<br/></td></tr>

<?
include("feed/rss_util.php");
publish_html("http://tmate.org/svn/");
?>
</tbody></table>
<br>
<div style="max-width: 800px; width: 800px;">
<center><small><span style="font-size: 80%;">(c) 2004-2005 TMate Software. All rights reserved.</span></small></center>
</div>
</body></html>