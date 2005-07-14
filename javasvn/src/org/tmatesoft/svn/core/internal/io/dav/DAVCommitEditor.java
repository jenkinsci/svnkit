/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.internal.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVMergeHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVProppatchHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

class DAVCommitEditor implements ISVNEditor {
    
    private String myLogMessage;
    private DAVConnection myConnection;
    private SVNRepositoryLocation myLocation;
	private DAVRepository myRepository;
    private Runnable myCloseCallback;
    private String myActivity;

    private Stack myDirsStack;
    private ISVNWorkspaceMediator myCommitMediator;
    private Map myPathsMap;
    private Map myFilesMap;

    public DAVCommitEditor(DAVRepository repository, DAVConnection connection, String message, ISVNWorkspaceMediator mediator, Runnable closeCallback) {
        myConnection = connection;
        myLogMessage = message;
        myLocation = repository.getLocation();
        myRepository = repository;
        myCloseCallback = closeCallback;
        myCommitMediator = mediator;

        myDirsStack = new Stack();
        myPathsMap = new HashMap();
        myFilesMap = new HashMap();
    }

    /* do nothing */
    public void targetRevision(long revision) throws SVNException {
    }
    public void absentDir(String path) throws SVNException {
    }
    public void absentFile(String path) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
        // make activity
        myActivity = createActivity(myLogMessage);
        DAVResource root = new DAVResource(myCommitMediator, myConnection, "", revision);
        root.getVersionURL();
        myDirsStack.push(root);
        myPathsMap.put(root.getURL(), root.getPath());
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        path = PathUtil.encode(path);
        // get parent's working copy. (checkout? or use checked out?)
        DAVResource parentResource = (DAVResource) myDirsStack.peek();
        checkoutResource(parentResource);
        String wPath = parentResource.getWorkingURL();
		// get root wURL and delete from it!

        // append name part of the path to the checked out location
		// should we append full name here?
        String url;
		if (myDirsStack.size() == 1) {
			wPath = PathUtil.append(parentResource.getWorkingURL(), path);
            url = PathUtil.append(parentResource.getURL(), path);
		} else {
			// we are inside openDir()...
			wPath = PathUtil.append(wPath, PathUtil.tail(path));
            url = PathUtil.append(parentResource.getURL(), PathUtil.tail(path));
		}

