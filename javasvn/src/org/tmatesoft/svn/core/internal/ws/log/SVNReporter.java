/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;

class SVNReporter implements ISVNReporterBaton {
    
    private ISVNAdminArea myRootAdminArea;
    private String myTarget;
    private boolean myIsRecursive;
    private ISVNOptions myOptions;

    public SVNReporter(ISVNOptions options, ISVNAdminArea root, String target, boolean recursive) {
        myRootAdminArea = root;
        myTarget = target;
        myIsRecursive = recursive;
        myOptions = options;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        // get revision number for entry.
        ISVNEntries entries = myRootAdminArea.getEntries();
        ISVNEntry entry = entries.getEntry(myTarget);
        if (entry == null || 
            (entry.isDirectory() && entry.isAdded())) {
            ISVNEntry parentEntry = entries.getEntry("");
            long revision = parentEntry.getRevision(); 
            reporter.setPath("", null, revision, true);
            reporter.deletePath("");
            reporter.finishReport();
            return;
        }
        long revision = entry.getRevision();
        if (revision < 0) {
            // something strange (?). may be dir we are updating directory (?)
            // directory entries has no revisions.
            
            // should we update "" target when updating directory?
            // then we will have revision, but what if directory is scheduled and added?
            revision = SVNProperty.longValue(entries.getProperty("", SVNProperty.REVISION));
        }
        // if it is a file it will be a target.
/*        
        if (myTarget != null) {
            // what to do if the file is deleted or added?
            // what to do if it is not versioned?
            restoreEntry(myRootAdminArea, myTarget);
            // report single target.
            entries = myRootAdminArea.getEntries();
            String lockToken = entries.getProperty(myTarget, SVNProperty.LOCK_TOKEN);
            revision = SVNProperty.longValue(entries.getProperty(myTarget, SVNProperty.REVISION));
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
        */
        reporter.finishReport();
    }
    
    private boolean isSwitched(ISVNEntries entries, String name) {
        String url = entries.getProperty(name, SVNProperty.URL);
        String expectedURL = entries.getProperty("", SVNProperty.URL);
        expectedURL = PathUtil.append(expectedURL, PathUtil.encode(url));
        
        return !url.equals(expectedURL);
    }
    
    private void restoreEntry(ISVNAdminArea dir, String name) {
        /*
        File entryFile = dir.getTextFile(name);
        if (entryFile.exists()) {
            return;
        }

        File baseFile = dir.getBaseFile(name);
        File tmpFile = dir.getTmpBaseFile(name);        
        ISVNEntries entries = dir.getEntries();
        
        boolean special = entries.getProperty(name, SVNProperty.SPECIAL) != null;
        byte[] eol = special ? null : SVNTranslator.getEOL(entries.getProperty(name, SVNProperty.EOL_STYLE));
        Map keywords = special ? null : SVNTranslator.computeKeywords(entries, name, true);
        try {
            SVNTranslator.translate(baseFile, tmpFile, eol, keywords, special);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            tmpFile.delete();
        }*/
        // mark readonly, executable, etc.
        /*
        SVNTranslator.setExecutable(tmpFile, entries, name);
        SVNTranslator.setReadonly(tmpFile, entries, name);
        */
        // mark resolved if there are conflict files in ws.
        // update file tstamp and entries text-time.
        // rename file to correct place.
        // notify client.
    }
}
