package com.arash.space;

import java.io.IOException;
import java.lang.reflect.Field;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

import com.threed.jpct.Camera;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Loader;
import com.threed.jpct.Logger;
import com.threed.jpct.Matrix;
import com.threed.jpct.Object3D;
import com.threed.jpct.Primitives;
import com.threed.jpct.RGBColor;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.World;
import com.threed.jpct.util.MemoryHelper;

public class MainActivity extends Activity {

	// Used to handle pause and resume...
	private static MainActivity master = null;

	private GLSurfaceView mGLView;
	private MyRenderer renderer = null;
	private FrameBuffer fb = null;
	private World world = null;
	private RGBColor back = new RGBColor(50, 50, 100);

	private float touchTurn = 0;
	private float touchTurnUp = 0;
	private float move = 0;

	private float xpos = -1;
	private float ypos = -1;

	private int fps = 0;

	
	private Object3D cube = null;
	private Object3D earth = null;
	private Object3D spaceship = null;

	private Light sun = null;
	
	private Camera cam = null;

	protected void onCreate(Bundle savedInstanceState) {
		
		// Remove title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		Logger.log("onCreate");

		if (master != null) {
			copy(master);
		}

		super.onCreate(savedInstanceState);
		mGLView = new GLSurfaceView(getApplication());

		mGLView.setEGLConfigChooser(new GLSurfaceView.EGLConfigChooser() {
			public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
				// Ensure that we get a 16bit framebuffer. Otherwise, we'll fall
				// back to Pixelflinger on some device (read: Samsung I7500)
				int[] attributes = new int[] { EGL10.EGL_DEPTH_SIZE, 16,
						EGL10.EGL_NONE };
				EGLConfig[] configs = new EGLConfig[1];
				int[] result = new int[1];
				egl.eglChooseConfig(display, attributes, configs, 1, result);
				return configs[0];
			}
		});

		renderer = new MyRenderer();
		mGLView.setRenderer(renderer);
		setContentView(mGLView);
		
