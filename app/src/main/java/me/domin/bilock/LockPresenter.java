package me.domin.bilock;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
//import com.chrischen.waveview.WaveView;
import com.example.administrator.mfcc.MFCC;
//import com.maple.recorder.recording.AudioChunk;
//import com.maple.recorder.recording.AudioRecordConfig;
//import com.maple.recorder.recording.MsRecorder;
//import com.maple.recorder.recording.PullTransport;
//import com.maple.recorder.recording.Recorder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


import github.bewantbe.audio_analyzer_for_android.STFT;

import static android.content.ContentValues.TAG;

/**
 * Created by Administrator on 2018/4/14.
 */

public class LockPresenter implements LockContract.Presenter {

    int TEST_MFCC_FILE = 1;
    int CURRENT_TEST_MFCC = 2;
    int WRITE_MODEL_MFCC = 3;
    int TEST_DATA_MFCC = 4;
    int MODEL_FILE = 5;
    int TEST_FILE = 6;


    public static String absolutePath = Environment.getExternalStorageDirectory().getAbsolutePath();
    private static String dictionaryPath = absolutePath + "/MFCC2/";
    public STFT stft;   // use with care

    private HashMap<Integer, String> mFileNameMap = new HashMap<>();

    private LockContract.View mLockView;
    RecordTask recordTask;

    private WaveFileReader mWaveReader;

    static {
        System.loadLibrary("native-lib");
    }

    private boolean isRecord = false;

    LockPresenter(LockContract.View lockView) {
        mLockView = lockView;
        mFileNameMap.put(TEST_MFCC_FILE, "TestMFCC.txt");
        mFileNameMap.put(CURRENT_TEST_MFCC, "testRecord.txt");
        mFileNameMap.put(WRITE_MODEL_MFCC, "model.txt");
        mFileNameMap.put(TEST_DATA_MFCC, "test.txt");
        mFileNameMap.put(TEST_FILE, "WaveRecord");
        mFileNameMap.put(MODEL_FILE, "ModeRecord");
    }

    public void initRecorder() {
        record = new AudioRecord(6, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, 2 * bufferSampleSize);
        record.startRecording();
        bufferSampleSize = (int) Math.ceil(1.0 * sampleRate / bufferSampleSize) * bufferSampleSize;
    }

    public void stopRecorder() {
        isRecord = false;
//        record.stop();
//        record.release();

    }

