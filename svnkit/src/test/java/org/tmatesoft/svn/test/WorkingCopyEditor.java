package org.tmatesoft.svn.test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

public class WorkingCopyEditor implements ISVNEditor {

    private static final List<String> specialProperties = new LinkedList<String>();

	// Static ===============================================================

	static {
		specialProperties.add("svn:wc:ra_dav:version-url");
		specialProperties.add("svn:entry:committed-rev");
		specialProperties.add("svn:entry:last-author");
		specialProperties.add("svn:entry:uuid");
		specialProperties.add("svn:entry:committed-date");
	}

    private final WorkingCopy workingCopy;
    private File currentPath;
    private SVNDeltaProcessor deltaProcessor;
    private SVNWCContext wcContext;
    private boolean newFile;

    public WorkingCopyEditor(WorkingCopy workingCopy) {
        this.workingCopy = workingCopy;
        this.currentPath = getFile("");
        this.deltaProcessor = new SVNDeltaProcessor();
        this.wcContext = new SVNWCContext(new DefaultSVNOptions(), null);
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
        currentPath = getFile("");
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        workingCopy.delete(getFile(path));
    }

    public void absentDir(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void absentFile(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        currentPath = getFile(path);
        if (copyFromPath == null) {
            currentPath.mkdirs();
            workingCopy.add(currentPath);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        currentPath = getFile(path);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (isSpecialProperty(name)) {
            return;
        }
        workingCopy.setProperty(currentPath, name, value);
    }

    public void closeDir() throws SVNException {
        currentPath = currentPath.getParentFile();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        currentPath = getFile(path);
        if (copyFromPath == null) {
            newFile = true;
            try {
                currentPath.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            workingCopy.add(currentPath);
        } else {
            newFile = false;
            throw new UnsupportedOperationException();
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        currentPath = getFile(path);
        newFile = false;
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        if (isSpecialProperty(propertyName)) {
            return;
        }
        workingCopy.setProperty(currentPath, propertyName, propertyValue);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        currentPath = currentPath.getParentFile();
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {

        final InputStream inputStream;
        final OutputStream outputStream;

        if (!newFile) {
            SVNWCContext.PristineContentsInfo pristineContents = wcContext.getPristineContents(currentPath, true, false);
            inputStream = pristineContents.stream;
        } else {
            inputStream = SVNFileUtil.DUMMY_IN;
        }
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(currentPath));
        } catch (FileNotFoundException e) {
            throw new RuntimeException();
        }

        deltaProcessor.applyTextDelta(inputStream, outputStream, false);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return deltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        deltaProcessor.textDeltaEnd();
    }

    private File getFile(String path) {
        return new File(workingCopy.getWorkingCopyDirectory(), path).getAbsoluteFile();
    }

    private boolean isSpecialProperty(String name) {
        return specialProperties.contains(name);
    }
}
