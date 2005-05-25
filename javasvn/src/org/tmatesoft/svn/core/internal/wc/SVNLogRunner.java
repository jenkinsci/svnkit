/*
 * Created on 18.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

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
import org.tmatesoft.svn.core.wc.SVNEventStatus;
import org.tmatesoft.svn.util.TimeUtil;

public class SVNLogRunner {
    
    private boolean myIsEntriesChanged;

    public void runCommand(SVNDirectory dir, String name, Map attributes) throws SVNException {
        String fileName = (String) attributes.remove(SVNLog.NAME_ATTR);
        if (SVNLog.DELETE_ENTRY.equals(name)) {
            // check if it is not disjoint entry not to delete another wc?
            dir.destroy(fileName, true);
        } else if (SVNLog.MODIFY_ENTRY.equals(name)) {
            SVNEntries entries = dir.getEntries();
            boolean modified = false;
            if (entries.getEntry(fileName) == null) {
                entries.addEntry(fileName);
                modified = true;
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
                modified = true;
            }
            setEntriesChanged(modified);
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
                setEntriesChanged(true);
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
            File dst = new File(dir.getRoot(), (String) attributes.get(SVNLog.DEST_ATTR));
            try {
                SVNFileUtil.rename(src, dst);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        } else if (SVNLog.APPEND.equals(name)) {
            File src = new File(dir.getRoot(), fileName);
            File dst = new File(dir.getRoot(), (String) attributes.get(SVNLog.DEST_ATTR));
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
        } else if (SVNLog.SET_TIMESTAMP.equals(name)) {
            File file = new File(dir.getRoot(), fileName);
            Date time = TimeUtil.parseDate((String) attributes.get(SVNLog.TIMESTAMP_ATTR));
            file.setLastModified(time.getTime());            
        } else if (SVNLog.MAYBE_READONLY.equals(name)) {
            SVNEntries entries = dir.getEntries();
            if (entries.getEntry(fileName) != null &&
                    entries.getEntry(fileName).getLockToken() == null) {
                try {
                    SVNFileUtil.setReadonly(new File(dir.getRoot(), fileName), true);
                } catch (IOException e) {
                    SVNErrorManager.error(0, e);
                }
            }
        } else if (SVNLog.COPY_AND_TRANSLATE.equals(name)) {
            String dstName = (String) attributes.get(SVNLog.DEST_ATTR);
            File dst = new File(dir.getRoot(), dstName);
            // get properties for this entry.
            SVNProperties props = dir.getProperties(dstName, false);
            boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
            
            SVNTranslator.translate(dir, dstName, fileName, dstName, true, true);
            if (executable) {
                SVNFileUtil.setExecutable(dst, true);
            }
            SVNEntry entry = dir.getEntries().getEntry(dstName);
            if (entry.getLockToken() == null && props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                try {
                    SVNFileUtil.setReadonly(dst, true);
                } catch (IOException e) {
                    SVNErrorManager.error(0, e);
                }
            }
        } else if (SVNLog.COPY_AND_DETRANSLATE.equals(name)) {
            String dstName = (String) attributes.get(SVNLog.DEST_ATTR);
            SVNTranslator.translate(dir, fileName, fileName, dstName, false, true);
        } else if (SVNLog.MERGE.equals(name)) {
            File target = new File(dir.getRoot(), fileName);
            String leftPath = (String) attributes.get(SVNLog.ATTR1);
            String rightPath = (String) attributes.get(SVNLog.ATTR2);
            String leftLabel = (String) attributes.get(SVNLog.ATTR3);
            leftLabel = leftLabel == null ? ".old" : leftLabel;
            String rightLabel = (String) attributes.get(SVNLog.ATTR4);
            rightLabel = rightLabel == null ? ".new" : rightLabel;
            String targetLabel = (String) attributes.get(SVNLog.ATTR5);
            targetLabel = targetLabel == null ? ".working" : targetLabel;
            
            SVNProperties props = dir.getProperties(fileName, false);
            SVNEntry entry = dir.getEntries().getEntry(fileName);
            
            SVNEventStatus mergeResult = dir.mergeText(fileName, leftPath, rightPath, targetLabel, leftLabel, rightLabel, false);

            if (props.getPropertyValue(SVNProperty.EXECUTABLE) != null) {
                SVNFileUtil.setExecutable(target, true);
            }
            if (props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null && entry.getLockToken() == null) {
                try {
                    SVNFileUtil.setReadonly(target, true);
                } catch (IOException e) {
                    SVNErrorManager.error(0, e);
                }
            }
            setEntriesChanged(mergeResult == SVNEventStatus.CONFLICTED);            
        }
    }
    
    private void setEntriesChanged(boolean modified) {
        myIsEntriesChanged |= modified;
    }

    public void logCompleted(SVNDirectory dir) throws SVNException {
        if (myIsEntriesChanged) {
            dir.getEntries().save(true);
        } else {
            dir.getEntries().close();
        }
        myIsEntriesChanged = false;
    }
    
    

}
