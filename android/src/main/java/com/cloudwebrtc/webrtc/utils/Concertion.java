package com.cloudwebrtc.webrtc.utils;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.webrtc.JavaI420Buffer;
import org.webrtc.VideoFrame;
import org.webrtc.YuvHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;


public  class Concertion{

 ;
    //美颜
//    public Bitmap applyBeautyFilter(Bitmap inputBitmap) {
//
//    }


     public static final String CASCADE_FILE_NAME = "lbpcascade_frontalface.xml"; // 级联分类器文件名
     private static final float BILATERAL_TRAVERSAL = 1.5f; // 双边滤波参数
     private static final float WHITENESS = 1.2f; // 美白强度

     public static CascadeClassifier mFaceDetector; // 级联分类器对象

     public static void BeautyFilter(File cascadeFile) {
          mFaceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
     }
    public static Bitmap applyBeautyFilter(Bitmap inputBitmap) {
        float bilityTraversal = 5.0f; // 将 Float 类型改为 float 类型，并修改变量名为 bilityTraversal

        Bitmap processedBitmap = inputBitmap.copy(inputBitmap.getConfig(), true); // 使用输入的 Bitmap 对象创建一个新的 Bitmap 对象

//        Mat kernel = new Mat(3, 3, CvType.CV_32F);
//        kernel.put(0, 0, 0, -1, 0, -1, 5, -1, 0, -1, 0);
        if (bilityTraversal > 0.0f) {
            // 磨皮美颜算法
            int dx = (int) (bilityTraversal * 5.0f); // 双边滤波参数之一，需要乘以一个浮点数
            double fc = bilityTraversal * 12.5; // 双边滤波参数之一
            double p = 0.1f; // 透明度

            Mat image = new Mat();
            Utils.bitmapToMat(processedBitmap, image);

            // 进行人脸检测，获取人脸位置信息
            org.opencv.core.Rect faceRect = detectFace(image);

            // 只对人脸部分进行美颜处理
            if (faceRect != null) {

                Mat faceMat = image.submat(faceRect );

                // 亮度局部会有块 放在image处理
//                Core.add(faceMat, new Scalar(10, 10, 10), faceMat);
//                Core.convertScaleAbs(faceMat, faceMat, 0.8f, 0.8f); // 调整对比度和亮度

                Imgproc.cvtColor(faceMat, faceMat, Imgproc.COLOR_BGRA2BGR);

                Mat matBilFilter = new Mat();
                Imgproc.bilateralFilter(faceMat, matBilFilter, dx, fc, fc);

                Mat matSubDest = new Mat();
                Core.subtract(matBilFilter, faceMat, matSubDest);

                Mat matGaussSrc = new Mat();
                Core.add(matSubDest, new Scalar(128, 128, 128, 128), matGaussSrc);
//
                Mat matGaussDest = new Mat();
                Imgproc.GaussianBlur(matGaussSrc, matGaussDest, new Size(2 * dx + 1, 2 * dx +1), 0, 0);
//
                Mat matTmpSrc = new Mat();
                matGaussDest.convertTo(matTmpSrc, -1, 2.0f, -255.0f);
//
                Mat matTmpDest = new Mat();
                Core.add(faceMat, matTmpSrc, matTmpDest);
//
                Core.addWeighted(faceMat, p, matTmpDest, 1 - p, 0.0, faceMat);

            }
            //高清处理 暂时不用
          //  Mat sharpenedImg = new Mat();;
//            Imgproc.filter2D(image, sharpenedImg, -1, kernel);

            Utils.matToBitmap(image, processedBitmap);
        }

        return processedBitmap;
     }
    private static  org.opencv.core.Rect detectFace(Mat image) {

        Mat grayImage = new Mat();
        Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);

        // 进行人脸检测
        MatOfRect faces = new MatOfRect();
        mFaceDetector.detectMultiScale(grayImage, faces);

        // 如果检测到人脸，则返回第一个检测到的人脸位置
        if (faces.toArray().length > 0) {
            return faces.toArray()[0];
        }

