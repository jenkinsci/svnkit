/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;

class SVNReporter implements ISVNReporterBaton {
    
    private ISVNAdminArea myRootAdminArea;
    private String myTarget;
    private boolean myIsRecursive;

    public SVNReporter(ISVNAdminArea root, String target, boolean recursive) {
        myRootAdminArea = root;
        myTarget = target;
        myIsRecursive = recursive;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        if (myTarget != null) {
            restoreEntry(myRootAdminArea, myTarget);
            // report single target.
            ISVNEntries entries = myRootAdminArea.getEntries();
            String lockToken = entries.getProperty(myTarget, SVNProperty.LOCK_TOKEN);
            long revision = SVNProperty.longValue(entries.getProperty(myTarget, SVNProperty.REVISION));
            if (revision < 0) {
                revision = SVNProperty.longValue(entries.getProperty("", SVNProperty.REVISION));
            }
            reporter.setPath("", lockToken, revision, false);
            if (SVNProperty.booleanValue(entries.getProperty(myTarget, SVNProperty.DELETED))) {
                reporter.deletePath("");
            } else if (isSwitched(entries, myTarget)) {
                SVNRepositoryLocation url = SVNRepositoryLocation.parseURL(entries.getProperty(myTarget, SVNProperty.URL));
                reporter.linkPath(url, "", lockToken, revision, false);
            }
        } else {
            // report everething recursively.
        }
        reporter.finishReport();
    }
    
    private boolean isSwitched(ISVNEntries entries, String name) {
        String url = entries.getProperty(name, SVNProperty.URL);
        String expectedURL = entries.getProperty("", SVNProperty.URL);
        expectedURL = PathUtil.append(expectedURL, PathUtil.encode(url));
        
        return !url.equals(expectedURL);
    }
    
    private void restoreEntry(ISVNAdminArea dir, String name) {
        File entryFile = dir.getTextFile(name);
        File baseFile = dir.getBaseFile(name);
        File tmpFile = dir.getTmpBaseFile(name);
        
        if (entryFile.exists()) {
            return;
        }
        ISVNEntries entries = dir.getEntries();
        
        boolean special = entries.getProperty(name, SVNProperty.SPECIAL) != null;
        byte[] eol = special ? null : SVNTranslator.getEOL(entries.getProperty(name, SVNProperty.EOL_STYLE));
        Map keywords = special ? null : SVNTranslator.computeKeywords(entries, name);
        try {
            SVNTranslator.translate(baseFile, tmpFile, eol, keywords, special);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            tmpFile.delete();
        }
        boolean executable = entries.getProperty(name, SVNProperty.EXECUTABLE) != null;        
        // mark readonly, executable, etc.
        // mark resolved.
        
        // update file tstamp and entries text-time.
        // rename file to correct place.
        // notify client.
    }
}