        // call DELETE for the composed path
        DAVStatus status = myConnection.doDelete(url, wPath, revision);
        if (status.getResponseCode() != 204 && status.getResponseCode() != 404) {
            throw new SVNException("DELETE failed: " + status);
        }
		if (myDirsStack.size() == 1) {
			myPathsMap.put(PathUtil.append(parentResource.getURL(), path), path);
		} else {
			myPathsMap.put(PathUtil.append(parentResource.getURL(), PathUtil.tail(path)), path);
		}
    }


    public void addDir(String path, String copyPath, long copyRevision) throws SVNException {
        path = PathUtil.encode(path);

        DAVResource parentResource = (DAVResource) myDirsStack.peek();
        if (parentResource.getWorkingURL() == null) {
        	String filePath = PathUtil.append(parentResource.getURL(), PathUtil.tail(path));
    		DAVResponse responce = DAVUtil.getResourceProperties(myConnection, filePath, null, DAVElement.STARTING_PROPERTIES, true);
    		if (responce != null) {
    			throw new SVNException("Directory '"  + filePath + "' already exists");
    		}
        }
        checkoutResource(parentResource);
        String wPath = parentResource.getWorkingURL();

        DAVResource newDir = new DAVResource(myCommitMediator, myConnection, path, -1, copyPath != null);
        newDir.setWorkingURL(PathUtil.append(wPath, PathUtil.tail(path)));

        myDirsStack.push(newDir);
        myPathsMap.put(newDir.getURL(), path);

        if (copyPath != null) {
            // convert to full path?
            copyPath = myRepository.getFullPath(copyPath);
            copyPath = PathUtil.encode(copyPath);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, copyPath, copyRevision, false, false, null);
            copyPath = PathUtil.append(info.baselineBase, info.baselinePath);

            // full url.
            wPath = myLocation.getProtocol() + "://" + myLocation.getHost() + ":" + myLocation.getPort() +
            	newDir.getWorkingURL();
            DAVStatus status = myConnection.doCopy(copyPath, wPath);
            if (status.getResponseCode() != 201 && status.getResponseCode() != 204) {
                throw new SVNException("COPY failed: " + status);
            }
        } else {
            DAVStatus status = myConnection.doMakeCollection(newDir.getWorkingURL());
            if (status.getResponseCode() != 201) {
                throw new SVNException("MKCOL failed: " + status);
            }
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        path = PathUtil.encode(path);
        // do nothing,
        DAVResource parent = myDirsStack.peek() != null ? (DAVResource) myDirsStack.peek() : null;
        DAVResource directory = new DAVResource(myCommitMediator, myConnection, path, revision, parent == null ? false : parent.isCopy());
        if (parent != null && parent.isCopy()) {
            // part of copied structure -> derive wurl
            directory.setWorkingURL(PathUtil.append(parent.getWorkingURL(), PathUtil.tail(path)));
        } else {
            directory.getVersionURL();
        }
        myDirsStack.push(directory);
        myPathsMap.put(directory.getURL(), directory.getPath());
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        DAVResource directory = (DAVResource) myDirsStack.peek();
        checkoutResource(directory);
        directory.putProperty(name, value);
        myPathsMap.put(directory.getURL(), directory.getPath());
    }

    public void closeDir() throws SVNException {
        DAVResource resource = (DAVResource) myDirsStack.pop();
        // do proppatch if there were property changes.
        if (resource.getProperties() != null) {
            StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, resource.getProperties());
            try {
                myConnection.doProppatch(resource.getURL(), resource.getWorkingURL(), request, null);
            } catch (SVNException e) {
                throw new SVNException("At least one property change failed for " + resource.getURL());
            }
        }
        resource.dispose();
    }

    public void addFile(String path, String copyPath, long copyRevision) throws SVNException {
        String originalPath = path;
        path = PathUtil.encode(path);
        // checkout parent collection.
        DAVResource parentResource = (DAVResource) myDirsStack.peek();
        if (parentResource.getWorkingURL() == null) {
        	String filePath = PathUtil.append(parentResource.getURL(), PathUtil.tail(path));
    		DAVResponse responce = DAVUtil.getResourceProperties(myConnection, filePath, null, DAVElement.STARTING_PROPERTIES, true);
    		if (responce != null) {
    			throw new SVNException("File '"  + filePath + "' already exists");
    		}
        }
        checkoutResource(parentResource);
        String wPath = parentResource.getWorkingURL();
        // create child resource.
        DAVResource newFile = new DAVResource(myCommitMediator, myConnection, path, -1, copyPath != null);
        newFile.setWorkingURL(PathUtil.append(wPath, PathUtil.tail(path)));
        // put to have working URL to make PUT or PROPPATCH later (in closeFile())
        myPathsMap.put(newFile.getURL(), newFile.getPath());
        myFilesMap.put(originalPath, newFile);

        if (copyPath != null) {
            copyPath = myRepository.getFullPath(copyPath);
            copyPath = PathUtil.encode(copyPath);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, copyPath, copyRevision, false, false, null);
            copyPath = PathUtil.append(info.baselineBase, info.baselinePath);

            // do "COPY" copyPath to parents working url ?
            wPath = myLocation.getProtocol() + "://" + myLocation.getHost() + ":" + myLocation.getPort() +
            	newFile.getWorkingURL();
            DAVStatus status = myConnection.doCopy(copyPath, wPath);
            if (status.getResponseCode() != 201 && status.getResponseCode() != 204) {
                throw new SVNException("COPY failed: " + status);
            }
            newFile.setAdded(false);
        } else {
            newFile.setAdded(true);
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        String originalPath = path;
        path = PathUtil.encode(path);
        DAVResource file = new DAVResource(myCommitMediator, myConnection, path, revision);
        DAVResource parent = (DAVResource) myDirsStack.peek();
        if (parent.isCopy()) {
            // part of copied structure -> derive wurl
            file.setWorkingURL(PathUtil.append(parent.getWorkingURL(), PathUtil.tail(path)));
        }
        checkoutResource(file);
        myPathsMap.put(file.getURL(), file.getPath());
        myFilesMap.put(originalPath, file);
    }
    
    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        // just do nothing.
    }
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        // save window, create temp file.
        DAVResource currentFile = (DAVResource) myFilesMap.get(path);
        try {
            OutputStream os = currentFile.addTextDelta();
            SVNDiffWindowBuilder.save(diffWindow, os);
            return os;
        } catch (IOException e) {
            throw new SVNException();
        }
    }
    public void textDeltaEnd(String path) throws SVNException {
        // again do nothing.
    }

    public void changeFileProperty(String path, String name, String value)  throws SVNException {
        DAVResource currentFile = (DAVResource) myFilesMap.get(path);
        currentFile.putProperty(name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        // do PUT of delta if there was one (diff window + temp file).
        // do subsequent PUT of all diff windows...
        DAVResource currentFile = (DAVResource) myFilesMap.get(path);
        try {
            if (currentFile.isAdded() && currentFile.getDeltaCount() == 0) {
                OutputStream os = textDeltaChunk(path, SVNDiffWindowBuilder.createReplacementDiffWindow(0));
                try {
                    os.close();
                } catch (IOException e) {
                    throw new SVNException(e);
                }
            }
            for(int i = 0; i < currentFile.getDeltaCount(); i++) {
                try {
                    InputStream data = currentFile.getTextDelta(i);
                    DAVStatus status = myConnection.doPutDiff(currentFile.getURL(), currentFile.getWorkingURL(), data);
                    data.close();
                    if (!(status.getResponseCode() ==201 || status.getResponseCode() == 204)) {
                        throw new SVNException("PUT failed: " + status);
                    }
                } catch (IOException e) {
                    throw new SVNException(e);
                } 
            } 
            // do proppatch if there were property changes.
            if (currentFile.getProperties() != null) {
                StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, currentFile.getProperties());
                try {
                    myConnection.doProppatch(currentFile.getURL(), currentFile.getWorkingURL(), request, null);
                } catch (SVNException e) {
                    throw new SVNException("At least one property change failed for " + currentFile.getURL());
                }
            }
        } finally {
            currentFile.dispose();
            myFilesMap.remove(path);
        }
    }
    
    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myDirsStack.isEmpty()) {
            DAVResource resource = (DAVResource) myDirsStack.pop();
            // do proppatch if there were property changes.
            if (resource.getProperties() != null) {
                StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, resource.getProperties());
                try {
                    myConnection.doProppatch(resource.getURL(), resource.getWorkingURL(), request, null);
                } catch (SVNException e) {
                    throw new SVNException("At least one property change failed for " + resource.getURL());
                }
            }
            resource.dispose();
        }
        DAVMergeHandler handler = new DAVMergeHandler(myCommitMediator, myPathsMap);
        DAVStatus status = myConnection.doMerge(myActivity, true, handler);
        if (status == null || status.getResponseCode() != 200) {
            throw new SVNException(status != null ? status.toString() : "");
        }
        abortEdit();
        return handler.getCommitInfo();
    }
    
    public void abortEdit() throws SVNException {
        // DELETE activity
        if (myActivity != null) {
            myConnection.doDelete(myActivity);
        }
        // dispose all resources!
        if (myFilesMap != null) {
            for (Iterator files = myFilesMap.values().iterator(); files.hasNext();) {
                DAVResource file = (DAVResource) files.next();
                file.dispose();
            }
            myFilesMap = null;
        }
        for(Iterator files = myDirsStack.iterator(); files.hasNext();) {
            DAVResource resource = (DAVResource) files.next();
            resource.dispose();            
        }
        myDirsStack = null;
        myCloseCallback.run();
    }
    
    private String createActivity(String logMessage) throws SVNException {
        String activity = myConnection.doMakeActivity();
        // checkout head...
        String vcc = (String) DAVUtil.getPropertyValue(myConnection, myLocation.getPath(), null, DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        
        String location;
        String head;
        DAVStatus status;
        try {
            head = (String) DAVUtil.getPropertyValue(myConnection, vcc, null, DAVElement.CHECKED_IN);
            status = myConnection.doCheckout(activity, null, head);
        } catch (SVNException e) {
            // may be the head changed, retry.
            throw e;
        }
        location = (String) status.getResponseHeader().get("Location");
        if (location == null) {
            throw new SVNException("failed to check out " +  head + " into " + activity + " : " + status.toString());
        }
        // proppatch log message.
        logMessage = logMessage == null ? "no message" : logMessage;
        StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, "svn:log", logMessage);
        myConnection.doProppatch(null, location, request, null);
        
        return activity;
    }
    
    private void checkoutResource(DAVResource resource) throws SVNException {
        if (resource.getWorkingURL() != null) {
            return;
        }
        if (resource.getVersionURL() == null) {
            throw new SVNException(resource.getURL() + " checkout failed: resource version URL is not set");
        }
        DebugLog.log("vURL: " + resource.getVersionURL());
        DAVStatus status = myConnection.doCheckout(myActivity, resource.getURL(), resource.getVersionURL());
        String location = (String) status.getResponseHeader().get("Location");
        if (status.getResponseCode() == 201 && location != null) {
            DebugLog.log("wURL: " + location);
            resource.setWorkingURL(location);
            DebugLog.log("CHECKED OUT: " + resource);
            return;
        }
        throw new SVNException(resource.getURL() + " checkout failed: " + status.toString());
    }
}
