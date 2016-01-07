//
// Created by krilin on 15. 12. 27.
//


#include <jni.h>

#include <stdio.h>
#include "com_project_creative_hellfireproject_LaneDetector.h"
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/opencv.hpp>
#include <math.h>
#include "utils.h"

#include <android/log.h>

#define PI 3.1415926

using namespace std;
using namespace cv;
#define USE_VIDEO 1

#undef MIN
#undef MAX
#define MAX(a,b) ((a)<(b)?(b):(a))
#define MIN(a,b) ((a)>(b)?(b):(a))

void crop(IplImage* src,  IplImage* dest, CvRect rect) {
    cvSetImageROI(src, rect);
    cvCopy(src, dest);
    cvResetImageROI(src);
}

struct Lane {
    Lane(){}
    Lane(CvPoint a, CvPoint b, float angle, float kl, float bl): p0(a),p1(b),angle(angle),
                                                                 votes(0),visited(false),found(false),k(kl),b(bl) { }

    CvPoint p0, p1;
    int votes;
    bool visited, found;
    float angle, k, b;
};

struct Status {
    Status():reset(true),lost(0){}
    ExpMovingAverage k, b;
    bool reset;
    int lost;
};

struct Vehicle {
    CvPoint bmin, bmax;
    int symmetryX;
    bool valid;
    unsigned int lastUpdate;
};

struct VehicleSample {
    CvPoint center;
    float radi;
    unsigned int frameDetected;
    int vehicleIndex;
};

#define GREEN CV_RGB(0,255,0)
#define RED CV_RGB(255,0,0)
#define BLUE CV_RGB(255,0,255)
#define PURPLE CV_RGB(255,0,255)

Status laneR, laneL;
std::vector<Vehicle> vehicles;
std::vector<VehicleSample> samples;

enum{
    SCAN_STEP = 5,			  // in pixels
    LINE_REJECT_DEGREES = 10, // in degrees
    BW_TRESHOLD = 250,		  // edge response strength to recognize for 'WHITE'
    BORDERX = 10,			  // px, skip this much from left & right borders

    CANNY_MIN_TRESHOLD = 50,	  // edge detector minimum hysteresis threshold
    CANNY_MAX_TRESHOLD = 100, // edge detector maximum hysteresis threshold

    HOUGH_TRESHOLD = 100,		// line approval vote threshold
    HOUGH_MIN_LINE_LENGTH = 50,	// remove lines shorter than this treshold
    HOUGH_MAX_LINE_GAP = 100,   // join lines to one with smaller than this gaps
};

#define K_VARY_FACTOR 0.2f
#define B_VARY_FACTOR 20
#define MAX_LOST_FRAMES 30


void processLanes(CvSeq* lines, IplImage* edges, IplImage* temp_frame,float* buf) {

    // classify lines to left/right side
    float theta_avg=90;
    float x_l=0;
    float x_r=0;
    float center=temp_frame->width/2;
    float theta_r=0;
    float theta_l=0;
    int cnt_r=0;
    int cnt_l=0;
    std::vector<Lane> left, right;

    for(int i = 0; i < lines->total; i++ )
    {
        CvPoint* line = (CvPoint*)cvGetSeqElem(lines,i);
        int dx = line[1].x - line[0].x;
        int dy = line[1].y - line[0].y;
        float angle = atan2f(dy, dx) * 180/CV_PI;

        if (fabs(angle) <= LINE_REJECT_DEGREES) { // reject near horizontal lines
            continue;
        }

        // assume that vanishing point is close to the image horizontal center
        // calculate line parameters: y = kx + b;
        dx = (dx == 0) ? 1 : dx; // prevent DIV/0!
        float k = dy/(float)dx;
        float b = line[0].y - k*line[0].x;

        // assign lane's side based by its midpoint position
        int midx = (line[0].x + line[1].x) / 2;
        ///for steering
        if (midx < temp_frame->width/2) {
            x_l+=midx;
            theta_l+=angle;
            cnt_l++;
            left.push_back(Lane(line[0], line[1], angle, k, b));
        } else if (midx > temp_frame->width/2) {
            x_r+=midx;
            theta_r+=angle;
            cnt_r++;
            right.push_back(Lane(line[0], line[1], angle, k, b));
        }
    }

    // show Hough lines
    for	(int i=0; i<right.size(); i++) {
        cvLine(temp_frame, right[i].p0, right[i].p1, CV_RGB(0, 0, 255), 2);
    }

    for	(int i=0; i<left.size(); i++) {
        cvLine(temp_frame, left[i].p0, left[i].p1, CV_RGB(255, 0, 0), 2);
    }




    if(cnt_l>cnt_r*3) {
        theta_l/=cnt_l;

        if(theta_l<0){
            theta_l=180+theta_l;
        }
        theta_avg = theta_l;
        x_l/=cnt_l;
        center=temp_frame->width/7;

    }else if(cnt_r>cnt_l*3){
        theta_r/=cnt_r;

        if(theta_r<0){
            theta_r=180+theta_r;
        }
        theta_avg=theta_r/cnt_r;
        x_r/=cnt_r;
        center=temp_frame->width*6/7;
        //center=x_r;

    }else if(cnt_l==0||cnt_r==0) {

        center=temp_frame->width/2;
        theta_avg=90;
    }
    else{

        theta_l /= cnt_l;
        theta_r /= cnt_r;
        x_r/=cnt_r;
        x_l/=cnt_l;
        if(theta_l<0){
            theta_l=180+theta_l;
        }
        if(theta_r<0){
            theta_r=180+theta_r;
        }
        theta_avg=(theta_l+theta_r)/2;
        center=(x_l+x_r)/2;


    }


    __android_log_print(ANDROID_LOG_ERROR, "HELLFIRE++ theta_r:", "%f %d", theta_r,cnt_r);
    __android_log_print(ANDROID_LOG_ERROR, "HELLFIRE++ theta_l:", "%f %d", theta_l,cnt_l);
    buf[0]=theta_avg;
    buf[1]=center;
}


