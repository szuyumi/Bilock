#include <jni.h>
#include <string>
#include <stdio.h>
#include <math.h>
#include <string.h>

#ifndef _MFCC_H_
#define FRAMES_PER_BUFFER (400)
//窗重叠长度
#define NOT_OVERLAP (200)
//Mel滤波器数量
#define NUM_FILTER (40)

double* MFCC(const short* sample, int sampleLen, int sampleRate);
void FFT_Power(float *in, double *energySpectrum);
void computeMel(double *mel, int sampleRate, const double *energySpectrum);
void DCT(const double *mel, double *c);

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_example_administrator_mfcc_MainActivity_mfcc(JNIEnv *env, jobject instance,
                                                      jshortArray sample_, jint sampleLen,
                                                      jint sampleRate) {
    jshort *sample = env->GetShortArrayElements(sample_, NULL);

    double *mel = MFCC(sample, sampleLen, sampleRate);

    jdoubleArray result = env->NewDoubleArray(13);

    env->SetDoubleArrayRegion(result, 0, 12, mel);
    env->ReleaseShortArrayElements(sample_, sample, 0);
    return result;
}

double* MFCC(const short *sample, int sampleLen, int sampleRate) {
    double *preemp = new double[sampleLen];
    preemp[0] = sample[0];
    //预加重：去出声带和嘴唇造成的效应，来补偿语音信号中被压抑的高频部分，并能突出高频的共振峰
    for (int i = 1; i < sampleLen; i++) {
        preemp[i] = sample[i] - 0.95 * sample[i - 1];
    }
    //calculate the total frames when overlaps exist
    int numFrames = (int) (ceil((sampleLen - FRAMES_PER_BUFFER) / NOT_OVERLAP) + 1);
    double hammingWindow[FRAMES_PER_BUFFER];
    float afterWin[512] = {0.0};
    double energySpectrum[512] = {0.0};
    double mel[NUM_FILTER] = {0};
    double result[13] = {0};
    //加窗：用于平滑信号，减弱FFT以后旁瓣大小以及频谱泄露
    for (int i = 0; i < FRAMES_PER_BUFFER; i++) {
        hammingWindow[i] = 0.54 - 0.46 * cos(2 * 3.14 * i / (FRAMES_PER_BUFFER - 1));   // * Sn??
    }
    //handle all frames one by one
    for (int i = 0; i < numFrames; i++) {
        int j;
        //windowing
        for (j = 0; j < FRAMES_PER_BUFFER && (j + (i ) * NOT_OVERLAP) < sampleLen; j++) {
            afterWin[j] = (float) (preemp[j + (i ) * NOT_OVERLAP] * hammingWindow[j]);
        }
        //Zero Padding
        for (int k = j - 1; k < 512; k++) afterWin[k] = 0.0f;
        //FFT + power
        // FFT_Power(afterWin, energySpectrum);
        //Warping the frequency axis + FilterBank
        memset(mel, 0, sizeof(float) * NUM_FILTER);
        //由于频域信号有很多
        computeMel(mel, sampleRate, energySpectrum);
        //DCT
        memset(result, 0, sizeof(float) * 13);
        DCT(mel, result);
    }
    delete[] preemp;
    return result;
}

void computeMel(double *mel, const int sampleRate, const double *energySpectrum) {
    int fmax = sampleRate / 2;
    double maxMelFreq = 1127 * log(1 + fmax / 700);
    double melFilters[NUM_FILTER][3];
    double delta = maxMelFreq / (NUM_FILTER + 1);
    double *m = new double[NUM_FILTER + 2];
    double *h = new double[NUM_FILTER + 2];
    double *f = new double[NUM_FILTER + 2];
    for (int i = 0; i < NUM_FILTER + 2; i++) {
        m[i] = i * delta;
        h[i] = 700 * (exp(m[i] / 1127) - 1);
        f[i] = floor((256 + 1) * h[i] / sampleRate);
    }
    //get start, peak, end point of every trigle filter
    for (int i = 0; i < NUM_FILTER; i++) {
        melFilters[i][0] = f[i];
        melFilters[i][1] = f[i + 1];
        melFilters[i][2] = f[i + 2];
    }
    delete[] m;
    delete[] h;
    delete[] f;
    //calculate the output of every trigle filter
    for (int i = 0; i < NUM_FILTER; i++) {
        for (int j = 0; j < 256; j++) {
            if (j >= melFilters[i][0] && j < melFilters[i][1]) {
                mel[i] += ((j - melFilters[i][0]) / (melFilters[i][1] - melFilters[i][0])) *
                          energySpectrum[j];
            }
            if (j > melFilters[i][1] && j <= melFilters[i][2]) {
                mel[i] += ((j - melFilters[i][2]) / (melFilters[i][1] - melFilters[i][2])) *
                          energySpectrum[j];
            }
        }
    }
}

void DCT(const double *mel, double *c) {
    for (int i = 0; i < 13; i++) {
        for (int j = 0; j < NUM_FILTER; j++) {
            if (mel[j] <= -0.0001 || mel[j] >= 0.0001)
                c[i] += log(mel[j]) * cos(3.14 * i / (2 * NUM_FILTER) * (2 * j + 1));
        }
    }
}extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_administrator_mfcc_MainActivity_name(JNIEnv *env, jobject instance) {

    // TODO

    jclass string = env->FindClass("java/lang/String");

    return env->NewStringUTF("JoJo");
}
#endif
