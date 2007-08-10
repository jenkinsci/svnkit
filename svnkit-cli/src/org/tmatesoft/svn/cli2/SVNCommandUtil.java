/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandUtil {
    
    public static String getLocalPath(String path) {
        path = path.replace('/', File.separatorChar);
        if ("".equals(path)) {
            path = ".";
        }
        return path;
    }

    public static String formatString(String str, int chars, boolean left) {
        if (str.length() > chars) {
            return str.substring(0, chars);
        }
        StringBuffer formatted = new StringBuffer();
        if (left) {
            formatted.append(str);
        }
        for(int i = 0; i < chars - str.length(); i++) {
            formatted.append(' ');
        }
        if (!left) {
            formatted.append(str);
        }
        return formatted.toString();
    }

    public static boolean isURL(String pathOrUrl){
        pathOrUrl = pathOrUrl != null ? pathOrUrl.toLowerCase() : null;
        return pathOrUrl != null
                && (pathOrUrl.startsWith("http://")
                        || pathOrUrl.startsWith("https://")
                        || pathOrUrl.startsWith("svn://") 
                        || (pathOrUrl.startsWith("svn+") && pathOrUrl.indexOf("://") > 4)
                        || pathOrUrl.startsWith("file://"));
    }
    
    public static byte[] runEditor(SVNCommandEnvironment env, String existingValue, String prefix) throws SVNException {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File tmpFile = SVNFileUtil.createUniqueFile(tmpDir, prefix, ".tmp");
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(tmpFile);
            os.write(existingValue.getBytes("UTF-8"));
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err);
        } finally {
            SVNFileUtil.closeFile(os);
        }
        tmpFile.setLastModified(System.currentTimeMillis() - 2000);
        long timestamp = tmpFile.lastModified();
        String editorCommand = getEditorCommand(env);
        try {
            String result = null;
            if (SVNFileUtil.isWindows) {
                String editor = editorCommand.trim().toLowerCase();
                if (!(editor.endsWith(".exe") || editor.endsWith(".bat") || editor.endsWith(".cmd"))) {
                    result = SVNFileUtil.execCommand(new String[] {"cmd.exe", "/C", editorCommand, tmpFile.getAbsolutePath()}, false);
                } else {
                    result = SVNFileUtil.execCommand(new String[] {editorCommand, tmpFile.getAbsolutePath()}, false);
                }
            } else {
                result = SVNFileUtil.execCommand(new String[] {editorCommand, tmpFile.getAbsolutePath()}, false);
            }
            if (result == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Editor command '" + editorCommand + " " + tmpFile.getAbsolutePath() + "' failed.");
                SVNErrorManager.error(err);
            }
            // now read from file.
            if (timestamp == tmpFile.lastModified()) {
                return null;
            }
            InputStream is = null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            try {
                is = SVNFileUtil.openFileForReading(tmpFile);
                while(true) {
                    int read = is.read(buffer);
                    if (read <= 0) {
                        break;
                    }
                    bos.write(buffer, 0, read);
                }
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                SVNErrorManager.error(err);
            } finally {
                SVNFileUtil.closeFile(is);
            }
            return bos.toByteArray();
        } finally {
            SVNFileUtil.deleteFile(tmpFile);
        }
    }
    
    private static String getEditorCommand(SVNCommandEnvironment env) throws SVNException {
        if (env.getEditorCommand() != null) {
            return env.getEditorCommand();
        } 
        String command = SVNFileUtil.getEnvironmentVariable("SVN_EDITOR");
        if (command == null) {
            command = env.getClientManager().getOptions().getEditor();
        }
        if (command == null) {
            command = SVNFileUtil.getEnvironmentVariable("VISUAL");
        }
        if (command == null) {
            command = SVNFileUtil.getEnvironmentVariable("EDITOR");
        }
        String errorMessage = null;
        if (command == null) {
            errorMessage = 
                "None of the environment variables SVN_EDITOR, VISUAL or EDITOR is " +
                "set, and no 'editor-cmd' run-time configuration option was found"; 
        } else if ("".equals(command.trim())) {
            errorMessage = 
                "The EDITOR, SVN_EDITOR or VISUAL environment variable or " +
                "'editor-cmd' run-time configuration option is empty or " +
                "consists solely of whitespace. Expected a shell command.";
        }
        if (errorMessage != null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_NO_EXTERNAL_EDITOR, errorMessage));
        }
        return command;
    }
}
