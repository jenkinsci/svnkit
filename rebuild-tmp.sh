for f in $(find . -name '.svn'); do echo $f; mkdir $f/tmp; done
svn cleanup
svn update
# then svn switch

