package net.nikcain.showmehills2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

public final class HillCanvas extends View {

    public static final float TEXT_SIZE_DECREMENT = 1;
    public static final float TEXT_SIZE_MIN = 7;
    public static final int ALPHA_LINE_MAX = 255;
    public static final int ALPHA_DECREMENT = 10;
    public static final int ALPHA_LINE_MIN = 50;
    public static final int ALPHA_LABEL_MAX = 255;
    // constants
    public static final int ALPHA_STROKE_MIN = 200;
    public static final int ALPHA_LABEL_MIN = 180;
    public float textsize = 35f;
    public int mMainTextSize = 35;
    boolean showdir = false;
    boolean showdist = false;
    private Paint strokePaint = new Paint();
    private Paint textPaint = new Paint();
    private Paint paint = new Paint();
    private Paint transpRedPaint = new Paint();
    private Paint variationPaint = new Paint();

    private Paint settingPaint = new Paint();
    private Paint settingPaint2 = new Paint();

    int subwidth;
    int subheight;
    int gap;
    int txtgap;
    int vtxtgap;
    RectF fovrect;


    class tmpHill {
        Hills h;
        double ratio;
        int toppt;
    };

    ArrayList<tmpHill> hillsToPlot;

    public class HillMarker {

        public HillMarker(int id, Rect loc) { location = loc; hillid=id; }
        public Rect location;
        public int hillid;
    }

    ArrayList<HillMarker> mMarkers = new ArrayList<HillMarker>();
    MainActivity mainact;
    Context mycontext;
    public HillCanvas(Context context, AttributeSet attrs)  {

        super(context, attrs);
        mycontext = context;
        }

    public void setvars(MainActivity _mainactivity)
    {

        mainact = _mainactivity;
/*
        CameraManager manager = (CameraManager) mycontext.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                float[] focus_lengths = characteristics.get(characteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS  );
                SizeF physize = characteristics.get(characteristics.SENSOR_INFO_PHYSICAL_SIZE);
                float hypot = (float) Math.sqrt(physize.getHeight() * physize.getHeight() + physize.getWidth() * physize.getWidth());
                double efl = focus_lengths[0] * hypot;
                mainact.hfov = (float) (2 * Math.atan(hypot / 2 * efl));
                int test =1;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
        }

 */
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setStrokeWidth(4);
        strokePaint.setTextAlign(Paint.Align.CENTER);
        strokePaint.setTypeface(Typeface.DEFAULT_BOLD);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(10);

        paint.setARGB(255, 255, 255, 255);
        transpRedPaint.setARGB(100,255,0,0);

        subwidth = (int)(mainact.scrwidth*0.7);
        subheight = (int)(mainact.scrheight*0.7);
        gap = (mainact.scrwidth - subwidth) / 2;
        txtgap = gap+(subwidth/30);
        vtxtgap = (int)(subheight / 10);

        hillsToPlot = new ArrayList<tmpHill>();
        fovrect = new RectF(gap,vtxtgap,mainact.scrwidth-gap,vtxtgap*11);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mainact.isCalibrated)
        {
            drawCalibrationInstructions(canvas);
            return;
        }

        ArrayList<Hills> localhills = mainact.myDbHelper.localhills;

        int topPt = calculateHillsCanFitOnCanvas((int)(mainact.scrheight/1.6), localhills);

        drawHillLabelLines(canvas, topPt);

        drawHillLabelText(canvas, topPt);

        drawLocationAndOrientationStatus(canvas);

        //drawSettingsButton(canvas);

