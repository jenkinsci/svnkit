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

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.tmatesoft.svn.core.SVNProperty;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author TMate Software Ltd.
 */
public class FSEntryHandler extends DefaultHandler {
    
    private Map myChildEntries;
    private static SAXParserFactory ourSAXParserFactory;
    
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
    
    public static FSEntryHandler parse(InputSource source) throws IOException {
        SAXParser parser = null;
        FSEntryHandler handler = new FSEntryHandler();
        try {
            parser = getSAXParserFactory().newSAXParser();
            parser.parse(source, handler);
        } catch (ParserConfigurationException e) {
            throw new IOException();
        } catch (SAXException e) {
            throw new IOException();
        }
        return handler;
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
                if (SVNProperty.REVISION.equals(e.getKey()) && SVNProperty.KIND_DIR.equals(entry.get(SVNProperty.KIND))) {
                    // do not save revision for child directories
                    continue;
                }
                if (SVNProperty.UUID.equals(e.getKey()) && e.getValue().equals(parent.get(SVNProperty.UUID))) {
                    continue;
                }
                if (SVNProperty.URL.equals(e.getKey()) && parent != null) {
                    // do not save for files.
                    continue;
                }
            }
            if (!first) {
                os.write('\n');
            }
            first = false; 
            os.write("   ");
            os.write(e.getKey().toString().substring(FSEntry.ENTRY_PREFIX.length()));
            os.write("=\"");
            os.write(e.getValue().toString());
            os.write("\"");
        }
        os.write("/>\n");
    }
    
    private static SAXParserFactory getSAXParserFactory() {
        if (ourSAXParserFactory == null) {
            ourSAXParserFactory = SAXParserFactory.newInstance();
            ourSAXParserFactory.setNamespaceAware(true);
        }
        return ourSAXParserFactory;
    }
    
    private FSEntryHandler() {
    }

    public Map getChildEntries() {
        if (myChildEntries == null) {
            myChildEntries = new HashMap();
        }
        return myChildEntries;
    }
    
    public void startElement(String uri, String localName, String qName,  Attributes attrs) throws SAXException {
        if ("entry".equals(qName)) {
            Map entry = createEntry(attrs);
            getChildEntries().put(entry.get(SVNProperty.NAME), entry);
        }
    }
    
    private static Map createEntry(Attributes attrs) {
        Map result = new HashMap();
        for(int i = 0; i < attrs.getLength(); i++) {
            result.put(FSEntry.ENTRY_PREFIX + attrs.getQName(i), attrs.getValue(i));
        }
        return result;
    }
}
