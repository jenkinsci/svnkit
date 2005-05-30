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
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.util.PathUtil;

public class SVNRemoteDiffEditor implements ISVNEditor {

    private File myRoot;
    private long myTargetRevision;
    private SVNRepository myRepos;
    private long myRevision;
    private ISVNDiffGenerator myDiffGenerator;
    
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private OutputStream myResult;
    private String myRevision1;
    private String myRevision2;

    public SVNRemoteDiffEditor(File tmpRoot, ISVNDiffGenerator diffGenerator, SVNRepository repos, long revision,
            OutputStream result) {
        myRoot = tmpRoot;
        myRepos = repos;
        myRevision = revision;
        myDiffGenerator = diffGenerator;
        myResult = result;
        myRevision1 = "(revision " + revision + ")";
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
        myRevision2 = "(revision " + revision + ")";
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(null, "", false);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        SVNNodeKind nodeKind = myRepos.checkPath(path, myRevision);
        // fire file deleted or dir deleted.
        if (nodeKind == SVNNodeKind.FILE) {
            String name = PathUtil.tail(path);
            File tmpFile = SVNFileUtil.createUniqueFile(myRoot, name, ".tmp");
            SVNFileInfo info = new SVNFileInfo(myCurrentDirectory, path, false);
            try {
                info.loadFromRepository(tmpFile, myRepos, myRevision);
                String mimeType = (String) info.myBaseProperties.get(SVNProperty.MIME_TYPE);
                myDiffGenerator.displayFileDiff(path, tmpFile, null, myRevision1, myRevision2, mimeType, mimeType, myResult);
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        } 
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.myBaseProperties = Collections.EMPTY_MAP;
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, false);

        myCurrentDirectory.myBaseProperties = new HashMap();
        myRepos.getDir(path, myRevision, myCurrentDirectory.myBaseProperties, (Collection) null);
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
        if (myCurrentDirectory.myPropertyDiff != null) {
            myDiffGenerator.displayPropDiff(myCurrentDirectory.myPath, myCurrentDirectory.myBaseProperties, 
                    myCurrentDirectory.myPropertyDiff, myResult);
        }
        myCurrentDirectory = myCurrentDirectory.myParent;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);

        myCurrentFile = new SVNFileInfo(myCurrentDirectory, path, true);
        myCurrentFile.myBaseProperties = Collections.EMPTY_MAP;
        myCurrentFile.myBaseFile = SVNFileUtil.createUniqueFile(myRoot, PathUtil.tail(path), ".tmp");
        try {
            myCurrentFile.myBaseFile.createNewFile();
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
        myCurrentFile.myFile = SVNFileUtil.createUniqueFile(myRoot, PathUtil.tail(path), ".tmp");
        try {
            myCurrentFile.myFile.createNewFile();
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        
        myCurrentFile = new SVNFileInfo(myCurrentDirectory, path, true);
        myCurrentFile.myBaseFile = SVNFileUtil.createUniqueFile(myRoot, PathUtil.tail(path), ".tmp"); 
        try {
            myCurrentFile.loadFromRepository(myCurrentFile.myBaseFile, myRepos, myRevision);
        } catch (SVNException th) {
            
            SVNErrorManager.error(0, th);
        }        
        myCurrentFile.myFile = SVNFileUtil.createUniqueFile(myRoot, PathUtil.tail(path), ".tmp");
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
        File chunkFile = SVNFileUtil.createUniqueFile(myRoot, PathUtil.tail(myCurrentFile.myPath), ".chunk");
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
        if (myCurrentFile.myFile != null) {
            String mimeType1 = (String) myCurrentFile.myBaseProperties.get(SVNProperty.MIME_TYPE);
            String mimeType2 = myCurrentFile.myPropertyDiff != null ? (String) myCurrentFile.myPropertyDiff.get(SVNProperty.MIME_TYPE) : null;
            if (mimeType2 == null) {
                mimeType2 = mimeType1;
            }
            myDiffGenerator.displayFileDiff(myCurrentFile.myPath, myCurrentFile.myBaseFile,
                    myCurrentFile.myFile, myRevision1, myRevision2, mimeType1, mimeType2, myResult);
        }
        if (myCurrentFile.myPropertyDiff != null) {
            myDiffGenerator.displayPropDiff(myCurrentFile.myPath, 
                    myCurrentFile.myBaseProperties, myCurrentFile.myPropertyDiff,
                    myResult);
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
        
        private Map myBaseProperties;
        private Map myPropertyDiff;
        
        private SVNDirectoryInfo myParent;
    }

    private static class SVNFileInfo {

        public SVNFileInfo(SVNDirectoryInfo parent, String path, boolean added) {
            myParent = parent;
            myPath = path;
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
