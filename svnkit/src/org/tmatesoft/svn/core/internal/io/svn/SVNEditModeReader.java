/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;

/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNEditModeReader {

    private static final Map COMMANDS_MAP = new HashMap();

    static {
        COMMANDS_MAP.put("target-rev", "r");
        COMMANDS_MAP.put("open-root", "(?r)c");
        COMMANDS_MAP.put("delete-entry", "c(?r)c");
        COMMANDS_MAP.put("add-dir", "ccc(?cr)");
        COMMANDS_MAP.put("open-dir", "ccc(?r)");
        COMMANDS_MAP.put("change-dir-prop", "cc(?s)");
        COMMANDS_MAP.put("close-dir", "c");
        COMMANDS_MAP.put("add-file", "ccc(?cr)");
        COMMANDS_MAP.put("open-file", "ccc(?r)");
        COMMANDS_MAP.put("apply-textdelta", "c(?c)");
        COMMANDS_MAP.put("textdelta-chunk", "cs");
        COMMANDS_MAP.put("textdelta-end", "c");
        COMMANDS_MAP.put("change-file-prop", "cc(?s)");
        COMMANDS_MAP.put("close-file", "c(?c)");
        COMMANDS_MAP.put("close-edit", "()");
        COMMANDS_MAP.put("abort-edit", "()");
        COMMANDS_MAP.put("finish-replay", "()");
        COMMANDS_MAP.put("absent-dir", "cc");
        COMMANDS_MAP.put("absent-file", "cc");
    }

    private SVNConnection myConnection;
    private ISVNEditor myEditor;
    private SVNDeltaReader myDeltaReader;
    private String myFilePath;

    private boolean myDone;
    private boolean myAborted;
    private Map myTokens;

    public SVNEditModeReader() {
    }

    public SVNEditModeReader(SVNConnection connection, ISVNEditor editor) {
        myConnection = connection;
        myEditor = editor;
        myDeltaReader = new SVNDeltaReader();
        myDone = false;
        myAborted = false;
        myTokens = new HashMap();
    }

    public void setEditor(ISVNEditor editor) {
        myEditor = editor;
        myDeltaReader = new SVNDeltaReader();
    }


    public boolean processCommand(String commandName, InputStream parameters) throws SVNException {
        String pattern = (String) COMMANDS_MAP.get(commandName);
        if (pattern == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD));
        }
        if ("textdelta-chunk".equals(commandName)) {
            Object[] items = SVNReader.parse(parameters, "(SB))", null);
            byte[] bytes = (byte[]) items[1];
            myDeltaReader.nextWindow(bytes, 0, bytes.length, myFilePath, myEditor);
            return true;
        }

        boolean last = "close-edit".equals(commandName) || "abort-edit".equals(commandName) || "finish-replay".equals(commandName);
        Object[] items = SVNReader.parse(parameters, pattern, new Object[10]);
        if ("target-rev".equals(commandName)) {
            myEditor.targetRevision(SVNReader.getLong(items, 0));
        } else if ("open-root".equals(commandName)) {
            myEditor.openRoot(SVNReader.getLong(items, 0));
        } else if ("delete-entry".equals(commandName)) {
            myEditor
                    .deleteEntry((String) items[0], SVNReader.getLong(items, 1));
        } else if ("add-dir".equals(commandName)) {
            myEditor.addDir((String) items[0], (String) items[3], SVNReader
                    .getLong(items, 4));
        } else if ("open-dir".equals(commandName)) {
            myEditor.openDir((String) items[0], SVNReader.getLong(items, 3));
        } else if ("change-dir-prop".equals(commandName)) {
            myEditor.changeDirProperty((String) items[1], (String) items[2]);
        } else if ("close-dir".equals(commandName)) {
            myEditor.closeDir();
        } else if ("add-file".equals(commandName)) {
            myEditor.addFile((String) items[0], (String) items[3], SVNReader.getLong(items, 4));
            myFilePath = (String) items[0];
        } else if ("open-file".equals(commandName)) {
            myEditor.openFile((String) items[0], SVNReader.getLong(items, 3));
            myFilePath = (String) items[0];
        } else if ("change-file-prop".equals(commandName)) {
            myEditor.changeFileProperty(myFilePath, (String) items[1], (String) items[2]);
        } else if ("close-file".equals(commandName)) {
            myEditor.closeFile(myFilePath, (String) items[1]);
        } else if ("apply-textdelta".equals(commandName)) {
            myEditor.applyTextDelta(myFilePath, (String) items[1]);
        } else if ("textdelta-end".equals(commandName)) {
            // reset delta reader, 
            // this should send empty window when diffstream contained only header. 
            myDeltaReader.reset(myFilePath, myEditor);
            myEditor.textDeltaEnd(myFilePath);
        } else if ("close-edit".equals(commandName)) {
            myEditor.closeEdit();
        } else if ("abort-edit".equals(commandName)) {
            myEditor.abortEdit();
        } else if ("absent-dir".equals(commandName)) {
            myEditor.absentDir((String) items[0]);
        } else if ("absent-file".equals(commandName)) {
            myEditor.absentFile((String) items[0]);
        }
        return !last;
    }

    private void storeToken(String token, boolean isFile) {
        myTokens.put(token, Boolean.valueOf(isFile));
    }

    private void lookupToken(String token, boolean isFile) throws SVNException {
        Boolean tokenType = (Boolean) myTokens.get(token);
        if (tokenType == null || tokenType != Boolean.valueOf(isFile)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Invalid file or dir token during edit");
            SVNErrorManager.error(err);
        }
    }

    private void removeToken(String token) {
        myTokens.remove(token);
    }

    private void processCommand(String commandName, List params) throws SVNException {
        if ("target-rev".equals(commandName)) {
            myEditor.targetRevision(SVNReader2.getLong(params, 0));
        } else if ("open-root".equals(commandName)) {
            myEditor.openRoot(SVNReader2.getLong(params, 0));
            String token = SVNReader2.getString(params, 1);
            storeToken(token, false);
        } else if ("delete-entry".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 2), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader2.getString(params, 0));
            myEditor.deleteEntry(path, SVNReader2.getLong(params, 1));
        } else if ("add-dir".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 1), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader2.getString(params, 0));
            String copyFromPath = SVNReader2.getString(params, 3);
            if (copyFromPath != null) {
                copyFromPath = SVNPathUtil.canonicalizePath(copyFromPath);
            }
            myEditor.addDir(path, copyFromPath, SVNReader2.getLong(params, 4));
            storeToken(SVNReader2.getString(params, 2), false);
        } else if ("open-dir".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 1), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader2.getString(params, 0));
            myEditor.openDir(path, SVNReader2.getLong(params, 3));
            storeToken(SVNReader2.getString(params, 2), false);
        } else if ("change-dir-prop".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 0), false);
            myEditor.changeDirProperty(SVNReader2.getString(params, 1), SVNReader2.getString(params, 2));
        } else if ("close-dir".equals(commandName)) {
            String token = SVNReader2.getString(params, 0);
            lookupToken(token, false);
            myEditor.closeDir();
            removeToken(token);
        } else if ("add-file".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 1), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader2.getString(params, 0));
            String copyFromPath = SVNReader2.getString(params, 3);
            if (copyFromPath != null) {
                copyFromPath = SVNPathUtil.canonicalizePath(copyFromPath);
            }
            storeToken(SVNReader2.getString(params, 2), true);
            myEditor.addFile(path, copyFromPath, SVNReader2.getLong(params, 4));
            myFilePath = path;
        } else if ("open-file".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 1), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader2.getString(params, 0));
            storeToken(SVNReader2.getString(params, 2), true);
            myEditor.openFile(SVNReader2.getString(params, 0), SVNReader2.getLong(params, 3));
            myFilePath = path;
        } else if ("change-file-prop".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 0), true);
            myEditor.changeFileProperty(myFilePath, SVNReader2.getString(params, 1), SVNReader2.getString(params, 2));
        } else if ("close-file".equals(commandName)) {
            String token = SVNReader2.getString(params, 0);
            lookupToken(token, true);
            myEditor.closeFile(myFilePath, SVNReader2.getString(params, 1));
            removeToken(token);
        } else if ("apply-textdelta".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 0), true);
            myEditor.applyTextDelta(myFilePath, SVNReader2.getString(params, 1));
        } else if ("textdelta-chunk".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 0), true);
            byte[] chunk = SVNReader2.getBytes(params, 1);            
            myDeltaReader.nextWindow(chunk, 0, chunk.length, myFilePath, myEditor);
        } else if ("textdelta-end".equals(commandName)) {
            // reset delta reader,
            // this should send empty window when diffstream contained only header.
            lookupToken(SVNReader2.getString(params, 0), true);
            myDeltaReader.reset(myFilePath, myEditor);
            myEditor.textDeltaEnd(myFilePath);
        } else if ("close-edit".equals(commandName)) {
            myEditor.closeEdit();
            myDone = true;
            myAborted = false;
        } else if ("abort-edit".equals(commandName)) {
            myEditor.abortEdit();
            myDone = true;
            myAborted = true;
        } else if ("absent-dir".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 1), false);
            myEditor.absentDir(SVNReader2.getString(params, 0));
        } else if ("absent-file".equals(commandName)) {
            lookupToken(SVNReader2.getString(params, 1), false);
            myEditor.absentFile(SVNReader2.getString(params, 0));
        } else if ("finish-replay".equals(commandName)){
            myDone = true;
            if (myAborted){
                myAborted = false;
            }
        }
    }


    public void driveEditor(boolean doReplay) throws SVNException {
        while (!myDone) {
            try {
                List items = readTuple("wl", true);
                String commandName = SVNReader2.getString(items, 0);
                boolean allowFinishReplay = doReplay && "finish-replay".equals(commandName);
                String template = (String) COMMANDS_MAP.get(commandName);
                if (template == null) {
                    if (!allowFinishReplay) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD));
                    }
                }
                List parameters = null;
                if (!allowFinishReplay) {
                    parameters = SVNReader2.parseTuple(template, (Collection) items.get(1), null);
                }
                processCommand(commandName, parameters);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_CMD_ERR) {
                    myAborted = true;
                    if (!myDone) {
                        myEditor.abortEdit();
                        break;
                    }
                }
                throw e;
            }
        }
        while (!myDone) {
            List items = readTuple("wl", true);
            String command = SVNReader2.getString(items, 0);
            myDone = "abort-edit".equals(command);
        }
    }

    private List readTuple(String template, boolean readMalformedData) throws SVNException {
        if (myConnection == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED));
        }
        return myConnection.readTuple(template, readMalformedData);
    }
}
