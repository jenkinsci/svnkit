/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.wc.SVNStatusType;

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
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNLog {

    public static final String DELETE_ENTRY = "delete-entry";

    public static final String MODIFY_ENTRY = "modify-entry";

    public static final String MODIFY_WC_PROPERTY = "modify-wcprop";

    public static final String DELETE_LOCK = "delete-lock";

    public static final String MOVE = "mv";

    public static final String APPEND = "append";

    public static final String DELETE = "rm";

    public static final String READONLY = "readonly";

    public static final String COPY_AND_TRANSLATE = "cp-and-translate";

    public static final String COPY_AND_DETRANSLATE = "cp-and-detranslate";

    public static final String MERGE = "merge";

    public static final String MAYBE_READONLY = "maybe-readonly";

    public static final String SET_TIMESTAMP = "set-timestamp";

    public static final String COMMIT = "committed";

    public static final String NAME_ATTR = "name";

    public static final String PROPERTY_NAME_ATTR = "propname";

    public static final String PROPERTY_VALUE_ATTR = "propval";

    public static final String DEST_ATTR = "dest";

    public static final String TIMESTAMP_ATTR = "timestamp";

    public static final String REVISION_ATTR = "revision";

    public static final String ATTR1 = "arg1";
    public static final String ATTR2 = "arg2";
    public static final String ATTR3 = "arg3";
    public static final String ATTR4 = "arg4";
    public static final String ATTR5 = "arg5";
    public static final String ATTR6 = "arg6";

    public static final String WC_TIMESTAMP = "working";

    private File myFile;

    private File myTmpFile;

    private Collection myCache;

    private SVNDirectory myDirectory;

    public SVNLog(SVNDirectory directory, int id) {
        String name = id == 0 ? "log" : "log." + id;
        myFile = new File(directory.getRoot(), ".svn/" + name);
        myTmpFile = new File(directory.getRoot(), ".svn/tmp/" + name);
        myDirectory = directory;
    }

    public void addCommand(String name, Map attributes, boolean save)
            throws SVNException {
        if (myCache == null) {
            myCache = new ArrayList();
        }
        attributes = new HashMap(attributes);
        attributes.put("", name);
        myCache.add(attributes);
        if (save) {
            save();
        }
    }

    public SVNStatusType logChangedEntryProperties(String name,
            Map modifiedEntryProps) throws SVNException {
        SVNStatusType status = SVNStatusType.LOCK_UNCHANGED;
        if (modifiedEntryProps != null) {
            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, name);
            for (Iterator names = modifiedEntryProps.keySet().iterator(); names
                    .hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) modifiedEntryProps.get(propName);
                String longPropName = SVNProperty.SVN_ENTRY_PREFIX + propName;
                if (SVNProperty.LOCK_TOKEN.equals(longPropName)) {
                    addCommand(SVNLog.DELETE_LOCK, command, false);
                    status = SVNStatusType.LOCK_UNLOCKED;
                } else if (propValue != null) {
                    command.put(propName, propValue);
                    addCommand(SVNLog.MODIFY_ENTRY, command, false);
                    command.remove(SVNEncodingUtil.xmlEncodeAttr(propName));
                }
            }
        }
        return status;
    }

    public void logChangedWCProperties(String name, Map modifiedWCProps)
            throws SVNException {
        if (modifiedWCProps != null) {
            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, name);
            for (Iterator names = modifiedWCProps.keySet().iterator(); names
                    .hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) modifiedWCProps.get(propName);
                command.put(SVNLog.PROPERTY_NAME_ATTR, propName);
                if (propValue != null) {
                    command.put(SVNLog.PROPERTY_VALUE_ATTR, propValue);
                } else {
                    command.remove(SVNLog.PROPERTY_VALUE_ATTR);
                }
                addCommand(SVNLog.MODIFY_WC_PROPERTY, command, false);
            }
        }
    }

    public void save() throws SVNException {
        Writer os = null;

        try {
            os = new OutputStreamWriter(SVNFileUtil
                    .openFileForWriting(myTmpFile), "UTF-8");
            for (Iterator commands = myCache.iterator(); commands.hasNext();) {
                Map command = (Map) commands.next();
                String name = (String) command.remove("");
                os.write("<");
                os.write(name);
                for (Iterator attrs = command.keySet().iterator(); attrs
                        .hasNext();) {
                    String attr = (String) attrs.next();
                    String value = (String) command.get(attr);
                    if (value == null) {
                        continue;
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
            SVNErrorManager.error("svn: Cannot save log file '" + myFile + "'");
        } finally {
            SVNFileUtil.closeFile(os);
            myCache = null;
        }
        SVNFileUtil.rename(myTmpFile, myFile);
        SVNFileUtil.setReadonly(myFile, true);
    }

    public void run(SVNLogRunner runner) throws SVNException {
        if (!myFile.exists()) {
            return;
        }
        BufferedReader reader = null;
        Collection commands = new ArrayList();
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil
                    .openFileForReading(myFile), "UTF-8"));
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
                        value = value.substring(0, value.length()
                                - "/>".length());
                    }
                    if (value.startsWith("\"")) {
                        value = value.substring(1);
                    }
                    if (value.endsWith("\"")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    value = SVNEncodingUtil.xmlDecode(value);
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
            SVNErrorManager.error("svn: Cannot read log file '" + myFile + "'");
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        try {
            for (Iterator cmds = commands.iterator(); cmds.hasNext();) {
                Map command = (Map) cmds.next();
                String name = (String) command.get("");
                if (runner != null) {
                    runner.runCommand(myDirectory, name, command);
                }
                cmds.remove();
            }
        } catch (SVNException e) {
            // save failed command and unexecuted commands back to the log file.
            myCache = null;
            for (Iterator cmds = commands.iterator(); cmds.hasNext();) {
                Map command = (Map) cmds.next();
                String name = (String) command.remove("");
                addCommand(name, command, false);
            }
            save();
            throw e;
        }
    }

    public String toString() {
        return "Log: " + myFile;
    }

    public void delete() {
        myFile.delete();
        myTmpFile.delete();
    }

    public boolean exists() {
        return myFile.exists();
    }

}
