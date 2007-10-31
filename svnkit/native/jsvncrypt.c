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
// jsvncrypt.c: Defines the entry point for the dll application.
//
#include <jni.h>
#include <windows.h>
#include <Wincrypt.h>
#include "SVNWinCryptPasswordCipher.h"


BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    return TRUE;
}

/*
 * Class:     org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher
 * Method:    encryptData
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jbyteArray JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher_encryptData
  (JNIEnv *env, jobject obj, jstring dataIn) {
    jbyteArray jResultArray;
    DATA_BLOB DataIn;
    DATA_BLOB DataOut;

	DataIn.pbData = (BYTE *)(env->GetStringUTFChars(dataIn, 0));    

    if (DataIn.pbData == NULL) {
        return NULL;
    }
    DataIn.cbData = (DWORD)strlen((const char*)DataIn.pbData) + 1;

    if (CryptProtectData(
		 &DataIn,
		 (LPCWSTR)L"auth_svn.simple.wincrypt",
		 NULL,                               
		 NULL,                               
		 NULL,                               
		 CRYPTPROTECT_UI_FORBIDDEN,
		 &DataOut))
	{
		jsize stringSize = (jsize) DataOut.cbData;
		jResultArray = env->NewByteArray(stringSize);
		env->SetByteArrayRegion(jResultArray, 0, stringSize, (const jbyte*) DataOut.pbData);
        LocalFree(DataOut.pbData);
	} else {
		jResultArray = NULL;
	} 

    env->ReleaseStringUTFChars(dataIn, (const char*)DataIn.pbData);
    return jResultArray;
}

/*
 * Class:     org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher
 * Method:    decryptData
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNWinCryptPasswordCipher_decryptData
  (JNIEnv *env, jobject obj, jbyteArray dataIn) {
    jstring result;
    DATA_BLOB DataIn;
    DATA_BLOB DataOut;
    LPWSTR description = NULL;

	DataIn.pbData = (BYTE *)(env->GetByteArrayElements(dataIn, 0));
    if (DataIn.pbData == NULL) {
        return NULL;
    }

	DataIn.cbData = (DWORD)env->GetArrayLength(dataIn);
    
    if (CryptUnprotectData(
                &DataIn,
                &description,
                NULL,
                NULL,
                NULL,
                CRYPTPROTECT_UI_FORBIDDEN,
                &DataOut))
    
    {
        if (lstrcmpW((LPCWSTR)L"auth_svn.simple.wincrypt", description) == 0) {
	        char * strDecrypted = (char*) malloc(DataOut.cbData + 1);
	        memcpy(strDecrypted, DataOut.pbData, DataOut.cbData);
	        strDecrypted[DataOut.cbData] = '\0';
	        result = (env)->NewStringUTF(strDecrypted);
	        free(strDecrypted);
		} else {
			result = NULL;
		}
        LocalFree(description);
        LocalFree(DataOut.pbData);
    } else {
    	result = NULL;
    }

	env->ReleaseByteArrayElements(dataIn, (jbyte*) DataIn.pbData, 0);
	return result;
}

