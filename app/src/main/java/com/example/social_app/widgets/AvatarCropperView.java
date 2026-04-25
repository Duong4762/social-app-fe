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
import android.graphics.Rect;
import android.graphics.RectF;
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

    /** Zoom tối đa so với mức scale tối thiểu để phủ vùng crop (không dùng scale tuyệt đối của matrix). */
    private static final float MAX_ZOOM_FACTOR = 5f;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ringOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cropCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Matrix imageMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private final ScaleGestureDetector scaleDetector;

    private Bitmap bitmap;
    private final RectF cropRect = new RectF();
    /** Scale tối thiểu (đồng đều) để ảnh luôn phủ hết vùng crop — neo pinch zoom ở đây. */
    private float minScaleCover = 1f;
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
                float s = getUniformScale();
                float minS = minScaleCover;
                float maxS = minScaleCover * MAX_ZOOM_FACTOR;
                float target = s * scaleFactor;
                if (target < minS) {
                    scaleFactor = minS / s;
                } else if (target > maxS) {
                    scaleFactor = maxS / s;
                }
                imageMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                clampPanToCoverCrop();
                invalidate();
                return true;
            }
        });
        scaleDetector.setQuickScaleEnabled(false);
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
                    clampPanToCoverCrop();
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

        minScaleCover = Math.max(
                cropRect.width() / bitmap.getWidth(),
                cropRect.height() / bitmap.getHeight()
        );

        imageMatrix.postScale(minScaleCover, minScaleCover);

        RectF imageRect = getMappedImageRect();
        float dx = cropRect.centerX() - imageRect.centerX();
        float dy = cropRect.centerY() - imageRect.centerY();
        imageMatrix.postTranslate(dx, dy);
        clampPanToCoverCrop();
    }

    /**
     * Giữ scale trong [minScaleCover, minScaleCover * MAX_ZOOM_FACTOR] và pan để ảnh luôn phủ vùng crop.
     */
    private void clampPanToCoverCrop() {
        if (bitmap == null || cropRect.width() <= 0f) {
            return;
        }

        float s = getUniformScale();
        float minS = minScaleCover;
        float maxS = minScaleCover * MAX_ZOOM_FACTOR;
        if (s < minS - 1e-4f) {
            imageMatrix.postScale(minS / s, minS / s, cropRect.centerX(), cropRect.centerY());
        } else if (s > maxS + 1e-4f) {
            imageMatrix.postScale(maxS / s, maxS / s, cropRect.centerX(), cropRect.centerY());
        }

        RectF imageRect = getMappedImageRect();
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

    /**
     * Độ scale đồng đều (không dùng MSCALE_X tuyệt đối làm ngưỡng [1,5] khi scale khởi tạo đã &gt; 1).
     */
    private float getUniformScale() {
        imageMatrix.getValues(matrixValues);
        float sx = matrixValues[Matrix.MSCALE_X];
        float sy = matrixValues[Matrix.MSCALE_Y];
        return (Math.abs(sx) + Math.abs(sy)) / 2f;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
