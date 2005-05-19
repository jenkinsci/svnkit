/*
 * Created on 18.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.TimeUtil;

public class SVNLogRunner {

    public void runCommand(SVNDirectory dir, String name, Map attributes) throws SVNException {
        String fileName = (String) attributes.remove(SVNLog.NAME_ATTR);
        if (SVNLog.DELETE_ENTRY.equals(name)) {
            dir.destroy(fileName, true);
        } else if (SVNLog.MODIFY_ENTRY.equals(name)) {
            SVNEntries entries = dir.getEntries();
            if (entries.getEntry(fileName) == null) {
                entries.addEntry(fileName);
            }
            for (Iterator atts = attributes.keySet().iterator(); atts.hasNext();) {
                String attName = (String) atts.next();
                String value = (String) attributes.get(attName);
                attName = SVNProperty.SVN_ENTRY_PREFIX + attName;

                if (SVNLog.WC_TIMESTAMP.equals(value)) {
                    if (SVNProperty.PROP_TIME.equals(attName)) {
                        String path = "".equals(fileName) ? ".svn/dir-props" : ".svn/props/" + fileName + ".svn-work";
                        File file = new File(dir.getRoot(), path);
                        value = TimeUtil.formatDate(new Date(file.lastModified()));
                    } else if (SVNProperty.TEXT_TIME.equals(attName)) {
                        String path = "".equals(fileName) ? "" : fileName;
                        File file = new File(dir.getRoot(), path);
                        value = TimeUtil.formatDate(new Date(file.lastModified()));
                    }
                }
                
                entries.setPropertyValue(fileName, attName, value);
            }
            entries.save(true);
        } else if (SVNLog.MODIFY_WC_PROPERTY.equals(name)) {
            SVNProperties props = dir.getWCProperties(fileName);
            String propName = (String) attributes.get(SVNLog.PROPERTY_NAME_ATTR);
            String propValue = (String) attributes.get(SVNLog.PROPERTY_VALUE_ATTR);
            props.setPropertyValue(propName, propValue);
        } else if (SVNLog.DELETE_LOCK.equals(name)) {
            SVNEntries entries = dir.getEntries();
            SVNEntry entry = entries.getEntry(fileName);
            if (entry != null) {
                entry.setLockToken(null);
                entry.setLockOwner(null);
                entry.setLockCreationDate(null);
                entry.setLockComment(null);
                entries.save(true);
            }
        } else if (SVNLog.DELETE.equals(name)) {
            File file = new File(dir.getRoot(), fileName);
            file.delete();
        } else if (SVNLog.READONLY.equals(name)) {
            File file = new File(dir.getRoot(), fileName);
            try {
                SVNFileUtil.setReadonly(file, true);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        } else if (SVNLog.MOVE.equals(name)) {
            File src = new File(dir.getRoot(), fileName);
            File dst = new File(dir.getRoot(), (String) attributes.get(SVNLog.DESTINATION_VALUE_ATTR));
            try {
                SVNFileUtil.rename(src, dst);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        } else if (SVNLog.APPEND.equals(name)) {
            File src = new File(dir.getRoot(), fileName);
            File dst = new File(dir.getRoot(), (String) attributes.get(SVNLog.DESTINATION_VALUE_ATTR));
            OutputStream os = null;
            InputStream is = null;
            try {
                os = new FileOutputStream(dst, true);
                is = new FileInputStream(src);
                while(true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                    os.write(r);
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
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
            
        }
    }
    
    

}
