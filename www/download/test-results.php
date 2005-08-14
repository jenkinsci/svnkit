<?
$home = "<html><body><meta http-equiv=\"REFRESH\" content=\"0;URL='http://tmate.org/svn/download/'\"></body></html>";

//Read plain PHP-XML output
$xmlData = fopen ($fileurl, "r");
if (!$xmlData) {
    echo $home;
    return;
}

// Stack up output into one String
while ($line = fgets ($xmlData))
  $xml .= $line;

// Gonna contain PHP-XML output
$arguments = array(
     '/_xml' => $xml,
);

$xslpath = dirname(__FILE__) . '/PythonTests.xsl';

$xh = xslt_create();

// Process the document
$result = xslt_process($xh, 'arg:/_xml', $xslpath, NULL, $arguments); 

$titlepos = strpos($result, "<title>");
if(titlepos == false){
	echo $home;
    return;
}

$result = substr($result, 0, $titlepos + strlen("<title>")) . $build . "-tests" . substr($result, $titlepos + strlen("<title>"));

// Print out your transformed document
echo $result;

xslt_free($xh);
?>

