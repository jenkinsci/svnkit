<?
include($_SERVER["DOCUMENT_ROOT"] . "/feed/feedcreator.class.php");
include($_SERVER["DOCUMENT_ROOT"] . "/feed/rss_util.php");
//include($_SERVER["DOCUMENT_ROOT"] . "/stats/counter.php");

$cacheFile = $_SERVER["DOCUMENT_ROOT"] . "/feed/rss22.cache";

if (file_exists($cacheFile)) {
//    if (time() - filemtime($cacheFile) <= 3600) {
        readfile($cacheFile);
        return;
  //  }
} 
                                           
$rss = new UniversalFeedCreator();
$rss->useCached("RSS2.0", $cacheFile);
$rss->title = "TMate SVNKit";
$rss->description = "TMate SVNKit Library Change Log";
$rss->link = "http://svnkit.com/";
$rss->syndicationURL = "http://svnkit.com/" . $PHP_SELF;
$rss->author = "TMate Software"; 
$rss->editor = "TMate Software"; 
$rss->authorEmail = "support@svnkit.com"; 
$rss->editorEmail = "support@svnkit.com"; 

$repository = "http://svn.svnkit.com/repos/svnkit/tags/";
$contents = read_contents($repository);
if (!$contents) {
   echo $rss->createFeed();
   exit;
}

$items = publish_rss20($repository, $contents, "http://www.snvkit.com/");
for($i = 0; $i < count($items); $i++) {

     $item = $items[$i];

     $rssitem = new FeedItem();

     $rssitem->title  = $item["title"];
     $rssitem->source = $item["source"];
     $rssitem->link   = $item["link"];
     $rssitem->author = $item["author"];
     $rssitem->date   = $item["date"];
     $rssitem->authorEmail = "support@svnkit.com"; 
     $rssitem->editorEmail = "support@svnkit.com"; 

     $rssitem->description = $item["rss_description"];
     $rss->addItem($rssitem);
}

$rss->saveFeed("RSS2.0", $cacheFile);
readfile($cacheFile);

exit;
?>