<?

if(strcmp($function, "display_file")==0){
	//code for displaying examples
	display_file($fileURL);
}else if(strcmp($function, "publish_examples")==0){
	publish_examples($url);
}

function publish_html($rss) {
$cacheFile = $_SERVER["DOCUMENT_ROOT"] . "/svn/feed/html.cache";
                                       
if (file_exists($cacheFile)) {
    readfile($cacheFile);
    return;
} 
                                     
$repository = "http://72.9.228.230:8080/svn/jsvn/tags/";
$contents = read_contents($repository);
$handle = fopen($cacheFile, "w+");
if (!$contents) {
   fwrite($handle, "<tr><td colspan=2><font color=red>Repository is temporary not responding. Try again later.</font></td></tr>");
   fwrite($handle, emergency_html());
   fclose($handle);
   readfile($cacheFile);
   return;
}
$items = publish_rss20($repository, $contents, $rss);
$handle = fopen($cacheFile, "w+");
for($i = 0; $i < count($items) && $i < 3; $i++) {
     fwrite($handle, $items[count($items) - $i - 1]["html_description"]);
}
if (count($items) == 0) {
     fwrite($handle, "<tr><td>repository is temporary not responding. try again later.</td></tr>");
}
fclose($handle);
readfile($cacheFile);
}

function publish_rss20($repository, $contents, $rss) {

if (preg_match_all("/<li><a href=\"(.*)\/\">(.*\..*\..*)(<\/a>)<\/li>/", $contents, $matches)) {

   $items = array();
   $index = 0;

   for($i = 0; $i < count($matches[1]); $i++) {
       $build = $matches[1][$i];
       $changelog_url = $repository . $build . "/changelog.txt";
       $changelog = read_contents($changelog_url);

       if (!$changelog) {
           continue;
       }

       $standalone_name = "org.tmatesoft.svn_" . $build . ".standalone.zip";
       $eclipse_name = "org.tmatesoft.svn_" . $build . ".eclipse.zip";
       $src_name = "org.tmatesoft.svn_" . $build . ".src.zip";
       $standalone_file = $_SERVER["DOCUMENT_ROOT"] . "/svn/" . $standalone_name;

       if (!file_exists($standalone_file)) {
          continue;  
       }

       $svn_url = $repository . $build . "/";
       $standalone_link = $rss . $standalone_name;
       $src_url    = $rss . $src_name;
       $eclipse_url    = $rss . $eclipse_name;
       $eclipse_update_url    = $rss;

       ereg("^=[^\n]+\n([^=]+).*$", $changelog, $m);
       $changelog_str = trim($m[1]);
       $date_string = date("j M Y, H:i", filemtime($standalone_file));
       
       $item_description  = "<b>" . $date_string . "</b>, build: " . $build . "<p><table style=\"font-size: 100%;\">";
       $item_description .= "<tr><td>standalone version:&nbsp;</td><td><a href=\"" . $standalone_link . "\">" . $standalone_name . "</a></td></tr>";
       $item_description .= "<tr><td>source code archive:&nbsp;</td><td><a href=\"" . $src_url . "\">" . $src_name . "</a></td></tr>";
       $item_description .= "<tr><td>source code:&nbsp;</td><td><a href=\"" . $svn_url . "\">@svn repository</a></td></tr>";
       $item_description .= "<tr><td>eclipse update site archive:&nbsp;</td><td><a href=\"" . $eclipse_url . "\">" . $eclipse_name . "</a></td></tr>";
       $item_description .= "<tr><td>eclipse update site location:&nbsp;</td><td><b>" . $eclipse_update_url . "</b></td></tr></table></p>";
       $item_description .= "<h5>ChangeLog</h5><pre>" . $changelog_str . "</pre>";
       $item_description .= "<a href=\"" . $changelog_url . "\">full changelog up to this build</a>";
       $item_description .= "<h5>Contact</h5><p>Your questions and feedback are welcome at <a href=\"mailto:support@tmatesoft.com\">support@tmatesoft.com</a></p>";


       $html_description  = "<tr bgcolor=#cccccc><td colspan=\"2\">&nbsp;" . $date_string . ", build <b>" . $build . "</b></td></tr>";
       $html_description .= "<tr><td colspan=\"2\">&nbsp;Changelog:</td></tr>";
       $html_description .= "<tr><td colspan=\"2\"><pre>" . $changelog_str . "</pre>";
       $html_description .= "<a href=\"" . $changelog_url . "\">full changelog up to this build</a></td></tr>";
       $html_description .= "<tr bgcolor=#cccccc><td>&nbsp;Standalone Version&nbsp;</td><td><a href=\"" . $standalone_link . "\">" . $standalone_name . "</a></td></tr>";
       $html_description .= "<tr bgcolor=#cccccc><td>&nbsp;Source Code Archive&nbsp;</td><td><a href=\"" . $src_url . "\">" . $src_name . " </a></td></tr>";
       $html_description .= "<tr bgcolor=#cccccc><td>&nbsp;Eclipse Update Site Archive&nbsp;</td><td><a href=\"" . $eclipse_url . "\">" . $eclipse_name . "</a></td></tr>";
       $html_description .= "<tr bgcolor=#cccccc><td>&nbsp;Eclipse Update Site Location&nbsp;</td><td>&nbsp;&nbsp;<b>" . $eclipse_update_url . "</b></td></tr>";
       $html_description .= "<tr bgcolor=#cccccc><td>&nbsp;Source Code&nbsp;</td><td><a href=\"" . $svn_url . "\">@svn repository</a></td></tr>";
       $html_description .= "<tr colspan=2><td></td></tr>";

       $item = array();
       $item["title"]  = "Build '" . $build . "' published";
       $item["source"] = "http://tmate.org/svn/";
       $item["link"]   = "http://tmate.org/svn/";
       $item["author"] = "TMate Software";
       $item["date"]   = filemtime($standalone_file);

       $item["rss_description"] = $item_description;
       $item["html_description"] = $html_description;

       $items[$index++] = $item;
   }
  
}
return $items;
}

