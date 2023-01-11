package bot1;

import battlecode.common.*;

strictfp class Pathing {

    /**
     * The direction that we are trying to use to go around the obstacle.
     * It is null if we are not trying to go around an obstacle.
     */
    private static Direction bugDirection = null;

    static void walkTowards(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) {
            // If our cooldown is too high, then don't even bother!
            // There is nothing we can do anyway.
            return;
        }

        MapLocation currentLocation = rc.getLocation();
        // if (currentLocation == target) // this is BAD! see Lecture 2 for why.
        if (currentLocation.equals(target)) {
            // We're already at our goal! Nothing to do either.
            return;
        }

        Direction d = currentLocation.directionTo(target);
        if (rc.canMove(d) && !isObstacle(rc, d)) {
            // Easy case of Bug 0!
            // No obstacle in the way, so let's just go straight for it!

            rc.move(d);
            if (rc.isMovementReady())
                walkTowards(rc, target);

            bugDirection = null;
        } else {
            // Hard case of Bug 0 :<
            // There is an obstacle in the way, so we're gonna have to go around it.
            if (bugDirection == null) {
                // If we don't know what we're trying to do
                // make something up
                // And, what better than to pick as the direction we want to go in
                // the best direction towards the goal?
                bugDirection = d;
            }
            // Now, try to actually go around the obstacle
            // using bugDirection!
            // Repeat 8 times to try all 8 possible directions.
            for (int i = 0; i < 8; i++) {
                if (rc.canMove(bugDirection) && !isObstacle(rc, bugDirection)) {

                    rc.move(bugDirection);
                    bugDirection = bugDirection.rotateLeft();
                    if (rc.isMovementReady())
                        walkTowards(rc, target);
                    break;
                } else {
                    bugDirection = bugDirection.rotateRight();
                }
            }
        }
    }

    /**
     * same as rc.canMove(d) for now. Might change in future
     */
    private static boolean isObstacle(RobotController rc, Direction d) throws GameActionException {
        return !rc.canMove(d);
    }
}
