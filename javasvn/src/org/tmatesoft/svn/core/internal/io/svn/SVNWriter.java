/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.TimeUtil;


/**
 * @author TMate Software Ltd.
 */
public class SVNWriter {
    
    private SVNWriter() {
    }
    
    public static void write(OutputStream os, String templateStr, Object[] src) throws SVNException {
        StringBuffer template = new StringBuffer(templateStr.length());
        for(int i = 0; i < templateStr.length(); i++) {
            char ch = templateStr.charAt(i);
            if (!Character.isWhitespace(ch)) {
                template.append(ch);
            }
        }
        int offset = 0;
        try {
	        for(int i = 0; i < template.length(); i++) {
	            char ch = template.charAt(i);
	            if (ch == '(' || ch == ')') {
	                os.write((byte) ch);
	                os.write(' ');
	                continue;
	            } 
	
	            Object item = src[offset++];
	            if (item == null) {
	                continue;
	            }
                if (item instanceof Date) {
                    item = TimeUtil.formatDate((Date) item);
                }
                if (ch == 'i') {
                    
                    InputStream is = ((SVNDataSource) item).getInputStream();
                    long length = ((SVNDataSource) item).lenght();
                    
                    os.write(Long.toString(length).getBytes("UTF-8"));
                    os.write(':');
                    byte[] buffer = new byte[Math.min(2048, (int) length)];
                    while(true) {
                        int read = is.read(buffer);
                        if (read > 0) {
                            os.write(buffer, 0, read);
                        } else {
                            break;
                        }
                    }
                } if (ch == 'b') {
                    byte[] bytes = (byte[]) item;
                    os.write(Integer.toString(bytes.length).getBytes("UTF-8"));
                    os.write(':');
                    os.write(bytes);                    
                } else if (ch == 'n') {
	                os.write(item.toString().getBytes("UTF-8"));
	            } else if (ch == 'w') {
	                os.write(item.toString().getBytes("UTF-8"));
	            } else if (ch == 's') {
	                os.write(Integer.toString(item.toString().getBytes("UTF-8").length).getBytes("UTF-8"));
	                os.write(':');
	                os.write(item.toString().getBytes("UTF-8"));                    
	            } else if (ch == '*') {
	                ch = template.charAt(i + 1);
	                if (item instanceof Object[]) {
	                    Object[] list = (Object[]) item;
	                    for (int j = 0; j < list.length; j++) {
	                        if (ch == 's') {
	        	                os.write(Integer.toString(list[j].toString().getBytes("UTF-8").length).getBytes("UTF-8"));
	        	                os.write(':');
	        	                os.write(list[j].toString().getBytes("UTF-8"));                    
	                        } else if (ch == 'w') {
	                            os.write(list[j].toString().getBytes("UTF-8"));
	                        }
	                        os.write(' ');
                        }
	                } else if (item instanceof long[]&& ch == 'n') {
	                    long[] list = (long[]) item;
	                    for (int j = 0; j < list.length; j++) {
	                        os.write(Long.toString(list[j]).getBytes("UTF-8"));
	                        os.write(' ');
                        }	                    
	                }
	                i++;
	            }
	            os.write(' ');
	        }
        } catch (IOException e) {
            e.printStackTrace();
            throw new SVNException("error while sending data", e);
        }
    }
}