    @Override
    public void svmTrain() {
        try {
            MFCC.svmTrain();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void currentRecord() {
        CurrentRecordTask currentRecordTask = new CurrentRecordTask();
        currentRecordTask.execute();
    }

    @Override
    public void trainData() {
        Executor executor = new ThreadPoolExecutor(10, 50, 10,
                TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(100));
//        TrainDataTask modelTask = new TrainDataTask();
//        modelTask.executeOnExecutor(executor, "ModeRecord");
        File[] testFiles = new File(absolutePath + File.separator + "ModeRecord").listFiles();
        for (File testFile : testFiles) {
            TrainDataTask trainDataTask = new TrainDataTask();
            trainDataTask.executeOnExecutor(executor, testFile);
        }
//        TrainDataTask testTask = new TrainDataTask();
//        testTask.executeOnExecutor(executor, "ljw2");
//        TrainDataTask testTask3 = new TrainDataTask();
//        testTask3.executeOnExecutor(executor, "yym");
//        TrainDataTask testTask4 = new TrainDataTask();
//        testTask4.executeOnExecutor(executor, "zm");

    }

    @Override
    public void startRecord() {
        isRecord = true;
        isSave = true;
        recordTask = new RecordTask();
        recordTask.execute();
    }

    @Override
    public void currentRecordTaskNew() {
        isRecord = true;
        new CurrentRecordTaskNew().execute();
    }

    @Override
    public void stopRecord() {
        recordTask.stop();
    }

    //用于训练模型
    @Override
    public void trainModel() throws IOException {

        LinkedList<Double[]> featureList = new LinkedList<>();

        File[] trainFiles = new File(mFileNameMap.get(MODEL_FILE)).listFiles();
        Log.e(TAG, "writeModel: ModelFiles = " + trainFiles.length);
        for (int times = 0; times < trainFiles.length; times++) {
            Double maxFeature = 0.0, minFeature = 0.0;

//            String number = String.valueOf(times);
//            InputStream in = mLockView.getInputStream("model" + number + ".wav");
            InputStream in = new FileInputStream(trainFiles[times]);

            double[] signal = getWaveData(in, times);
            if (signal == null)
                continue;

            //提取峰值的索引
            int result[] = getPeaks(signal);

            double[] bufferDouble = getBufferBetween(result[0], result[1], signal);

            //MfCC特征提起
            Double[] Feature = MFCC.mfcc(dictionaryPath + "model_" + times + ".txt", bufferDouble, bufferDouble.length, 44100);
//                double[] feature = MFCC.MFCC(bufferDouble, bufferDouble.length, 44100);

            featureList.add(Feature);
//            normalizationData(maxFeature, minFeature, Feature);
        }
        Log.e(TAG, "writeModel: number of model = " + featureList.size());

        //写入测试数据文件
//        File file = new File(dictionaryPath + "model.txt");
//        BufferedWriter bw = new BufferedWriter(new FileWriter((file)));
        BufferedWriter bw = createBufferedWriter(dictionaryPath, "MFCCs_model.txt");
        //将所有MFCC特征写入文件
        while (!featureList.isEmpty()) {
            Double[] feature = featureList.poll();
            //将数据存入文件
            writeData(feature, bw);
        }
        MFCC.svmTrain();
    }

    @Override
    public boolean hasModel() {
        File file = new File(dictionaryPath);
        file.mkdir();
        File[] files = file.listFiles();
        for (File file1 : files) {
            if (file1.getName().equals("data_model.txt"))
                return true;
        }

        return false;
    }


    //判断录音是否可用
    @Override
    public boolean isRecordSuccess() {

        File file = new File(absolutePath + "/TestRecord/waveRecord.wav");

        InputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //波形出错
        mWaveReader = new WaveFileReader(" ", in);

        int[][] buffer = mWaveReader.getData();

        //将buffer转为double
        double[] signal = new double[buffer[0].length];
        //归一化
        for (int i = 0; i < buffer[0].length; i++) {
            signal[i] = buffer[0][i];
        }

        //得到峰值索引
        int result[] = getPeaks(signal);

        //得到峰值之间的数据
        Double[] featureDouble = null;
        double[] bufferBetween = getBufferBetween(result[0], result[1], signal);
        Log.e(TAG, "isRecordSuccess: 0 = " + signal[result[0]]);
        Log.e(TAG, "isRecordSuccess: 1 = " + signal[result[1]]);

        Log.e(TAG, "isRecordSuccess: buffer len = " + bufferBetween.length);

        try {
            BufferedWriter bw = createBufferedWriter(absolutePath + "/singal/", "touch.txt");
            for (int i = 0; i < bufferBetween.length; i++) {
                bw.write(bufferBetween[i] + " ");
            }
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            featureDouble = MFCC.mfcc(dictionaryPath + mFileNameMap.get(TEST_MFCC_FILE), bufferBetween, bufferBetween.length, 44100);
//            featureDouble[featureDouble.length - 1] = getRMS(signal, result);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        normalizationData(maxBuffer, minBuffer, featureDouble);

//        File file2 = new File(dictionaryPath + "testRecord.txt");
        BufferedWriter bw = null;
        try {
            bw = createBufferedWriter(dictionaryPath, mFileNameMap.get(CURRENT_TEST_MFCC));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //将所有MFCC特征写入文件
        //将数据存入文件
//            Log.d(TAG, "writeModel: flag = " + flag + " len = " + feature.length);
        writeData(featureDouble, bw);

        try {
            return (MFCC.svmPredict(dictionaryPath + mFileNameMap.get(CURRENT_TEST_MFCC))) == 1;
        } catch (IOException e) {
            e.printStackTrace();
        }

//        file.delete();

        return false;
    }

    //用于作为测试大量wav样本识别率
    private LinkedList<Double[]> TestData() throws IOException {
        LinkedList<Double[]> featureList = new LinkedList<>();
//        char number = 'a';
//        for (int j = 0; j < 4; j++,number++) {
//            Log.d(TAG, "trainTextData: number = " + number);
        File[] testFiles = new File(absolutePath + File.separator + mFileNameMap.get(TEST_FILE)).listFiles();
        for (File testFile : testFiles) {
            String fileName = testFile.getName();
            File[] files = new File(testFile.getAbsolutePath()).listFiles();
            Log.e(TAG, "TestData: TestFiles = " + testFiles.length);

            //循环wav文件
            for (int j = 0; j < files.length; j++) {
                String number = String.valueOf(j);
//            InputStream in = mLockView.getInputStream("model" + number + ".wav");
//            InputStream in = mLockView.getInputStream(n umber + ".wav");
                InputStream in = new FileInputStream(files[j]);

                double[] signal = getWaveData(in, j);
                if (signal == null)
                    continue;

                normalizationData(signal);

//                double max = 0, min = signal[0];
//
//                //归一化
//                for (int i = 0; i < signal.length; i++) {
//                    //得到最大值和最小值
//                    if (max < signal[i])
//                        max = signal[i];
//                    if (min > signal[i])
//                        min = signal[i];
//                }
                int result[] = getPeaks(signal);
                //单峰值

                double[] bufferBetween = getBufferBetween(result[0], result[1], signal);
//                double[] bufferDouble = getBufferBetween(result[0], result[1], signal);
//                writeFile(bufferDouble, dictionaryPath + "TrainDebug_" + j + ".txt");

                //MfCC特征提起
                Double[] Feature = MFCC.mfcc(dictionaryPath + "Zm_mfcc.txt", bufferBetween, bufferBetween.length, 44100);
//                    Feature[Feature.length - 1] = getRMS(signal, aResult);
                featureList.add(Feature);
            }

//            File file = new File(dictionaryPath + fileName + ".txt");
            File mFile = new File(dictionaryPath + fileName + "_negative.txt");
//            BufferedWriter bw = new BufferedWriter(new FileWriter((file)));
            BufferedWriter mbw = new BufferedWriter(new FileWriter((mFile)));
            while (!featureList.isEmpty()) {
                Double[] feature = featureList.poll();
                //将数据存入文件
//            Log.d(TAG, "writeModel: flag = " + flag + " len = " + feature.length);
                assert feature != null;
                writeDataForMatLab(feature, mbw);
//                writeDataMinus(feature, bw);
//            for (int i = 0; i < feature.length; i++) {
//                bw.write(String.valueOf(feature[i]));
//                if (i != feature.length - 1)
//                    bw.write(",");
//                else bw.write("\n");
//            }
//                bw.flush();
            }


        }
//        mbw.flush();
        Log.e(TAG, "TestData: done.");
        return featureList;
    }

    private int[] getPeaks(double[] signal) {
        int[] result = new int[2];
        double max = 0;
        for (int i = 0; i < signal.length; i++) {
            if (max < signal[i]) {
                max = signal[i];
                result[0] = i;
            }
        }

        max = 0;
        for (int i = 0; i < signal.length; i++) {
            if (max < signal[i] && (i > result[0] + 1000 || i < result[0] - 1000)) {
                max = signal[i];
                result[1] = i;
            }
        }

        Arrays.sort(result);

//        Log.d(TAG, "getPeaks: 1 = " + result[0] + " 2 = " + result[1]);
        return result;
    }

    private int getPeaksOne(double[] signal) {
        int result = 0;
        double max = 0;
        for (int i = 0; i < signal.length; i++) {
            if (max < signal[i]) {
                max = signal[i];
                result = i;
            }
        }

//        Log.d(TAG, "getPeaks: 1 = " + result[0] + " 2 = " + result[1]);
        return result;
    }

    public synchronized double[] IIRFilter(double[] signal, double[] a, double[] b) {
        double[] in = new double[b.length];
        double[] out = new double[a.length - 1];

        double[] outData = new double[signal.length];

        for (int i = 0; i < signal.length; i++) {

            System.arraycopy(in, 0, in, 1, in.length - 1);
            in[0] = signal[i];

            //calculate y based on a and b coefficients
            //and in and out.
            float y = 0;
            for (int j = 0; j < b.length; j++) {
                y += b[j] * in[j];

            }

            for (int j = 0; j < a.length - 1; j++) {
                y -= a[j + 1] * out[j];
            }

            //shift the out array
            System.arraycopy(out, 0, out, 1, out.length - 1);
            out[0] = y;

            outData[i] = y;


        }
        return outData;
    }

    private BufferedWriter createBufferedWriter(String path, String name) throws IOException {
        File file = new File(path + name);
        if (!file.getParentFile().exists())
            file.getParentFile().mkdir();
        return new BufferedWriter(new FileWriter(file));
    }

    double getStandardDeviation(int[] result, double[] data) {
        double sd = 0;
        double average = 0;
        double N = result[1] - result[0];
        for (int i = result[0]; i <= result[1]; i++) {
            average += data[i];
        }
        average /= N;

        for (int i = result[0]; i <= result[1]; i++) {
            sd += Math.pow(data[i] - average, 2);
        }

        sd = Math.sqrt(sd / N);

        return sd;
    }

    //用于测试，训练出一个模型并测试其正确率
    @Override
    public void writeModel() throws IOException {

        LinkedList<Double[]> textSampleList = TestData();

        LinkedList<Double[]> featureList = new LinkedList<>();

        File[] trainFiles = new File(absolutePath + File.separator + mFileNameMap.get(MODEL_FILE)).listFiles();
        for (File trainFile : trainFiles) {
            Log.e(TAG, "writeModel: ModelFiles = " + trainFiles.length);

            File[] files = new File(trainFile.getAbsolutePath()).listFiles();
            for (int times = 0; times < files.length; times++) {


                String number = String.valueOf(times);
//            InputStream in = mLockView.getInputStream("model" + number + ".wav");
                InputStream in = new FileInputStream(files[times]);

                double[] signal = getWaveData(in, times);
                if (signal == null)
                    continue;
//                writeFile(signal, dictionaryPath + "signal_" + times + ".txt");
//                Butterworth butterworth = new Butterworth();
//                butterworth.bandPass(6,44100,100,1);
//                for (int i = 0; i < signal.length; i++) {
//                    signal[i] = butterworth.filter(signal[i]);
//                }
////                IirFilterCoefficients iirFilterCoefficients = IirFilterDesignExstrom.design(FilterPassType.bandpass, 6,
////                        100.0 / 44100, 100.0 / 44100);
////                double[] signalFilter = IIRFilter(signal, iirFilterCoefficients.a, iirFilterCoefficients.b);
//                writeFile(signal, dictionaryPath + "signalFilter_" + times + ".txt");

//            BandPass bandPass = new BandPass(15000,100,41400);
//            AudioDispatcher audioDispatcher = new AudioDispatcher(new UniversalAudioInputStream(inStream,new TarsosDSPAudioFormat(sampleRate,bufferSize,1,true,true)),1024,512);
//            audioDispatcher.addAudioProcessor(bandPass);
//            audioDispatcher.run();

                //提取峰值的索引
                int result[] = getPeaks(signal);

                normalizationData(signal);

//            double[] bufferDouble = getBufferBetween(result[0], result[1], signal);

                double[] bufferDouble = getBufferBetween(result[0], result[1], signal);
                //MfCC特征提起
                Double[] Feature = MFCC.mfcc(dictionaryPath + "Ljw_mfcc_.txt", bufferDouble, bufferDouble.length, 44100);
//                Feature[Feature.length - 1] = getRMS(bufferDouble, aResult);
//                double[] feature = MFCC.MFCC(bufferDouble, bufferDouble.length, 44100);
//            BufferedWriter bw_feature = createBufferedWriter(dictionaryPath + "/Zm_Model", "yym_mfcc_" + number + ".txt");
                featureList.add(Feature);
            }
            Log.e(TAG, "writeModel: number of model = " + featureList.size());


//        File file = new File(dictionaryPath + mFileNameMap.get(WRITE_MODEL_MFCC));
            File mFile = new File(dictionaryPath + trainFile.getName() + "_positive.txt");
//        BufferedWriter bw = new BufferedWriter(new FileWriter((file)));
            BufferedWriter mbw = new BufferedWriter(new FileWriter((mFile)));
            //将所有MFCC特征写入文件
            while (!featureList.isEmpty()) {
                Double[] feature = featureList.poll();
                //将数据存入文件
//            Log.d(TAG, "writeModel: flag = " + flag + " len = " + feature.length);
//            writeData(feature, bw);
                writeDataForMatLab(feature, mbw);
//            for (int i = 0; i < feature.length; i++) {
//                bw.write(String.valueOf(feature[i]));
//                if (i != feature.length - 1)
//                    bw.write(",");
//                else bw.write("\n");
//            }
//            bw.flush();
            }
            mbw.flush();
        }
        MFCC.svmTrain();
//        File textSampleFile = new File(dictionaryPath + "textSample");

//        for (int i = 1; i < 18; i++) {
        double sum = 0, correct = 0;
//        while (!textSampleList.isEmpty()) {
//            sum++;
//            Double[] featureSample = textSampleList.poll();
//            File textFile = new File(dictionaryPath + "testMfccData" + ".txt");
//            BufferedWriter bw_txt = new BufferedWriter(new FileWriter(textFile));
//            writeData(featureSample, bw_txt);

        //SVM训练
//        for (int i = 1; i <= 20; i++) {
//            sum++;
//            Log.e(TAG, "writeModel: N0." + i);
//            if ((int) (MFCC.svmPredict(dictionaryPath + "test_" + i + ".txt")) == 1) {
//                correct++;
//            }
//        }
//        }

        MFCC.svmPredict(dictionaryPath + "MFCC_test.txt");
//        Log.e(TAG, "writeModel: result = " + (correct / sum) + " " + correct + "/" + sum);
//        }
        Log.e(TAG, "writeModel: done.");
    }

    private double getRMS(double[] signal, int a) {
        double rms = 0;
        int i = 0;

        if (a - 100 > 0) {
            for (; i <= 300 && a - 100 + i < signal.length; i++) {
                rms += Math.pow(signal[a + i - 100], 2);
            }
            i--;
            rms = Math.sqrt(rms / i);
        } else {
            for (; i < signal.length && i <= 300 - a; i++) {
                rms += Math.pow(signal[i], 2);
            }
            i--;
            rms = Math.sqrt(rms / i);
        }

        return rms;
    }

    private double getRMS(double[] signal, int... A) {
        double rms = 0;
        int i = 0;
        List<Double> list = new LinkedList<>();
        for (int a : A) {
            if (a - 100 > 0) {
                for (; i <= 300 && a - 100 + i < signal.length; i++) {
                    rms = Math.pow(signal[a + i - 100], 2);
                    list.add(rms);
                }
            } else {
                for (; i < signal.length && i <= 300 - a; i++) {
                    rms = Math.pow(signal[i], 2);
                    list.add(rms);
                }
            }
        }
        int num = list.size();
        double sum = 0;
        for (int j = 0; j < num; j++) {
            sum += list.get(j);
        }
        rms = Math.sqrt(sum / num);

        return rms;
    }


    private double getRMSbetween(double[] signal, int a, int b) {
        double rms = 0;
        int i = (a - 100 > 0) ? a - 100 : 0;
        for (; i < b + 200 & i < signal.length; i++) {
            rms += Math.pow(signal[i], 2);
        }
        rms = Math.sqrt(rms / (--i));

        return rms;
    }

    private double getStandardDeviation(double[] signal, int a) {
        double rms = 0;
        //峰值数目
        int n = 0;
        double average = 0;

        //峰值索引
        int i = (a - 100 > 0) ? a - 100 : 0;
        //平均值
        for (; i < a + 200 & i < signal.length; i++) {
            average += signal[i];
            n++;
        }
        average /= n;

        //峰值索引
        i = (a - 100 > 0) ? a - 100 : 0;
        //平均值
        for (; i < a + 200 & i < signal.length; i++) {
            rms += Math.pow(signal[i] - average, 2);
        }

        rms = Math.sqrt(rms / n);

        return rms;
    }

    private double getStandardDeviation(double[] signal, int a, int b) {
        double rms = 0;
        //峰值数目
        int n = 0;
        double average = 0;

        //峰值索引
        int i = (a - 100 > 0) ? a - 100 : 0;
        int j = (b - 100 > 0) ? b - 100 : 0;

        //平均值
        for (; i < a + 200 & i < signal.length; i++) {
            average += signal[i];
            n++;
        }
        for (; j < b + 200 & j < signal.length; j++) {
            average += signal[j];
            n++;
        }
        average /= n;

        //峰值索引
        i = (a - 100 > 0) ? a - 100 : 0;
        j = (b - 100 > 0) ? b - 100 : 0;
        for (; i < a + 200 & i < signal.length; i++) {
            rms += Math.pow(signal[i] - average, 2);
        }
        for (; j < b + 200 & j < signal.length; j++) {
            rms += Math.pow(signal[j] - average, 2);
        }

        rms = Math.sqrt(rms / n);

        return rms;
    }

    private double[] getWaveData(InputStream in, int number) {
        mWaveReader = new WaveFileReader("", in);
        if (!mWaveReader.isSuccess()) {
            Log.d(TAG, "writeModel: No. " + number + "WaveReader Wrong.");
            return null;
        }
        //得到wave
        int[][] buffer = mWaveReader.getData();
        //将buffer转为double
        double[] signal = new double[buffer[0].length];
        //归一化
        for (int i = 0; i < buffer[0].length; i++) {
            signal[i] = buffer[0][i];
        }
        return signal;
    }


    //数据归一化
    void normalizationData(Double max, Double min, LinkedList<Double[]> list) {

        for (Double[] data : list) {
            for (int i = 0; i < data.length; i++) {
                data[i] = (data[i] - min) / (max - min) * 2 - 1;
            }
        }
    }

    //数据归一化
    private void normalizationData(Double max, Double min, Double[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = (data[i] - min) / (max - min) * 2 - 1;
        }
    }

    //数据归一化
    void normalizationData(Double[] data) {

        Double max = data[0];
        Double min = data[0];

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

    void normalizationData(double[] data) {

        Double max = data[0];
        Double min = data[0];

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


    private void writeData(Double feature[], BufferedWriter bw) {

        try {
            bw.write(1 + " ");
            for (int i = 0; i < feature.length; i++) {
                bw.write((i + 1) + ":" + String.valueOf(feature[i]));
                if (i != feature.length - 1)
                    bw.write(" ");
                else bw.write("\n");
//                Log.d(TAG, "writeModel: feature = " + feature[i]);
            }

            bw.write(" \n");
            bw.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeDataForMatLab(Double feature[], BufferedWriter bw) {

        try {
            for (int i = 0; i < feature.length; i++) {
                bw.write(String.valueOf(feature[i]));
                if (i != feature.length - 1)
                    bw.write(" ");
                else bw.write("\n");
//                Log.d(TAG, "writeModel: feature = " + feature[i]);
            }
            bw.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeDataMinus(Double feature[], BufferedWriter bw) {

        try {
            bw.write(0 + " ");
            for (int i = 0; i < feature.length; i++) {
                bw.write((i + 1) + ":" + String.valueOf(feature[i]));
                if (i != feature.length - 1)
                    bw.write(" ");
                else bw.write("\n");
//                Log.d(TAG, "writeModel: feature = " + feature[i]);
            }

            bw.write(" \n");
            bw.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeFile(double[] feature, String fileName) throws IOException {

        File file = new File(fileName);
//        DataOutputStream fos = new DataOutputStream(new FileOutputStream(file));
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
//        Log.d(TAG, "writeFile: " + fileName);
        try {
            int flag = 0;
            for (double aFeature : feature) {
                bw.write(String.valueOf(aFeature) + ", ");
            }

//            bw.write(" \n");
            bw.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param a
     * @param b
     * @param sample
     * @return
     */
    //得到峰值索引的那一段buffera，双峰值
    private double[] getBufferBetween(int a, int b, double[] sample) {

//        Log.d(TAG, "getBufferBetween: a = " + a + " b = " + b);

        LinkedList<Double> bufferList = new LinkedList<>();
        //-100 ~ +200
        if (a - 100 > 0) {
            for (int i = 0; i <= 300 && a - 100 + i < sample.length; i++) {
//                if ((int) sample[a - 100 + i] != 0)
                bufferList.add(sample[a - 100 + i]);
            }
        } else {
            for (int i = 0; i < sample.length && i <= 300 - a; i++) {
                bufferList.add(sample[i]);
            }
        }

        if (b - 100 > 0) {
            for (int i = 0; i <= 300 && b - 100 + i < sample.length; i++) {
//                if ((int) sample[a - 100 + i] != 0)
                bufferList.add(sample[b - 100 + i]);
            }
        } else {
            for (int i = 0; i < sample.length && i <= 300 - b; i++) {
                bufferList.add(sample[i]);
            }
        }

//        if (b - 401 + i > 0)
//            for (; i <= 600 && b - 401 + i < sample.length; i++) {
////                if ((int) sample[b - 401 + i] != 0)
//                bufferList.add(sample[b - 401 + i]);
//
//            }

        double[] buffer = new double[bufferList.size()];
        for (int j = 0; j < buffer.length; j++) {
            buffer[j] = bufferList.get(j);
        }
        return buffer;
    }

    //切割单峰值
    double[] getBufferBetween(int a, double[] sample) {
        LinkedList<Double> bufferList = new LinkedList<>();

        if (a - 100 > 0) {
            for (int i = 0; i <= 300 && a - 100 + i < sample.length; i++) {
//                if ((int) sample[a - 100 + i] != 0)
                bufferList.add(sample[a - 100 + i]);
            }
        } else {
            for (int i = 0; i < sample.length && i <= 300 - a; i++) {
                bufferList.add(sample[i]);
            }
        }

//        double[] buffer = new double[602];
//        int i = 0;
//        for (; i <= 600; i++) {
//            buffer[i] = sample[a - 100 + i];
//        }

        double[] buffer = new double[bufferList.size()];
        for (int j = 0; j < buffer.length; j++) {
            buffer[j] = bufferList.get(j);
        }

        return buffer;
    }

    class TrainDataTask extends AsyncTask<File, Void, Boolean> {

        @Override
        protected Boolean doInBackground(File... file) {
            File fileDir = file[0];
            Log.e(TAG, fileDir + ": start.");

            LinkedList<Double[]> featureList = new LinkedList<>();

            String fileName = fileDir.getName();
            File[] files = new File(fileDir.getAbsolutePath()).listFiles();
            try {

                //循环wav文件
                for (int j = 0; j < files.length; j++) {
                    InputStream in = null;
                    in = new FileInputStream(files[j]);

                    double[] signal = getWaveData(in, j);
                    if (signal == null)
                        continue;
                    if (fileDir.equals(mFileNameMap.get(TEST_FILE)))
                        normalizationData(signal);
                    int result[] = getPeaks(signal);
                    double[] bufferBetween = getBufferBetween(result[0], result[1], signal);
//                    double[] bufferBetween2 = getBufferBetween(result[1], signal);

                    //MfCC特征提起
                    Double[] Feature = MFCC.mfcc(dictionaryPath + "Feature_" + fileName + ".txt", bufferBetween, bufferBetween.length, 44100);
                    //提取两个峰值-100~200范围的RMS
//                    Feature[Feature.length - 1] = getRMS(signal, result[0]);
                    featureList.add(Feature);

//                    Feature = MFCC.mfcc(dictionaryPath + "Feature_" + fileName + ".txt", bufferBetween2, bufferBetween2.length, 44100);
//                    Feature[Feature.length - 1] = getRMS(signal, result[1]);
//                    featureList.add(Feature);

                }
                File mFile = new File(dictionaryPath + fileDir.getParentFile().getName() + File.separator + fileName + "_matlab.txt");
                mFile.getParentFile().mkdirs();
                BufferedWriter mbw = new BufferedWriter(new FileWriter((mFile)));
                while (!featureList.isEmpty()) {
                    Double[] feature = featureList.poll();
                    //将数据存入文件
                    assert feature != null;
                    writeDataForMatLab(feature, mbw);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.e(TAG, fileName + ": done.");
            return null;
        }
    }

    class CurrentRecordTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            File file = new File(absolutePath + "/TestRecord/waveRecord.wav");

            InputStream in = null;
            try {
                in = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            //波形出错
            mWaveReader = new WaveFileReader(" ", in);

            int[][] buffer = mWaveReader.getData();

            //将buffer转为double
            double[] signal = new double[buffer[0].length];
            //归一化
            for (int i = 0; i < buffer[0].length; i++) {
                signal[i] = buffer[0][i];
            }

            //得到峰值索引
            int result[] = getPeaks(signal);

            //得到峰值之间的数据
            Double[] featureDouble = null;
            double[] bufferBetween = getBufferBetween(result[0], result[1], signal);
            try {
                featureDouble = MFCC.mfcc(dictionaryPath + mFileNameMap.get(TEST_MFCC_FILE), bufferBetween, bufferBetween.length, 44100);
//            featureDouble[featureDouble.length - 1] = getRMS(signal, result);
            } catch (IOException e) {
                e.printStackTrace();
            }
//        normalizationData(maxBuffer, minBuffer, featureDouble);

//        File file2 = new File(dictionaryPath + "testRecord.txt");
            BufferedWriter bw = null;
            try {
                bw = createBufferedWriter(dictionaryPath, mFileNameMap.get(CURRENT_TEST_MFCC));
            } catch (IOException e) {
                e.printStackTrace();
            }
            //将所有MFCC特征写入文件
            //将数据存入文件
//            Log.d(TAG, "writeModel: flag = " + flag + " len = " + feature.length);
            writeData(featureDouble, bw);

            try {
                return (MFCC.svmPredict(dictionaryPath + mFileNameMap.get(CURRENT_TEST_MFCC))) == 1;
            } catch (IOException e) {
                e.printStackTrace();
            }

            file.delete();

            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
//                Toast.makeText(LockScreenActivity.this, "Welcome", Toast.LENGTH_SHORT).show();
//                soundOfUnlock();
////                        startActivity(intentToMain);
//                finish();
            } else {

            }
        }
    }

    private static boolean isSave = true;

    class RecordTask extends AsyncTask<Void, Void, Void> {

        int sampleRate = 44100;
        int fftlen = 1024;
        AudioRecord record;
        WavWriter wavWriter = new WavWriter("TestRecord", sampleRate);

        @Override
        protected Void doInBackground(Void... voids) {


            wavWriter.start();

            int minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int numOfReadShort;
            int readChunkSize = fftlen;  // Every hopLen one fft result (overlapped analyze window)
            short[] audioSamples = new short[readChunkSize];
            int bufferSampleSize = Math.max(minBytes / 2, fftlen / 2) * 2;
            // tolerate up to about 1 sec.
            bufferSampleSize = (int) Math.ceil(1.0 * sampleRate / bufferSampleSize) * bufferSampleSize;
            record = new AudioRecord(6, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, 2 * bufferSampleSize);

            record.startRecording();

            while (isSave) {
                numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
                wavWriter.pushAudioShort(audioSamples, numOfReadShort);  // Maybe move this to another thread?
            }


            return null;
        }

        void stop() {
            isSave = false;
            record.stop();
            record.release();
            wavWriter.stop();
        }
    }

    public AudioRecord record = null;
    int sampleRate = 44100;
    int fftlen = 1024;
    int minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    int numOfReadShort;
    int readChunkSize = fftlen;  // Every hopLen one fft result (overlapped analyze window)
    short[] audioSamples = new short[readChunkSize];
    int bufferSampleSize = Math.max(minBytes / 2, fftlen / 2) * 2;

    public int getMax() {

        // tolerate up to about 1 sec.

        int max = 0;
        for (int i = 0; i < audioSamples.length; i++) {
            if (max < audioSamples[i])
                max = audioSamples[i];
        }
        return max;
    }

    class CurrentRecordTaskNew extends AsyncTask<Void, Boolean, Void> {
        WavWriter wavWriter = new WavWriter("TestRecord", sampleRate);

        @Override
        protected Void doInBackground(Void... voids) {
//            int bufferSampleSize = Math.max(minBytes / 2, fftlen / 2) * 2;
            wavWriter.start();
            int max = 0;

            int num = 0;
            //max < MAX_NOISE
            while (num != 2 && isRecord) {
                numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
                max = wavWriter.pushAudioShortNew(audioSamples, numOfReadShort);  // Maybe move this to another thread?
                if (max == -1)
                    num++;
            }

//            for (int i = 0; i < audioSamples.length; i++) {
//                audioSamples[i] = 1000;
//            }
            if (!isRecord)
            {
                record.stop();
                record.release();
                return null;
            }
            int[] singal = wavWriter.getSignal();

            BufferedWriter bw = null;
            stop();

            double[] buffer = new double[singal.length];
            for (int i = 0; i < singal.length; i++) {
                buffer[i] = singal[i];
            }

//            try {
//                bw = createBufferedWriter(absolutePath + "/singal/", "air.txt");
//                for (int i = 0; i < singal.length; i++) {
//                    bw.write(buffer[i] + " ");
//                }
//                bw.flush();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            Double[] featureDouble = null;
            try {
                featureDouble = MFCC.mfcc(dictionaryPath + mFileNameMap.get(TEST_MFCC_FILE), buffer, singal.length, 44100);
//            featureDouble[featureDouble.length - 1] = getRMS(signal, result);
            } catch (IOException e) {
                e.printStackTrace();
            }
//        normalizationData(maxBuffer, minBuffer, featureDouble);

//        File file2 = new File(dictionaryPath + "testRecord.txt");
            try {
                bw = createBufferedWriter(dictionaryPath, mFileNameMap.get(CURRENT_TEST_MFCC));
            } catch (IOException e) {
                e.printStackTrace();
            }
            //将所有MFCC特征写入文件
            //将数据存入文件
//            Log.d(TAG, "writeModel: flag = " + flag + " len = " + feature.length);
            writeData(featureDouble, bw);

            try {
                if ((MFCC.svmPredict(dictionaryPath + mFileNameMap.get(CURRENT_TEST_MFCC))) == 1) {
                    publishProgress(true);
                } else {
                    publishProgress(false);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }


            return null;
        }

        @Override
        protected void onProgressUpdate(Boolean... values) {
            if (values[0])
                mLockView.unlockSuccess();
            else mLockView.unlockFail();
        }

        void stop() {
            wavWriter.stop();
        }
    }

}
