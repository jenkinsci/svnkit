package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;

public class SVNUpdateEditor implements ISVNEditor {
    
    private long myTargetRevision;
    private boolean myIsRootOpen;
    private String myTarget;
    private String mySwitchURL;
    private String myTargetURL;
    
    private File myRootPath;
    private SVNDirectoryInfo myCurrentDirectory;

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirectoryInfo(null, null, false);
        if (myTarget == null) {
            SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
            SVNEntry entry = entries.getEntry("");
            entry.setRevision(myTargetRevision);
            entry.setURL(myCurrentDirectory.URL);
            entry.setIncomplete(true);
            entries.save(true);
        }
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void openDir(String path, long revision) throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void changeDirProperty(String name, String value) throws SVNException {
    }

    public void closeDir() throws SVNException {
        
        // 'complete' entries.

        // now run log if any
        
        // somehow register this dir to be 'bumped' and 'synced' at 'close-edit' 
        myCurrentDirectory.dispose();
        myCurrentDirectory = myCurrentDirectory.Parent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void changeFileProperty(String name, String value) throws SVNException {
    }

    public void applyTextDelta(String baseChecksum) throws SVNException {
    }

    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd() throws SVNException {
    }

    public void closeFile(String textChecksum) throws SVNException {
    }

    public void abortEdit() throws SVNException {
    }
    
    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNDirectoryInfo info = new SVNDirectoryInfo();
        info.Parent = parent;
        info.IsAdded = added;
        info.Path = parent != null ? new File(parent.Path, path) : myRootPath;
        info.Name = path != null ? PathUtil.tail(path) : "";

        if (mySwitchURL == null) {
            SVNDirectory dir = info.getDirectory();
            if (dir.getEntries().getEntry("") != null) {
                info.URL = dir.getEntries().getEntry("").getURL();
            }
            if (info.URL == null && parent != null) {
                info.URL = PathUtil.append(parent.URL, PathUtil.encode(info.Name));
            } else if (info.URL == null && parent == null) {
                info.URL = myTargetURL;
            }
        } else {
            if (parent == null) {
                info.URL = myTarget == null ? mySwitchURL : PathUtil.removeTail(mySwitchURL);
            } else {
                if (myTarget != null && parent.Parent == null) {
                    info.URL = mySwitchURL;
                } else {
                    info.URL = PathUtil.append(parent.URL, PathUtil.encode(info.Name));
                }
            }
        }
        return info;
    }
    
    private static class SVNDirectoryInfo {
        public String URL;
        public File Path;
        public String Name;
        public boolean IsAdded;
        public SVNDirectoryInfo Parent;
        
        private  SVNDirectory myDirectory;
        
        public SVNDirectory getDirectory() {
            if (myDirectory == null) {
                myDirectory = new SVNDirectory(Path);
            }
            return myDirectory;
        }
        
        public void dispose() {
            if (myDirectory != null) {
                myDirectory.dispose();
                myDirectory = null;
            }
        }
    }
}
