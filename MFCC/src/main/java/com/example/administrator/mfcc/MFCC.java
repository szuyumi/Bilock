package com.example.administrator.mfcc;

import android.os.Environment;
import android.util.Log;

import com.google.corp.productivity.specialprojects.android.fft.RealDoubleFFT;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
//import be.tarsos.dsp.io.android.AudioDispatcherFactory;

import static android.content.ContentValues.TAG;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.floor;
import static java.lang.Math.log;
import static java.lang.Math.log10;
import static java.lang.Math.pow;

public class MFCC {

    private static int FRAMES_PER_BUFFER = 512;           //帧长？海明窗大小
    private static int NOT_OVERLAP;                 //每帧的样本数
    private static final int NUM_FILTER = 40;       //滤波器的数目
    private static final int LEN_SPECTRUM = 2048;   //2的k次幂，与每帧的样本数最接近,输入FFT的数据大小？
    private static final int LEN_MELREC = 13;       //特征维度
    private static final double PI = 3.1415926;
    private static String pathName = Environment.getExternalStorageDirectory().getAbsolutePath();
    private static String dictionaryPath = pathName + "/MFCC2/";


    public static double[] MFCC(double[] sample, int sampleLen, int sampleRate) {
//        FRAMES_PER_BUFFER = (int) (sampleRate * 0.025);
        NOT_OVERLAP = (int) pow(2, ceil(log(0.025 * sampleRate) / log(2.0)));

        double[] preemp = new double[sampleLen];
        preemp[0] = sample[0];
        //预加重：去出声带和嘴唇造成的效应，来补偿语音信号中被压抑的高频部分，并能突出高频的共振峰
        for (int i = 1; i < sampleLen; i++) {
            preemp[i] = sample[i] - 0.95 * sample[i - 1];
        }
        //calculate the total frames when overlaps exist
        //（N）FRAMES_PER_BUFFER = sampleRate * Win_Time（把Win_Time时间内的点当做一个点计算）?
        //分帧一般分128、256、512？ 帧长=分帧/分辨率 *1000
//        int numFrames = (int) (ceil((sampleLen - FRAMES_PER_BUFFER) / NOT_OVERLAP) + 1);        //帧数
        int numFrames = (int) (sampleLen * 0.01);        //帧数
        double hammingWindow[] = new double[FRAMES_PER_BUFFER];
        double mel[] = new double[NUM_FILTER];
        double result[] = new double[LEN_MELREC];

        int frameSampleLen = (int) (NOT_OVERLAP * ceil((double) sampleLen / numFrames));
        Log.d(TAG, "MFCC: " + frameSampleLen);
        float afterWin[] = new float[frameSampleLen];
        double energySpectrum[] = new double[frameSampleLen];


        //加窗：用于平滑信号，减弱FFT以后旁瓣大小以及频谱泄露
        for (int i = 0; i < FRAMES_PER_BUFFER; i++) {
            hammingWindow[i] = 0.54 - 0.46 * cos(2 * PI * i / (FRAMES_PER_BUFFER - 1));   // * Sn??
        }
        //handle all frames one by one
        for (int i = 0; i * numFrames < sampleLen; i++) {
            int j;
            //windowing
            for (j = 0; j < NOT_OVERLAP; j++) {
                if (j < FRAMES_PER_BUFFER && (j + (i) * numFrames) < sampleLen)
                    afterWin[j + i * NOT_OVERLAP] = (float) (preemp[j + i * numFrames] * hammingWindow[j]);
                else afterWin[j + i * NOT_OVERLAP] = 0;
            }
            //Zero Padding
            //fft(afterWin,LEN_SPECTRUM);
            //FFT + power
            // FFT_Power(afterWin, energySpectrum);

            energySpectrum = getSpectrum(FFT(afterWin, LEN_SPECTRUM));

            //Warping the frequency axis + FilterBank
            //由于频域信号有很多
            computeMel(mel, sampleRate, energySpectrum);
            //DCT
            DCT(mel, result);
        }

        result = Arrays.copyOfRange(result, 1, result.length);

        return result;
    }

