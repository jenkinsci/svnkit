/*
 * Created on 18.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNException;

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
            
        }
    }
    
    

}
