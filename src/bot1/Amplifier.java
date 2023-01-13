package bot1;

import battlecode.common.*;
import java.util.Random;

public strictfp class Amplifier {

    static final Random rng = new Random(6147);
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    static MapLocation exploreLoc = null, randomExploreLoc = null, clearLocation = null;
    static boolean needToClear = false;

    static void runAmplifier(RobotController rc) throws GameActionException {
        int exploreRadiusSquare = 8;
        if (rc.getRoundNum() % 2 == 0) {
            Communication.clearExploreLoc(rc, clearLocation, exploreRadiusSquare);
            clearLocation = null;
            needToClear = false;
        }

        if (exploreLoc == null && needToClear == false)
            exploreLoc = Communication.getClosestExploreLoc(rc);

        if (exploreLoc != null && needToClear == false) {
            if (rc.getLocation().distanceSquaredTo(exploreLoc) <= exploreRadiusSquare) {
                exploreLoc = null;
                clearLocation = rc.getLocation();
                needToClear = true;
            } else {
                rc.setIndicatorString(String.format("comms expLoc: %d,%d bc:%d", exploreLoc.x, exploreLoc.y,
                        Clock.getBytecodesLeft()));
                Pathing.walkTowards(rc, exploreLoc);
            }
            return;
        }

        if (needToClear == true)
            return;

        // random exploreLoc
        if (randomExploreLoc == null)
            randomExploreLoc = new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));
        rc.setIndicatorString(String.format("rand expLoc: %d,%d bc:%d", randomExploreLoc.x, randomExploreLoc.y,
                Clock.getBytecodesLeft()));
        Pathing.walkTowards(rc, randomExploreLoc);
        if (rc.getLocation().distanceSquaredTo(randomExploreLoc) <= exploreRadiusSquare)
            randomExploreLoc = null;
    }

}