    static void computeMel(double mel[], int sampleRate, double energySpectrum[]) {
        int fmax = sampleRate / 2;
        double maxMelFreq = 2595 * log10(1 + fmax / 700);
        double melFilters[][] = new double[NUM_FILTER][3];
        double delta = maxMelFreq / (NUM_FILTER + 1);
        double m[] = new double[NUM_FILTER + 2];
        double h[] = new double[NUM_FILTER + 2];
        double f[] = new double[NUM_FILTER + 2];
        for (int i = 0; i < NUM_FILTER + 2; i++) {
            m[i] = i * delta;
            h[i] = 700 * (exp(m[i] / 1127) - 1);
            f[i] = floor((LEN_SPECTRUM / 2 + 1) * h[i] / sampleRate);
        }
        //get start, peak, end point of every trigle filter
        for (int i = 0; i < NUM_FILTER; i++) {
            melFilters[i][0] = f[i];
            melFilters[i][1] = f[i + 1];
            melFilters[i][2] = f[i + 2];
        }

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

    static void DCT(double mel[], double c[]) {
        for (int i = 0; i < 13; i++) {
            for (int j = 0; j < NUM_FILTER; j++) {
                if (mel[j] <= -0.0001 || mel[j] >= 0.0001)
                    c[i] += log(mel[j]) * cos(PI * i / (2 * NUM_FILTER) * (2 * j + 1));
            }
        }
    }

    public static double[] getSpectrum(double[] data) {
        double spectrum[] = new double[LEN_SPECTRUM];

        for (int i = 0; i < LEN_SPECTRUM; i++) {
            spectrum[i] = data[i] * data[i];
        }

//        for (int i = 0; i + 1 < LEN_SPECTRUM; i++) {
//            spectrum[i] = data[i] * data[i] + data[i + 1] * data[i + 1];
//        }
//        List<Double> spectrumVals=new ArrayList<Double>();
//        double totalEnergy;
//        double highS=0;
//        // Clear the total energy sum
//        totalEnergy=0.0;
//        int a;
//        for (a=2;a<data.length;a=a+2) {
//            spectrum[a]=Math.sqrt(Math.pow(data[a],2.0)+Math.pow(data[a+1],2.0));
//            spectrumVals.add(spectrum[a]);
//            if (spectrum[a]>highS) highS=spectrum[a];
//            // Add this to the total energy sum
//            totalEnergy=totalEnergy+spectrum[a];
//        }
        return spectrum;
    }

    public static double[] FFT(float sample[], int sampleLen) {
        double doubleFFT[] = new double[sampleLen];
        for (int i = 0; i < sampleLen; i++) {
            // 除以32768.0 得到-1.0到1.0之间的数字
            doubleFFT[i] = (double) sample[i];
        }

//        normalizationData(doubleFFT);
        //将时域转化为频域
//        DoubleFFT_1D doubleFFT_1D = new DoubleFFT_1D(sampleLen);
        RealDoubleFFT spectrumAmpFFT;
        spectrumAmpFFT = new RealDoubleFFT(sampleLen);
        spectrumAmpFFT.ft(doubleFFT);

//        doubleFFT_1D.realForward(doubleFFT);
//        doubleFFT_1D.realInverse(doubleFFT, true);
        return doubleFFT;
    }

    //数据归一化
    static void normalizationData(double[] data) {

        double max = data[0];
        double min = data[0];

        for (int i = 1; i < data.length; i++) {
            if (data[i] > max)
                max = data[i];
            if (data[i] < min)
                min = data[i];
        }

        for (int i = 0; i < data.length; i++) {
            data[i] = (data[i] - min) / (max - min) * 2 - 1;
        }
    }

    public static double[] MFCC() {
        return new double[0];
    }

    public static double svmPredict(String testFile) throws IOException {
        String predictArgs[] = new String[]{"-b", "0", testFile, dictionaryPath + "data_model.txt", dictionaryPath + "result.txt"};
//        String predictArgs2[] = new String[]{"-b", "0", dictionaryPath + "model.txt", dictionaryPath + "data_model.txt", dictionaryPath + "result.txt"};

        double accuracy = svm_predict.main(predictArgs);
//        double accuracy2 = svm_predict.main(predictArgs2);

//        Log.d(TAG, "LibSvm: accuracy = " + accuracy2);
//        Log.d(TAG, "LibSvm: accuracy2 = " + accuracy2);

        if (accuracy == 1)
            Log.e(TAG, "LibSvm: accuracy = " + accuracy);
        else Log.d(TAG, "LibSvm: accuracy = " + accuracy);

        return accuracy;
    }

    public static void svmTrain() throws IOException {

        svm_train train = new svm_train();
//        String trainArgs[] = new String[]{"-s", "2","-t","2", dictionaryPath + "model.txt", dictionaryPath + "data_model.txt"};
        String trainArgs[] = new String[]{dictionaryPath + "MFCCs_model.txt", dictionaryPath + "data_model.txt"};

//        String scaleArgs[] = new String[]{"-s", dictionaryPath + "model_one.txt" , "-l ", "-1", "-u ", "1", dictionaryPath + "iris.txt"};
//        String trainArgs[] = new String[]{"-s", "2", fileName + "model_one.txt", fileName + "data.model"};
//        String predictArgs[] = new String[]{"-b", "0", fileName + "text.txt", fileName + "data.model", fileName + "result.txt"};

//        scale.main(scaleArgs);
        svm_train.main(trainArgs);
        //查看是否出错
//        Log.i(TAG, "LibSvm: " + train.error_msg);
    }

    public static Double[] mfcc(String absolutePath, double[] bufferDouble, int length, int i) throws IOException {
        int sampleRate = 44100;
        int bufferSize = 1024;
        int bufferOverlap = 512;
        File file = new File(absolutePath);
        file.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
//        DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
//        for (double u : bufferDouble
//                ) {
//            dos.writeDouble(u);
//            dos.writeUTF(",");
//        }
        for (double u : bufferDouble
                ) {
            bw.write(String.valueOf(u));
            bw.write(",");
        }
        bw.flush();
        InputStream inStream = new FileInputStream(absolutePath);
//        final float[] floatBuffer = TestUtilities.audioBufferSine();
//        final AudioDispatcher dispatcher = AudioDispatcherFactory.fromFloatArray(floatBuffer, sampleRate, bufferSize, bufferOverlap);
        AudioDispatcher dispatcher = new AudioDispatcher(new UniversalAudioInputStream(inStream, new TarsosDSPAudioFormat(sampleRate, bufferSize, 1, true, true)), bufferSize, bufferOverlap);
        final be.tarsos.dsp.mfcc.MFCC mfcc = new be.tarsos.dsp.mfcc.MFCC(bufferSize, sampleRate, 26, 50, 300, 3000);
        dispatcher.addAudioProcessor(mfcc);
        dispatcher.addAudioProcessor(new AudioProcessor() {

            @Override
            public void processingFinished() {
            }

            @Override
            public boolean process(be.tarsos.dsp.AudioEvent audioEvent) {
                return true;
            }
        });
        dispatcher.run();

        float[] data = mfcc.getMFCC();
        Double data2[] = new Double[data.length];
        for (int j = 0; j < data.length; j++) {
            data2[j] = (double) data[j];
        }

        return data2;
    }

}