        // 如果没有检测到人脸，则返回 null
        return null;
    }
    private static android.graphics.Rect convertRect(org.opencv.core.Rect rect) {
        return new android.graphics.Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height);
    }
     public static Bitmap applySkinWhitening(Bitmap bitmap, float brightness) {
          // 创建一个新的Bitmap对象，用于修改后的图像
          Bitmap resultBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

          // 创建一个画布，并将原始图像绘制到画布上
          Canvas canvas = new Canvas(resultBitmap);
          Paint paint = new Paint();
          canvas.drawBitmap(bitmap, 0, 0, paint);

          // 创建一个颜色矩阵，用于调整图像的亮度
          ColorMatrix brightnessMatrix = new ColorMatrix();
          brightnessMatrix.set(new float[]{
                  brightness, 0, 0, 0, 0,
                  0, brightness, 0, 0, 0,
                  0, 0, brightness, 0, 0,
                  0, 0, 0, 1, 0
          });

          // 创建一个颜色矩阵，用于实现美白效果
          ColorMatrix skinWhiteningMatrix = new ColorMatrix();
          skinWhiteningMatrix.setSaturation(1); // 设置饱和度为0，即变成灰度图像

          // 将亮度调整矩阵和美白矩阵相乘，得到最终的颜色矩阵
          ColorMatrix finalMatrix = new ColorMatrix();
          finalMatrix.postConcat(brightnessMatrix);
          finalMatrix.postConcat(skinWhiteningMatrix);

          // 创建一个颜色过滤器，将颜色矩阵应用于画笔
          ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(finalMatrix);
          paint.setColorFilter(colorFilter);

          // 绘制经过美白和亮度调整后的图像
          canvas.drawBitmap(bitmap, 0, 0, paint);

          return resultBitmap;
     }
     public static void bitmapToI420(Bitmap src, JavaI420Buffer dest) {
          int width = src.getWidth();
          int height = src.getHeight();

          if (width != dest.getWidth() || height != dest.getHeight())
               return;

          int[] argb = new int[width * height];
          src.getPixels(argb, 0, width, 0, 0, width, height);

          int yStride = dest.getStrideY();
          int uStride = dest.getStrideU();
          int vStride = dest.getStrideV();

          ByteBuffer dataY = dest.getDataY();
          ByteBuffer dataU = dest.getDataU();
          ByteBuffer dataV = dest.getDataV();

          for (int i = 0; i < height; i++) {
               int yIndex = i * yStride;
               int uIndex = (i / 2) * uStride;
               int vIndex = (i / 2) * vStride;

               for (int j = 0; j < width; j++) {
                    int pixel = argb[i * width + j];
                    int r = (pixel >> 16) & 0xff;
                    int g = (pixel >> 8) & 0xff;
                    int b = pixel & 0xff;

                    int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                    dataY.put(yIndex + j, (byte) y);

                    if (i % 2 == 0 && j % 2 == 0) {
                         dataU.put(uIndex + j / 2, (byte) u);
                         dataV.put(vIndex + j / 2, (byte) v);
                    }
               }
          }
     }

     private static void adjustSkinColor(Mat srcMat, Mat resultMat) {
          // 对图像进行色度调整，可以通过增加亮度、减少饱和度等方式实现美白效果
          Imgproc.cvtColor(srcMat, resultMat, Imgproc.COLOR_YUV2BGR);

          for (int i = 0; i < resultMat.rows(); i++) {
               for (int j = 0; j < resultMat.cols(); j++) {
                    double[] pixel = resultMat.get(i, j);

                    // 调整亮度和饱和度
                    pixel[0] += 50; // 增加亮度
                    pixel[1] *= 0.9; // 减少饱和度

                    resultMat.put(i, j, pixel);
               }
          }

          Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_RGB2YUV); // 将BGR图像转换为RGBA图像
     }
    //视频帧转图位帧
   public static Bitmap videoFrameToBitMao(VideoFrame frame){
        VideoFrame.Buffer buffer = frame.getBuffer();
        VideoFrame.I420Buffer i420Buffer = buffer.toI420();
        ByteBuffer y = i420Buffer.getDataY();
        ByteBuffer u = i420Buffer.getDataU();
        ByteBuffer v = i420Buffer.getDataV();
        int width = i420Buffer.getWidth();
        int height = i420Buffer.getHeight();
        int[] strides = new int[] {
                i420Buffer.getStrideY(),
                i420Buffer.getStrideU(),
                i420Buffer.getStrideV()
        };
        final int chromaWidth = (width + 1) / 2;
        final int chromaHeight = (height + 1) / 2;
        final int minSize = width * height + chromaWidth * chromaHeight * 2;

        ByteBuffer yuvBuffer = ByteBuffer.allocateDirect(minSize);
        // NV21 is the same as NV12, only that V and U are stored in the reverse oder
        // NV21 (YYYYYYYYY:VUVU)
        // NV12 (YYYYYYYYY:UVUV)
        // Therefore we can use the NV12 helper, but swap the U and V input buffers
        YuvHelper.I420ToNV12(y, strides[0], v, strides[2], u, strides[1], yuvBuffer, width, height);

        // For some reason the ByteBuffer may have leading 0. We remove them as
        // otherwise the
        // image will be shifted
        byte[] cleanedArray = Arrays.copyOfRange(yuvBuffer.array(), yuvBuffer.arrayOffset(), minSize);

        YuvImage yuvImage = new YuvImage(
                cleanedArray,
                ImageFormat.NV21,
                width,
                height,
                // We omit the strides here. If they were included, the resulting image would
                // have its colors offset.
                null);


        // 将视频帧数据转换成Bitmap对象

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0, width, height),
                100,
                outputStream
        );
        byte[] jpegData = outputStream.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

        return bitmap;

    }
}