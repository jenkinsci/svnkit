<? 
publish_examples("http://tmate.org/svn");

function publish_examples($url){
	$docURL = "http://72.9.228.230:8080/svn/jsvn/branches/0.9.0/doc/examples/src/org/tmatesoft/svn/examples";
	$examplesPath = array($docURL."/repository/",$docURL."/wc/");
	
	$packageDescription = array();
	$packageDescription[$examplesPath[0]] = "<p>This package contains examples on how to use a low-level API from the <i>org.tmatesoft.svn.core.io</i> ".
							    "package to work directly with a repository. ".
							    "The major point of this API - the <b>SVNRepository</b> class which provides a developer the interface ".
							    "to interact with a repository. Note that the low-level API itself knows nothing about working copies, ".
							    "it only allows you to work with a Subversion repository.";
	$packageDescription[$examplesPath[1]] = "<p>This package contains examples on how to use a high-level API from the <i>org.tmatesoft.svn.core.wc</i> package ".
							    "to manage working copies. Each command of the native Subversion command line client is reflected in <b>SVN*Client</b> ".
							    "classes. These \"clients\" relies upon the low-level API (<i>org.tmatesoft.svn.core.io</i>) when an access to a repository is needed.";
	$result = array();
	for($k = 0; $k < count($examplesPath); $k++){
		$result[$examplesPath[$k]] = collect_examples($examplesPath[$k]);
	}
	if(count($result)<1){
		return $url;
	}		
	
	$examplesListFrameFile = $_SERVER["DOCUMENT_ROOT"] . "/svn/feed/ExamplesList.html";

	$fhandle = fopen($examplesListFrameFile, "w+");


	fwrite($fhandle, "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?><!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Example Programs :: Documentation :: Pure Java Subversion (SVN) Client Library</title><link rel=\"stylesheet\" href=\"stylesheet.css\" /><meta name=\"KEYWORDS\" content=\"Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development\"><meta name=\"DESCRIPTION\" content=\"Pure Java Subversion Library. Open Source, provided by TMate Software\"><style>html body {    margin: 0px;    padding: 0px;    padding-top: 0.2em;    margin-left: 1em;    margin-right: 1em;    font-family: trebuchet ms, verdana, tahoma, arial;    line-height: 1.5em;    color: #333;    text-align: left;    max-width: 30em;    width: 30em;    font-size: 82%;}".
				"h4 { font-size: 120%;  margin-bottom: 0.5em;} code {   font-weight: bold;   font-size: 100%;   color: darkblue;} #normal {    padding-left: 0;} small {    color: #669999;    font-size: 100%;} p, pre {    margin: 0px;    padding: 0px;    margin-top: 0.5em;} li {    padding-bottom: 0.5em;}</style><!-- base href=\"http://tmate.org/svn/\" --></head>".
				"<body  style=\"width: 370px;\"><div  style=\"width: 370px;\"><small><a id=\"normal\" href=\"".$url."\" target=_top>Home</a>&nbsp;::&nbsp;Documentation&nbsp;::&nbsp;Example&nbsp;Programs</small>".
				"<h4>Example Programs</h4><p style=\"color: darkblue; margin: 4px; padding: 0.5em; font-size: 110%; width: 370px; background-color: #eee;\">".
				"The following examples may help you<br>".
				"to become more familiar with the<br>".
				"JavaSVN API:<br>");

	for($k = 0; $k < count($examplesPath); $k++) {
		$matches = array();
		$exampleDirectory = $examplesPath[$k];
		if(ereg("org/tmatesoft/svn/examples/[^/]+", $examplesPath[$k], $matches)){
			$exampleDirectory = str_replace("/", ".", $matches[0]);
		}

		fwrite($fhandle, "<div class=\"info-box-title\" style=\"font-size: 120%;\">".$exampleDirectory."</div>");
		fwrite($fhandle, $packageDescription[$examplesPath[$k]]);
		fwrite($fhandle, "<div style=\"color: black; margin: 4px; padding: 0.5em; font-size: 100%; line-height: 150%; width: 25em; background-color: white;\">");
		fwrite($fhandle, "<span style=\"font-size: 110%; width: 25em;\"><img src=\"folder.gif\" border=\"0\">".$exampleDirectory."</span>");

		for($l = 0; $l < count($result[$examplesPath[$k]][$examplesPath[$k]]); $l++){
			fwrite($fhandle, "<br><span class=\"tree-icon\" style=\"font-size: 100%; width: 25em;\">&nbsp;&nbsp;&nbsp;...<a style=\"text-decoration: none;\" href=\"../examples/display_example.php?fileURL=".$examplesPath[$k].$result[$examplesPath[$k]][$examplesPath[$k]][$l]."\" TARGET=\"ExamplesDisplay\"><img src=\"text.gif\" border=\"0\">".$result[$examplesPath[$k]][$examplesPath[$k]][$l]."</a></span>");
		}
		fwrite($fhandle, "</div>");

	}
	fwrite($fhandle, "<div style=\"max-width: 800px; width: 350px;\"><center><small><span style=\"font-size: 80%;\">(c) 2004-2005 TMate Software. All rights reserved.</span></small></center></div></body></html>");

	fclose($fhandle);
	
	echo "<html><head><title>Example Programs :: Documentation :: Pure Java Subversion (SVN) Client Library</title><meta name=\"KEYWORDS\" content=\"Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development\"><meta name=\"DESCRIPTION\" content=\"Pure Java Subversion Library. Open Source, provided by TMate Software\"></head><frameset COLS=\"34%, *\" ><frame SRC=\"../feed/ExamplesList.html\" NAME=\"ExamplesList\" MARGINWIDTH=5><frame NAME=\"ExamplesDisplay\"></frameset>";

}


function collect_examples($examplesPath) {

      $handle = fopen($examplesPath, "rt");
      if (!$handle) {
	     	return false;
      }
	$contents = '';
	while (!feof($handle)) {
	  $contents .= fread($handle, 8192);
	}
	fclose($handle);

	if (preg_match_all("|<a href=\"(.+\.java)\">|U", $contents, $matches, PREG_PATTERN_ORDER)) {
		$entries = array();
		$index=0;
		for($i = 0; $i < count($matches[0]); $i++) {
			$entries[$index++] = $matches[1][$i];
		}
		$directoryEntries = array();
		$directoryEntries[$examplesPath] = $entries;
		return $directoryEntries;
	}
	return false;
}
?>