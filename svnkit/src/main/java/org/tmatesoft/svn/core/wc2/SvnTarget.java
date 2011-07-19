package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNURL;

public class SvnTarget {
    
    private SVNURL url;
    private File file;
    
    public static SvnTarget fromFile(File file) {
        return new SvnTarget(file);
    }

    public static SvnTarget fromURL(SVNURL url) {
        return new SvnTarget(url);
    }
    
    private SvnTarget(File file) {
        this.file = file;
    }
    
    private SvnTarget(SVNURL url) {
        this.url = url;
    }
    
    public boolean isFile() {
        return this.file != null;
    }
    
    public boolean isURL() {
        return this.url != null;
    }
    
    public SVNURL getURL() {
        return this.url;
    }
    
    public File getFile() {
        return this.file;
    }
}
