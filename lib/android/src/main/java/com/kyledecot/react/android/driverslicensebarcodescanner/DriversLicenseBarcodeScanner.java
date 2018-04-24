package com.kyledecot.react.android.driverslicensebarcodescanner;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ThemedReactContext;
import com.manateeworks.BarcodeScanner;
import com.manateeworks.CameraManager;
import com.manateeworks.MWParser;
import com.manateeworks.BarcodeScanner.MWResult;

import java.io.IOException;

public class DriversLicenseBarcodeScanner extends SurfaceView implements SurfaceHolder.Callback {

    public static final boolean USE_MWANALYTICS = false;
    public static final boolean PDF_OPTIMIZED = false;

    private enum State {
        STOPPED, PREVIEW, DECODING
    }

    private enum OverlayMode {
        OM_IMAGE, OM_MWOVERLAY, OM_NONE
    }

    State state = State.STOPPED;

    /* Parser */
    /*
     * MWPARSER_MASK - Set the desired parser type Available options:
     * MWParser.MWP_PARSER_MASK_ISBT MWParser.MWP_PARSER_MASK_AAMVA
     * MWParser.MWP_PARSER_MASK_IUID MWParser.MWP_PARSER_MASK_HIBC
     * MWParser.MWP_PARSER_MASK_SCM MWParser.MWP_PARSER_MASK_NONE
     */
    public static final int MWPARSER_MASK = MWParser.MWP_PARSER_MASK_NONE;

    public static final int USE_RESULT_TYPE = BarcodeScanner.MWB_RESULT_TYPE_MW;

    public static final DriversLicenseBarcodeScanner.OverlayMode OVERLAY_MODE = DriversLicenseBarcodeScanner.OverlayMode.OM_MWOVERLAY;

    // !!! Rects are in format: x, y, width, height !!!
    public static final Rect RECT_LANDSCAPE_1D = new Rect(3, 20, 94, 60);
    public static final Rect RECT_LANDSCAPE_2D = new Rect(20, 5, 60, 90);
    public static final Rect RECT_PORTRAIT_1D = new Rect(20, 3, 60, 94);
    public static final Rect RECT_PORTRAIT_2D = new Rect(20, 5, 60, 90);
    public static final Rect RECT_FULL_1D = new Rect(3, 3, 94, 94);
    public static final Rect RECT_FULL_2D = new Rect(20, 5, 60, 90);
    public static final Rect RECT_DOTCODE = new Rect(30, 20, 40, 60);

    private static final String MSG_CAMERA_FRAMEWORK_BUG = "Sorry, the Android camera encountered a problem: ";

    public static final int ID_AUTO_FOCUS = 0x01;
    public static final int ID_DECODE = 0x02;
    public static final int ID_RESTART_PREVIEW = 0x04;
    public static final int ID_DECODE_SUCCEED = 0x08;
    public static final int ID_DECODE_FAILED = 0x10;

    private Handler decodeHandler;
    private boolean hasSurface;
    private String package_name;

    private int activeThreads = 0;
    public static int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private boolean surfaceChanged = false;

    private ReactApplicationContext appContext;

    public DriversLicenseBarcodeScanner(ThemedReactContext reactContext, ReactApplicationContext appContext) {
        super(reactContext);

        this.appContext = appContext;
    }

