package com.project.creative.hellfireproject;

/**
 * Created by krilin on 15. 12. 24.
 */
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Point;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import static org.opencv.imgproc.Imgproc.Canny;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.putText;

/**
 * This is the main Activity which takes Camera input, captures frame, interact with OpenCV library, process frame and display overlays
 * on lanes detection. It uses Hough Value which is provided by the VerticalSliderActivity. This java activity acts as the Controller
 * here and core image processing work is done by the C++ files
 *
 * @author Wahib-Ul-Haq, wahib.tech@gmail.com, 2014
 *         <p/>
 *         contains code taken from:
 *         <p/>
 *         "Lane detection using OpenCV"
 *         https://github.com/jdorweiler/lane-detection
 *         Acknowledgments: jdorweiler
 *         Contact: jason@transistor.io
 */

public class LaneDetector extends Activity implements CvCameraViewListener2 {

    private CameraBridgeViewBase mOpenCvCameraView;
    private static final String TAG = "OCVSample::Activity";
    private Mat img;
    private Mat gray;
    private int houghValue;
    private int centerx;
    final static double PI = 3.141592;

    private Context mContext = null;
    private static SerialConnector mSerialConn = null;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    System.loadLibrary("LaneDetectionNative");
                    mOpenCvCameraView.enableView();

                }
                break;

                default: {
                    super.onManagerConnected(status);
                }
                break;
            }

        }
    };
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */

    @Override
    public void onResume() {

        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_lane_test);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.AndriveLaneView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mContext = getApplicationContext();
        mSerialConn = new SerialConnector(mContext);
        mSerialConn.initialize();

        Intent intent = getIntent();
        houghValue = Integer.valueOf(intent.getStringExtra("houghvalue"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    ;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    ;


    @Override
    public void onCameraViewStarted(int width, int height) {

        Log.v("Screen Resolution", "Height: " + height + " Width: " + width);


        img = new Mat(height, width, CvType.CV_8UC4);
        gray = new Mat(height, width, CvType.CV_8UC4);

    }

    ;

    @Override
    public void onCameraViewStopped() {
        img.release();
    }

    ;

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        float angle[];
        String text,center,frame;
        int ang;
        img = inputFrame.rgba().clone();
        Mat ROI = img.submat(img.height() *2/ 3, img.height(), img.width() * 1 / 5, img.width() * 4 / 5);
        angle = laneDetection(ROI.getNativeObjAddr(), ROI.getNativeObjAddr());
        ang = steerLogic(angle[0], angle[1], inputFrame.rgba().width() * 3 / 5);

        text = Integer.toString(ang);
        center=Float.toString(angle[1]);
        frame=Integer.toString(inputFrame.rgba().width()*3/5);
        putText(img, text + " " + center+ " " + frame, new Point(100, 100), 2, 1.2, new Scalar(255));


        Log.e(TAG, "End Java Detection");
        return img;
    }

    public static int steerLogic(float angle, float center, int width) {

        Log.e(TAG, "angle:" + angle);
        int pow = 2;
        int ang = 0;
        int val = width / 7;
        if (center < val * 2) {
            ang = 1;
        } else if (center < val * 5) {
            if (angle > 150) {
                ang = 1; //자동차를 왼쪽으로
            } else if (angle < 150 && angle > 110) {
                ang = 2; //자동차를 왼쪽으로
            } else if (angle < 110 && angle > 70) {
                ang = 3; //작진
            } else if (angle < 70 && angle > 30) {
                ang = 4; //자동차를 오른쪽으로
            } else if (angle < 30) {
                ang = 5; //자동차를 오른쪽으로
            }
        } else {
            ang = 5;
        }


        Log.e(TAG, "pow:" + pow + " " + "ang" + ang);


        ////Send Serial to Arduino

        byte[] buf = {0x0c, 0x00};
        buf[0] = (byte) pow;
        buf[1] = (byte) ang;
        mSerialConn.sendCommand(buf);
        return ang;

    }


    public native float[] laneDetection(long input, long output);

}