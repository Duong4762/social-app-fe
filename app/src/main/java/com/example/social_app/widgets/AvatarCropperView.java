package com.example.social_app.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Rect;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

public class AvatarCropperView extends View {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ringOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cropSquarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cropCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Matrix imageMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private final ScaleGestureDetector scaleDetector;

    private Bitmap bitmap;
    private final RectF cropRect = new RectF();
    private float lastTouchX;
    private float lastTouchY;
    private boolean isDragging = false;

    public AvatarCropperView(Context context) {
        this(context, null);
    }

    public AvatarCropperView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AvatarCropperView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ringOverlayPaint.setColor(Color.parseColor("#66000000"));
        cropSquarePaint.setStyle(Paint.Style.STROKE);
        cropSquarePaint.setStrokeWidth(dp(1f));
        cropSquarePaint.setColor(Color.parseColor("#66FFFFFF"));
        cropCirclePaint.setStyle(Paint.Style.STROKE);
        cropCirclePaint.setStrokeWidth(dp(2f));
        cropCirclePaint.setColor(Color.parseColor("#CCFFFFFF"));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (bitmap == null) {
                    return false;
                }
                float scaleFactor = detector.getScaleFactor();
                float currentScale = getCurrentScale();
                float targetScale = currentScale * scaleFactor;
                if (targetScale < MIN_SCALE) {
                    scaleFactor = MIN_SCALE / currentScale;
                } else if (targetScale > MAX_SCALE) {
                    scaleFactor = MAX_SCALE / currentScale;
                }
                imageMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                ensureImageCoversCropRect();
                invalidate();
                return true;
            }
        });
    }

    public void setImageUri(@NonNull Uri uri) {
        try (InputStream is = getContext().getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return;
            }
            Bitmap decoded = BitmapFactory.decodeStream(is);
            setBitmap(decoded);
        } catch (IOException ignored) {
            // Ignore and keep previous state.
        }
    }

    public void setBitmap(@Nullable Bitmap bitmap) {
        this.bitmap = bitmap;
        setupInitialMatrix();
        invalidate();
    }

    @Nullable
    public Bitmap getCroppedCircularBitmap(int outputSizePx) {
        if (bitmap == null || cropRect.width() <= 0f || outputSizePx <= 0) {
            return null;
        }

        Matrix inverse = new Matrix();
        if (!imageMatrix.invert(inverse)) {
            return null;
        }

        float[] points = {
                cropRect.left, cropRect.top,
                cropRect.right, cropRect.bottom
        };
        inverse.mapPoints(points);
        RectF srcRect = new RectF(points[0], points[1], points[2], points[3]);

        RectF bitmapBounds = new RectF(0f, 0f, bitmap.getWidth(), bitmap.getHeight());
        if (!srcRect.intersect(bitmapBounds)) {
            return null;
        }

        Rect src = new Rect(
                Math.round(srcRect.left),
                Math.round(srcRect.top),
                Math.round(srcRect.right),
                Math.round(srcRect.bottom)
        );
        src.intersect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (src.width() <= 0 || src.height() <= 0) {
            return null;
        }

        Bitmap squareBitmap = Bitmap.createBitmap(outputSizePx, outputSizePx, Bitmap.Config.ARGB_8888);
        Canvas squareCanvas = new Canvas(squareBitmap);
        squareCanvas.drawBitmap(bitmap, src, new RectF(0f, 0f, outputSizePx, outputSizePx), bitmapPaint);

        Bitmap circularBitmap = Bitmap.createBitmap(outputSizePx, outputSizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(circularBitmap);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = outputSizePx / 2f;
        canvas.drawCircle(radius, radius, radius, maskPaint);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(squareBitmap, 0f, 0f, maskPaint);
        squareBitmap.recycle();
        return circularBitmap;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateCropRect(w, h);
        setupInitialMatrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, imageMatrix, bitmapPaint);
        }

        Path squarePath = new Path();
        squarePath.addRect(cropRect, Path.Direction.CW);
        Path circlePath = new Path();
        circlePath.addCircle(
                cropRect.centerX(),
                cropRect.centerY(),
                cropRect.width() / 2f,
                Path.Direction.CW
        );
        squarePath.op(circlePath, Path.Op.DIFFERENCE);
        canvas.drawPath(squarePath, ringOverlayPaint);

        canvas.drawRect(cropRect, cropSquarePaint);
        canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width() / 2f, cropCirclePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && isDragging) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    imageMatrix.postTranslate(dx, dy);
                    ensureImageCoversCropRect();
                    invalidate();
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateCropRect(int width, int height) {
        float margin = dp(16f);
        float cropSize = Math.min(width, height) - (margin * 2f);
        cropSize = Math.max(cropSize, dp(180f));
        if (cropSize > Math.min(width, height)) {
            cropSize = Math.min(width, height);
        }
        float left = (width - cropSize) / 2f;
        float top = (height - cropSize) / 2f;
        cropRect.set(left, top, left + cropSize, top + cropSize);
    }

    private void setupInitialMatrix() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0 || cropRect.width() == 0f) {
            return;
        }
        imageMatrix.reset();

        float scale = Math.max(
                cropRect.width() / bitmap.getWidth(),
                cropRect.height() / bitmap.getHeight()
        );
        imageMatrix.postScale(scale, scale);

        RectF imageRect = getMappedImageRect();
        float dx = cropRect.centerX() - imageRect.centerX();
        float dy = cropRect.centerY() - imageRect.centerY();
        imageMatrix.postTranslate(dx, dy);
        ensureImageCoversCropRect();
    }

    private void ensureImageCoversCropRect() {
        if (bitmap == null) {
            return;
        }

        RectF imageRect = getMappedImageRect();
        float scaleAdjustment = 1f;
        if (imageRect.width() < cropRect.width()) {
            scaleAdjustment = Math.max(scaleAdjustment, cropRect.width() / imageRect.width());
        }
        if (imageRect.height() < cropRect.height()) {
            scaleAdjustment = Math.max(scaleAdjustment, cropRect.height() / imageRect.height());
        }
        if (scaleAdjustment > 1f) {
            float currentScale = getCurrentScale();
            float targetScale = Math.min(currentScale * scaleAdjustment, MAX_SCALE);
            float postScale = targetScale / currentScale;
            imageMatrix.postScale(postScale, postScale, cropRect.centerX(), cropRect.centerY());
            imageRect = getMappedImageRect();
        }

        float dx = 0f;
        float dy = 0f;
        if (imageRect.left > cropRect.left) {
            dx = cropRect.left - imageRect.left;
        } else if (imageRect.right < cropRect.right) {
            dx = cropRect.right - imageRect.right;
        }
        if (imageRect.top > cropRect.top) {
            dy = cropRect.top - imageRect.top;
        } else if (imageRect.bottom < cropRect.bottom) {
            dy = cropRect.bottom - imageRect.bottom;
        }
        if (dx != 0f || dy != 0f) {
            imageMatrix.postTranslate(dx, dy);
        }
    }

    private RectF getMappedImageRect() {
        RectF rect = new RectF(0f, 0f, bitmap.getWidth(), bitmap.getHeight());
        imageMatrix.mapRect(rect);
        return rect;
    }

    private float getCurrentScale() {
        imageMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
