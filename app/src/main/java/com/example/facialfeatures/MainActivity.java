/*
 * FaceSDK Library Sample
 * Copyright (C) 2013 Luxand, Inc. 
 */

package com.example.facialfeatures;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.Type;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;

import com.luxand.FSDK;
import com.luxand.FSDK.*;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String[] PERMISSIONS = new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int REQUEST_CODE = 100;
	private static final String TAG = "MainActivity";
	protected HImage oldpicture;
	private static int RESULT_LOAD_IMAGE = 1;
	protected boolean processing;
	ImageView normalImage;
	// Adding button
	Button processButton;
	private TextView infoText;
	FaceImageView mFaceView;
	ImageView mBlurImageView;
	private Bitmap mRawBitmap;
	private Button mCropButton;


	@Override
	protected void onCreate(Bundle savedInstanceState) {

		processing = true; //prevent user from pushing the button while initializing

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main); //using res/layout/activity_main.xml


		//Check storage permissions
		if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
				PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE);
		}

		infoText =  findViewById(R.id.infoText);
		normalImage = findViewById(R.id.normalImage);
		processButton = findViewById(R.id.process);
		mFaceView = findViewById(R.id.faceImageView);
		mBlurImageView = findViewById(R.id.blurredImage);
		mCropButton = findViewById(R.id.crop);


		try {
			int res = FSDK.ActivateLibrary(getString(R.string.fsdk_license));
			FSDK.Initialize();
			FSDK.SetFaceDetectionParameters(false, false, 100);
			FSDK.SetFaceDetectionThreshold(5);

			if (res == FSDK.FSDKE_OK) {
				infoText.setText("FaceSDK activated\n");
			} else {
				infoText.setText(String.format(Locale.getDefault(), "Error activating FaceSDK: %d",res));
			}
		}
		catch (Exception e) {
			infoText.setText(String.format("exception: %s", e.getMessage() ));
		}

		processing = false;
		AnimatedGifEncoder encoder = new AnimatedGifEncoder();
	}



	// Subclass for async processing of FaceSDK functions.
	// If long-run task runs in foreground - Android kills the process.
	private class DetectFaceInBackground extends AsyncTask<String, Void, String> {
		protected FSDK_Features features;
		protected TFacePosition faceCoords;
		protected String picturePath;
		protected HImage picture;
		protected int result;

		@Override
		protected String doInBackground(String... params) {
			String log = new String();
			picturePath = params[0];
			faceCoords = new TFacePosition();
			faceCoords.w = 0;
			picture = new HImage();
			result = FSDK.LoadImageFromFile(picture, picturePath);
			if (result == FSDK.FSDKE_OK) {
				result = FSDK.DetectFace(picture, faceCoords);
				FSDK.DetectFacialFeatures(picture, new FSDK_Features());
				features = new FSDK_Features();
				if (result == FSDK.FSDKE_OK) {
					//DEBUG
				    //FSDK.SetFaceDetectionThreshold(1);
				    //FSDK.SetFaceDetectionParameters(false, false, 70);
				    //long t0 = System.currentTimeMillis();
				    //for (int i=0; i<10; ++i)
				        //result = FSDK.DetectFacialFeatures(picture, features);
				        result = FSDK.DetectFacialFeaturesInRegion(picture, faceCoords, features);
				    //Log.d("TT", "TIME: " + ((System.currentTimeMillis()-t0)/10.0f));

				}

			}
			processing = false; //long-running code is complete, now user may push the button
			return log;
		}   
		
		@Override
		protected void onPostExecute(String resultstring) {
			TextView tv = (TextView) findViewById(R.id.infoText);
			
			if (result != FSDK.FSDKE_OK)
				return;

			final Bitmap bmp = BitmapFactory.decodeFile(picturePath);
			mFaceView.setImageBitmap(bmp);
						
		    tv.setText(resultstring);
		    
			mFaceView.detectedFace = faceCoords;
			
			if (features.features[0] != null) // if detected
				mFaceView.facial_features = features;
			
			int [] realWidth = new int[1];
			FSDK.GetImageWidth(picture, realWidth);
			mFaceView.faceImageWidthOrig = realWidth[0];
			mFaceView.invalidate(); // redraw, marking up faces and features
			
			if (oldpicture != null)
				FSDK.FreeImage(oldpicture);
			oldpicture = picture;

			//Crop the image
			mCropButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Bitmap srcBitmap = Bitmap.createBitmap(bmp, faceCoords.xc-faceCoords.w/2, faceCoords.yc - faceCoords.w/2, faceCoords.w, faceCoords.w);
					Bitmap newBitmap = cropBitmap(srcBitmap, faceCoords);
					ImageView onTheFlyImg = new ImageView(MainActivity.this);
					onTheFlyImg.setImageBitmap(newBitmap);
					AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
							.setView(onTheFlyImg)
							.setMessage("Cropped Image");
					builder.show();
					normalImage.setImageBitmap(newBitmap);
				}
			});

		}
		
		@Override
		protected void onPreExecute() {
		}
		@Override
		protected void onProgressUpdate(Void... values) {
		}
	}

	private Bitmap cropBitmap(Bitmap src, TFacePosition faceCoords) {

		Bitmap output = Bitmap.createBitmap(src.getWidth(),
				src.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(output);

		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, src.getWidth(), src.getHeight());

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		// canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
		canvas.drawCircle(src.getWidth() / 2, src.getHeight() / 2,src.getWidth() / 2, paint);
		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
		canvas.drawBitmap(src, rect, rect, paint);
//		Bitmap _bmp = Bitmap.createScaledBitmap(output, 60, 60, false);
		//return _bmp;
//		Bitmap resBmp = Bitmap.createBitmap(src, faceCoords.xc-faceCoords.w/2, faceCoords.yc - faceCoords.w/2, faceCoords.w, faceCoords.w);
		return output;
	}
	//end of DetectFaceInBackground class
	
	
	

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()){
			case R.id.menu_load:
				if (!processing) {
					processing = true;
					Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
					startActivityForResult(i, RESULT_LOAD_IMAGE);
				}

				break;
		}
		return super.onOptionsItemSelected(item);

	}

	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
		if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
			Uri selectedImage = data.getData();
			String[] filePathColumn = { MediaStore.Images.Media.DATA };
			Log.d(TAG, "onActivityResult: Image uri: " + selectedImage.toString());

			Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
			cursor.moveToFirst();
			int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
			String picturePath = cursor.getString(columnIndex);

			Log.d(TAG, "onActivityResult: picturePath: " + picturePath);
			cursor.close();

			mRawBitmap = BitmapFactory.decodeFile(picturePath);
			normalImage.setImageBitmap(mRawBitmap);



			processButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Bitmap blurredBitmap = blurBitmap(mRawBitmap, 10, MainActivity.this);
					mBlurImageView.setImageBitmap(blurredBitmap);
				}
			});


			TextView tv = (TextView) findViewById(R.id.infoText);
	        tv.setText("processing...");
			new DetectFaceInBackground().execute(picturePath);
		} else {
			processing = false;
		}
    }

	public static Bitmap blurBitmap(Bitmap bitmap, float radius, Context context) {
		//Create renderscript
		RenderScript rs = RenderScript.create(context);

		//Create allocation from Bitmap
		Allocation allocation = Allocation.createFromBitmap(rs, bitmap);

		Type t = allocation.getType();

		//Create allocation with the same type
		Allocation blurredAllocation = Allocation.createTyped(rs, t);

		//Create script
		ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
		//Set blur radius (maximum 25.0)
		blurScript.setRadius(radius);
		//Set input for script
		blurScript.setInput(allocation);
		//Call script for output allocation
		blurScript.forEach(blurredAllocation);

		//Copy script result into bitmap
		blurredAllocation.copyTo(bitmap);

		//Destroy everything to free memory
		allocation.destroy();
		blurredAllocation.destroy();
		blurScript.destroy();
//		t.destroy();
		rs.destroy();

		return bitmap;
	}

	/**
	 * Generates GIF
	 * cf. https://stackoverflow.com/a/20277649/2009178
	 * @return
	 */
	public byte[] generateGIF() {
		ArrayList<Bitmap> bitmaps = null;/*adapter.getBitmapArray();*/
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		AnimatedGifEncoder encoder = new AnimatedGifEncoder();
		encoder.start(bos);
		for (Bitmap bitmap : bitmaps) {
			encoder.addFrame(bitmap);
		}
		encoder.finish();
		return bos.toByteArray();
	}

//	FileOutputStream outStream = null;
//        try{
//		outStream = new FileOutputStream("/sdcard/generate_gif/test.gif");
//		outStream.write(generateGIF());
//		outStream.close();
//	}catch(Exception e){
//		e.printStackTrace();
//	}
}
