/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
#include <jni.h>
/* Header for class org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher */

#ifndef _Included_org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher
#define _Included_org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher
 * Method:    encryptData
 * Signature: (Ljava/lang/String;)[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher_encryptData
  (JNIEnv *, jobject, jstring);

/*
 * Class:     org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher
 * Method:    decryptData
 * Signature: ([B)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher_decryptData
  (JNIEnv *, jobject, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif
