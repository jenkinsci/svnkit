package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class SvnDiffStatus extends SvnObject {

    private SVNStatusType modificationType;
    private boolean propertiesModified;
    private SVNNodeKind kind;
    private SVNURL url;
    private String path;
    private File file;
    
    public SVNStatusType getModificationType() {
        return modificationType;
    }
    public void setModificationType(SVNStatusType modificationType) {
        this.modificationType = modificationType;
    }
    public boolean isPropertiesModified() {
        return propertiesModified;
    }
    public void setPropertiesModified(boolean propertiesModified) {
        this.propertiesModified = propertiesModified;
    }
    public SVNNodeKind getKind() {
        return kind;
    }
    public void setKind(SVNNodeKind kind) {
        this.kind = kind;
    }
    public SVNURL getUrl() {
        return url;
    }
    public void setUrl(SVNURL url) {
        this.url = url;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public File getFile() {
        return file;
    }
    public void setFile(File file) {
        this.file = file;
    }
    
    

}
