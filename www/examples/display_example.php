<?
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
	$geshi->set_url_for_keyword_group(3,"http://java.sun.com/j2se/1.4.2/docs/api/");
	$code = $geshi->parse_code();
	echo "<html><body><h2 class=\"class-name\">".$fileName."</h2>".$code."<div style=\"max-width: 800px; width: 800px;\"><center><small style=\"color: #669999; font-size: 100%; margin: 0px; padding: 0px; margin-top: 0.5em; font-weight: normal;\"><span style=\"font-size: 80%;\">(c) 2004-2005 TMate Software. All rights reserved.</span></small></center></div></body></html>";
?>