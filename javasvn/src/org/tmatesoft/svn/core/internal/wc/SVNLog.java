/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;

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

    public static final String NAME_ATTR = "name";
    public static final String PROPERTY_NAME_ATTR = "propname";
    public static final String PROPERTY_VALUE_ATTR = "propvalue";
    public static final String DEST_ATTR = "dest";
    public static final String TIMESTAMP_ATTR = "timestamp";
    public static final String ATTR1 = "attr1";
    public static final String ATTR2 = "attr2";
    public static final String ATTR3 = "attr3";
    public static final String ATTR4 = "attr4";
    public static final String ATTR5 = "attr5";

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

    public SVNEventStatus logChangedEntryProperties(String name, Map modifiedEntryProps) throws SVNException {
        SVNEventStatus status = SVNEventStatus.LOCK_UNCHANGED;
        if (modifiedEntryProps != null) {
            Map command = new HashMap();
            command.put(SVNLog.NAME_ATTR, name);
            for (Iterator names = modifiedEntryProps.keySet().iterator(); names.hasNext();) {                
                String propName = (String) names.next();
                String propValue = (String) modifiedEntryProps.get(propName);
                if (SVNProperty.LOCK_TOKEN.equals(propName)) {
                    addCommand(SVNLog.DELETE_LOCK, command, false);
                    status = SVNEventStatus.LOCK_UNLOCKED;
                } else if (propValue != null) {
                    command.put(propName, propValue);
                    addCommand(SVNLog.MODIFY_ENTRY, command, false);
                    command.remove(SVNTranslator.xmlEncode(propName));
                } 
            }
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
            }
        }
    }
    
    public void save() throws SVNException {
        Writer os = null;
        
        try {
            os = new OutputStreamWriter(new FileOutputStream(myTmpFile), "UTF-8");
            for (Iterator commands = myCache.iterator(); commands.hasNext();) {
                Map command = (Map) commands.next();
                String name = (String) command.remove("");
                os.write("<");
                os.write(name);
                for (Iterator attrs = command.keySet().iterator(); attrs.hasNext();) {
                    String attr = (String) attrs.next();
                    String value = (String) command.get(attr);
                    if (value == null) {
                        continue;
                    }
                    value = SVNTranslator.xmlEncode(value);
                    os.write("\n   ");
                    os.write(attr);
                    os.write("=\"");
                    os.write(value);
                    os.write("\"");
                }
                os.write("/>\n");
            }            
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
            myCache = null;
        }
        try {
            SVNFileUtil.rename(myTmpFile, myFile);
            SVNFileUtil.setReadonly(myFile, true);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
    }
    
    public void run(SVNLogRunner runner) throws SVNException {
        if (!myFile.exists()) {
            return;
        }
        BufferedReader reader = null;
        Collection commands = new ArrayList();
        try {
            reader = new BufferedReader(new FileReader(myFile));
            String line;
            Map attrs = new HashMap();
            String name = null;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("<")) {
                    name = line.substring(1);
                    continue;
                } else {
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
                        value = SVNTranslator.xmlDecode(value);
                        attrs.put(attrName, value);
                    }                    
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
            SVNErrorManager.error(0, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        SVNException error = null;
        try {
            for (Iterator cmds = commands.iterator(); error == null && cmds.hasNext();) {
                Map command = (Map) cmds.next();
                String name = (String) command.remove("");
                if (runner != null) {
                    try {
                        runner.runCommand(myDirectory, name, command);
                        cmds.remove();
                    } catch (SVNException th) {
                        command.put("", name);
                        myCache = commands;
                        save();
                        error = th;
                    }
                }
            }
            if (error == null) {
                delete();
            }
        } finally {
            try {
                runner.logCompleted(myDirectory);
            } catch (SVNException e) {
                if (error == null) {
                    error = e;
                }
            }
            if (error != null) {
                throw error;
            }
        }
    }
    
    public void delete() {
        myFile.delete();
        myTmpFile.delete();
    }

    public boolean exists() {
        return myFile.exists();
    }

}
