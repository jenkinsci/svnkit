package org.tmatesoft.svn.cli2;

import java.io.File;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNOperatingPath {
    
    private String myPath;
    private File myFile;

    public SVNOperatingPath(String path, File file) {
        myPath = path;
        myFile = file;
    }

    public File getFile() {
        return myFile;
    }
    
    public String getPath(File file) {
        // convert all to '/'.
        String inPath = file.getAbsolutePath().replace(File.separatorChar, '/');
        String basePath = myFile.getAbsolutePath().replace(File.separatorChar, '/');
        if (inPath.equals(basePath)) {
            return myPath;
        } else if (inPath.length() > basePath.length() && inPath.startsWith(basePath + "/")) {
            if ("".equals(myPath)) {
                return inPath.substring(basePath.length() + 1);
            }
            return myPath + inPath.substring(basePath.length());
        }
        return inPath;
    }
}