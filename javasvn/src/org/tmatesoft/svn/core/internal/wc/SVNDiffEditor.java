/*
 * Created on 26.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.util.PathUtil;

public class SVNDiffEditor implements ISVNEditor {

    private SVNWCAccess myWCAccess;
    private ISVNDiffGenerator myDiffGenerator;
    private boolean myUseAncestry;
    private boolean myIsReverseDiff;
    private OutputStream myResult;

    private boolean myIsRootOpen;
    private long myTargetRevision;
    private SVNDirectoryInfo myCurrentDirectory;

    public SVNDiffEditor(SVNWCAccess wcAccess, ISVNDiffGenerator diffGenerator, boolean useAncestry,
            boolean reverseDiff, OutputStream result) {
        myWCAccess = wcAccess;
        myDiffGenerator = diffGenerator;
        myUseAncestry = useAncestry;
        myIsReverseDiff = reverseDiff;
        myResult = result;
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirInfo(null, "", false);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        
    }   

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, true);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, false);
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        if (myCurrentDirectory.myPropertyDiff == null) {
            myCurrentDirectory.myPropertyDiff = new HashMap();
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
        if (myCurrentDirectory.myBaseProperties == null) {
            SVNDirectory dir = myWCAccess.getDirectory(myCurrentDirectory.myPath);
            if (dir != null) {
                myCurrentDirectory.myBaseProperties = dir.getBaseProperties("", false).asMap();
            } else {
                myCurrentDirectory.myBaseProperties = new HashMap();
            }
        }
    }

    public void closeDir() throws SVNException {
        // display base->wc diff????
        
        // display dir prop changes.
        Map diff = myCurrentDirectory.myPropertyDiff;
        Map base = myCurrentDirectory.myBaseProperties;
        if (diff != null && !diff.isEmpty()) {
            // reverse changes
            if (!myIsReverseDiff) {
                reversePropChanges(base, diff);
            }
            myDiffGenerator.displayPropDiff(myCurrentDirectory.myPath, base, diff, myResult);
        }
        String name = PathUtil.tail(myCurrentDirectory.myPath);
        myCurrentDirectory = myCurrentDirectory.myParent;
        if (myCurrentDirectory != null) {
            myCurrentDirectory.myComparedEntries.add(name);
        }
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void changeFileProperty(String name, String value)
            throws SVNException {
    }

    public void applyTextDelta(String baseChecksum) throws SVNException {
    }

    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow)
            throws SVNException {
        return null;
    }

    public void textDeltaEnd() throws SVNException {
    }

    public void closeFile(String textChecksum) throws SVNException {
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }
    
    private SVNDirectoryInfo createDirInfo(SVNDirectoryInfo parent, String path, boolean added) {
        SVNDirectoryInfo info = new SVNDirectoryInfo();
        if (!"".equals(path)) {
            path = PathUtil.removeLeadingSlash(path);
            path = PathUtil.removeTrailingSlash(path);
        }
        info.myParent = parent;
        info.myPath = path;
        info.myIsAdded = added;
        return info;
    }
    
    private class SVNDirectoryInfo {
        
        private boolean myIsAdded;
        private String myPath;
        private File myFile;
        
        private Map myBaseProperties;
        private Map myPropertyDiff;
        
        private SVNDirectoryInfo myParent;
        private Set myComparedEntries = new HashSet();
    }

    private class SVNFileInfo {
        
        private boolean myIsAdded;
        private String myPath;
        private File myFile;
        
        private Map myBaseProperties;
        private Map myPropertyDiff;
        
        private boolean myIsScheduledForDeletion;
        private SVNDirectory myParent;
    }
    
    private static void reversePropChanges(Map base, Map diff) {
        Collection namesList = new ArrayList(diff.keySet());
        for (Iterator names = namesList.iterator(); names.hasNext();) {
            String name = (String) names.next();
            String newValue = (String) diff.get(name);
            String oldValue = (String) base.get(name);
            if (oldValue == null && newValue != null) {
                base.put(name, newValue);
                diff.put(name, null);
            } else if (oldValue != null && newValue == null) {
                base.put(name, null);
                diff.put(name, oldValue);
            } else if (oldValue != null && newValue != null) {
                base.put(name, newValue);
                diff.put(name, oldValue);
            }
        }
    }
}
