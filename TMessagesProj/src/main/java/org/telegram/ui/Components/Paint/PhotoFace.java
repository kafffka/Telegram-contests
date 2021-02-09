package org.telegram.ui.Components.Paint;

import android.graphics.Bitmap;
import android.graphics.PointF;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import org.telegram.ui.Components.Size;
import org.telegram.ui.Components.Point;

import java.util.List;

public class PhotoFace {

    private float width;
    private float angle;

    private Point foreheadPoint;

    private Point eyesCenterPoint;
    private float eyesDistance;

    private Point mouthPoint;
    private Point chinPoint;

    public PhotoFace(Face face, Bitmap sourceBitmap, Size targetSize, boolean sideward) {
        List<FaceLandmark> landmarks = face.getAllLandmarks();

        Point leftEyePoint = null;
        Point rightEyePoint = null;

        Point leftMouthPoint = null;
        Point rightMouthPoint = null;

        for (FaceLandmark landmark : landmarks) {
            PointF point = landmark.getPosition();

            switch (landmark.getLandmarkType()) {
                case FaceLandmark.LEFT_EYE: {
                    leftEyePoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case FaceLandmark.RIGHT_EYE: {
                    rightEyePoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case FaceLandmark.MOUTH_LEFT: {
                    leftMouthPoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;

                case FaceLandmark.MOUTH_RIGHT: {
                    rightMouthPoint = transposePoint(point, sourceBitmap, targetSize, sideward);
                }
                break;
            }
        }

        if (leftEyePoint != null && rightEyePoint != null) {
            if (leftEyePoint.x < rightEyePoint.x) {
                Point temp = leftEyePoint;
                leftEyePoint = rightEyePoint;
                rightEyePoint = temp;
            }
            eyesCenterPoint = new Point(0.5f * leftEyePoint.x + 0.5f * rightEyePoint.x,
                    0.5f * leftEyePoint.y + 0.5f * rightEyePoint.y);
            eyesDistance = (float)Math.hypot(rightEyePoint.x - leftEyePoint.x, rightEyePoint.y - leftEyePoint.y);
            angle = (float)Math.toDegrees(Math.PI + Math.atan2(rightEyePoint.y - leftEyePoint.y, rightEyePoint.x - leftEyePoint.x));

            width = eyesDistance * 2.35f;

            float foreheadHeight = 0.8f * eyesDistance;
            float upAngle = (float)Math.toRadians(angle - 90);
            foreheadPoint = new Point(eyesCenterPoint.x + foreheadHeight * (float)Math.cos(upAngle),
                    eyesCenterPoint.y + foreheadHeight * (float)Math.sin(upAngle));
        }

        if (leftMouthPoint != null && rightMouthPoint != null) {
            if (leftMouthPoint.x < rightMouthPoint.x) {
                Point temp = leftMouthPoint;
                leftMouthPoint = rightMouthPoint;
                rightMouthPoint = temp;
            }
            mouthPoint = new Point(0.5f * leftMouthPoint.x + 0.5f * rightMouthPoint.x,
                    0.5f * leftMouthPoint.y + 0.5f * rightMouthPoint.y);

            float chinDepth = 0.7f * eyesDistance;
            float downAngle = (float)Math.toRadians(angle + 90);
            chinPoint = new Point(mouthPoint.x + chinDepth * (float)Math.cos(downAngle),
                    mouthPoint.y + chinDepth * (float)Math.sin(downAngle));
        }
    }

    public boolean isSufficient() {
        return eyesCenterPoint != null;
    }

    private Point transposePoint(PointF point, Bitmap sourceBitmap, Size targetSize, boolean sideward) {
        float bitmapW = sideward ? sourceBitmap.getHeight() : sourceBitmap.getWidth();
        float bitmapH = sideward ? sourceBitmap.getWidth() : sourceBitmap.getHeight();
        return new Point(targetSize.width * point.x / bitmapW, targetSize.height * point.y / bitmapH);
    }

    public Point getPointForAnchor(int anchor) {
        switch (anchor) {
            case 0: {
                return foreheadPoint;
            }

            case 1: {
                return eyesCenterPoint;
            }

            case 2: {
                return mouthPoint;
            }

            case 3: {
                return chinPoint;
            }

            default: {
                return null;
            }
        }
    }

    public float getWidthForAnchor(int anchor) {
        if (anchor == 1) {
            return eyesDistance;
        }
        return width;
    }

    public float getAngle() {
        return angle;
    }
 }