function read_contents($url) {
	$fp = fsockopen("72.9.228.230", 8080, $errno, $errstr, 1);	
        if (!$fp) {
        	return false; 
        }
        fclose($fp);

        $handle = fopen($url, "rb");
        if (!$handle) {
        	return false;
        }
	$contents = '';
	while (!feof($handle)) {
	  $contents .= fread($handle, 8192);
	}
	fclose($handle);
        return $contents;
}

function emergency_html() {
// Open a known directory, and proceed to read its contents
$dir = $_SERVER["DOCUMENT_ROOT"] . "/svn/";
$result = "";
if (is_dir($dir)) {
   if ($dh = opendir($dir)) {
       $entries = array();
       $index = 0; 
       while (($file = readdir($dh)) !== false) {
        $matches = array();
        ereg("^.+_([0-9]+)\.standalone\.zip$", $file, $matches);
        if (count($matches) > 1) {
          $entries[$index++] = $matches[1];
        }
       }
       closedir($dh);
       sort($entries);
       if (count($entries) > 0) {
         $result = $entries[count($entries) - 1];
         $result = "<tr><td colspan=2>Latest binary version is <a id=normal href=\"org.tmatesoft.svn_" . $result . ".standalone.zip\">org.tmatesoft.svn_" . $result .".standalone.zip</a></td></tr>";
       }
   }
}    
return $result;
}

