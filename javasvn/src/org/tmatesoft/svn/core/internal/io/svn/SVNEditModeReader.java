/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;

/**
 * @version 1.0
 * @author TMate Software Ltd.
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
    }

    private ISVNEditor myEditor;
    private SVNDiffWindowBuilder myBuilder;
    private OutputStream myDiffStream;
    private long myLenght;
    private String myFilePath;
    private boolean myIsDelta;

    public void setEditor(ISVNEditor editor) {
        myEditor = editor;
        myBuilder = SVNDiffWindowBuilder.newInstance();
    }

    public boolean processCommand(String commandName, InputStream parameters) throws SVNException {
        String pattern = (String) COMMANDS_MAP.get(commandName);
        if (pattern == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD));
        }
        if ("textdelta-chunk".equals(commandName)) {
            if (myBuilder.getDiffWindow() == null) {
                Object[] items = null;
                try {
                    items = SVNReader.parse(parameters, "(SB))", null);
                } catch (Throwable th) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Cannot read editor command: {0}", th.getLocalizedMessage());
                    SVNErrorManager.error(err, th);
                } 
                byte[] bytes = (byte[]) items[1];
                myBuilder.accept(bytes, 0);
                if (myBuilder.getDiffWindow() != null) {
                    myIsDelta = false;
                    myLenght = myBuilder.getDiffWindow().getNewDataLength();
                    myDiffStream = myEditor.textDeltaChunk(myFilePath, myBuilder.getDiffWindow());
                    if (myDiffStream == null) {
                        myDiffStream = SVNFileUtil.DUMMY_OUT;
                    }
                    if (myLenght == 0) {
                        closeDiffStream();
                    }
                }
            } else if (myDiffStream != null) {
                if (myLenght > 0) {
                    byte[] line;
                    line = (byte[]) SVNReader.parse(parameters, "(sB))", null)[0];
                    myLenght -= line.length;
                    try {
                        myDiffStream.write(line);
                    } catch (IOException e) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
                    }
                }
                if (myLenght == 0) {
                    closeDiffStream();
                }
            }
            return true;
        }

        boolean last = "close-edit".equals(commandName)
                || "abort-edit".equals(commandName);
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
            myBuilder.reset();
            myLenght = 0;
            myDiffStream = null;
            myIsDelta = true;
            myEditor.applyTextDelta(myFilePath, (String) items[1]);
        } else if ("textdelta-end".equals(commandName)) {
            // if there was text-delta-chunk, but no window was sent, sent empty one.
            if (myIsDelta) {
                OutputStream os = myEditor.textDeltaChunk(myFilePath, new SVNDiffWindow(0,0,0,0,0));
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
                    }
                }
            }
            myIsDelta = false;
            myEditor.textDeltaEnd(myFilePath);
        } else if ("close-edit".equals(commandName)) {
            myEditor.closeEdit();
        } else if ("abort-edit".equals(commandName)) {
            myEditor.abortEdit();
        }
        return !last;
    }

    private void closeDiffStream() throws SVNException {
        try {
            myDiffStream.close();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
        }
        myBuilder.reset(SVNDiffWindowBuilder.OFFSET);
        myDiffStream = null;
        myLenght = -1;
    }

}
