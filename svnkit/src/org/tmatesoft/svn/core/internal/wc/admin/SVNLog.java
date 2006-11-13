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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNLog {
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

    public static final String COPY = "cp";

    public static final String MERGE = "merge";

    public static final String MAYBE_READONLY = "maybe-readonly";

    public static final String SET_TIMESTAMP = "set-timestamp";

    public static final String COMMIT = "committed";

    public static final String UPGRADE_FORMAT = "upgrade-format";

    public static final String NAME_ATTR = "name";

    public static final String PROPERTY_NAME_ATTR = "propname";

    public static final String PROPERTY_VALUE_ATTR = "propval";

    public static final String DEST_ATTR = "dest";

    public static final String TIMESTAMP_ATTR = "timestamp";

    public static final String REVISION_ATTR = "revision";

    public static final String FORMAT_ATTR = "format";

    public static final String ATTR1 = "arg1";
    public static final String ATTR2 = "arg2";
    public static final String ATTR3 = "arg3";
    public static final String ATTR4 = "arg4";
    public static final String ATTR5 = "arg5";
    public static final String ATTR6 = "arg6";

    public static final String WC_TIMESTAMP = "working";

    protected Collection myCache;
    protected SVNAdminArea myAdminArea;

    public abstract void save() throws SVNException;

    public abstract String toString();

    public abstract void delete() throws SVNException;

    public abstract boolean exists();
    
    protected SVNLog(SVNAdminArea adminArea) {
        myAdminArea = adminArea;
    }
    
    public void addCommand(String name, Map attributes, boolean save) throws SVNException {
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

    public SVNStatusType logChangedEntryProperties(String name, Map modifiedEntryProps) throws SVNException {
        SVNStatusType status = SVNStatusType.LOCK_UNCHANGED;
        if (modifiedEntryProps != null) {
            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, name);
            for (Iterator names = modifiedEntryProps.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) modifiedEntryProps.get(propName);
                String longPropName = propName;
                if (!(SVNProperty.CACHABLE_PROPS.equals(propName) || SVNProperty.PRESENT_PROPS.equals(propName) ||
                        SVNProperty.HAS_PROPS.equals(propName) || SVNProperty.HAS_PROP_MODS.equals(propName))) {
                    longPropName = SVNProperty.SVN_ENTRY_PREFIX + propName;
                }
                if (SVNProperty.LOCK_TOKEN.equals(longPropName)) {
                    Map deleteLockCommand = new HashMap();
                    deleteLockCommand.put(SVNLog.NAME_ATTR, name);
                    addCommand(SVNLog.DELETE_LOCK, deleteLockCommand, false);
                    status = SVNStatusType.LOCK_UNLOCKED;
                } else if (propValue != null) {
                    command.put(propName, propValue);
                }
            }
            addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        return status;
    }

    public void logChangedWCProperties(String name, Map modifiedWCProps) throws SVNException {
        if (modifiedWCProps != null) {
            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, name);
            for (Iterator names = modifiedWCProps.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) modifiedWCProps.get(propName);
                command.put(SVNLog.PROPERTY_NAME_ATTR, propName);
                if (propValue != null) {
                    command.put(SVNLog.PROPERTY_VALUE_ATTR, propValue);
                } else {
                    command.remove(SVNLog.PROPERTY_VALUE_ATTR);
                }
                addCommand(SVNLog.MODIFY_WC_PROPERTY, command, false);
                command.clear();
            }
        }
    }

    public void run(SVNLogRunner runner) throws SVNException {
        Collection commands = readCommands();
        if (commands == null || commands.isEmpty()) {
            return;
        }
        
        try {
            int count = 0;
            for (Iterator cmds = commands.iterator(); cmds.hasNext();) {
                Map command = (Map) cmds.next();
                String name = (String) command.get("");
                String attrName = (String) command.get(SVNLog.NAME_ATTR);
                if (attrName == null && !SVNLog.UPGRADE_FORMAT.equals(name)) {
                    SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
                    SVNErrorMessage err = SVNErrorMessage.create(code, "Log entry missing 'name' attribute (entry ''{0}'' for directory ''{1}'')", new Object[]{name, myAdminArea.getRoot()});
                    SVNErrorManager.error(err);
                }
                if (runner != null) {
                    runner.runCommand(myAdminArea, name, command, ++count);
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
    
    protected abstract Collection readCommands() throws SVNException;

}