function publish_examples($url){
	$docURL = "http://72.9.228.230:8080/svn/jsvn/branches/0.9.0/doc/examples/src/org/tmatesoft/svn/examples";
	$examplesPath = array("http://test1.ru/doc/examples/src/org/tmatesoft/svn/examples/repository/","http://test1.ru/doc/examples/src/org/tmatesoft/svn/examples/wc/");//array($docURL."/repository/",$docURL."/wc/");
	$result = array();
	for($k = 0; $k < count($examplesPath); $k++){
		$result[$examplesPath[$k]] = collect_examples($examplesPath[$k]);
	}
	if(count($result)<1){
		return $url;
	}		
	
	$examplesListFrameFile = $_SERVER["DOCUMENT_ROOT"] . "/feed/ExamplesList.html";

	$fhandle = fopen($examplesListFrameFile, "w+");


	fwrite($fhandle, "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?><!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Example Programs :: Documentation :: Pure Java Subversion (SVN) Client Library</title><link rel=\"stylesheet\" href=\"stylesheet.css\" /><meta name=\"KEYWORDS\" content=\"Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development\"><meta name=\"DESCRIPTION\" content=\"Pure Java Subversion Library. Open Source, provided by TMate Software\"><style>html body {    margin: 0px;    padding: 0px;    padding-top: 0.2em;    margin-left: 1em;    margin-right: 1em;    font-family: trebuchet ms, verdana, tahoma, arial;    line-height: 1.5em;    color: #333;    text-align: left;    max-width: 30em;    width: 30em;    font-size: 82%;}".
				"h4 { font-size: 120%;  margin-bottom: 0.5em;} code {   font-weight: bold;   font-size: 100%;   color: darkblue;} #normal {    padding-left: 0;} small {    color: #669999;    font-size: 100%;} p, pre {    margin: 0px;    padding: 0px;    margin-top: 0.5em;} li {    padding-bottom: 0.5em;}</style><!-- base href=\"http://tmate.org/svn/\" --></head>".
				"<body  style=\"width: 370px;\"><div  style=\"width: 370px;\"><small><a id=\"normal\" href=\"".$url."\" target=_top>Home</a>&nbsp;::&nbsp;Documentation&nbsp;::&nbsp;Example&nbsp;Programs</small>".
				"<h4>Example Programs</h4><p style=\"color: darkblue; margin: 4px; padding: 0.5em; font-size: 110%; width: 19em; background-color: #eee;\">".
				"The following examples may help you<br>".
				"to become more familiar with the<br>".
				"JavaSVN API:<br><hr>");
	

	for($k = 0; $k < count($examplesPath); $k++) {
		$matches = array();
		$exampleDirectory = $examplesPath[$k];
		if(ereg("org/tmatesoft/svn/examples/[^/]+", $examplesPath[$k], $matches)){
			$exampleDirectory = str_replace("/", ".", $matches[0]);
		}
	
		fwrite($fhandle, "<div style=\"color: black; margin: 4px; padding: 0.5em; font-size: 100%; line-height: 150%; width: 28em; background-color: white;\">");
		fwrite($fhandle, "<br><span style=\"font-size: 110%; width: 25em;\"><!--p style=\"color: black; margin: 4px; padding: 0.5em; font-size: 110%; width: 25em; background-color: white;\"--><img src=\"/feed/folder.gif\" border=\"0\">".$exampleDirectory."</span>");

		for($l = 0; $l < count($result[$examplesPath[$k]][$examplesPath[$k]]); $l++){
			fwrite($fhandle, "<br><span class=\"tree-icon\" style=\"font-size: 100%; width: 25em;\">&nbsp;&nbsp;&nbsp;...<a style=\"text-decoration: none;\" href=\"/feed/rss_util.php?function=display_file&fileURL=".$examplesPath[$k].$result[$examplesPath[$k]][$examplesPath[$k]][$l]."\" TARGET=\"ExamplesDisplay\"><img src=\"/feed/text.gif\" border=\"0\">".$result[$examplesPath[$k]][$examplesPath[$k]][$l]."</a></span>");
		}
		fwrite($fhandle, "</div>");
	}
	fwrite($fhandle, "<div style=\"max-width: 800px; width: 350px;\"><center><small><span style=\"font-size: 80%;\">(c) 2004-2005 TMate Software. All rights reserved.</span></small></center></div></body></html>");

	fclose($fhandle);
	
/*
	$examplesFramesFile = $_SERVER["DOCUMENT_ROOT"] . "/ExamplesFrames.html";
	$fhandle = fopen($examplesFramesFile, "w+");
	$file = $_SERVER["DOCUMENT_ROOT"] . "/tmp.txt";
	$fhandle = fopen($file, "w+");
	fwrite($fhandle, $doc_contents);
	fclose($fhandle);
*/

	echo "<html><head><title>Example Programs :: Documentation :: Pure Java Subversion (SVN) Client Library</title><meta name=\"KEYWORDS\" content=\"Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development\"><meta name=\"DESCRIPTION\" content=\"Pure Java Subversion Library. Open Source, provided by TMate Software\"></head><frameset COLS=\"30%, *\" ><frame SRC=\"ExamplesList.html\" NAME=\"ExamplesList\" MARGINWIDTH=5><frame NAME=\"ExamplesDisplay\"></frameset>";

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

	if (preg_match_all("|<A HREF=\"(.+\.java)\">|U", $contents, $matches, PREG_PATTERN_ORDER)) {
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

function display_file($fileURL){
      $handle = fopen($fileURL, "rt");
      if (!$handle) {
	     	return false;
      }
	$contents = '';
	while (!feof($handle)) {
	  $contents .= fread($handle, 8192);
	}
	fclose($handle);

	$fileName = $fileURL;

	if(ereg("[^/]+\.java", $fileURL, $matches)){
		$fileName = $matches[0];
	}

	include_once 'geshi/geshi.php';

	$geshi = new GeSHi($contents, 'java');
	$geshi->set_header_type(GESHI_HEADER_PRE);
	$geshi->set_numbers_highlighting(false); 	
	$geshi->set_overall_style('color: rgb(0,0,0); border: 1px solid #d0d0d0; background-color: #f0f0f0;', true);
	// Note the use of set_code_style to revert colours...
	$geshi->set_line_style('font: normal normal 95% \'Courier New\', Courier, monospace; color: black;', 'font-weight: bold; color: blue;', true);

	//for methods 
	$geshi->set_methods_style(1, "color: black;", false);
	
	//for multi-line comments /**/
	$geshi->set_comments_style('MULTI','color: rgb(63,127,95); font-style: code;', false);

	//for 'import' keyword
	$geshi->set_comments_style(2,'color: rgb(127,0,85); font-weight: bold;', false);

	//for string constants
	$geshi->set_strings_style('color: rgb(42,0,255);', true);

	//for links (standard classes, etc.)
	$geshi->set_link_styles(GESHI_LINK, 'color: #000060;');
	$geshi->set_link_styles(GESHI_HOVER, 'background-color: #f0f000;');

	//for keywords
	$geshi->set_keyword_group_style(1,'color: rgb(127,0,85); font-weight: bold;', false);
	$geshi->set_keyword_group_style(2,'color: rgb(127,0,85); font-weight: bold;', false); 
	$geshi->set_keyword_group_style(4,'color: rgb(127,0,85); font-weight: bold;', false); 
	//new keyword group for 'package'
	$geshi->add_keyword_group(5, 'color: rgb(127,0,85); font-weight: bold;', true, array('package', 'import'));

	$geshi->set_header_content('JavaSVN API examlpe: '.$fileName);
	$geshi->set_header_content_style('font-family: Verdana, Arial, sans-serif; color: #808080; font-size: 70%; font-weight: bold; background-color: #f0f0ff; border-bottom: 1px solid #d0d0d0; padding: 2px;');
	$geshi->set_symbols_highlighting(false);
	$geshi->set_footer_content_style('font-family: Verdana, Arial, sans-serif; color: #808080; font-size: 70%; font-weight: bold; background-color: #f0f0ff; border-top: 1px solid #d0d0d0; padding: 2px;');

	$code = $geshi->parse_code();
	echo "<html><body>".$code."<div style=\"max-width: 800px; width: 800px;\"><center><small style=\"color: #669999; font-size: 100%; margin: 0px; padding: 0px; margin-top: 0.5em; font-weight: normal;\"><span style=\"font-size: 80%;\">(c) 2004-2005 TMate Software. All rights reserved.</span></small></center></div></body></html>";
}

?>