JNIEXPORT jfloatArray JNICALL Java_com_project_creative_hellfireproject_LaneDetector_laneDetection
        (JNIEnv *env, jobject obj, jlong in, jlong out){
    __android_log_print(ANDROID_LOG_ERROR, "HELLFIRE++", "%s", "start laneDetection");
    jfloatArray result=env->NewFloatArray(2);
    jfloat hello[2];
    float buf[2];//buf[0]==angle, buf[1]=centerx


    CvMemStorage* houghStorage = cvCreateMemStorage(0);

    Mat& input = *(Mat*)in;
    Mat& output = *(Mat*)out;
    IplImage *temp_frame=new IplImage(input);

    CvSize frame_size = cvSize(temp_frame->width, temp_frame->height);
    IplImage *grey = cvCreateImage(frame_size, IPL_DEPTH_8U, 1);
    IplImage *edges = cvCreateImage(frame_size, IPL_DEPTH_8U, 1);


    if (temp_frame==NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "HELLFIRE++", "%s", "Cannot access the camera");

        exit(0);
    }

    cvCvtColor(temp_frame, grey, CV_BGR2GRAY); // convert to grayscale

    // Perform a Gaussian blur ( Convolving with 5 X 5 Gaussian) & detect edges
    cvSmooth(grey, grey, CV_GAUSSIAN, 23, 23);
    cvCanny(grey, edges, CANNY_MIN_TRESHOLD, CANNY_MAX_TRESHOLD);
    // do Hough transform to find lanes
    double rho = 1;
    double theta = CV_PI/180;
    CvSeq* lines = cvHoughLines2(edges, houghStorage, CV_HOUGH_PROBABILISTIC,rho, theta, HOUGH_TRESHOLD, HOUGH_MIN_LINE_LENGTH, HOUGH_MAX_LINE_GAP);

    processLanes(lines, edges, temp_frame,buf);

    // show middle line
    cvLine(temp_frame, cvPoint(frame_size.width/2,0), cvPoint(frame_size.width/2,frame_size.height), CV_RGB(255, 255, 0), 1);

    cvReleaseMemStorage(&houghStorage);

    cvReleaseImage(&grey);
    cvReleaseImage(&edges);

    char buffer[25];
    sprintf(buffer, "result : %f", buf[0]);
    __android_log_print(ANDROID_LOG_ERROR, "HELLFIRE++", "%f", buf[0]);
    CvFont font;
    cvInitFont(&font, CV_FONT_HERSHEY_SIMPLEX, 0.5, 1);
    cvPutText(temp_frame, buffer, cvPoint(50, 50), &font, cvScalar(255));
    hello[0]=buf[0];
    hello[1]=buf[1];



    env->SetFloatArrayRegion(result, 0, 2, hello);

    output = cvarrToMat(temp_frame);//show Image
    __android_log_print(ANDROID_LOG_ERROR, "HELLFIRE++", "%s", "end laneDetection");
    return result;

}