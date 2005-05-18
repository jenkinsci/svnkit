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
        if (SVNLog.DELETE_ENTRY.equals(name)) {
            String fileName = (String) attributes.get(SVNLog.NAME_ATTR);
            dir.destroy(fileName, true);
        } else if (SVNLog.MODIFY_ENTRY.equals(name)) {
            String fileName = (String) attributes.get(SVNLog.NAME_ATTR);
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
        }
    }
    
    

}
