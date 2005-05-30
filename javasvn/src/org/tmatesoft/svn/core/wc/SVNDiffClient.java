/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNRemoteDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

public class SVNDiffClient extends SVNBasicClient {

    private ISVNDiffGenerator myDiffGenerator;

    public SVNDiffClient(final ISVNCredentialsProvider credentials, ISVNEventListener eventDispatcher) {
        super(new ISVNRepositoryFactory() {
            public SVNRepository createRepository(String url) throws SVNException {
                SVNRepository repos = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
                repos.setCredentialsProvider(credentials);
                return repos;
            }
        }, null, eventDispatcher);
    }

    public SVNDiffClient(ISVNRepositoryFactory repositoryFactory,
            SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }
    
    public void setDiffGenerator(ISVNDiffGenerator diffGenerator) {
        myDiffGenerator = diffGenerator;
    }
    
    public ISVNDiffGenerator getDiffGenerator() {
        if (myDiffGenerator == null) {
            myDiffGenerator = new DefaultSVNDiffGenerator();
        }
        return myDiffGenerator;
    }
    
    public void doDiff(File path, boolean recursive, final boolean useAncestry, final OutputStream result) throws SVNException {
        doDiff(path, SVNRevision.BASE, SVNRevision.WORKING, recursive, useAncestry, result);
    }
    
