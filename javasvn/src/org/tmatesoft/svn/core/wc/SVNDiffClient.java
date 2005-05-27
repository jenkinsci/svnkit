/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
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
        doDiff(path, SVNRevision.BASE, SVNRevision.WORKING, null, recursive, useAncestry, result);
    }
    
    public void doDiff(File path, SVNRevision rN, SVNRevision rM, SVNRevision pegRev,
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
        getDiffGenerator().init(path.getAbsolutePath(), path.getAbsolutePath());
        try {
            if (rN == SVNRevision.BASE && rM == SVNRevision.WORKING) {
                // case 1.1            
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
                // case 2.4 (not supported)
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
                long revN = getRevisionNumber(path, rN);
                long revM = getRevisionNumber(path, rM);
                url = getURL(url, pegRev, rN);
                // TODO call url:url version of diff.            }
            }
        } finally {
            wcAccess.close(true, recursive);
        }
    }
}
