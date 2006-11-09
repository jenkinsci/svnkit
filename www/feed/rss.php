<?
include("feedcreator.class.php");
include("rss_util.php");

$cacheFile = $_SERVER["DOCUMENT_ROOT"] . "/svn/feed/rss20.cache";
                                           
$rss = new UniversalFeedCreator();
$rss->useCached("RSS1.0", $cacheFile);
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

$items = publish_rss20($repository, $contents, "http://svnkit.com/");
for($i = count($items); $i >=0 && $i > count($items) - 5; $i--) {

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

$rss->saveFeed("RSS1.0", $cacheFile);
readfile($cacheFile);

exit;
?>