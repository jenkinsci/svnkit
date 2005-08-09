<?
include($_SERVER["DOCUMENT_ROOT"] . "/svn/feed/feedcreator.class.php");
include($_SERVER["DOCUMENT_ROOT"] . "/svn/feed/rss_util.php");
//include($_SERVER["DOCUMENT_ROOT"] . "/stats/counter.php");

$cacheFile = $_SERVER["DOCUMENT_ROOT"] . "/svn/feed/rss20.cache";

if (file_exists($cacheFile)) {
//    if (time() - filemtime($cacheFile) <= 3600) {
        readfile($cacheFile);
        return;
  //  }
} 
                                           
$rss = new UniversalFeedCreator();
$rss->useCached("RSS1.0", $cacheFile);
$rss->title = "TMate JavaSVN";
$rss->description = "TMate JavaSVN Library Change Log";
$rss->link = "http://tmate.org/svn/";
$rss->syndicationURL = "http://tmate.org/" . $PHP_SELF;
$rss->author = "TMate Software"; 
$rss->editor = "TMate Software"; 
$rss->authorEmail = "support@tmatesoft.com"; 
$rss->editorEmail = "support@tmatesoft.com"; 

$repository = "http://72.9.228.230/svn/jsvn/tags/";
$contents = read_contents($repository);
if (!$contents) {
   echo $rss->createFeed();
   exit;
}

$items = publish_rss20($repository, $contents, "http://tmate.org/svn/");
for($i = 0; $i < count($items); $i++) {

     $item = $items[$i];

     $rssitem = new FeedItem();

     $rssitem->title  = $item["title"];
     $rssitem->source = $item["source"];
     $rssitem->link   = $item["link"];
     $rssitem->author = $item["author"];
     $rssitem->date   = $item["date"];
     $rssitem->authorEmail = "support@tmatesoft.com"; 
     $rssitem->editorEmail = "support@tmatesoft.com"; 

     $rssitem->description = $item["rss_description"];
     $rss->addItem($rssitem);
}

$rss->saveFeed("RSS1.0", $cacheFile);
readfile($cacheFile);

exit;
?>