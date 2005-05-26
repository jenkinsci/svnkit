/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.ISVNCrawler;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;

public class SVNDiffClient extends SVNBasicClient {

    private ISVNDiffGenerator myDiffGenerator;

    public SVNDiffClient(ISVNRepositoryFactory repositoryFactory,
            SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }
    
    public void setDiffGenerator(ISVNDiffGenerator diffGenerator) {
        myDiffGenerator = diffGenerator;
    }
    
    protected ISVNDiffGenerator getDiffGenerator() {
        if (myDiffGenerator == null) {
            myDiffGenerator = new DefaultSVNDiffGenerator();
        }
        return myDiffGenerator;
    }
    
    public void doDiff(File path, boolean recursive, final boolean useAncestry, boolean force, final OutputStream result) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(true, recursive);
        try {
            // check for entry in anchor.
            if (!"".equals(wcAccess.getTargetName())) {
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName());
                if (entry == null) {
                    SVNErrorManager.error(0, null);
                }
            }
            getDiffGenerator().init(getDiffGenerator().getDisplayPath(path), 
                    getDiffGenerator().getDisplayPath(path));
            getDiffGenerator().setForcedBinaryDiff(force);
            wcAccess.visitDirectories(new ISVNCrawler() {
                public void visitDirectory(SVNWCAccess owner, SVNDirectory dir) throws SVNException {
                    localDirectoryDiff(owner, dir, useAncestry, result);
                }
            });
        } finally {
            wcAccess.close(true, recursive);
        }
        // diff externals?
    }

    public void doDiff(File path, SVNRevision revision1, SVNRevision revision2, 
            boolean recursive, boolean useAncestry, boolean force,
            OutputStream result) throws SVNException {
        if (revision1 == null || revision1 == SVNRevision.UNDEFINED) {
            revision1 = SVNRevision.BASE;
        }
        if (revision2 == null || revision2 == SVNRevision.UNDEFINED) {
            revision2 = SVNRevision.WORKING;
        }
        if (revision1 == revision2) {
            // nothing to compare.
            return;
        } else if (revision1 == SVNRevision.BASE && revision2 == SVNRevision.WORKING) {
            // case0: r1 == BASE, r2 == WORKING => wc:wc diff
            doDiff(path, recursive, useAncestry, force, result);
        } else if (revision1 == SVNRevision.BASE && revision2 != SVNRevision.WORKING) {
            // case1.1: r1 == BASE, r2 != WORKING => wc:url diff
        } else if (revision2 == SVNRevision.BASE && revision1 != SVNRevision.WORKING) {
            // case1.2: r1 != WORKING, r2 == BASE => url:wc diff
        } else if (revision1 != SVNRevision.BASE && revision1 != SVNRevision.WORKING 
                && revision2 != SVNRevision.BASE && revision2 != SVNRevision.WORKING) {
            // case2: url:url diff.
        } else {
            // not valid case.
            SVNErrorManager.error("invalid revisions range: " + revision1 + ":" + revision2);
        }
    }

    // wc-repos: only BASE:REV is supported, base is compared to rev
    // run repos.diff BASE_URL:REPOS_URL, create tmp files when needed.
    // iterate over wc comparing base files to tmp files.
    public void doDiff(File path, String url, SVNRevision pegRevision, SVNRevision revision, 
            boolean recursive, boolean useAncestry, boolean force,
            OutputStream result) {
    }
    
    // repos-repos: REV:REV
    // run diff, apply received deltas to OLD_URL:REV files (tmp) and copy result to tmp2, 
    // run diff between tmp and tmp2 files.
    public void doDiff(String oldURL, SVNRevision oldPegRevision, SVNRevision oldRevision, 
            String newURL, SVNRevision newPegRevision, SVNRevision newRevision, 
            boolean recursive, boolean useAncestry, boolean force,
            OutputStream result) {
        
    }
    
    private void localDirectoryDiff(SVNWCAccess owner, SVNDirectory dir, boolean useAncestry, OutputStream result) throws SVNException {
        boolean anchor = !"".equals(owner.getTargetName()) && owner.getAnchor() != owner.getTarget() && dir == owner.getAnchor();
        
        if (!anchor) {
            // generate prop diff for dir.
            if (dir.hasPropModifications("")) {
                SVNProperties baseProps = dir.getBaseProperties("", false);
                Map propDiff = baseProps.compareTo(dir.getProperties("", false));
                String displayPath = getDiffGenerator().getDisplayPath(dir.getRoot());
                getDiffGenerator().displayPropDiff(displayPath, baseProps.asMap(), propDiff, result);
            }
        }
        SVNEntries svnEntries = dir.getEntries();
        for (Iterator entries = svnEntries.entries(); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (entry.isDirectory() || entry.isHidden()) {
                continue;
            }
            if (anchor && !owner.getTargetName().equals(entry.getName())) {
                continue;
            }
            String name = entry.getName();
            boolean added = entry.isScheduledForAddition();
            boolean replaced = entry.isScheduledForReplacement();
            boolean deleted = entry.isScheduledForDeletion();
            boolean copied = entry.isCopied();
            if (copied) {
                added = false;
                deleted = false;
                replaced = false;
            }
            if (replaced && !useAncestry) {
                replaced = false;
            }
            SVNProperties props = dir.getProperties(name, false);
            String fullPath = getDiffGenerator().getDisplayPath(dir.getFile(name, false));
            Map baseProps = dir.getBaseProperties(name, false).asMap();
            Map propDiff = null;
            if (!deleted && dir.hasPropModifications(name)) {
                propDiff = dir.getBaseProperties(name, false).compareTo(dir.getProperties(name, false));
            } 
            if (deleted || replaced) {
                // display text diff for deleted file.
                String mimeType1 = (String) baseProps.get(SVNProperty.MIME_TYPE);
                String rev1 = "(revision " + Long.toString(entry.getRevision()) + ")";
                getDiffGenerator().displayFileDiff(fullPath, dir.getBaseFile(name, false), dir.getFile(".svn/empty-file", false), rev1, null, mimeType1, null, result);
                if (deleted) {
                    return;
                }
            }
            File tmpFile = null;
            try {
                if (added || replaced) {
                    tmpFile = dir.getBaseFile(name, true);
                    SVNTranslator.translate(dir, name, name, SVNFileUtil.getBasePath(tmpFile), false, false);
                    // display text diff for added file.

                    String mimeType1 = null;
                    String mimeType2 = props.getPropertyValue(SVNProperty.MIME_TYPE);
                    String rev1 = "(revision " + Long.toString(entry.getRevision()) + ")";
                    String rev2 = rev1;

                    getDiffGenerator().displayFileDiff(fullPath, dir.getFile(".svn/empty-file", false), tmpFile, rev1, rev2, mimeType1, mimeType2, result);
                    if (propDiff != null && propDiff.size() > 0) {
                        // display prop diff.
                        getDiffGenerator().displayPropDiff(fullPath, baseProps, propDiff, result);
                    }
                    return;
                }
                boolean isTextModified = dir.hasTextModifications(name, false);
                if (isTextModified) {
                    tmpFile = dir.getBaseFile(name, true);
                    SVNTranslator.translate(dir, name, name, SVNFileUtil.getBasePath(tmpFile), false, false);

                    String mimeType1 = (String) baseProps.get(SVNProperty.MIME_TYPE);
                    String mimeType2 = props.getPropertyValue(SVNProperty.MIME_TYPE);
                    String rev1 = "(revision " + Long.toString(entry.getRevision()) + ")";
                    getDiffGenerator().displayFileDiff(fullPath, dir.getBaseFile(name, false), tmpFile, rev1, null, mimeType1, mimeType2, result);
                    if (propDiff != null && propDiff.size() > 0) {
                        getDiffGenerator().displayPropDiff(fullPath, baseProps, propDiff, result);
                    }
                }
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        }
        svnEntries.close();
    }   
}
