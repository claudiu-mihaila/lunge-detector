package fit.magic.cv.repcounter;

class LungeFeatures {
    private double hipHeightDiff;
    private double leftKneeAngle;
    private double rightKneeAngle;

    // other features that can be evaluated are torso position, shoulder position,
    // knee-ankle alignment, bottom knee-floor closeness bottom knee-hip alignment, etc.
    // correction messages can be issued when departures from recommended posture are observed.

    public LungeFeatures(double hipHeightDiff, double leftKneeAngle, double rightKneeAngle) {
        this.hipHeightDiff = hipHeightDiff;
        this.leftKneeAngle = leftKneeAngle;
        this.rightKneeAngle = rightKneeAngle;
    }

    public double getHipHeightDiff() {
        return hipHeightDiff;
    }

    public double getLeftKneeAngle() {
        return leftKneeAngle;
    }

    public double getRightKneeAngle() {
        return rightKneeAngle;
    }
}
