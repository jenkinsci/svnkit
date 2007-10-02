/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNSSLUtil {
    
    public static StringBuffer getServerCertificatePrompt(X509Certificate cert, String realm, String hostName) {
        int failures = getServerCertificateFailures(cert, hostName);
        StringBuffer prompt = new StringBuffer();
        prompt.append("Error validating server certificate for '");
        prompt.append(realm);
        prompt.append("':\n");
        if ((failures & 8) != 0) {
            prompt.append(" - The certificate is not issued by a trusted authority. Use the\n" +
                          "   fingerprint to validate the certificate manually!\n");
        }
        if ((failures & 4) != 0) {
            prompt.append(" - The certificate hostname does not match.\n");
        }
        if ((failures & 2) != 0) {
            prompt.append(" - The certificate has expired.\n");
        }
        if ((failures & 1) != 0) {
            prompt.append(" - The certificate is not yet valid.\n");
        }
        getServerCertificateInfo(cert, prompt);
        return prompt;

    }
    
    private static String getFingerprint(X509Certificate cert) {
        StringBuffer s = new StringBuffer();
        try  {
           MessageDigest md = MessageDigest.getInstance("SHA1");
           md.update(cert.getEncoded());
           byte[] digest = md.digest();
           for (int i= 0; i < digest.length; i++)  {
              if (i != 0) {
                  s.append(':');
              }
              int b = digest[i] & 0xFF;
              String hex = Integer.toHexString(b);
              if (hex.length() == 1) {
                  s.append('0');
              }
              s.append(hex.toLowerCase());
           }
        } catch (Exception e)  {
        } 
        return s.toString();
     }

  private static void getServerCertificateInfo(X509Certificate cert, StringBuffer info) {
      info.append("Certificate information:");
      info.append('\n');
      info.append(" - Subject: ");
      info.append(cert.getSubjectDN().getName());
      info.append('\n');
      info.append(" - Valid: ");
      info.append("from " + cert.getNotBefore() + " until " + cert.getNotAfter());
      info.append('\n');
      info.append(" - Issuer: ");
      info.append(cert.getIssuerDN().getName());
      info.append('\n');
      info.append(" - Fingerprint: ");
      info.append(getFingerprint(cert));
  }

  private static int getServerCertificateFailures(X509Certificate cert, String realHostName) {
      int mask = 8;
      Date time = new Date(System.currentTimeMillis());
      if (time.before(cert.getNotBefore())) {
          mask |= 1;
      }
      if (time.after(cert.getNotAfter())) {
          mask |= 2;
      }
      String hostName = cert.getSubjectDN().getName();
      int index = hostName.indexOf("CN=") + 3;
      if (index >= 0) {
          hostName = hostName.substring(index);
          if (hostName.indexOf(' ') >= 0) {
              hostName = hostName.substring(0, hostName.indexOf(' '));
          }
          if (hostName.indexOf(',') >= 0) {
              hostName = hostName.substring(0, hostName.indexOf(','));
          }
      }
      if (realHostName != null && !realHostName.equals(hostName)) {
          mask |= 4;
      }
      return mask;
  }

	public static class CertificateNotTrustedException extends CertificateException {

		public CertificateNotTrustedException() {
			super();
		}

		public CertificateNotTrustedException(String msg) {
			super(msg);
		}
	}
}
