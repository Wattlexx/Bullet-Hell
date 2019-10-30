package com.groo.bullethell;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.IOException;
import java.util.Random;

class BulletHellGame extends SurfaceView implements Runnable{

    public boolean mDebugging = true;

    //Objects fot the game loop/thread
    private Thread mGameThread = null;
    private volatile boolean mPlaying;
    private boolean mPaused = true;

    //These objects are needed to do the drawing
    private SurfaceHolder mOurHolder;
    private Canvas mCanvas;
    private Paint mPaint;

    //How many frames per second did we get?
    private long mFPS;

    //The number of milliseconds in a second
    private final int MILLIS_IN_SECOND = 1000;

    //Holds the resolution of the screen
    private int mScreenX;
    private int mScreenY;

    //How big will the text be?
    private int mFontSize;
    private int mFontMargin;

    //All these are for playing sounds
    private SoundPool mSP;
    private int mBeepID = -1;
    private int mTeleportID = -1;

    private Bullet [] mBullets = new Bullet[1000];
    private int mNumBullets = 0;
    private int mSpawnRate = 1;

    private Random mRandomX = new Random();
    private Random mRandomY = new Random();

    private Bob mBob;
    private boolean mHit = false;
    private int mNumHits;
    private int mShield = 10;

    //Let's time the game
    private long mStartGameTime;
    private long mBestGameTime;
    private long mTotalGameTime;


    public BulletHellGame(Context context, int x, int y){
        super(context);

        //Initialize these two members/fields with the values passed in as parameters
        mScreenX = x;
        mScreenY = y;

        //Font is 5% of screen width
        mFontSize = mScreenX / 20;
        //Margin is 2% of the screen width
        mFontMargin = mScreenX / 50;

        //Initialize the objects ready for drawing with get holder is a method of SurfaceView
        mOurHolder = getHolder();
        mPaint = new Paint();


        //Prepare the SoundPool instance. Depending on your version of Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            AudioAttributes audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            mSP = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(audioAttributes).build();
        }
        else{
            mSP = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
        }

        //Open each of the sound files in turn and load them into RAM ready to play
        //The try-Catch blocks handle when this fails and is required
        try{
            AssetManager assetManager = context.getAssets();
            AssetFileDescriptor descriptor;

            descriptor = assetManager.openFd("beep.ogg");
            mBeepID = mSP.load(descriptor, 0);

            descriptor = assetManager.openFd("teleport.ogg");
            mTeleportID = mSP.load(descriptor, 0);

        }catch (IOException e) {
            Log.d ("error", "failed to load sound files");
        }

        for (int i = 0; i < mBullets.length; i++){
            mBullets[i] = new Bullet(mScreenX);
        }

