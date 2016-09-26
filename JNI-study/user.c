#include <jni.h>
#include <stdio.h>
#include "HelloWorld.h"

/*
 * Class:     HelloWorld
 * Method:    showHello
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_HelloWorld_showHello (JNIEnv *env, jclass jc){
	printf("HelloWorld!");  
}
