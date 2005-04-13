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

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
public class FSEntryHandler {
    
    public static void save(Writer os, Map entry, Collection childEntries, Collection deletedEntries) throws IOException {
        os.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        os.write("<wc-entries\n");
        os.write("   xmlns=\"svn:\">\n");
        saveEntry(os, null, entry);
        for(Iterator entries = childEntries.iterator(); entries.hasNext();) {
            saveEntry(os, entry, (Map) entries.next());
        }
        for(Iterator entries = deletedEntries.iterator(); entries.hasNext();) {
            saveEntry(os, entry, (Map) entries.next());
        }
        os.write("</wc-entries>\n");
    }
    
    public static Map parse(BufferedReader reader) throws IOException {
        String line;
        Map attrs = null;
        Map childEntries = new HashMap();
        while((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("<entry")) {
                attrs = new HashMap();
                continue;
            } 
            if (attrs != null) {
                String name = line.substring(0, line.indexOf('='));
                String value = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
                value = xmlDecode(value);
                attrs.put(FSEntry.ENTRY_PREFIX + name, value);
                if (line.endsWith("/>")) {
                    childEntries.put(attrs.get(SVNProperty.NAME), attrs);
                    attrs = null;
                }
            }
        }
        return childEntries;
    }
    
    private static void saveEntry(Writer os, Map parent, Map entry) throws IOException {
        os.write("<entry\n");
        boolean first = true;
        for(Iterator entries = entry.entrySet().iterator(); entries.hasNext();) {
            Map.Entry e = (Map.Entry) entries.next();
            if (e.getValue() == null) {
                continue;
            }
            if (parent != null) {
                if (SVNProperty.REVISION.equals(e.getKey()) && e.getValue().equals(parent.get(SVNProperty.REVISION))) {
                    continue;
                }
                if (SVNProperty.COPYFROM_REVISION.equals(e.getKey()) && e.getValue().equals(parent.get(SVNProperty.COPYFROM_REVISION))) {
                    continue;
                }
                if ((SVNProperty.REVISION.equals(e.getKey()) || SVNProperty.URL.equals(e.getKey())) && 
                        SVNProperty.KIND_DIR.equals(entry.get(SVNProperty.KIND))) {
                    // do not save revision and url for child directories
                    continue;
                }
                if (SVNProperty.UUID.equals(e.getKey()) && e.getValue().equals(parent.get(SVNProperty.UUID))) {
                    continue;
                }
                if (SVNProperty.URL.equals(e.getKey()) || SVNProperty.COPYFROM_URL.equals(e.getKey())) {
                    String name = (String) entry.get(SVNProperty.NAME);
                    name = xmlDecode(name);
                    String url = (String) parent.get(e.getKey());
                    String expected = PathUtil.append(url, PathUtil.encode(name));
                    if (e.getValue().equals(expected)) {
                        continue;
                    }
                }
            }
            if (!first) {
                os.write('\n');
            }
            first = false; 
            os.write("   ");
            os.write(e.getKey().toString().substring(FSEntry.ENTRY_PREFIX.length()));
            os.write("=\"");
            String value = e.getValue().toString();
            value = xmlEncode(value);
            os.write(value);
            os.write("\"");
        }
        os.write("/>\n");
    }

    private static String xmlEncode(String value) {
        value = value.replaceAll("&", "&amp;");
        value = value.replaceAll("<", "&lt;");
        value = value.replaceAll(">", "&gt;");
        value = value.replaceAll("\"", "&quot;");
        value = value.replaceAll("'", "&apos;");
        value = value.replaceAll("\t", "&#09;");
        return value;
    }

    private static String xmlDecode(String value) {
        value = value.replaceAll("&lt;", "<");
        value = value.replaceAll("&gt;", ">");
        value = value.replaceAll("&quot;", "\"");
        value = value.replaceAll("&apos;", "'");
        value = value.replaceAll("&#09;", "\t");
        value = value.replaceAll("&amp;", "&");
        return value;
    }
}
