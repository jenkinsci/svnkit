/*
 * Created on 25.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

public class SVNExportEditor implements ISVNEditor {
    
    private File myRoot;
    private boolean myIsForce;
    private String myEOLStyle;
    
    private File myCurrentDirectory;
    private File myCurrentFile;
    private File myCurrentTmpFile;
    private String myCurrentPath;
    private Map myExternals;
    private Map myFileProperties;
    private Collection myDiffWindows;
    private Collection myDataFiles;
    private ISVNEventListener myEventDispatcher;
    private String myURL;

    public SVNExportEditor(ISVNEventListener eventDispatcher, String url, File dstPath, boolean force, String eolStyle) {
        myRoot = dstPath;
        myIsForce = force;
        myEOLStyle = eolStyle;
        myExternals = new HashMap();
        myEventDispatcher = eventDispatcher;
        myURL = url;
    }
    
    public Map getCollectedExternals() {
        return myExternals;
    }

    public void openRoot(long revision) throws SVNException {
        // create root if missing or delete (if force).
        addDir("", null, -1);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        myCurrentDirectory = new File(myRoot, path);
        myCurrentPath = path;

        if (!myIsForce && myCurrentDirectory.isFile()) {
            // export is obstructed.
            SVNErrorManager.error(0, null);
        } else if (myIsForce && myCurrentDirectory.exists()) {
            SVNFileUtil.deleteAll(myCurrentDirectory);
        }
        if (!myCurrentDirectory.exists()) {
            if (!myCurrentDirectory.mkdirs()) {
                SVNErrorManager.error(0, null);
            }
        }
        myEventDispatcher.svnEvent(SVNEventFactory.createExportAddedEvent(myRoot, myCurrentDirectory), ISVNEventListener.UNKNOWN);
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        if (SVNProperty.EXTERNALS.equals(name) && value != null) {
            myExternals.put(myCurrentDirectory, value);
        }
    }

    public void closeDir() throws SVNException {
        myCurrentDirectory = myCurrentDirectory.getParentFile();
        myCurrentPath = PathUtil.tail(myCurrentPath);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        File file = new File(myRoot, path);

        if (!myIsForce && file.exists()) {
            // export is obstructed.
            SVNErrorManager.error(0, null);
        } 
        myCurrentFile = file;
        myFileProperties = new HashMap();
    }

    public void changeFileProperty(String name, String value) throws SVNException {
        myFileProperties.put(name, value);
    }

    public void applyTextDelta(String baseChecksum) throws SVNException {
    }

    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        if (myDiffWindows == null) {
            myDiffWindows = new LinkedList();
            myDataFiles = new LinkedList();
        }
        myDiffWindows.add(diffWindow);
        File tmpFile = SVNFileUtil.createUniqueFile(myCurrentDirectory, myCurrentFile.getName(), ".tmp");
        myDataFiles.add(tmpFile);
        
        try {
            return new FileOutputStream(tmpFile);
        } catch (FileNotFoundException e) {
            SVNErrorManager.error(0, e);
        }
        return null;
    }

    public void textDeltaEnd() throws SVNException {
        // apply all deltas
        myCurrentTmpFile = SVNFileUtil.createUniqueFile(myCurrentDirectory, myCurrentFile.getName(), ".tmp");
        try {
            myCurrentTmpFile.createNewFile();
        } catch (IOException e) {
            myCurrentTmpFile.delete();
            SVNErrorManager.error(0, e);
        }
        SVNRAFileData target = new SVNRAFileData(myCurrentTmpFile, false);
        File fakeBase = SVNFileUtil.createUniqueFile(myCurrentDirectory, myCurrentFile.getName(), ".tmp");
        SVNRAFileData base = new SVNRAFileData(fakeBase, true);

        try {
            int index = 0;
            Iterator windows = myDiffWindows.iterator();
            for (Iterator files = myDataFiles.iterator(); files.hasNext();) {
                File dataFile = (File) files.next();
                SVNDiffWindow window = (SVNDiffWindow) windows.next();
                // apply to tmp file, use 'fake' base.
                InputStream is = null;
                try {
                    is = new FileInputStream(dataFile);
                    window.apply(base, target, is, target.length());
                } catch (FileNotFoundException e) {
                    SVNErrorManager.error(0, e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        } finally {
            if (target != null) {
                try {
                    target.close();
                } catch (IOException e) {
                }
            }
            if (base != null) {
                try {
                    base.close();
                } catch (IOException e) {
                }
            }
            for (Iterator files = myDataFiles.iterator(); files.hasNext();) {
                File file = (File) files.next();
                file.delete();
            }
            if (myDiffWindows != null) {
                myDataFiles.clear();
                myDiffWindows.clear();
            }
            fakeBase.delete();
        }
    }

    public void closeFile(String textChecksum) throws SVNException {
        if (textChecksum == null) {
            textChecksum = (String) myFileProperties.get(SVNProperty.CHECKSUM);
        }
        if (myIsForce) {
            myCurrentFile.delete();
        }
        try {
            if (textChecksum != null && !textChecksum.equals(SVNFileUtil.computeChecksum(myCurrentTmpFile))) {
                SVNErrorManager.error(0, null);
            }
        } catch (IOException e) {
            SVNErrorManager.error(0, null);
        }
        // retranslate.
        try {
            String date = (String) myFileProperties.get(SVNProperty.COMMITTED_DATE);
            String keywords = (String) myFileProperties.get(SVNProperty.KEYWORDS);
            Map keywordsMap = null;
            if (keywords != null) {
                String url = PathUtil.append(myURL, PathUtil.encode(myCurrentPath));
                url = PathUtil.append(url, PathUtil.encode(myCurrentFile.getName()));
                String author = (String) myFileProperties.get(SVNProperty.LAST_AUTHOR);
                String revStr = (String) myFileProperties.get(SVNProperty.COMMITTED_REVISION);
                keywordsMap = SVNTranslator.computeKeywords(keywords, url, author, date, revStr);
            }
            String eolStyle = myEOLStyle != null ? myEOLStyle : (String) myFileProperties.get(SVNProperty.EOL_STYLE);
            byte[] eolBytes = eolStyle == null ? null : SVNTranslator.getEOL(eolStyle);
            boolean special = myFileProperties.get(SVNProperty.SPECIAL) != null;
            boolean executable = myFileProperties.get(SVNProperty.EXECUTABLE) != null;

            DebugLog.log("eolStyle: " + eolStyle);
            DebugLog.log("myEOLStyle: " + myEOLStyle);
            SVNTranslator.translate(myCurrentTmpFile, myCurrentFile, eolBytes, keywordsMap, special, true);
            if (executable) {
                SVNFileUtil.setExecutable(myCurrentFile, true);
            }
            if (!special && date != null) {
                myCurrentFile.setLastModified(TimeUtil.parseDate(date).getTime());
            }
            myEventDispatcher.svnEvent(SVNEventFactory.createExportAddedEvent(myRoot, myCurrentFile), ISVNEventListener.UNKNOWN);
        } finally {
            myCurrentTmpFile.delete();
        }
        
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }


    public void targetRevision(long revision) throws SVNException {
    }
    
    public void deleteEntry(String path, long revision) throws SVNException {
    }
 
    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void openDir(String path, long revision) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void abortEdit() throws SVNException {
    }
}