package fit.magic.cv.repcounter;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
class LungeFeatures {
    private double hipHeightDiff;
    private double leftKneeAngle;
    private double rightKneeAngle;

    // other features that can be evaluated are torso position, shoulder position,
    // knee-ankle alignment, bottom knee-floor closeness bottom knee-hip alignment, etc.
    // correction messages can be issued when departures from recommended posture are observed.
}
