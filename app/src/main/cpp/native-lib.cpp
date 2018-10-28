#include <jni.h>
#include <string>
#include <stdlib.h>
#include <iostream>
#include <vector>
#include <android/log.h>

#define LOG_TAG "cmake-lib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)

using namespace std;

double *deal_signal(double *signal, short peak_floor, int span, int len);

void select_peak(int *jresult, jshort peakFloor, double *signal, jint length, jint span);

double *smooth(double *signal, int span, int size);

void deleteListElement(int *list, int index, int len);

double *EWMAFilter(double *signal, int span, int len);


/*
 * @param signal_ 音频数组
 * @param peakFloor 阈值
 * @param span 距离
 * @param length 数组大小
 * return result 坐标数组
 */
extern "C"
JNIEXPORT jintArray JNICALL
Java_com_wshadow_mywaveaudio_LockPresenter_findPeak(JNIEnv *env, jobject instance,
                                                    jdoubleArray signal_, jshort peakFloor,
                                                    jint span, jint length) {
    jdouble *signal = env->GetDoubleArrayElements(signal_, NULL);
    jint jresult[length];

    for (int i = 0; i < length; ++i) {
        jresult[i] = 0;
    }

    signal = deal_signal(signal, peakFloor, span, length);
    select_peak(jresult, peakFloor, signal, length, span);

    env->ReleaseDoubleArrayElements(signal_, signal, 0);
    jintArray result = env->NewIntArray(length);
    env->SetIntArrayRegion(result, 0, length, jresult);

    return result;
}

void select_peak(int *jresult, jshort peakFloor, double *signal, jint length, jint span) {

    int flag = 1;
    for (int i = 1; i < length - 1; ++i) {
        if (signal[i] >= signal[i - 1] && signal[i] >= signal[i + 1] && signal[i] > peakFloor &&
            signal[i] < 25000) {
//            LOGD("signal = %lf i = %d" , signal[i] , i );
            jresult[flag++] = i;
        }
    }

    --flag;

    for (int i = 1; i < flag;) {
        if (jresult[i + 1] - jresult[i] < 50 ) {
            //相距很近的点删除较小的数据
            if (signal[jresult[i]] < signal[jresult[i + 1]]) {
                deleteListElement(jresult, i, flag);
            } else {
                deleteListElement(jresult, i + 1, flag);
            }
            flag--;
        } else {
            ++i;
        }


    }
    //存入数组大小
    jresult[0] = flag;
//    LOGD("end");
}

//删除数组第index个元素
void deleteListElement(int *list, int index, int len) {
    for (int i = index; i < len; ++i) {
        list[i] = list[i + 1];
    }
}

/**
 * 信号预处理
 * @param signal 待查找信号
 * @param peak_floor 最小峰值阈值
 * @param span 查找窗口
 */
double *deal_signal(double *signal, short peak_floor, int span, int len) {
    //切除阈值以下的数据
    for (int i = 0; i < len; ++i) {
        if (signal[i] < peak_floor) {
            signal[i] = 0;
        }
    }
    //平滑处理
    signal = EWMAFilter(signal, span, len);

    return signal;
}

/**
 * 滑动滤波
 * @param signal 待滤噪信号
 * @param span 窗口
 */
//double* smooth(double *signal, int span, int len) {
//
//    double *afterFilter = (double*) malloc(len * sizeof(double));
//    double *dataBuf = (double*) malloc(span * sizeof(double));
//    double sum = 0;
//    double temp , avg;
//
//    for (int i = 0 ; i < span ; i++){
//        sum = 0;
//        dataBuf[i] = signal[i];
//        sum += signal[i];
//        signal[i] = sum/span;
//    }
//
//    for (int i = span; i < len; ++i) {
//        sum = 0;
//        temp = signal[i];
//
//        for (int j = 0; j < span - 1; ++j) {
//            sum += dataBuf[j];
//            dataBuf[j] = dataBuf[j + 1];
//        }
//        dataBuf[span - 1] = temp;
//        sum += temp;
//        avg = sum / span;
//        afterFilter[i] = avg;
//    }
//
//    delete dataBuf;
//    return afterFilter;
//}

double *EWMAFilter(double *signal, int span, int len) {
    double *afterFilter = (double *) malloc(len * sizeof(double));
    double EMA = 0;
    double weight = 2.0 / (span + 1);
    //LOGD("weight = %f span = %d" , weight,span);
    for (int i = 0; i < len; i++) {
        EMA = weight * signal[i] + (1 - weight) * EMA;
        afterFilter[i] = EMA;
    }
    return afterFilter;
}

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_wshadow_mywaveaudio_LockPresenter_getSignalAfterSmooth(JNIEnv *env, jobject instance,
                                                                jdoubleArray signal_, jint span,
                                                                jint len, jint peak_floor) {
    double *signal = env->GetDoubleArrayElements(signal_, NULL);

    for (int i = 0; i < len; ++i) {
        if (signal[i] < peak_floor) {
            signal[i] = 0;
        }
    }

    signal = EWMAFilter(signal, span, len);

    env->SetDoubleArrayRegion(signal_, 0, len, signal);
    env->ReleaseDoubleArrayElements(signal_, signal, 0);
    return signal_;
}extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_example_smcc_audioanalysis_MFCC_mfcc(JNIEnv *env, jobject instance, jshortArray sample_,
                                              jint sampleLen, jint sampleRate) {
    jshort *sample = env->GetShortArrayElements(sample_, NULL);

    // TODO

    env->ReleaseShortArrayElements(sample_, sample, 0);
}