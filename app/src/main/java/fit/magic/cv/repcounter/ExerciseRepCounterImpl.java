package fit.magic.cv.repcounter;

import androidx.annotation.NonNull;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import fit.magic.cv.PoseLandmarkerHelper;

public class ExerciseRepCounterImpl extends ExerciseRepCounter {

    enum LungeState {
        STANDING,
        LUNGE_DOWN,
        LUNGE_UP,
    }

    enum Landmark {
        RIGHT_HIP(24),
        LEFT_HIP(23),
        RIGHT_KNEE(26),
        LEFT_KNEE(25),
        RIGHT_ANKLE(28),
        LEFT_ANKLE(27);

        private final int code;

        Landmark(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private static final float LUNGING_KNEE_ANGLE_THRESHOLD_DEGREES = 80.0f; // Lunging knee angle threshold
    private static final float STANDING_KNEE_ANGLE_THRESHOLD_DEGREES = 170.0f; // Standing knee angle threshold
    private static final float STANDING_KNEE_ANGLE_DEGREES = 180.0f; // Standing knee angle
    private static LungeState currentState = LungeState.STANDING;
    private static final Queue<LungeFeatures> lastLungeFeatures = new ArrayDeque<>(10);

    @Override
    public void setResults(@NonNull PoseLandmarkerHelper.ResultBundle resultBundle) {
        // extract the features of lunges
        List<LungeFeatures> lungeFeatures = resultBundle.getResults().stream()
                .filter(poseLandmarkerResult -> !poseLandmarkerResult.landmarks().isEmpty())
                .map(poseLandmarkerResult -> extractLungeFeatures(poseLandmarkerResult.landmarks().get(0)))
                .collect(Collectors.toList());

        // smooth the results by moving average
        // the window size should be selected following in-depth experimenting with angles, frame rates, etc.
        List<LungeFeatures> smoothedFeatures = movingAverage(lungeFeatures, 5);

        // detect lunges
        detectLunges(smoothedFeatures);
    }

    private void detectLunges(List<LungeFeatures> smoothedFeatures) {
        for (LungeFeatures features : smoothedFeatures) {
            switch (currentState) {
                case STANDING:
                    float downProgress = (float) (Math.max(features.getLeftKneeAngle(), features.getRightKneeAngle()) / (STANDING_KNEE_ANGLE_DEGREES - LUNGING_KNEE_ANGLE_THRESHOLD_DEGREES));
                    if (downProgress > 1f) {
                        downProgress = 1.0f;
                    }
                    sendProgressUpdate(downProgress);
                    if (isLegAngleLow(features.getLeftKneeAngle(), features.getRightKneeAngle())) {
                        currentState = LungeState.LUNGE_DOWN;
                    }
                    break;
                case LUNGE_DOWN:
                    float upProgress = (float) (Math.max(features.getLeftKneeAngle(), features.getRightKneeAngle()) / (STANDING_KNEE_ANGLE_DEGREES - LUNGING_KNEE_ANGLE_THRESHOLD_DEGREES));
                    if (upProgress < 0f) {
                        upProgress = 0.0f;
                    }
                    sendProgressUpdate(upProgress);
                    if (isLegAngleHigh(features.getLeftKneeAngle(), features.getRightKneeAngle())) {
                        currentState = LungeState.LUNGE_UP;
                    }
                    break;
                case LUNGE_UP:
                    if (isLegAngleHigh(features.getLeftKneeAngle(), features.getRightKneeAngle())) { //Add a check to avoid counting rep immediately after LUNGE_UP
                        currentState = LungeState.STANDING;
                        incrementRepCount();
                    }
                    break;
            }
        }
    }

    private boolean isLegAngleHigh(double leftKneeAngle, double rightKneeAngle) {
        return leftKneeAngle < STANDING_KNEE_ANGLE_DEGREES - STANDING_KNEE_ANGLE_THRESHOLD_DEGREES || rightKneeAngle < STANDING_KNEE_ANGLE_DEGREES - STANDING_KNEE_ANGLE_THRESHOLD_DEGREES;
    }

    private boolean isLegAngleLow(double leftKneeAngle, double rightKneeAngle) {
        return leftKneeAngle > LUNGING_KNEE_ANGLE_THRESHOLD_DEGREES || rightKneeAngle > LUNGING_KNEE_ANGLE_THRESHOLD_DEGREES;
    }

    @NonNull
    private List<LungeFeatures> movingAverage(@NonNull List<LungeFeatures> features,
                                              int windowSize) {
        lastLungeFeatures.addAll(features);
        while (lastLungeFeatures.size() > windowSize) {
            lastLungeFeatures.poll();
        }

        List<LungeFeatures> result = new ArrayList<>();

        double hipHeightDiffAvg = lastLungeFeatures.stream()
                .mapToDouble(LungeFeatures::getHipHeightDiff)
                .average().orElse(0.0);
        double leftKneeAngleAvg = lastLungeFeatures.stream()
                .mapToDouble(LungeFeatures::getLeftKneeAngle)
                .average().orElse(0.0);
        double rightKneeAngleAvg = lastLungeFeatures.stream()
                .mapToDouble(LungeFeatures::getRightKneeAngle)
                .average().orElse(0.0);

        LungeFeatures smoothedFeatures = new LungeFeatures(hipHeightDiffAvg, leftKneeAngleAvg, rightKneeAngleAvg);
        result.add(smoothedFeatures);

        return result;
    }

    private static LungeFeatures extractLungeFeatures(@NonNull List<NormalizedLandmark> landmarkList) {
        NormalizedLandmark leftHip = landmarkList.get(Landmark.LEFT_HIP.getCode());
        NormalizedLandmark rightHip = landmarkList.get(Landmark.RIGHT_HIP.getCode());
        NormalizedLandmark leftKnee = landmarkList.get(Landmark.LEFT_KNEE.getCode());
        NormalizedLandmark rightKnee = landmarkList.get(Landmark.RIGHT_KNEE.getCode());
        NormalizedLandmark leftAnkle = landmarkList.get(Landmark.LEFT_ANKLE.getCode());
        NormalizedLandmark rightAnkle = landmarkList.get(Landmark.RIGHT_ANKLE.getCode());

        // Calculate hip-knee-ankle angles
        double leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle);
        double rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle);

        // Calculate hip height difference
        double hipHeightDiff = Math.abs(leftHip.y() - rightHip.y());

        return new LungeFeatures(hipHeightDiff, leftKneeAngle, rightKneeAngle);
    }

    // calculate the angle between 3 points
    private static double calculateAngle(@NonNull NormalizedLandmark p1,
                                         @NonNull NormalizedLandmark p2,
                                         @NonNull NormalizedLandmark p3) {
        double v1x = p2.x() - p1.x();
        double v1y = p2.y() - p1.y();
        double v2x = p3.x() - p2.x();
        double v2y = p3.y() - p2.y();
        double dotProduct = v1x * v2x + v1y * v2y;
        double magnitude1 = Math.sqrt(v1x * v1x + v1y * v1y);
        double magnitude2 = Math.sqrt(v2x * v2x + v2y * v2y);
        double angleRad = Math.acos(dotProduct / (magnitude1 * magnitude2));
        return Math.toDegrees(angleRad);
    }
}
