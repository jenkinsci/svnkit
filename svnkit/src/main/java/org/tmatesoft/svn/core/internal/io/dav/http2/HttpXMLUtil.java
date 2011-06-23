package org.tmatesoft.svn.core.internal.io.dav.http2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.DefaultHandler;

public class HttpXMLUtil {

    private static SAXParserFactory ourSAXParserFactory;
    
    public static final EntityResolver NO_ENTITY_RESOLVER = new EntityResolver() {
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return new InputSource(new ByteArrayInputStream(new byte[0]));
        }
    };
    public static final DefaultHandler DEFAULT_SAX_HANDLER = new DefaultHandler();



    public static synchronized SAXParserFactory getSAXParserFactory() throws FactoryConfigurationError {
        if (ourSAXParserFactory == null) {
            ourSAXParserFactory = createSAXParserFactory();
            SVNHashMap supportedFeatures = new SVNHashMap();
            try {
                ourSAXParserFactory.setFeature("http://xml.org/sax/features/namespaces", true);
                supportedFeatures.put("http://xml.org/sax/features/namespaces", Boolean.TRUE);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
              try {
                ourSAXParserFactory.setFeature("http://xml.org/sax/features/validation", false);
                supportedFeatures.put("http://xml.org/sax/features/validation", Boolean.FALSE);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                ourSAXParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                supportedFeatures.put("http://apache.org/xml/features/nonvalidating/load-external-dtd", Boolean.FALSE);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            if (supportedFeatures.size() < 3) {
                ourSAXParserFactory = createSAXParserFactory();
                for (Iterator<?> names = supportedFeatures.keySet().iterator(); names.hasNext();) {
                    String name = (String) names.next();
                    try {
                        ourSAXParserFactory.setFeature(name, supportedFeatures.get(name) == Boolean.TRUE);
                    } catch (SAXNotRecognizedException e) {
                    } catch (SAXNotSupportedException e) {
                    } catch (ParserConfigurationException e) {
                    }
                }
            }
            ourSAXParserFactory.setNamespaceAware(true);
            ourSAXParserFactory.setValidating(false);
        }
        return ourSAXParserFactory;
    }
    
    private static SAXParserFactory createSAXParserFactory() {
        String legacy = System.getProperty("svnkit.sax.useDefault");
        if (legacy == null || !Boolean.valueOf(legacy).booleanValue()) {
            return SAXParserFactory.newInstance();
        }
        // instantiate JVM parser.
        String[] parsers = {"com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl", // 1.5, 1.6
                            "org.apache.crimson.jaxp.SAXParserFactoryImpl", // 1.4
                            };
        for (int i = 0; i < parsers.length; i++) {
            String className = parsers[i];
            ClassLoader loader = HttpXMLUtil.class.getClassLoader();
            try {
                Class<?> clazz = null;
                if (loader != null) {
                    clazz = loader.loadClass(className);
                } else {
                    clazz = Class.forName(className);
                }
                if (clazz != null) {
                    Object factory = clazz.newInstance();
                    if (factory instanceof SAXParserFactory) {
                        return (SAXParserFactory) factory;
                    }
                }
            } catch (ClassNotFoundException e) {
            } catch (InstantiationException e) {
            } catch (IllegalAccessException e) {
            }
        }
        return SAXParserFactory.newInstance();
    }
}
