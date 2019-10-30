package com.groo.bullethell;

import android.graphics.RectF;

class Bullet {

    //these are the member variable (fields)
    //They all have the m prefix
    //They are all private because access is not required
    private RectF mRect;
    private float mXVelocity;
    private float mYVelocity;
    private float mWidth;
    private float mHeight;

    //This is the constructor method.
    public Bullet(int screenX) {

        //Make the ball square and 1% of the screen width
        mWidth = screenX/100;
        mHeight = screenX/100;

        //Initialize the RectF with 0, 0, 0, 0
        //We do it here because we only want to do it once.
        //We will initialize the detail at the start of each game.
        mRect = new RectF();
        mXVelocity = (screenX/5);
        mYVelocity = (screenX/5);

    }

    //Return a reference to mRect to PongGame
    public RectF getRect(){
        return mRect;
    }

    //Update the ball position. Called each frame/loop
    public void update(long fps){
        //Move the ball based upon the horizontal and vertical speed and the current frame rate

        //Move the top left corner
        mRect.left = mRect.left + (mXVelocity / fps);
        mRect.top = mRect.top + (mYVelocity / fps);

        //Match up the botton right corner based on the size of the ball
        mRect.right = mRect.left + mWidth;
        mRect.bottom = mRect.top - mHeight;
    }

    //Reverse the vertical direction of travel
    public void reverseYVelocity(){
        mYVelocity = -mYVelocity;
    }

    //Reverse the horizontal direction of travel
    public void reverseXVelocity(){
        mXVelocity = -mXVelocity;
    }

    public void spawn(int pX, int pY, int vX, int vY){
        //Spawn the bullet at the location passed in as parameters
        mRect.left = pX;
        mRect.top = pY;
        mRect.right = pX + mWidth;
        mRect.bottom = pY + mHeight;

        //Head away from the player
        mXVelocity = mXVelocity * vX;
        mYVelocity = mYVelocity * vY;
    }

}
