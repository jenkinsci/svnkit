package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnTarget {
    
    private SVNURL url;
    private File file;
    private SVNRevision pegRevision;
    
    public static SvnTarget fromFile(File file) {
        return fromFile(file, SVNRevision.UNDEFINED);
    }

    public static SvnTarget fromFile(File file, SVNRevision pegRevision) {
        return new SvnTarget(file, pegRevision);
    }

    public static SvnTarget fromURL(SVNURL url) {
        return fromURL(url, SVNRevision.UNDEFINED);
    }

    public static SvnTarget fromURL(SVNURL url, SVNRevision pegRevision) {
        return new SvnTarget(url, pegRevision);
    }
    
    private SvnTarget(File file, SVNRevision pegRevision) {
        this.file = new File(SVNPathUtil.validateFilePath(file.getAbsolutePath()));
        setPegRevision(pegRevision);
    }
    
    private SvnTarget(SVNURL url, SVNRevision pegRevision) {
        this.url = url;
        setPegRevision(pegRevision);
    }
    
    public boolean isLocal() {
        return isFile() && getResolvedPegRevision().isLocal();
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
    
    public SVNRevision getPegRevision() {
        return this.pegRevision;
    }

    public SVNRevision getResolvedPegRevision() {
        return getResolvedPegRevision(SVNRevision.HEAD, SVNRevision.WORKING);
    }
    
    public SVNRevision getResolvedPegRevision(SVNRevision defaultRemote, SVNRevision defaultLocal) {
        if (getPegRevision() == null || getPegRevision() == SVNRevision.UNDEFINED) {
            if (defaultLocal == null) {
                defaultLocal = SVNRevision.WORKING;
            }
            if (defaultRemote == null) {
                defaultRemote = SVNRevision.HEAD;
            }
            return isURL() ? defaultRemote : defaultLocal;
        }
        
        return getPegRevision();
    }
    
    private void setPegRevision(SVNRevision revision) {
        if (revision == null) {
            revision = SVNRevision.UNDEFINED;
        }
        this.pegRevision = revision;
    }
    
    public String toString() {
        if (isFile()) {
            return getFile().getAbsolutePath() + '@' + getPegRevision();
        } else if (isURL()) {
            return getURL().toString() + '@' + getPegRevision();
        }
        return "INVALID TARGET";
    }
}