    private void initCamera() {
        final Activity activity = this.appContext.getCurrentActivity();

        if (activity == null) {
            Log.e("KYLEDECOT", "No Activity Yet!");
            return;
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            /* WHEN TARGETING ANDROID 6 OR ABOVE, PERMISSION IS NEEDED */
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.CAMERA)) {

                new AlertDialog.Builder(activity).setMessage("You need to allow access to the Camera")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(activity,
                                        new String[]{Manifest.permission.CAMERA}, 12322);
                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // onBackPressed();
                    }
                }).create().show();
            } else {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA},
                        12322);
            }
        } else {
            try {
                // Select desired camera resoloution. Not all devices
                // supports all
                // resolutions, closest available will be chosen
                // If not selected, closest match to screen resolution will
                // be
                // chosen
                // High resolutions will slow down scanning proccess on
                // slower
                // devices

                if (MAX_THREADS > 2) {
                    CameraManager.setDesiredPreviewSize(1280, 720);
                } else {
                    CameraManager.setDesiredPreviewSize(800, 480);
                }

                CameraManager.get().openDriver(getHolder(), true);

            } catch (IOException ioe) {
//                 displayFrameworkBugMessageAndExit(ioe.getMessage());
                return;
            } catch (RuntimeException e) {
                // Barcode Scanner has seen crashes in the wild of this
                // variety:
                // java.?lang.?RuntimeException: Fail to connect to camera
                // service
//                 displayFrameworkBugMessageAndExit(e.getMessage());
                return;
            }

            CameraManager.get().startPreview();
            restartPreviewAndDecode();
        }
    }

    public Handler getDecodeHandler() {
        return decodeHandler;
    }

    private void restartPreviewAndDecode() {
        if (state == State.STOPPED) {
            state = State.PREVIEW;

            CameraManager.get().requestPreviewFrame(getDecodeHandler(), ID_DECODE);
//             CameraManager.get().requestAutoFocus(getDecodeHandler(), ID_AUTO_FOCUS);
        }
    }

  public void onResume() {
      if (hasSurface) {
          Log.i("Init Camera", "On resume");
          initCamera();
      } else if (getHolder() != null) {
          getHolder().addCallback(this);
      }
      
      int registerResult = BarcodeScanner.MWBregisterSDK("umDQbMBzRwwXVuRPBtLbzcYfPd0SVfpSoq3wVebSGtw=", this.appContext.getCurrentActivity());

      switch (registerResult) {
          case BarcodeScanner.MWB_RTREG_OK:
              Log.i("MWBregisterSDK", "Registration OK");
              break;
          case BarcodeScanner.MWB_RTREG_INVALID_KEY:
              Log.e("MWBregisterSDK", "Registration Invalid Key");
              break;
          case BarcodeScanner.MWB_RTREG_INVALID_CHECKSUM:
              Log.e("MWBregisterSDK", "Registration Invalid Checksum");
              break;
          case BarcodeScanner.MWB_RTREG_INVALID_APPLICATION:
              Log.e("MWBregisterSDK", "Registration Invalid Application");
              break;
          case BarcodeScanner.MWB_RTREG_INVALID_SDK_VERSION:
              Log.e("MWBregisterSDK", "Registration Invalid SDK Version");
              break;
          case BarcodeScanner.MWB_RTREG_INVALID_KEY_VERSION:
              Log.e("MWBregisterSDK", "Registration Invalid Key Version");
              break;
          case BarcodeScanner.MWB_RTREG_INVALID_PLATFORM:
              Log.e("MWBregisterSDK", "Registration Invalid Platform");
              break;
          case BarcodeScanner.MWB_RTREG_KEY_EXPIRED:
              Log.e("MWBregisterSDK", "Registration Key Expired");
              break;
          default:
              Log.e("MWBregisterSDK", "Registration Unknown Error");
              break;
      }

      BarcodeScanner.MWBsetDirection(BarcodeScanner.MWB_SCANDIRECTION_HORIZONTAL);
      BarcodeScanner.MWBsetActiveCodes(BarcodeScanner.MWB_CODE_MASK_PDF);
      BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_PDF, RECT_LANDSCAPE_1D);
      BarcodeScanner.MWBsetLevel(2);
      BarcodeScanner.MWBsetResultType(USE_RESULT_TYPE);

      Activity activity = appContext.getCurrentActivity();

      CameraManager.init(activity);

      hasSurface = false;
      state = State.STOPPED;
      decodeHandler = new Handler(new Handler.Callback() {

          @Override
          public boolean handleMessage(Message msg) {
              switch (msg.what) {
                  case ID_DECODE:
                      decode((byte[]) msg.obj, msg.arg1, msg.arg2);
                      break;

                  case ID_AUTO_FOCUS:
                      if (state == State.PREVIEW || state == State.DECODING) {
                          CameraManager.get().requestAutoFocus(decodeHandler, ID_AUTO_FOCUS);
                      }
                      break;
                  case ID_RESTART_PREVIEW:
                      restartPreviewAndDecode();
                      break;
                  case ID_DECODE_SUCCEED:
                      state = State.STOPPED;
                      handleDecode((MWResult) msg.obj);
                      break;
                  case ID_DECODE_FAILED:
                      break;
              }
              return false;
          }
      });
  }

  public void onPause() {
      Log.e("KYLEDECOT", "onPause");
  }

  public void onDestroy() {
      Log.e("KYLEDECOT", "onDestroy");
  }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        initCamera();
        surfaceChanged = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    private void decode(final byte[] data, final int width, final int height) {
        if (activeThreads >= MAX_THREADS || state == State.STOPPED) {
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                activeThreads++;

                byte[] rawResult = BarcodeScanner.MWBscanGrayscaleImage(data, width, height);

                if (state == State.STOPPED) {
                    activeThreads--;
                    return;
                }

                MWResult mwResult = null;

                if (rawResult != null && BarcodeScanner.MWBgetResultType() == BarcodeScanner.MWB_RESULT_TYPE_MW) {

                    BarcodeScanner.MWResults results = new BarcodeScanner.MWResults(rawResult);

                    if (results.count > 0) {
                        mwResult = results.getResult(0);
                        rawResult = mwResult.bytes;
                    }

                } else if (rawResult != null
                        && BarcodeScanner.MWBgetResultType() == BarcodeScanner.MWB_RESULT_TYPE_RAW) {
                    mwResult = new MWResult();
                    mwResult.bytes = rawResult;
                    mwResult.text = rawResult.toString();
                    mwResult.type = BarcodeScanner.MWBgetLastType();
                    mwResult.bytesLength = rawResult.length;
                }

                if (mwResult != null) {
                    state = State.STOPPED;
                    Message message = Message.obtain(getDecodeHandler(), ID_DECODE_SUCCEED, mwResult);
                    message.arg1 = mwResult.type;
                    message.sendToTarget();
                } else {
                    Message message = Message.obtain(getDecodeHandler(), ID_DECODE_FAILED);
                    message.sendToTarget();
                }

                activeThreads--;
            }
        }).start();
    }

    public void handleDecode(MWResult result) {
        String barcode = result.text;

        Log.e("KYLEDECOT", barcode);
    }
}
