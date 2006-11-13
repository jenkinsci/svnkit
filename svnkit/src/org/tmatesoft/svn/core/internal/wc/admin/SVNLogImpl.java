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
package org.tmatesoft.svn.core.internal.wc.admin;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNLogImpl extends SVNLog {

    private File myFile;
    private File myTmpFile;

    public SVNLogImpl(File logFile, File tmpFile, SVNAdminArea adminArea) {
        super(adminArea);
        myFile = logFile;
        myTmpFile = tmpFile;
    }

    public void save() throws SVNException {
        if (myTmpFile == null || myCache == null) {
            return;
        }
        
        Writer os = null;
        try {
            os = new OutputStreamWriter(SVNFileUtil.openFileForWriting(myTmpFile), "UTF-8");
            for (Iterator commands = myCache.iterator(); commands.hasNext();) {
                Map command = (Map) commands.next();
                String name = (String) command.remove("");
                os.write("<");
                os.write(name);
                for (Iterator attrs = command.keySet().iterator(); attrs.hasNext();) {
                    String attr = (String) attrs.next();
                    String value = (String) command.get(attr);
                    if (value == null) {
                        value = "";
                    }
                    value = SVNEncodingUtil.xmlEncodeAttr(value);
                    os.write("\n   ");
                    os.write(attr);
                    os.write("=\"");
                    os.write(value);
                    os.write("\"");
                }
                os.write("/>\n");
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot write log file ''{0}'': {1}", new Object[] {myFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(os);
            myCache = null;
        }
        SVNFileUtil.rename(myTmpFile, myFile);
        SVNFileUtil.setReadonly(myFile, true);
    }

    public Collection readCommands() throws SVNException {
        if (!myFile.exists()) {
            return null;
        }
        BufferedReader reader = null;
        Collection commands = new ArrayList();
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(myFile), "UTF-8"));
            String line;
            Map attrs = new HashMap();
            String name = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("<")) {
                    name = line.substring(1);
                    continue;
                }
                int index = line.indexOf('=');
                if (index > 0) {
                    String attrName = line.substring(0, index).trim();
                    String value = line.substring(index + 1).trim();
                    if (value.endsWith("/>")) {
                        value = value.substring(0, value.length() - "/>".length());
                    }
                    if (value.startsWith("\"")) {
                        value = value.substring(1);
                    }
                    if (value.endsWith("\"")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    value = SVNEncodingUtil.xmlDecode(value);
                    if ("".equals(value) && !SVNLog.NAME_ATTR.equals(attrName)) {
                        value = null;
                    }
                    attrs.put(attrName, value);
                }
                if (line.endsWith("/>") && name != null) {
                    // run command
                    attrs.put("", name);
                    commands.add(attrs);
                    attrs = new HashMap();
                    name = null;
                }
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read log file ''{0}'': {1}", new Object[] {myFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        
        return commands;
    }

    public String toString() {
        return "Log: " + myFile;
    }

    public void delete() throws SVNException {
        SVNFileUtil.deleteFile(myFile);
        SVNFileUtil.deleteFile(myTmpFile);
    }

    public boolean exists() {
        return myFile.exists();
    }

}
