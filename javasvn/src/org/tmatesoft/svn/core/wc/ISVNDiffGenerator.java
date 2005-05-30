/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.Map;

import org.tmatesoft.svn.core.io.SVNException;

public interface ISVNDiffGenerator {
    
    public void init(String anchorPath1, String anchorPath2);
    
    public void setBasePath(File basePath);
    
    public void setForcedBinaryDiff(boolean forced);
    
    public void setEncoding(String encoding);
    
    public String getEncoding();

    public void setDiffDeleted(boolean isDiffDeleted);
    
    public boolean isDiffDeleted();
    
    public String getDisplayPath(File file);
    
    public File createTempDirectory() throws SVNException;
    
    public void displayPropDiff(String path, Map baseProps, Map diff, OutputStream result) throws SVNException;

    public void displayFileDiff(String path, File file1, File file2, String rev1, String rev2, String mimeType1, String mimeType2, OutputStream result) throws SVNException;
}
