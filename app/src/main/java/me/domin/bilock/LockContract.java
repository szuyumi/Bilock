package me.domin.bilock;

//import com.chrischen.waveview.WaveView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Administrator on 2018/4/13.
 */

public interface LockContract  {
    interface View{

        void clear();

        InputStream getInputStream(String fileName);

        void unlockSuccess();

        void unlockFail();

    }

    interface Presenter {

        void svmTrain();

        void currentRecord();

        void trainData();

        void startRecord();

        void currentRecordTaskNew();

        void stopRecord();

        void trainModel() throws IOException;

        boolean hasModel();

        boolean isRecordSuccess();

        void writeModel() throws IOException;
    }
}
