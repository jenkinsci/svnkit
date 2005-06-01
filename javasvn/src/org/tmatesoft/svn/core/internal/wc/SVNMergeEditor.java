/*
 * Created on 30.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.diff.ISVNRAData;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

public class SVNMergeEditor implements ISVNEditor {

    private SVNRepository myRepos;
    private long myRevision1;
    private long myRevision2;
    private SVNWCAccess myWCAccess;
    private long myTargetRevision;
    private String myTarget;
    
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private SVNMerger myMerger;

    public SVNMergeEditor(SVNWCAccess wcAccess, SVNRepository repos, long revision1, long revision2, 
            SVNMerger merger) {
        myRepos = repos;
        myRevision1 = revision1;
        myRevision2 = revision2;
        myWCAccess = wcAccess;
        myMerger = merger;
        myTarget = "".equals(myWCAccess.getTargetName()) ? null : myWCAccess.getTargetName();
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(null, "", false);
        myCurrentDirectory.myWCPath = myTarget != null ? myTarget : "";
        
        myCurrentDirectory.myBaseProperties = new HashMap();
        myRepos.getDir("", myRevision1, myCurrentDirectory.myBaseProperties, (Collection) null);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        SVNNodeKind nodeKind = myRepos.checkPath(path, myRevision1);
        SVNEventAction action = SVNEventAction.SKIP;
        SVNStatusType mergeResult = null;

        
        if (nodeKind == SVNNodeKind.FILE) {
            mergeResult = myMerger.fileDeleted(path);
        } else if (nodeKind == SVNNodeKind.DIR) {
            path = PathUtil.append(myWCAccess.getTargetName(), path);
            path = PathUtil.removeLeadingSlash(path);
            mergeResult = myMerger.directoryDeleted(path);
        }
        if (mergeResult != SVNStatusType.OBSTRUCTED && mergeResult != SVNStatusType.MISSING) {
            action = SVNEventAction.UPDATE_DELETE;
        }
        SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess, path, action, null, null);
        myWCAccess.svnEvent(event, ISVNEventListener.UNKNOWN);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.myBaseProperties = Collections.EMPTY_MAP;

        String wcPath = PathUtil.append(myTarget, path);
        wcPath = PathUtil.removeLeadingSlash(wcPath);
        wcPath = PathUtil.removeTrailingSlash(wcPath);
        myCurrentDirectory.myWCPath = wcPath;
        
        // merge dir added.
        SVNEventAction action = SVNEventAction.ADD;
        SVNStatusType mergeResult = myMerger.directoryAdded(myCurrentDirectory.myWCPath, myRevision2); 
        if (mergeResult == SVNStatusType.MISSING || mergeResult == SVNStatusType.OBSTRUCTED) {
            action = SVNEventAction.SKIP;
        }
        SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess, path, action, null, null);
        myWCAccess.svnEvent(event, ISVNEventListener.UNKNOWN);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, false);

        myCurrentDirectory.myBaseProperties = new HashMap();
        String wcPath = PathUtil.append(myTarget, path);
        wcPath = PathUtil.removeLeadingSlash(wcPath);
        wcPath = PathUtil.removeTrailingSlash(wcPath);
        myCurrentDirectory.myWCPath = wcPath;
        
        myRepos.getDir(path, myRevision1, myCurrentDirectory.myBaseProperties, (Collection) null);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        if (name == null || name.startsWith(SVNProperty.SVN_WC_PREFIX) ||
                name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentDirectory.myPropertyDiff == null) {
            myCurrentDirectory.myPropertyDiff = new HashMap();
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
    }

    public void closeDir() throws SVNException {
        SVNStatusType propStatus = SVNStatusType.UNCHANGED;
        if (myCurrentDirectory.myPropertyDiff != null) {
            SVNDirectory dir = myWCAccess.getDirectory(myCurrentDirectory.myWCPath);
            if (dir == null) {
                SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess, myCurrentDirectory.myWCPath, SVNEventAction.SKIP, null, null);
                myWCAccess.svnEvent(event, ISVNEventListener.UNKNOWN);
                myCurrentDirectory = myCurrentDirectory.myParent;
                return;
            } else {
                // no need to do this if it is dry run?
                propStatus = myMerger.directoryPropertiesChanged(myCurrentDirectory.myWCPath,
                        myCurrentDirectory.myBaseProperties, myCurrentDirectory.myPropertyDiff);
                
            }
        }
        if (propStatus != SVNStatusType.UNCHANGED) {
            SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess, myCurrentDirectory.myWCPath, SVNEventAction.UPDATE_UPDATE, null, propStatus);
            myWCAccess.svnEvent(event, ISVNEventListener.UNKNOWN);
        }
        myCurrentDirectory = myCurrentDirectory.myParent;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);

        myCurrentFile = new SVNFileInfo(myCurrentDirectory, path, true);
        myCurrentFile.myBaseProperties = Collections.EMPTY_MAP;
        myCurrentFile.myBaseFile = myMerger.getFile(myCurrentFile.myWCPath, true);
        try {
            myCurrentFile.myBaseFile.createNewFile();
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
        myCurrentFile.myFile = myMerger.getFile(myCurrentFile.myWCPath, false);
        try {
            myCurrentFile.myFile.createNewFile();
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        DebugLog.log("open file: " + path);
        DebugLog.log("target: " + myTarget);
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        
        myCurrentFile = new SVNFileInfo(myCurrentDirectory, path, false);
        myCurrentFile.myBaseFile = myMerger.getFile(myCurrentFile.myWCPath, true); 

        myCurrentFile.loadFromRepository(myCurrentFile.myBaseFile, myRepos, myRevision1);
        myCurrentFile.myFile = myMerger.getFile(myCurrentFile.myWCPath, false);
        try {
            myCurrentFile.myFile.createNewFile();
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
    }


    public void changeFileProperty(String name, String value) throws SVNException {
        if (name == null || name.startsWith(SVNProperty.SVN_WC_PREFIX) ||
                name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentFile.myPropertyDiff == null) {
            myCurrentFile.myPropertyDiff = new HashMap();            
        }
        myCurrentFile.myPropertyDiff.put(name, value);
    }
    
    public void applyTextDelta(String baseChecksum) throws SVNException {
        myCurrentFile.myDiffWindows = new ArrayList();
        myCurrentFile.myDataFiles = new ArrayList();
    }
    
    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        myCurrentFile.myDiffWindows.add(diffWindow);
        File chunkFile = SVNFileUtil.createUniqueFile(myCurrentFile.myBaseFile.getParentFile(), PathUtil.tail(myCurrentFile.myPath), ".chunk");
        myCurrentFile.myDataFiles.add(chunkFile);
        OutputStream os = null;
        try {
            os = new FileOutputStream(chunkFile);
        } catch (FileNotFoundException e) {
            SVNErrorManager.error(0, e);
        }
        return os;
    }

    public void textDeltaEnd() throws SVNException {
        int index = 0;
        File baseTmpFile = myCurrentFile.myBaseFile;
        File targetFile = myCurrentFile.myFile;
        ISVNRAData baseData = new SVNRAFileData(baseTmpFile, true);
        ISVNRAData target = new SVNRAFileData(targetFile, false);
        for (int i = 0; i < myCurrentFile.myDiffWindows.size(); i++) {
            SVNDiffWindow window = (SVNDiffWindow) myCurrentFile.myDiffWindows.get(i);
            File dataFile = (File) myCurrentFile.myDataFiles.get(i);
            InputStream data = null;
            try {
                data = new FileInputStream(dataFile);
                window.apply(baseData, target, data, target.length());
            } catch (FileNotFoundException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (data != null) {
                    try {
                        data.close();
                    } catch (IOException e) {
                    }
                }
            }
            dataFile.delete();
            index++;
        }
        try {
            target.close();
            baseData.close();
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
    }

    public void closeFile(String textChecksum) throws SVNException {
        SVNDirectory dir = myWCAccess.getDirectory(myCurrentDirectory.myWCPath);
        if (dir == null) {
            SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess, myCurrentFile.myWCPath, SVNEventAction.SKIP, null, null);
            myWCAccess.svnEvent(event, ISVNEventListener.UNKNOWN);
        } else {
            SVNStatusType contents = SVNStatusType.UNCHANGED;
            SVNStatusType props = SVNStatusType.UNCHANGED;
            if (myCurrentFile.myPropertyDiff != null || myCurrentFile.myFile != null) {
                String mimeType1 = (String) myCurrentFile.myBaseProperties.get(SVNProperty.MIME_TYPE);
                String mimeType2 = myCurrentFile.myPropertyDiff != null ? (String) myCurrentFile.myPropertyDiff.get(SVNProperty.MIME_TYPE) : null;
                if (mimeType2 == null) {
                    mimeType2 = mimeType1;
                }
                SVNStatusType[] result = null;
                if (myCurrentFile.myIsAdded) {
                    result = myMerger.fileAdded(myCurrentFile.myWCPath, myCurrentFile.myBaseFile, myCurrentFile.myFile, myRevision1, myRevision2,
                            mimeType1, mimeType2, myCurrentFile.myBaseProperties, myCurrentFile.myPropertyDiff);
                    DebugLog.log("file added, merge result: " + result[0]);
                } else {
                    result = myMerger.fileChanged(myCurrentFile.myWCPath, myCurrentFile.myBaseFile, myCurrentFile.myFile, myRevision1, myRevision2,
                            mimeType1, mimeType2, myCurrentFile.myBaseProperties, myCurrentFile.myPropertyDiff);
                    DebugLog.log("file modified, merge result: " + result[0]);
                }
                contents = result[0];
                props = result[1];
            }
            
            DebugLog.log("close file, merge result: " + contents + ":" + props);
            SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess, myCurrentFile.myWCPath, SVNEventAction.UPDATE_UPDATE, contents, props);
            myWCAccess.svnEvent(event, ISVNEventListener.UNKNOWN);
        }
        if (myCurrentFile.myFile != null) {
            myCurrentFile.myFile.delete();
        }
        if (myCurrentFile.myBaseFile != null) {
            myCurrentFile.myBaseFile.delete();
        }
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
    
    private static class SVNDirectoryInfo {
        
        public SVNDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) {
            myParent = parent;
            myPath = path;
            myIsAdded = added;
        }
        
        private boolean myIsAdded;
        private String myPath;
        private File myFile;
        private String myWCPath;
        
        private Map myBaseProperties;
        private Map myPropertyDiff;
        
        private SVNDirectoryInfo myParent;
    }

    private static class SVNFileInfo {

        public SVNFileInfo(SVNDirectoryInfo parent, String path, boolean added) {
            myParent = parent;
            myPath = path;
            myWCPath = PathUtil.append(parent.myWCPath, PathUtil.tail(path));
            myWCPath = PathUtil.removeLeadingSlash(myWCPath);
            myWCPath = PathUtil.removeTrailingSlash(myWCPath);
            myIsAdded = added;            
        }
        
        public void loadFromRepository(File dst, SVNRepository repos, long revision) throws SVNException {
            OutputStream os = null;
            try {
                os = new FileOutputStream(dst);
                myBaseProperties = new HashMap();
                repos.getFile(myPath, revision, myBaseProperties, os);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        
        private boolean myIsAdded;
        private String myWCPath;
        private String myPath;
        private File myFile;
        private File myBaseFile;
        
        private Map myBaseProperties;
        private Map myPropertyDiff;

        private SVNDirectoryInfo myParent;
        
        private List myDiffWindows;
        private List myDataFiles;
    }
}
