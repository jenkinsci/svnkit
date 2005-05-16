package org.tmatesoft.svn.core.internal.ws.log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

public class SVNProperties {
    
    private File myFile;
    public SVNProperties(File properitesFile) {
        myFile = properitesFile;
    }
    
    public Collection properties(Collection target) throws IOException {
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
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return target;
    }
    
    public boolean compareTo(SVNProperties properties, ISVNPropertyComparator comparator) throws IOException {
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
        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String added = (String) props.next();

            File tmpFile = File.createTempFile("property", "tmp", myFile.getParentFile());
            tmpFile.deleteOnExit();
            
            OutputStream os = new FileOutputStream(tmpFile);
            properties.getPropertyValue(added, os);
            os.close();
            InputStream is = new FileInputStream(tmpFile);
            comparator.propertyAdded(added, is, (int) tmpFile.length());
            equals = false;
            is.close();
            
            tmpFile.delete();
        }        
        
        // changed in props2
        props2.retainAll(props1);
        for (Iterator props = props2.iterator(); props.hasNext();) {
            String changed = (String) props.next();

            File tmpFile1 = File.createTempFile("property", "tmp", myFile.getParentFile());
            tmpFile1.deleteOnExit();
            File tmpFile2 = File.createTempFile("property", "tmp", myFile.getParentFile());
            tmpFile2.deleteOnExit();
            
            OutputStream os = new FileOutputStream(tmpFile1);
            getPropertyValue(changed, os);
            os.close();
            os = new FileOutputStream(tmpFile2);
            properties.getPropertyValue(changed, os);
            os.close();
            if (tmpFile2.length() != tmpFile1.length()) {
                InputStream is = new FileInputStream(tmpFile2);
                comparator.propertyChanged(changed, is, (int) tmpFile2.length());
                equals = false;
                is.close();
            } else {
                InputStream is1 = new FileInputStream(tmpFile1);
                InputStream is2 = new FileInputStream(tmpFile2);
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
            tmpFile2.delete();
            tmpFile1.delete();
        }
        return equals;
    }
    
    public String getPropertyValue(String name) throws IOException {
        if (!myFile.exists()) {
            return null;
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        getPropertyValue(name, os);
        if (os.size() > 0) {
            byte[] bytes = os.toByteArray();
            return new String(bytes, "UTF-8");
        }
        return null;
    }

    public void getPropertyValue(String name, OutputStream os) throws IOException {
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
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
    }

    public void setPropertyValue(String name, String value) throws IOException {
        byte[] bytes = value != null ? value.getBytes("UTF-8") : null;
        int length = bytes != null && bytes.length > 0 ? bytes.length : -1;
        setPropertyValue(name, bytes != null ? new ByteArrayInputStream(bytes) : null, length);
    }

    public void setPropertyValue(String name, InputStream is, int length) throws IOException {
        InputStream src = null;
        OutputStream dst = null;
        File tmpFile = null;
        try {
            tmpFile = File.createTempFile("property", "tmp", myFile.getParentFile());
            tmpFile.deleteOnExit();
            if (myFile.exists()) {
                src = new FileInputStream(myFile);
            }
            dst = new FileOutputStream(tmpFile);
            copyProperties(src, dst, name, is, length);
            src.close();
            dst.close();
            src = null;
            dst = null;
            if (myFile.exists()) {
                myFile.delete();                
            }
            tmpFile.renameTo(myFile);
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
            if (tmpFile != null && tmpFile.exists()) {
                tmpFile.delete();
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