		buildLayoutes();
	}

	private void buildLayoutes() {
		Button forwardBtn = new Button(getApplication());
		forwardBtn.setText("F");
		forwardBtn.setY((int) (getWindowManager().getDefaultDisplay().getHeight() - (forwardBtn.getTextSize() * 4)));
		forwardBtn.setX((int) (getWindowManager().getDefaultDisplay().getWidth() - (forwardBtn.getTextSize()*4)));
		forwardBtn.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					move =  - (int) (event.getDownTime()/10000000);
					Logger.log(" " + move);
				}
				return false;
			}
		});
		addContentView(forwardBtn, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		
		Button backwardBtn = new Button(getApplication());
		backwardBtn.setText("B");
		backwardBtn.setY((int) (getWindowManager().getDefaultDisplay().getHeight() - (backwardBtn.getTextSize() * 4)));
		backwardBtn.setX((int) (backwardBtn.getTextSize()));
		backwardBtn.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					move =  (int) (event.getDownTime()/10000000);
					Logger.log(" " + move);
				}
				return false;
			}
		});
		addContentView(backwardBtn, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		mGLView.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mGLView.onResume();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	private void copy(Object src) {
		try {
			Logger.log("Copying data from master Activity!");
			Field[] fs = src.getClass().getDeclaredFields();
			for (Field f : fs) {
				f.setAccessible(true);
				f.set(this, f.get(src));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public boolean onTouchEvent(MotionEvent me) {

		if (me.getAction() == MotionEvent.ACTION_DOWN) {
			xpos = me.getX();
			ypos = me.getY();
			return true;
		}

		if (me.getAction() == MotionEvent.ACTION_UP) {
			xpos = -1;
			ypos = -1;
			touchTurn = 0;
			touchTurnUp = 0;
			return true;
		}

		if (me.getAction() == MotionEvent.ACTION_MOVE) {
			float xd = me.getX() - xpos;
			float yd = me.getY() - ypos;

			xpos = me.getX();
			ypos = me.getY();

			touchTurn = xd / -100f;
			touchTurnUp = yd / -100f;
			return true;
		}

		try {
			Thread.sleep(15);
		} catch (Exception e) {
			// No need for this...
		}

		return super.onTouchEvent(me);
	}

	protected boolean isFullscreenOpaque() {
		return true;
	}

	class MyRenderer implements GLSurfaceView.Renderer {

		private long time = System.currentTimeMillis();

		public MyRenderer() {
		}

		public void onSurfaceChanged(GL10 gl, int w, int h) {
			if (fb != null) {
				fb.dispose();
			}
			fb = new FrameBuffer(gl, w, h);

			if (master == null) {

				world = new World();
				world.setAmbientLight(20, 20, 20);

				sun = new Light(world);
				sun.setIntensity(250, 250, 250);

				// Create a texture out of the icon...:-)
//				Texture texture = new Texture(BitmapHelper.rescale(
//						BitmapHelper.convert(getResources().getDrawable(
//								R.drawable.icon)), 64, 64));
//				TextureManager.getInstance().addTexture("texture", texture);

//				cube = Primitives.getCube(10);
////				cube.calcTextureWrapSpherical();
////				cube.setTexture("texture");
//				cube.setAdditionalColor(new RGBColor(180, 70, 1));
//				cube.strip();
//				cube.build();
//
//				world.addObject(cube);
				
				
				earth = Primitives.getSphere(120, 90);

				earth.setAdditionalColor(new RGBColor(10, 10, 200));
				earth.strip();
				earth.build();
				earth.translate(500, 0, 0);
				
				world.addObject(earth);
				
				Object3D[] model;
				try {
					model = Loader.load3DS(getAssets().open("spaceshipwhite.3ds"), 0.5f);
					spaceship = new Object3D(0);
			        Object3D temp = null;
			        for (int i = 0; i < model.length; i++) {
			            temp = model[i];
			            temp.setCenter(SimpleVector.ORIGIN);
			            temp.rotateX((float)( -.5*Math.PI));
			            temp.rotateMesh();
			            temp.setRotationMatrix(new Matrix());
			            spaceship = Object3D.mergeObjects(spaceship, temp);
			            spaceship.build();
			        }
			        
			        world.addObject(spaceship); 
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				

				cam = world.getCamera();
				cam.moveCamera(Camera.CAMERA_MOVEIN, 150);
//				cam.moveCamera(Camera.CAMERA_MOVEUP, 200);
				spaceship.align(cam);
				spaceship.translate(0.3718221f, 0.12529162f, -0.026728213f);
				cam.lookAt(spaceship.getTransformedCenter());

				SimpleVector sv = new SimpleVector();
				sv.set(spaceship.getTransformedCenter());
				sv.y -= 5000;
				sv.z -= 5000;
				sun.setPosition(sv);
				MemoryHelper.compact();

				if (master == null) {
					Logger.log("Saving master Activity!");
					master = MainActivity.this;
				}
			}
		}

		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		}

		public void onDrawFrame(GL10 gl) {
			if (touchTurn != 0) {
//				cam.rotateY(touchTurn);
				cam.moveCamera(Camera.CAMERA_MOVEOUT, 150);
//				cam.moveCamera(Camera.CAMERA_MOVEUP, -200);
				SimpleVector rotationAxis = cam.getYAxis();
				spaceship.rotateAxis(rotationAxis, touchTurn);
				Logger.log("x: " + spaceship.getTransformedCenter().x);
				Logger.log("y: " + spaceship.getTransformedCenter().y);
				Logger.log("z: " + spaceship.getTransformedCenter().z);

				
				cam.rotateAxis(rotationAxis, touchTurn);
				cam.moveCamera(Camera.CAMERA_MOVEIN, 150);
//				cam.moveCamera(Camera.CAMERA_MOVEUP, 200);
				cam.lookAt(spaceship.getTransformedCenter());
				touchTurn = 0;
			}

			if (touchTurnUp != 0) {
//				cam.rotateX(touchTurnUp);
				cam.moveCamera(Camera.CAMERA_MOVEOUT, 150);
//				cam.moveCamera(Camera.CAMERA_MOVEUP, -200);
				SimpleVector rotationAxis = cam.getXAxis();
				spaceship.rotateAxis(rotationAxis, touchTurnUp);
				cam.rotateAxis(rotationAxis, touchTurnUp);
				cam.moveCamera(Camera.CAMERA_MOVEIN, 150);
//				cam.moveCamera(Camera.CAMERA_MOVEUP, 200);
				cam.lookAt(spaceship.getTransformedCenter());
				touchTurnUp = 0;
			}
			
			if (move != 0) {
				cam.moveCamera(Camera.CAMERA_MOVEOUT, move);
				move = 0;
			}

			fb.clear(back);
			world.renderScene(fb);
			world.draw(fb);
			fb.display();

			if (System.currentTimeMillis() - time >= 1000) {
				Logger.log(fps + "fps");
				fps = 0;
				time = System.currentTimeMillis();
			}
			fps++;
		}
	}

}
