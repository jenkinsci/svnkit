/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNEditModeReader {

    private static final Map COMMANDS_MAP = new HashMap();

    static {
        COMMANDS_MAP.put("target-rev", "(N)");
        COMMANDS_MAP.put("open-root", "((?N)S)");
        COMMANDS_MAP.put("delete-entry", "(S(?N)S)");
        COMMANDS_MAP.put("add-dir", "(SSS(?S?N))");
        COMMANDS_MAP.put("open-dir", "(SSS(?N))");
        COMMANDS_MAP.put("change-dir-prop", "(SS(?S))");
        COMMANDS_MAP.put("close-dir", "(S)");
        COMMANDS_MAP.put("add-file", "(SSS(?S?N))");
        COMMANDS_MAP.put("open-file", "(SSS(?N))");
        COMMANDS_MAP.put("apply-textdelta", "(S(?S))");
        COMMANDS_MAP.put("textdelta-chunk", "(SS)");
        COMMANDS_MAP.put("textdelta-end", "(S)");
        COMMANDS_MAP.put("change-file-prop", "(SS(?S))");
        COMMANDS_MAP.put("close-file", "(S(?S))");
        COMMANDS_MAP.put("close-edit", "()");
        COMMANDS_MAP.put("abort-edit", "()");
        COMMANDS_MAP.put("finish-replay", "()");
    }

    private ISVNEditor myEditor;
    private SVNDeltaReader myDeltaReader;
    private String myFilePath;

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
            myEditor.addFile((String) items[0], (String) items[3], SVNReader
                    .getLong(items, 4));
            myFilePath = (String) items[0];
        } else if ("open-file".equals(commandName)) {
            myEditor.openFile((String) items[0], SVNReader.getLong(items, 3));
            myFilePath = (String) items[0];
        } else if ("change-file-prop".equals(commandName)) {
            myEditor.changeFileProperty(myFilePath, (String) items[1],
                    (String) items[2]);
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
        }
        return !last;
    }
}