        mBob = new Bob(context, mScreenX, mScreenY);
        //Everything is ready so start the game
        startGame();
    }



    public void startGame(){
        mNumHits = 0;
        mNumBullets = 0;
        mHit = false;

        //Did the player survive longer than previously
        if (mTotalGameTime > mBestGameTime){
            mBestGameTime = mTotalGameTime;
        }
    }

    private void spawnBullet(){
        //Add one to the number of bullets
        mNumBullets++;

        //Where to spawn the next bullet and in which direction should it travel
        int spawnX;
        int spawnY;
        int velocityX;
        int velocityY;

        //Dont spawn to close to Bob
        if (mBob.getRect().centerX() < mScreenX/2){
            //Bob is ont he left. Sown  bullet on the right
            spawnX = mRandomX.nextInt(mScreenX/2) + mScreenX/2;
            //Head right
            velocityX = 1;
        }
        else{
            //Bob is on the right. Spawn bullet on the left
            spawnX = mRandomX.nextInt(mScreenX /2);
            //Head left
            velocityX = -1;
        }

        //Dont spawn to close to bob
        if( mBob.getRect().centerY() < mScreenY/2){
            //Bob is on the top. Spawn the bullet on the bottom
            spawnY = mRandomY.nextInt(mScreenY/2)+mScreenY/2;
            //head down
            velocityY = 1;
        }
        else {
            //Bob is on the botton. Spawn bullet on the top
            spawnY = mRandomY.nextInt(mScreenY / 2);
            //head up
            velocityY = -1;
        }

        //Spawn the bullet
        mBullets[mNumBullets - 1].spawn(spawnX, spawnY, velocityX, velocityY);
    }


    //When we start the thread with: mGameThread.start(); the run method is continuously called by android because
    //we implemented the Runnable interface. Calling mGameThread.join(); will stop the thread
    @Override
    public void run(){

        //mPlaying gives us finer control rather than just relying on th calls to run
        //mPlaying must be true AND the thread running for the main loop to execute
        while (mPlaying){

            //What time is it now at the start of the loop?
            long frameStartTime = System.currentTimeMillis();

            //Provided the game isnt paused call the update method
            if (!mPaused){
                update();
                //now the bat and ball are in their new positions
                //we can see if there have been any collisions
                detectCollisions();
            }
            //The movement has been handled and collisions detected now we can draw the scene.
            draw();

            //how long did this frame/loop take?
            //Store the answer in timeThisFrame
            long timeThisFrame = System.currentTimeMillis() - frameStartTime;

            //Make sure timeThisFrame is at least 1 millisecond because accidentally dividing by zero crashes the game
            if (timeThisFrame >= 1){
                //Store the current frame rate in mFPS

                //ready to pass to the update methods of mBat and mBall next frame/loop
                mFPS = MILLIS_IN_SECOND/timeThisFrame;
            }
        }

    }

    private void update(){
        for (int i = 0; i < mNumBullets; i++){
            mBullets[i].update(mFPS);
        }
    }

    private void detectCollisions(){
        //Has a bullet collided with a wall?
        //Loop through wach active bullet in turn
        for (int i = 0; i < mNumBullets; i++){
            if (mBullets[i].getRect().bottom > mScreenY){
                mBullets[i].reverseYVelocity();
            }
            else if (mBullets[i].getRect().top < 0){
                mBullets[i].reverseYVelocity();
            }
            else if (mBullets[i].getRect().left < 0){
                mBullets[i].reverseXVelocity();
            }
            else if (mBullets[i].getRect().right > mScreenX){
                mBullets[i].reverseXVelocity();
            }
        }

        //Has a bullet hit Bob? CHeck each bullet for an intersection with bob's RectF
        for (int i = 0; i < mNumBullets; i++){
            if (RectF.intersects(mBullets[i].getRect(), mBob.getRect())){
                //Bob has been hit
                mSP.play(mBeepID, 1, 1, 0, 0, 1);

                //This flags that a hit occured sot hat the draw mwthod knows as well
                mHit = true;

                //Rebound the bullet that collided
                mBullets[i].reverseXVelocity();
                mBullets[i].reverseYVelocity();

                //keep track of the number od hits
                mNumHits++;
                if (mNumHits == mShield){
                    mPaused = true;
                    mTotalGameTime = System.currentTimeMillis() - mStartGameTime;

                    startGame();
                }
            }
        }
    }


    //draw the game objects and the HUD
    private void draw() {

        if (mOurHolder.getSurface().isValid()) {

            //Lock the canvas (graphics memory) ready to draw
            mCanvas = mOurHolder.lockCanvas();

            //Fill the screen with a solid color
            mCanvas.drawColor(Color.argb(255, 243, 111, 36));

            //Choose a color to paint with
            mPaint.setColor(Color.argb(255, 255, 255, 255));

            //All the drawing code will go here
            for (int i = 0; i < mNumBullets; i++){
                mCanvas.drawRect(mBullets[i].getRect(), mPaint);
            }

            mCanvas.drawBitmap(mBob.getBitmap(), mBob.getRect().left, mBob.getRect().top, mPaint);
            mPaint.setTextSize(mFontSize);

            mCanvas.drawText("Bullets: " + mNumBullets + " Shield: " + (mShield - mNumHits) + " Best Time: " + mBestGameTime/MILLIS_IN_SECOND, mFontMargin, mFontSize, mPaint);

            //Dont draw the current time when paused
            if (!mPaused){
                mCanvas.drawText("Seconds Survived: " + ((System.currentTimeMillis() - mStartGameTime)/MILLIS_IN_SECOND), mFontMargin, mFontMargin*27, mPaint);
            }

            if (mDebugging) {
                printDebuggingText();
            }

            //Display the drawing on screen
            //unlockCanvasAndPost is a method of SurfaceView
            mOurHolder.unlockCanvasAndPost(mCanvas);
        }
    }

    //Handle all the screen touches
    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){

        switch (motionEvent.getAction() & MotionEvent.ACTION_MASK){
            case MotionEvent.ACTION_DOWN:
                if(mPaused){
                    mStartGameTime = System.currentTimeMillis();
                    mPaused = false;
                }
                if(mBob.teleport(motionEvent.getX(), motionEvent.getY())){
                    mSP.play(mTeleportID, 1, 1, 0, 0, 1);
                }
                break;
            case MotionEvent.ACTION_UP:
                mBob.setTeleportAvailable();
                spawnBullet();
                break;
        }
        return true;
    }


    private void printDebuggingText(){
        int debugSize = 35;
        int debugStart = 150;
        mPaint.setTextSize(debugSize);
        mCanvas.drawText("FPS: " + mFPS, 10, debugStart + debugSize, mPaint);

        mCanvas.drawText("Bob left: " + mBob.getRect().left, 10, debugStart + debugSize*2, mPaint);
        mCanvas.drawText("Bob top: " + mBob.getRect().top, 10, debugStart + debugSize*3, mPaint);
        mCanvas.drawText("Bob right: " + mBob.getRect().right, 10, debugStart + debugSize*4, mPaint);
        mCanvas.drawText("Bob bottom: " + mBob.getRect().bottom, 10, debugStart + debugSize*5, mPaint);
        mCanvas.drawText("Bob centerX: " + mBob.getRect().centerX(), 10, debugStart + debugSize*6, mPaint);
        mCanvas.drawText("Bob centerY: " + mBob.getRect().centerY(), 10, debugStart + debugSize*7, mPaint);


    }

    //This method is called by PongActivity when the player quits the game
    public void pause(){
        //Set mPlaying to false stopping the thread isnt always instant
        mPlaying = false;

        try {
            //Stop the thread
            mGameThread.join();
        }catch(InterruptedException e){
            Log.e("Error:", "joining threads");
        }
    }

    //This method is called by pongActivity when the player starts the game
    public void resume(){
        mPlaying = true;
        //Initialize the instance of thread
        mGameThread = new Thread(this);
        //Start the thread
        mGameThread.start();
    }
}