    public void doDiff(String url, SVNRevision pegRevision, File path, SVNRevision rN, SVNRevision rM,
            boolean recursive, final boolean useAncestry, final OutputStream result) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(path);
        String url2;
        try {
            url2 = wcAccess.getTargetEntryProperty(SVNProperty.URL);
        } finally {
            wcAccess.close(true, false);
        }
        String rootPath = wcAccess.getAnchor().getRoot().getAbsolutePath();
        // by default use no base path.
        getDiffGenerator().init(rootPath, rootPath);
        getDiffGenerator().setBasePath(wcAccess.getAnchor().getRoot());
        doDiff(url, pegRevision, url2, SVNRevision.UNDEFINED, rN, rM, recursive, useAncestry, result);
    }

    public void doDiff(File path, String url, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM,
            boolean recursive, final boolean useAncestry, final OutputStream result) throws SVNException {    
        SVNWCAccess wcAccess = SVNWCAccess.create(path);
        String url1;
        try {
            url1 = wcAccess.getTargetEntryProperty(SVNProperty.URL);
        } finally {
            wcAccess.close(true, false);
        }
        String rootPath = wcAccess.getAnchor().getRoot().getAbsolutePath();
        // by default use no base path.
        getDiffGenerator().init(rootPath, rootPath);
        getDiffGenerator().setBasePath(wcAccess.getAnchor().getRoot());
        doDiff(url1, SVNRevision.UNDEFINED, url, pegRevision, rN, rM, recursive, useAncestry, result);
    }

    public void doDiff(File path, File path2, SVNRevision rN, SVNRevision rM,
            boolean recursive, final boolean useAncestry, final OutputStream result) throws SVNException {
        rN = rN == null ? SVNRevision.UNDEFINED : rN;
        rM = rM == null ? SVNRevision.UNDEFINED : rM;
        if (rM == SVNRevision.WORKING || rN == SVNRevision.BASE ||
                rN == SVNRevision.WORKING || rN == SVNRevision.BASE) {
            if (path.equals(path2)) {
                doDiff(path, rN, rM, recursive, useAncestry, result);
                return;
            }
            SVNErrorManager.error("svn: invalid diff revisions range: '" + rN + ":" + rM + "'");
        }
        if (rN == SVNRevision.UNDEFINED) {
            rN = SVNRevision.HEAD;
        }
        if (rM == SVNRevision.UNDEFINED) {
            rM = SVNRevision.HEAD;
        }
        SVNWCAccess wcAccess = SVNWCAccess.create(path);
        String url1;
        try {
            url1 = wcAccess.getTargetEntryProperty(SVNProperty.URL);
        } finally {
            wcAccess.close(true, false);
        }

        SVNWCAccess wcAccess2 = SVNWCAccess.create(path2);
        String rootPath = wcAccess2.getAnchor().getRoot().getAbsolutePath();
        getDiffGenerator().init(rootPath, rootPath);
        getDiffGenerator().setBasePath(wcAccess2.getAnchor().getRoot());
        String url2;
        try {
            url2 = wcAccess2.getTargetEntryProperty(SVNProperty.URL);
        } finally {
            wcAccess.close(true, false);
        }
        long revN = getRevisionNumber(path, rN);
        long revM = getRevisionNumber(path, rM);
        doDiff(url1, SVNRevision.UNDEFINED, url2, SVNRevision.UNDEFINED, SVNRevision.create(revN), SVNRevision.create(revM), 
                recursive, useAncestry, result);
    }

    public void doDiff(String url1, SVNRevision pegRevision1, String url2, SVNRevision pegRevision2, SVNRevision rN, SVNRevision rM,
            boolean recursive, final boolean useAncestry, final OutputStream result) throws SVNException {
        rN = rN == null ? SVNRevision.HEAD : rN;
        rM = rM == null ? SVNRevision.HEAD : rM;
        if (rN != SVNRevision.HEAD || rN.getNumber() < 0 || rN.getDate() == null) {
            SVNErrorManager.error("svn: invalid revision: '" + rN + "'");
        }
        if (rM != SVNRevision.HEAD || rM.getNumber() < 0 || rM.getDate() == null) {
            SVNErrorManager.error("svn: invalid revision: '" + rM + "'");
        }
        url1 = validateURL(url1);
        url2 = validateURL(url2);
        
        pegRevision1 = pegRevision1 == null ? SVNRevision.UNDEFINED : pegRevision1;
        pegRevision2 = pegRevision2 == null ? SVNRevision.UNDEFINED : pegRevision2;
        url1 = getURL(url1, pegRevision1, rN);
        url2 = getURL(url2, pegRevision2, rM);
        final long revN = getRevisionNumber(url1, rN);
        final long revM = getRevisionNumber(url1, rM);
        
        SVNRepository repos = createRepository(url1);
        SVNNodeKind nodeKind = repos.checkPath("", revN);
        if (nodeKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("'" + url1 + "' was not found in the repository at revision " + revN);
        }
        SVNRepository repos2 = createRepository(url2);
        SVNNodeKind nodeKind2 = repos2.checkPath("", revM);
        if (nodeKind2 == SVNNodeKind.NONE) {
            SVNErrorManager.error("'" + url2 + "' was not found in the repository at revision " + revM);
        }
        String target = null;
        if (nodeKind == SVNNodeKind.FILE || nodeKind2 == SVNNodeKind.FILE) {
            target = PathUtil.tail(url1);
            target = PathUtil.decode(target);
            url1 = PathUtil.removeTail(url1);
            repos = createRepository(url1);
        }
        File tmpFile = getDiffGenerator().getTempDirectory();
        try {
            SVNRemoteDiffEditor editor = new SVNRemoteDiffEditor(getDiffGenerator().getTempDirectory(), 
                    getDiffGenerator(), repos, revN, result);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, revN, false);
                    reporter.finishReport();
                }            
            };
            repos = createRepository(url1);
            repos.diff(url2, revM, target, !useAncestry, recursive, reporter, editor);
        } finally {
            if (tmpFile != null) {
                DebugLog.log("deleting: " + tmpFile);
                SVNFileUtil.deleteAll(tmpFile);
            }
        }
    }
    
    // url:url should use 'merge' editor with diff generation callback.
    // merge editor collects diff bewtween two revs and creates resulting files
    
    // (file@rev1 and file@rev2)
    // then these files could be merged with corresponding wc files. 
    
    public void doDiff(File path, SVNRevision rN, SVNRevision rM,
            boolean recursive, final boolean useAncestry, final OutputStream result) throws SVNException {
        if (rN == null || rN == SVNRevision.UNDEFINED) {
            rN = SVNRevision.BASE;
        }
        if (rM == null || rM == SVNRevision.UNDEFINED) {
            rM = SVNRevision.WORKING;
        }
        // cases:
        // 1.1 wc-wc: BASE->WORKING
        // 1.2 wc-wc: WORKING->BASE (reversed to 1.1)
        
        // 2.1 wc-url: BASE:REV
        // 2.2 wc-url: WORKING:REV
        // 2.3 wc-url: REV:BASE     (reversed to 2.1)
        // 2.4 wc-url: REV:WORKING  (reversed to 2.2)
        
        // 3.1 url-url: REV:REV
        
        // path should always point to valid wc dir or file.
        // for 'REV' revisions there could be also 'peg revision' defined, used to get real WC url.
        
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(true, recursive);
        path = path.getAbsoluteFile();
        getDiffGenerator().init(path.getAbsolutePath(), path.getAbsolutePath());
        try {
            if (rN == SVNRevision.BASE && rM == SVNRevision.WORKING) {
                // case 1.1
                if (!"".equals(wcAccess.getTargetName())) {
                    if (wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName()) == null) {
                        SVNErrorManager.error("svn: path '" + path.getAbsolutePath() + "' is not under version control");
                    }
                }
                SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(), useAncestry, false, false, result);
                editor.closeEdit();
            } else if (rN == SVNRevision.WORKING && rM == SVNRevision.BASE) {
                // case 1.2 (not supported)
                SVNErrorManager.error("svn: not supported diff revisions range: '" + rN + ":" + rM + "'");
            } else if (rN == SVNRevision.BASE) {
                // case 2.1 (BASE->REV)
                String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                long revNumber = getRevisionNumber(url, rM);
                url = wcAccess.getAnchor().getEntries().getEntry("").getURL();
                
                String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
                SVNRepository repos = createRepository(url);
                SVNReporter reporter = new SVNReporter(wcAccess, recursive);
                
                SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(), useAncestry, 
                        true /*reverse*/, true /*compare to base*/, result);
                String targetURL = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                targetURL = PathUtil.decode(targetURL);
                repos.diff(targetURL, revNumber, target, !useAncestry, recursive, reporter, editor);
            } else if (rN == SVNRevision.WORKING) {
                // case 2.2 (WORKING->REV)
                String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                long revNumber = getRevisionNumber(url, rM);

                url = wcAccess.getAnchor().getEntries().getEntry("").getURL();
                SVNRepository repos = createRepository(url);
                
                String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
                SVNReporter reporter = new SVNReporter(wcAccess, recursive);
                
                SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(), useAncestry, 
                        true /*reverse*/, false /*compare to base*/, result);
                String targetURL = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                targetURL = PathUtil.decode(targetURL);
                repos.diff(targetURL, revNumber, target, !useAncestry, recursive, reporter, editor);
            } else if (rM == SVNRevision.BASE) {
                // case 2.3
                String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                long revNumber = getRevisionNumber(url, rN);
                url = wcAccess.getAnchor().getEntries().getEntry("").getURL();
                
                String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
                SVNRepository repos = createRepository(url);
                SVNReporter reporter = new SVNReporter(wcAccess, recursive);
                
                SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(), useAncestry, 
                        false /*reverse*/, true /*compare to base*/, result);
                String targetURL = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                targetURL = PathUtil.decode(targetURL);
                repos.diff(targetURL, revNumber, target, !useAncestry, recursive, reporter, editor);
            } else if (rM == SVNRevision.WORKING) {
                // case 2.4 
                String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                long revNumber = getRevisionNumber(url, rN);
                
                url = wcAccess.getAnchor().getEntries().getEntry("").getURL();
                String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
                SVNRepository repos = createRepository(url);
                SVNReporter reporter = new SVNReporter(wcAccess, recursive);
                
                SVNDiffEditor editor = new SVNDiffEditor(wcAccess, getDiffGenerator(), useAncestry, 
                        false /*reverse*/, false /*compare to base*/, result);
                String targetURL = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                targetURL = PathUtil.decode(targetURL);
                repos.diff(targetURL, revNumber, target, !useAncestry, recursive, reporter, editor);
            } else {
                String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
                String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
                final long revN = getRevisionNumber(path, rN);
                long revM = getRevisionNumber(path, rM);

                // prepend paths with anchor.
                getDiffGenerator().setBasePath(wcAccess.getAnchor().getRoot());
                // and then trunkate root.
                File tmpRoot = SVNFileUtil.createUniqueFile(wcAccess.getAnchor().getRoot(), ".diff", ".tmp");
                tmpRoot.mkdirs();                
                try {
                    SVNRepository repos1 = createRepository(url);
                    SVNRepository repos2 = createRepository(url);
                    SVNRemoteDiffEditor editor = new SVNRemoteDiffEditor(tmpRoot, getDiffGenerator(), repos1,
                            revN, result);
                    url = PathUtil.decode(url);
                    repos2.diff(url, revM, target, !useAncestry, recursive, new ISVNReporterBaton() {
                        public void report(ISVNReporter reporter) throws SVNException {
                            reporter.setPath("", null, revN, false);
                            reporter.finishReport();
                        }
                    }, editor);
                } finally {
                    if (tmpRoot != null) {
                        SVNFileUtil.deleteAll(tmpRoot);
                    }
                }
            }
        } finally {
            wcAccess.close(true, recursive);
        }
    }
}
