package org.tmatesoft.svn.core.internal.wc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import org.tmatesoft.svn.core.io.SVNException;

public class SVNProperties {
    
    private File myFile;
    private String myPath;
    
    public SVNProperties(File properitesFile, String path) {
        myFile = properitesFile;
        myPath = path;
    }
    
    public File getFile() {
        return myFile;
    }
    
    public String getPath() {
        return myPath;
    }
    
    public Collection properties(Collection target) throws SVNException {
        target = target == null ? new TreeSet() : target;
        if (!myFile.exists()) {
            return target;
        }
        ByteArrayOutputStream nameOS = new ByteArrayOutputStream();
        InputStream is = null;
        try {
            is = new FileInputStream(myFile);
            while(readProperty('K', is, nameOS)) {
                target.add(new String(nameOS.toByteArray(), "UTF-8"));
                nameOS.reset();
                readProperty('V', is, null);
            }
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return target;
    }
    
    public Map asMap() throws SVNException {
        Map result = new HashMap();
        if (getFile().exists()) {
            return result;
        }
        for(Iterator names = properties(null).iterator(); names.hasNext();) {
            String name = (String) names.next();
            String value  = getPropertyValue(name);
            result.put(name, value);
        }
        return result;
    }
    
    public boolean compareTo(SVNProperties properties, ISVNPropertyComparator comparator) throws SVNException {
        boolean equals = true;
        Collection props1 = properties(null); 
        Collection props2 = properties.properties(null);
        
        // missed in props2.
        Collection tmp = new TreeSet(props1);
        tmp.removeAll(props2);
        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String missing = (String) props.next();
            comparator.propertyDeleted(missing);
            equals = false;
        }
        
        // added in props2.
        tmp = new TreeSet(props2);
        tmp.removeAll(props1);        
        
        File tmpFile = null;
        File tmpFile1 = null;
        File tmpFile2 = null;
        OutputStream os = null;
        InputStream is = null;
        InputStream is1 = null;
        InputStream is2 = null;

        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String added = (String) props.next();
            try {
                tmpFile = SVNFileUtil.createUniqueFile(myFile.getParentFile(), myFile.getName(), ".tmp");
    
                os = new FileOutputStream(tmpFile);
                properties.getPropertyValue(added, os);
                os.close();

                is = new FileInputStream(tmpFile);
                comparator.propertyAdded(added, is, (int) tmpFile.length());
                equals = false;
                is.close();
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {}
                }
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {}
                }
                tmpFile = null;
                is = null;
                os = null;
            }
        }        
        
        // changed in props2
        props2.retainAll(props1);
        for (Iterator props = props2.iterator(); props.hasNext();) {
            String changed = (String) props.next();
            
            try {
                tmpFile1 = SVNFileUtil.createUniqueFile(myFile.getParentFile(), myFile.getName(), ".tmp1");
                tmpFile2 = SVNFileUtil.createUniqueFile(myFile.getParentFile(), myFile.getName(), ".tmp2");
                
                os = new FileOutputStream(tmpFile1);
                getPropertyValue(changed, os);
                os.close();
                os = new FileOutputStream(tmpFile2);
                properties.getPropertyValue(changed, os);
                os.close();
                if (tmpFile2.length() != tmpFile1.length()) {
                    is = new FileInputStream(tmpFile2);
                    comparator.propertyChanged(changed, is, (int) tmpFile2.length());
                    equals = false;
                    is.close();
                } else {
                    is1 = new FileInputStream(tmpFile1);
                    is2 = new FileInputStream(tmpFile2);
                    boolean differs = false;
                    for(int i = 0; i < tmpFile1.length(); i++) {
                        if (is1.read() != is2.read()) {
                            differs = true;
                            break;
                        }
                    }
                    is1.close();
                    is2.close();
                    if (differs) {
                        is2 = new FileInputStream(tmpFile2);
                        comparator.propertyChanged(changed, is2, (int) tmpFile2.length());
                        equals = false;
                        is2.close();
                    }
                }
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (tmpFile2 != null) {
                    tmpFile2.delete();
                }
                if (tmpFile2 != null) {
                    tmpFile1.delete();
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {}
                }
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {}
                }
                if (is1 != null) {
                    try {
                        is1.close();
                    } catch (IOException e) {}
                }
                if (is2 != null) {
                    try {
                        is2.close();
                    } catch (IOException e) {}
                }
                os = null;
                tmpFile1 = tmpFile2 = null;
                is = is1 = is2 = null;
            }
        }
        return equals;
    }
    
    public String getPropertyValue(String name) throws SVNException {
        if (!myFile.exists()) {
            return null;
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        getPropertyValue(name, os);
        if (os.size() > 0) {
            byte[] bytes = os.toByteArray();
            try {
                return new String(bytes, "UTF-8");
            } catch (IOException e) {
            }
            return new String(bytes);
        }
        return null;
    }

    public void getPropertyValue(String name, OutputStream os) throws SVNException {
        if (!myFile.exists()) {
            return;
        }
        ByteArrayOutputStream nameOS = new ByteArrayOutputStream();
        InputStream is = null;
        try {
            is = new FileInputStream(myFile);
            while(readProperty('K', is, nameOS)) {
                String currentName = new String(nameOS.toByteArray(), "UTF-8");
                nameOS.reset();
                if (currentName.equals(name)) {
                    readProperty('V', is, os);
                    return;
                } else {
                    readProperty('V', is, null);
                }
            }
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
    }

    public void setPropertyValue(String name, String value) throws SVNException {
        
        byte[] bytes = null;
        if (value != null) {
            try {
                bytes = value.getBytes("UTF-8");
            } catch (IOException e) {
                bytes = value.getBytes();
            }
        }
        int length = bytes != null && bytes.length > 0 ? bytes.length : -1;
        setPropertyValue(name, bytes != null ? new ByteArrayInputStream(bytes) : null, length);
    }

    public void setPropertyValue(String name, InputStream is, int length) throws SVNException {
        InputStream src = null;
        OutputStream dst = null;
        File tmpFile = null;
        try {
            tmpFile = SVNFileUtil.createUniqueFile(myFile.getParentFile(), myFile.getName(), ".tmp");
            if (myFile.exists()) {
                src = new FileInputStream(myFile);
            }
            dst = new FileOutputStream(tmpFile);
            copyProperties(src, dst, name, is, length);
        } catch(IOException e) {
            SVNErrorManager.error(0, e);
        } finally {        
            if (src != null) {
                try {
                    src.close();
                } catch (IOException e) {}
            }
            if (dst != null) {
                try {
                    dst.close();
                } catch (IOException e) {}
            }
        }
        try {
            if (tmpFile != null) {
                SVNFileUtil.rename(tmpFile, myFile);
                SVNFileUtil.setReadonly(myFile, true);
            }            
        } catch (IOException e) {
            if (tmpFile != null) {
                tmpFile.delete();
            }
            SVNErrorManager.error(0, e);
        }
    }
    
    public Map compareTo(SVNProperties properties) throws SVNException {
        final Map locallyChangedProperties = new HashMap();
        compareTo(properties, new ISVNPropertyComparator() {
            public void propertyAdded(String name, InputStream value, int length) {
                propertyChanged(name, value, length);
            }
            public void propertyChanged(String name, InputStream newValue, int length) {
                ByteArrayOutputStream os = new ByteArrayOutputStream(length);
                for(int i = 0; i < length; i++) {
                    try {
                        os.write(newValue.read());
                    } catch (IOException e) {
                    }
                }
                byte[] bytes = os.toByteArray();
                try {
                    locallyChangedProperties.put(name, new String(bytes, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    locallyChangedProperties.put(name, new String(bytes));
                }
            }
            public void propertyDeleted(String name) {
                locallyChangedProperties.put(name, null);
            }
        });
        return locallyChangedProperties;        
    }
    
    public void copyTo(SVNProperties destination) throws SVNException {
        if (destination.getFile().exists()) {
            SVNErrorManager.error(0, null);
        }
        if (!getFile().exists()) {
            // just create empty dst.
            destination.setPropertyValue("tmp", "empty");
            destination.setPropertyValue("tmp", null);
            // this will leave "end\n";
        } else {
            try {
                SVNFileUtil.copy(getFile(), destination.getFile(), false);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        }
    }
    
    public void delete() {
        myFile.delete();
    }
    
    private static void copyProperties(InputStream is, OutputStream os, String name, InputStream value, int length) throws IOException {
        // read names, till name is met, then insert value or skip this property.
        try {
            if (is != null) {
                ByteArrayOutputStream nameOS = new ByteArrayOutputStream();
                int l = 0;
                while((l = readLength(is, 'K')) > 0) {
                    byte[] nameBytes = new byte[l];
                    is.read(nameBytes);
                    is.read();
                    if (name.equals(new String(nameBytes, "UTF-8"))) {
                        // skip property, will be appended. 
                        readProperty('V', is, null);
                        continue;
                    } 
                    // save name
                    writeProperty(os, 'K', nameBytes);
                    l = readLength(is, 'V');
                    writeProperty(os, 'V', is, l);
                    is.read();
                }
            } 
            if (value != null && length > 0) {
                byte[] nameBytes = name.getBytes("UTF-8");
                writeProperty(os, 'K', nameBytes);
                writeProperty(os, 'V', value, length);
            }
            os.write(new byte[] {'E', 'N', 'D', '\n'});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static boolean readProperty(char type, InputStream is, OutputStream os) throws IOException {
        int length = readLength(is, type);
        if (length < 0) {
            return false;
        }
        if (os != null) {
            for(int i = 0; i < length; i++) {
                int r = is.read();
                os.write(r);
            }
        } else {
            is.skip(length);
        }
        return is.read() == '\n';
    }
    
    private static void writeProperty(OutputStream os, char type, byte[] value) throws IOException {
        os.write((byte) type);
        os.write(' ');
        os.write(Integer.toString(value.length).getBytes());
        os.write('\n');
        os.write(value);
        os.write('\n');
    }

    private static void writeProperty(OutputStream os, char type, InputStream value, int length) throws IOException {
        os.write((byte) type);
        os.write(' ');
        os.write(Integer.toString(length).getBytes());
        os.write('\n');
        for(int i = 0; i < length; i++) {
            int r = value.read();
            os.write(r);
        }
        os.write('\n');
    }

    private static int readLength(InputStream is, char type) throws IOException {
        byte[] buffer = new byte[255];
        int r = is.read(buffer, 0, 4);
        if (r != 4) {
            throw new IOException("invalid properties file format");
        }
        // either END\n or K x\n 
        if (buffer[0] == 'E' && buffer[1] == 'N' && buffer[2] == 'D' && buffer[3] == '\n') {
            return -1;
        } else if (buffer[0] == type && buffer[1] == ' ') {
            int i = 4;
            if (buffer[3] != '\n') {
                while(true) {
                    int b = is.read();
                    if (b < 0) {
                        throw new IOException("invalid properties file format");
                    } else if (b == '\n') {
                        break;
                    }
                    buffer[i] = (byte) (0xFF & b);
                    i++;
                }
            } else {
                i = 3;
            }
            String length = new String(buffer, 2, i - 2);
            return Integer.parseInt(length);
        }
        throw new IOException("invalid properties file format");
    }
}
