package com.zhiping.alibaba.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


/**
 * Created by huangdaju on 17/7/21.
 */

public class WaveView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = WaveView.class.getSimpleName();
    private final Object mSurfaceLock = new Object();
    private DrawThread mDrawThread;
    private final static long SLEEP_TIME = 16;
    private int width = 0;
    private int height = 0;
    private int amplitude = 0;
    private static final int SAMPLING_SIZE = 64;
    private float[] samplingX;
    private float[] mapX;
    //波形函数的绝对值，用于筛选波峰和交错点
    float absLastV, absCurV, absNextV;


    /**
     * 绘图交叉模式。放在成员变量避免每次重复创建。
     */
    private final Xfermode xfermode = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);

    private final int backGroundColor = Color.rgb(24, 33, 41);
    private final int centerPathColor = Color.argb(64, 255, 255, 255);

    /**
     * 画布中心的高度
     */
    private int centerHeight;


    private double ox = 0;
    private double oy = 150;
    private double t = 0;

    private final Path firstPath = new Path();
    private final Path secondPath = new Path();
    private final Path thirdPath = new Path();


    /**
     * 波峰和两条路径交叉点的记录，包括起点和终点，用于绘制渐变。
     * 通过日志可知其数量范围为7~10个，故这里size取10。
     * <p>
     * 每个元素都是一个float[2]，用于保存xy值
     */
    private final float[][] crestAndCrossPints = new float[10][];

    {//直接分配内存
        for (int i = 0; i < 10; i++) {
            crestAndCrossPints[i] = new float[2];
        }
    }

    /**
     * 用于处理矩形的rectF
     */
    private final RectF rectF = new RectF();

    Paint paint = new Paint();

    {
        paint.setDither(true);
        paint.setAntiAlias(true);
    }

    public WaveView(Context context) {
        this(context, null);
    }

    public WaveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);

    }

    public WaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(this);
        setFocusable(false);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mDrawThread = new DrawThread(holder);
        mDrawThread.setRun(true);
        mDrawThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (mSurfaceLock) {
            mDrawThread.setRun(false);
        }
    }

    private class DrawThread extends Thread {
        private SurfaceHolder mHolder;
        private boolean mIsRun = false;

        public DrawThread(SurfaceHolder holder) {
            super(TAG);
            mHolder = holder;
//            mHolder.addCallback(WaveView.this);
        }

        @Override
        public void run() {
            long startAt = System.currentTimeMillis();
            while (true) {
                synchronized (mSurfaceLock) {
                    if (!mIsRun) {
                        return;
                    }
                    Canvas canvas = mHolder.lockCanvas();
                    if (canvas != null) {
                        doDraw(canvas, startAt);  //这里做真正绘制的事情
                        mHolder.unlockCanvasAndPost(canvas);
                    }
                }
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void setRun(boolean isRun) {
            this.mIsRun = isRun;
        }
    }

    /**
     * 真正实现画图操作
     *
     * @param canvas
     */
    private void doDraw(Canvas canvas,long startAt) {
        if (samplingX == null){
            //赋值基本参数
            width = canvas.getWidth();
            height = canvas.getHeight();
            centerHeight = height >> 1;
            amplitude = width >> 3;//振幅为宽度的1/8
            //初始化采样点和映射
            samplingX = new float[SAMPLING_SIZE + 1];//因为包括起点和终点所以需要+1个位置
            mapX = new float[SAMPLING_SIZE + 1];
            float gap = width / (float) SAMPLING_SIZE;//确定采样点之间的间距
            float x1;
            for (int i =0; i <= SAMPLING_SIZE; i++){
                x1 = i * gap;
                samplingX[i] = x1;
                mapX[i] =  (x1 / width) * 4 - 2;
            }
        }
        //绘制背景
        canvas.drawColor(backGroundColor);

        firstPath.rewind();
        secondPath.rewind();
        thirdPath.rewind();
        firstPath.moveTo(0, centerHeight);
        secondPath.moveTo(0,centerHeight);
        thirdPath.moveTo(0,centerHeight);

        //当前时间的偏移量，通过该偏移量使得每次绘图都向右偏移，让画面动起来
        //如果希望速度快一点，可以调小分母
        float offset = (System.currentTimeMillis() - startAt) / 500F;
        //上一个筛选出的点是波峰还是交错点
        boolean lastIsCrest = false;
        float[] xy;
        //提前申明各种临时参数
        float x, curV = 0,nextV = (float) (amplitude * calcValue(mapX[0], offset)),lastV;
        //筛选出的波峰和交叉点的数量，包括起点和终点
        int crestAndCrossCount = 0;

        for (int i = 0; i <= SAMPLING_SIZE; i++) {

            x = samplingX[i];
            lastV = curV;
            float curX = mapX[i];
            curV = nextV;
            nextV = i < SAMPLING_SIZE ? (float) (amplitude * calcValue(mapX[i+1], offset)) : 0;
            Log.d(TAG, "x " + curX + "Y " + curV + "System.currentTimeMillis() - startAt " + (System.currentTimeMillis() - startAt));
            //连接路径
            firstPath.lineTo(x, centerHeight + curV);
            secondPath.lineTo(x,centerHeight - curV);
            thirdPath.lineTo(x,centerHeight + curV / 5);


            //记录极值点
            absLastV = Math.abs(lastV);
            absCurV = Math.abs(curV);
            absNextV = Math.abs(nextV);

            if (i == 0 || i == SAMPLING_SIZE/*起点终点*/ || (lastIsCrest && absCurV < absLastV && absCurV < absNextV)/*上一个点为波峰，且该点是极小值点*/) {
                xy = crestAndCrossPints[crestAndCrossCount++];
                xy[0] = x;
                xy[1] = 0;
                lastIsCrest = false;
            } else if (!lastIsCrest && absCurV > absLastV && absCurV > absNextV) {/*上一点是交叉点，且该点极大值*/
                xy = crestAndCrossPints[crestAndCrossCount++];
                xy[0] = x;
                xy[1] = curV;
                lastIsCrest = true;
            }
        }
        //连接所有路径到终点
        firstPath.lineTo(width, centerHeight);
        secondPath.lineTo(width, centerHeight);
        thirdPath.lineTo(width, centerHeight);

        //记录layer
        int saveCount = canvas.saveLayer(0, 0, width, height, null, Canvas.ALL_SAVE_FLAG);
        //填充上下两条正弦函数
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(1);
        canvas.drawPath(firstPath, paint);
        canvas.drawPath(secondPath, paint);


        //绘制渐变
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.FILL);
        paint.setXfermode(xfermode);
        float startX, crestY, endX;

        for (int i = 2; i < crestAndCrossCount; i += 2) {
            //每隔两个点可绘制一个矩形。这里先计算矩形的参数
            startX = crestAndCrossPints[i - 2][0];
            crestY = crestAndCrossPints[i - 1][1];
            endX = crestAndCrossPints[i][0];

            //crestY有正有负，无需去计算渐变是从上到下还是从下到上
            paint.setShader(new LinearGradient(0, centerHeight + crestY, 0, centerHeight - crestY, Color.BLUE, Color.GREEN, Shader.TileMode.CLAMP));
            rectF.set(startX, centerHeight + crestY, endX, centerHeight - crestY);
            canvas.drawRect(rectF, paint);
        }
        //清理一下
        paint.setShader(null);
        paint.setXfermode(null);
        //叠加layer，因为使用了SRC_IN的模式所以只会保留波形渐变重合的地方
        canvas.restoreToCount(saveCount);

        //绘制上弦线
        paint.setStrokeWidth(3);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLUE);
        canvas.drawPath(firstPath, paint);

        //绘制下弦线
        paint.setColor(Color.GREEN);
        canvas.drawPath(secondPath, paint);

        //绘制中间线
        paint.setColor(centerPathColor);
        canvas.drawPath(thirdPath, paint);
    }


    /**
     * 计算波形函数中x对应的y值
     *
     * @param mapX   换算到[-2,2]之间的x值
     * @param offset 偏移量
     * @return
     */
    private double calcValue(float mapX, float offset) {
        offset %= 2;
        double sinFunc = Math.sin(0.75 * Math.PI * mapX - offset * Math.PI);
        double recessionFunc = Math.pow(4 / (4 + Math.pow(mapX, 4)), 2.5);
        return sinFunc * recessionFunc;
    }
}