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

    static MapLocation exploreLoc = null, randomExploreLoc = null;

    static void runAmplifier(RobotController rc) throws GameActionException {
        int exploreRadiusSquare = 8;
        if (rc.getRoundNum() % 2 == 0)
            Communication.clearExploreLoc(rc, exploreRadiusSquare);

        if (exploreLoc == null)
            exploreLoc = Communication.getClosestExploreLoc(rc);
        if (exploreLoc != null) {
            rc.setIndicatorString(String.format("comms expLoc: %d,%d", exploreLoc.x, exploreLoc.y));
            Pathing.walkTowards(rc, exploreLoc);
            if (rc.getLocation().distanceSquaredTo(exploreLoc) <= exploreRadiusSquare)
                exploreLoc = null;
            return;
        }

        // random exploreLoc
        if (randomExploreLoc == null)
            randomExploreLoc = new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));
        rc.setIndicatorString(String.format("rand expLoc: %d,%d", randomExploreLoc.x, randomExploreLoc.y));
        Pathing.walkTowards(rc, randomExploreLoc);
        if (rc.getLocation().distanceSquaredTo(randomExploreLoc) <= exploreRadiusSquare)
            randomExploreLoc = null;
    }

}