        super.onDraw(canvas);
    }

    private int calculateHillsCanFitOnCanvas(int topPt, ArrayList<Hills> localhills) {
        Float drawtextsize = textsize;
        hillsToPlot.clear();
        mMarkers.clear();
        for (int h = 0; h < localhills.size() && topPt > 0; h++)
        {
            Hills h1 = localhills.get(h);

            // this is the angle of the peak from our line of sight
            double offset = mainact.fd.getDirection() - h1.direction;
            double offset2 = mainact.fd.getDirection() - (360+h1.direction);
            double offset3 = 360+mainact.fd.getDirection() - (h1.direction);
            double ratio = 0;
            // is it in our line of sight
            boolean inlineofsight=false;
            if (Math.abs(offset) * 2 < mainact.hfov)
            {
                ratio = offset / mainact.hfov * -1;
                inlineofsight = true;
            }
            if (Math.abs(offset2) * 2 < mainact.hfov)
            {
                ratio = offset2 / mainact.hfov * -1;
                inlineofsight = true;
            }
            if (Math.abs(offset3) * 2 < mainact.hfov)
            {
                ratio = offset3 / mainact.hfov * -1;
                inlineofsight = true;
            }
            if (inlineofsight)
            {
                tmpHill th = new tmpHill();

                th.h = h1;
                th.ratio = ratio;
                th.toppt = topPt;
                hillsToPlot.add(th);

                topPt -= (showdir || showdist || mainact.showheight && th.h.height > 0)?(1 + drawtextsize*2):drawtextsize;

                if (drawtextsize - TEXT_SIZE_DECREMENT >= TEXT_SIZE_MIN)
                {
                    drawtextsize -= TEXT_SIZE_DECREMENT;
                }
            }
        }

        // Fudge-factor because we don't know exactly how high label text will display until we draw it later.
        // A tiny font at the top needs to be moved down slightly to avoid being clipped; larger fonts seem OK.
        topPt -= Math.max(0, 13 - drawtextsize);
        return topPt;
    }

    private void drawHillLabelLines(Canvas canvas, int toppt) {
        int alpha = ALPHA_LINE_MAX;
        // draw lines first
        for (int i = 0; i < hillsToPlot.size(); i++)
        {
            textPaint.setARGB(alpha, 255, 255, 255);
            strokePaint.setARGB(alpha, 0, 0, 0);
            tmpHill th = hillsToPlot.get(i);
            double vratio = Math.toDegrees(th.h.visualElevation - mainact.fe.getDirection());
            int yloc = (int)((mainact.scrheight * vratio / mainact.vfov) + (mainact.scrheight/2));
            int xloc = ((int)(mainact.scrwidth * th.ratio) + (mainact.scrwidth/2));
            canvas.drawLine(xloc, yloc, xloc, th.toppt - toppt, strokePaint);
            canvas.drawLine(xloc, yloc, xloc, th.toppt - toppt, textPaint);
            canvas.drawLine(xloc-20, th.toppt - toppt, xloc+20, th.toppt - toppt, strokePaint);
            canvas.drawLine(xloc-20, th.toppt - toppt, xloc+20, th.toppt - toppt, textPaint);

            if (alpha - ALPHA_DECREMENT >= ALPHA_LINE_MIN)
            {
                alpha -= ALPHA_DECREMENT;
            }
        }
    }

    private void drawHillLabelText(Canvas canvas, int toppt) {
        boolean moreinfo;
        Float drawtextsize = textsize;
        int alpha = ALPHA_LABEL_MAX;
        // draw text over top
        for (int i = 0; i < hillsToPlot.size(); i++)
        {
            textPaint.setARGB(alpha, 255, 255, 255);
            strokePaint.setARGB(Math.min(alpha, ALPHA_STROKE_MIN), 0, 0, 0);

            textPaint.setTextSize(drawtextsize);
            strokePaint.setTextSize(drawtextsize);

            tmpHill th = hillsToPlot.get(i);
            moreinfo = (showdir || showdist || mainact.showheight && th.h.height > 0);
            int xloc = ((int)(mainact.scrwidth * th.ratio) + (mainact.scrwidth/2));

            Rect bnds = new Rect();
            strokePaint.getTextBounds(th.h.hillname,0,th.h.hillname.length(),bnds);
            bnds.left += xloc - (textPaint.measureText(th.h.hillname) / 2.0);
            bnds.right += xloc - (textPaint.measureText(th.h.hillname) / 2.0);
            bnds.top += th.toppt - 5 - toppt;
            if (moreinfo) bnds.top -= drawtextsize;
            bnds.bottom += th.toppt-5 - toppt;

            // draws bounding box of touch region to select hill
            //canvas.drawRect(bnds, strokePaint);

            mMarkers.add(new HillMarker(th.h.id, bnds));
            canvas.drawText(th.h.hillname, xloc, th.toppt - ((moreinfo)?drawtextsize:0) - 5 - toppt, strokePaint);
            canvas.drawText(th.h.hillname, xloc, th.toppt - ((moreinfo)?drawtextsize:0) - 5 - toppt, textPaint);

            if (showdir || showdist || mainact.showheight)
            {
                boolean hascontents = false;
                String marker = " (";
                if (showdir)
                {
                    hascontents = true;
                    marker += Math.floor(10*th.h.direction)/10 + "\u00B0";
                }
                if (showdist)
                {
                    hascontents = true;
                    double multip = (mainact.typeunits)?1:0.621371;
                    marker += (showdir ? " " : "") + Math.floor(10*th.h.distance*multip)/10;
                    if (mainact.typeunits) marker += "km"; else marker += "miles";
                }
                if (mainact.showheight)
                {
                    if (th.h.height > 0)
                    {
                        hascontents = true;
                        marker += ((showdir || showdist) ? " " : "") + mainact.distanceAsImperialOrMetric(th.h.height);
                    }
                }
                marker += ")";
                if (hascontents)
                {
                    canvas.drawText(marker, xloc, th.toppt-5 - toppt, strokePaint);
                    canvas.drawText(marker, xloc, th.toppt-5 - toppt, textPaint);
                }
            }

            if (alpha - ALPHA_DECREMENT >= ALPHA_LABEL_MIN)
            {
                alpha -= ALPHA_DECREMENT;
            }

            if (drawtextsize - TEXT_SIZE_DECREMENT >= TEXT_SIZE_MIN)
            {
                drawtextsize -= TEXT_SIZE_DECREMENT;
            }
        }
    }

    private void drawLocationAndOrientationStatus(Canvas canvas) {
        textPaint.setTextSize(mMainTextSize);
        strokePaint.setTextSize(mMainTextSize);
        textPaint.setARGB(255, 255, 255, 255);
        strokePaint.setARGB(255, 0, 0, 0);

        String compadj = (mainact.compassAdjustment>=0)?"+":"";
        compadj += String.format("%.01f", mainact.compassAdjustment);

        String basetext = "" + (int)mainact.fd.getDirection() + (char)0x00B0;
        basetext +=" (adj:"+compadj+")";
        basetext +=" FOV: "+String.format("%.01f", mainact.hfov);

        if (mainact.badsensor)
        {
            canvas.drawText( "Recalibrate sensor!", 10, 80, paint);
        }

        Location curLocation = mainact.mGPS.getCurrentLocation();
        if (curLocation != null)
        {
            mainact.acc = "+/- " + mainact.distanceAsImperialOrMetric(curLocation.getAccuracy());
        }
        else
        {
            mainact.acc = "?";
        }

        basetext +=" Location " + mainact.acc;
        canvas.drawText( basetext, mainact.scrwidth/2, mainact.scrheight-70, strokePaint);
        canvas.drawText( basetext, mainact.scrwidth/2, mainact.scrheight-70, textPaint);

        basetext = "";

        if (curLocation == null) basetext = "No GPS position yet";
        else if (curLocation.getAccuracy() > 200) basetext = "Warning - GPS position too inaccurate";

        if (basetext != "")
        {
            canvas.drawText( basetext, mainact.scrwidth/2, mainact.scrheight/2, strokePaint);
            canvas.drawText( basetext, mainact.scrwidth/2, mainact.scrheight/2, textPaint);
        }

        int va = mainact.fd.GetVariation();
        variationPaint.setARGB(255, 255, 0, 0);
        variationPaint.setStrokeWidth(4);
        int dashlength = mainact.scrheight / 10;
        for (int i = 0; i < 360; i+=15)
        {
            if (i > va) variationPaint.setARGB(255, 0, 255, 0);
            canvas.drawLine((mainact.scrwidth/10)+(dashlength/5*(float)Math.sin( Math.toRadians(i))),
                    mainact.scrheight-(mainact.scrheight/5)-(dashlength/5*(float)Math.cos( Math.toRadians(i))),
                    (mainact.scrwidth/10)+(dashlength*(float)Math.sin( Math.toRadians(i))),
                    mainact.scrheight-(mainact.scrheight/5)-(dashlength*(float)Math.cos( Math.toRadians(i))),
                    variationPaint);
        }
    }

    private void drawSettingsButton(Canvas canvas) {


        //settingPaint.setStyle(Paint.Style.STROKE);
        settingPaint2.setStyle(Paint.Style.STROKE);
        settingPaint.setAntiAlias(true);
        settingPaint2.setAntiAlias(true);
        settingPaint2.setStrokeWidth((int)(mainact.scrwidth/100.0));
        //settingPaint.setStrokeWidth((int)(scrwidth/80.0));
        settingPaint2.setARGB(255, 255, 255, 255);
        settingPaint.setARGB(255, 0, 0, 0);

        float barwidth = mainact.scrwidth/12.0f;
        int startPtw = mainact.scrwidth/60;
        int startPth = mainact.scrheight/60;
        int baroffset = mainact.scrwidth/50;
        canvas.drawRect(0.0f, 0.0f, barwidth + (startPtw*2), baroffset * 3.3f, settingPaint);

        canvas.drawLine(startPtw, startPth, startPtw + barwidth, startPth, settingPaint2);

        canvas.drawLine(startPtw, startPth+baroffset, startPtw + barwidth, startPth+baroffset, settingPaint2);
        baroffset += baroffset;
        canvas.drawLine(startPtw, startPth+baroffset, startPtw + barwidth, startPth+baroffset, settingPaint2);
    }

    private void drawCalibrationInstructions(Canvas canvas) {
        // adjust text to fit any screen - lol, so hacky :-D
        boolean happyWithSize = false;
        do
        {
            textPaint.setTextSize(mMainTextSize);
            float sz = textPaint.measureText("screen, wait for stabilisation, and tap again.");
            if (sz > mainact.scrwidth*0.7 )
            {
                mMainTextSize--;
            }
            else if (sz < mainact.scrwidth*0.6)
            {
                mMainTextSize++;
            }
            else
            {
                happyWithSize = true;
            }
        } while (!happyWithSize);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setARGB(255, 255, 255, 255);
        paint.setARGB(100, 0, 0, 0);

        // left, top, right, bottom
        canvas.drawRoundRect(fovrect, 50,50,paint);
        canvas.drawText( "To calibrate, view an object at the very", txtgap, vtxtgap*3, textPaint);
        canvas.drawText( "left edge of the screen, and wait for", txtgap, vtxtgap*4, textPaint);
        canvas.drawText( "the direction sensor to stabilise. Then", txtgap, vtxtgap*5, textPaint);
        canvas.drawText( "tap the screen (gently, so you don't move", txtgap, vtxtgap*6, textPaint);
        canvas.drawText( "the view!). Then turn around until the ", txtgap, vtxtgap*7, textPaint);
        canvas.drawText( "object is at the very right edge of the ", txtgap, vtxtgap*8, textPaint);
        canvas.drawText( "screen, wait for stabilisation, and tap again.", txtgap, vtxtgap*9, textPaint);

        canvas.drawText( "Dir: " + (int)mainact.fd.getDirection() + (char)0x00B0 + " SD: "+mainact.fd.GetVariation(), mainact.scrwidth/2, mainact.scrheight-(vtxtgap*2), textPaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        if (mainact.calibrationStep == -1)
        {
            canvas.drawRect(0,0, 10, mainact.scrheight, transpRedPaint);
        }
        else
        {
            canvas.drawRect(mainact.scrwidth-10,0, mainact.scrwidth, mainact.scrheight, transpRedPaint);
        }
        int va = mainact.fd.GetVariation();
        variationPaint.setARGB(255, 255, 0, 0);
        variationPaint.setStrokeWidth(4);
        int dashlength = mainact.scrheight / 10;
        for (int i = 0; i < 360; i+=15)
        {
            if (i > va) variationPaint.setARGB(255, 0, 255, 0);
            canvas.drawLine((mainact.scrwidth/10)+(dashlength/5*(float)Math.sin( Math.toRadians(i))),
                    mainact.scrheight-(mainact.scrheight/5)-(dashlength/5*(float)Math.cos( Math.toRadians(i))),
                    (mainact.scrwidth/10)+(dashlength*(float)Math.sin( Math.toRadians(i))),
                    mainact.scrheight-(mainact.scrheight/5)-(dashlength*(float)Math.cos( Math.toRadians(i))),
                    variationPaint);
        }
    }
}
