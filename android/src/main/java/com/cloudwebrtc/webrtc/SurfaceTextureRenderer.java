package com.cloudwebrtc.webrtc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.util.Log;

import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.JavaI420Buffer;
import org.webrtc.NV21Buffer;
import org.webrtc.RendererCommon;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.YuvHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import com.cloudwebrtc.webrtc.utils.Concertion;
/**
 * Display the video stream on a Surface.
 * renderFrame() is asynchronous to avoid blocking the calling thread.
 * This class is thread safe and handles access from potentially three different threads:
 * Interaction from the main app in init, release and setMirror.
 * Interaction from C++ rtc::VideoSinkInterface in renderFrame.
 * Interaction from SurfaceHolder lifecycle in surfaceCreated, surfaceChanged, and surfaceDestroyed.
 */
public class SurfaceTextureRenderer extends EglRenderer {
  // Callback for reporting renderer events. Read-only after initilization so no lock required.
  private RendererCommon.RendererEvents rendererEvents;
  private final Object layoutLock = new Object();
  private boolean isRenderingPaused;
  private boolean isFirstFrameRendered;
  private int rotatedFrameWidth;
  private int rotatedFrameHeight;
  private int frameRotation;
  public static final String LOG_TAG = "wysaid";

  /**
   * In order to render something, you must first call init().
   */
  public SurfaceTextureRenderer(String name) {
    super(name);
  }

  public void init(final EglBase.Context sharedContext,
                   RendererCommon.RendererEvents rendererEvents) {
    init(sharedContext, rendererEvents, EglBase.CONFIG_PLAIN, new GlRectDrawer());
  }

  /**
   * Initialize this class, sharing resources with |sharedContext|. The custom |drawer| will be used
   * for drawing frames on the EGLSurface. This class is responsible for calling release() on
   * |drawer|. It is allowed to call init() to reinitialize the renderer after a previous
   * init()/release() cycle.
   */
  public void init(final EglBase.Context sharedContext,
                   RendererCommon.RendererEvents rendererEvents, final int[] configAttributes,
                   RendererCommon.GlDrawer drawer) {
    ThreadUtils.checkIsOnMainThread();
    this.rendererEvents = rendererEvents;
    synchronized (layoutLock) {
      isFirstFrameRendered = false;
      rotatedFrameWidth = 0;
      rotatedFrameHeight = 0;
      frameRotation = -1;
    }
    super.init(sharedContext, configAttributes, drawer);
  }
  @Override
  public void init(final EglBase.Context sharedContext, final int[] configAttributes,
                   RendererCommon.GlDrawer drawer) {
    init(sharedContext, null /* rendererEvents */, configAttributes, drawer);



  }
  /**
   * Limit render framerate.
   *
   * @param fps Limit render framerate to this value, or use Float.POSITIVE_INFINITY to disable fps
   *            reduction.
   */
  @Override
  public void setFpsReduction(float fps) {
    synchronized (layoutLock) {
      isRenderingPaused = fps == 0f;
    }
    super.setFpsReduction(fps);
  }
  @Override
  public void disableFpsReduction() {
    synchronized (layoutLock) {
      isRenderingPaused = false;
    }
    super.disableFpsReduction();
  }
  @Override
  public void pauseVideo() {
    synchronized (layoutLock) {
      isRenderingPaused = true;
    }
    super.pauseVideo();
  }
  // VideoSink interface.
  @Override
  public void onFrame(VideoFrame frame) {
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

    Bitmap beautyBitmap = Concertion.gpuBeauty(bitmap);;
    JavaI420Buffer javaI420Buffer = JavaI420Buffer.allocate(width,height);
    // 将美颜后的Bitmap对象转换成视频帧数据
    Concertion.bitmapToI420(beautyBitmap,javaI420Buffer);
    VideoFrame outputFrame = new VideoFrame(javaI420Buffer, 0, frame.getTimestampNs());
    updateFrameDimensionsAndReportEvents(outputFrame);

    super.onFrame(outputFrame);
    outputFrame.release();
  }







  private SurfaceTexture texture;

  public void surfaceCreated(final SurfaceTexture texture) {
    ThreadUtils.checkIsOnMainThread();
    this.texture = texture;
    createEglSurface(texture);
  }

  public void surfaceDestroyed() {
    ThreadUtils.checkIsOnMainThread();
    final CountDownLatch completionLatch = new CountDownLatch(1);
    releaseEglSurface(completionLatch::countDown);
    ThreadUtils.awaitUninterruptibly(completionLatch);
  }

  // Update frame dimensions and report any changes to |rendererEvents|.
  private void updateFrameDimensionsAndReportEvents(VideoFrame frame) {
    synchronized (layoutLock) {
      if (isRenderingPaused) {
        return;
      }
      if (!isFirstFrameRendered) {
        isFirstFrameRendered = true;
        if (rendererEvents != null) {
          rendererEvents.onFirstFrameRendered();
        }
      }
      if (rotatedFrameWidth != frame.getRotatedWidth()
              || rotatedFrameHeight != frame.getRotatedHeight()
              || frameRotation != frame.getRotation()) {
        if (rendererEvents != null) {
          rendererEvents.onFrameResolutionChanged(
                  frame.getBuffer().getWidth(), frame.getBuffer().getHeight(), frame.getRotation());
        }
        rotatedFrameWidth = frame.getRotatedWidth();
        rotatedFrameHeight = frame.getRotatedHeight();
        texture.setDefaultBufferSize(rotatedFrameWidth, rotatedFrameHeight);
        frameRotation = frame.getRotation();
      }
    }
  }
}
