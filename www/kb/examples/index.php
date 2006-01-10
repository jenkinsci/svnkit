<? 
publish_examples("http://tmate.org/svn");

function publish_examples($url){
	$docURL = "http://svn.tmate.org/repos/jsvn/trunk/doc/examples/src/org/tmatesoft/svn/examples";
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


	fwrite($fhandle, "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?><!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Library Usage Examples</title><link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"/svn/css/home.css\" /><meta name=\"KEYWORDS\" content=\"Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development\"><meta name=\"DESCRIPTION\" content=\"Pure Java Subversion Library. Open Source, provided by TMate Software\">".
				"</head>".
				"<body  style=\"width: 400px; padding-left: 5px; text-align: left; background: #fff;\"><table align=\"left\" valign=\"top\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"400\" style=\"height: 100%; font-size: 100%; font-weight: normal;\"><tr valign=\"top\" ><td colspan=\"1\" rowspan=\"1\"><small><a style=\"border-bottom: 0px;\" href=\"".$url."\" target=_top>Home</a>&nbsp;::&nbsp;<a style=\"border-bottom: 0px;\" href=\"/svn/kb/index.html\" target=_top>Knowledge&nbsp;Base</a></small>".
				"<h1>Library Usage Examples</h1><p>The following examples may help you to become more familiar with the JavaSVN API:</p>");

	for($k = 0; $k < count($examplesPath); $k++) {
		$matches = array();
		$exampleDirectory = $examplesPath[$k];
		if(ereg("org/tmatesoft/svn/examples/[^/]+", $examplesPath[$k], $matches)){
			$exampleDirectory = str_replace("/", ".", $matches[0]);
		}

		fwrite($fhandle, "<h4>".$exampleDirectory."</h4>");
		fwrite($fhandle, $packageDescription[$examplesPath[$k]]);
		fwrite($fhandle, "<div style=\"color: black; margin: 4px; padding: 0.5em; font-size: 100%; line-height: 150%;\">");
		fwrite($fhandle, "<span style=\"font-size: 100%;\"><img src=\"/svn/img/folder.gif\" border=\"0\">".$exampleDirectory."</span>");

		for($l = 0; $l < count($result[$examplesPath[$k]][$examplesPath[$k]]); $l++){
			fwrite($fhandle, "<br><span style=\"font-size: 100%;\">&nbsp;&nbsp;&nbsp;...<a style=\"border-bottom: 0px; text-decoration: none;\" href=\"/svn/kb/examples/display_example.php?fileURL=".$examplesPath[$k].$result[$examplesPath[$k]][$examplesPath[$k]][$l]."\" TARGET=\"ExamplesDisplay\"><img src=\"/svn/img/file.gif\" border=\"0\">".$result[$examplesPath[$k]][$examplesPath[$k]][$l]."</a></span>");
		}
		fwrite($fhandle, "</div>");

	}
	fwrite($fhandle, "<table style=\"margin-top: 1em; font-size: 90%;\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td id=\"footer\" align=\"left\">Copyright &copy;2004-2006, TMate Software</td></tr></table></td></tr></table></body></html>");

/*
 * <div style=\"width: 350px;\"><center><small><span style=\"font-size: 80%;\">(c) 2004-2006 TMate Software. All rights reserved.</span></small></center></div>
 */
	fclose($fhandle);
	
	echo "<html><head><title>Library Usage Examples</title><meta name=\"KEYWORDS\" content=\"Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development\"><meta name=\"DESCRIPTION\" content=\"Pure Java Subversion Library. Open Source, provided by TMate Software\"></head><frameset COLS=\"34%, *\" ><frame SRC=\"/svn/feed/ExamplesList.html\" NAME=\"ExamplesList\" MARGINWIDTH=1><frame NAME=\"ExamplesDisplay\"></frameset>";

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