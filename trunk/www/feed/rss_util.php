<?

function publish_html($rss) {
$cacheFile = $_SERVER["DOCUMENT_ROOT"] . "/svn/feed/html.cache";
                                       
if (file_exists($cacheFile)) {
    readfile($cacheFile);
    return;
} 
                                     
$repository = "http://72.9.228.230/svn/jsvn/tags/";
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
	$fp = fsockopen("72.9.228.230", 80, $errno, $errstr, 1);	
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
?>