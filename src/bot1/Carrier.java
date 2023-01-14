package bot1;

import battlecode.common.*;

import java.util.Random;

public strictfp class Carrier {

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
    static boolean dumpResource = false;
    static int totalResources = 0, adamantium = 0, mana = 0;
    static int MAX_RESOURCE = 38;
    static MapLocation islandLocation = null;
    static int islandId = -1;

    /**
     * Run a single turn for a Carrier.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    static void runCarrier(RobotController rc) throws GameActionException {

        // get reources info of the carrier
        adamantium = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        mana = rc.getResourceAmount(ResourceType.MANA);
        totalResources = adamantium + mana;

        if (totalResources == 0 && rc.getAnchor() == null) {
            MapLocation closestHQWithAnchor = Communication.getClosestOwnHQ(rc, -1, 1);
            if (closestHQWithAnchor != null) {
                rc.setIndicatorString(String.format("target HQ loc for anchor: (%d,%d)", closestHQWithAnchor.x,
                        closestHQWithAnchor.y));
                if (rc.canTakeAnchor(closestHQWithAnchor, Anchor.STANDARD))
                    rc.takeAnchor(closestHQWithAnchor, Anchor.STANDARD);
                else {
                    Pathing.walkTowards(rc, closestHQWithAnchor);
                    return;
                }
            }
        }

        MapLocation curLoc = rc.getLocation();
        if (rc.getAnchor() != null) {
            // If I have an anchor singularly focus on getting it to the first island I see

            try {
                if (rc.senseAnchor(islandId) != null)
                    islandLocation = null;
            } catch (GameActionException e) {
                islandLocation = null;
            }

            if (islandLocation == null) {
                islandLocation = Communication.getClosestIslandLoc(rc, 0);
            }

            // if (islandLocation != null)
            // System.out.println(String.format("FREE ISLAND FROM COMMS:%d,%d",
            // islandLocation.x, islandLocation.y));

            if (islandLocation == null) {
                int[] islands = rc.senseNearbyIslands();
                for (int id : islands) {
                    if (rc.senseAnchor(id) == null) {
                        MapLocation[] thisIslandLocs = rc.senseNearbyIslandLocations(id);
                        for (MapLocation loc : thisIslandLocs) {
                            if (islandLocation == null
                                    || (curLoc.distanceSquaredTo(loc) < curLoc.distanceSquaredTo(islandLocation)))
                                islandLocation = loc;
                            islandId = id;
                        }
                    }
                }
            }

            if (islandLocation == null) {
                Direction dir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(dir))
                    rc.move(dir);
                return;
            }

            // better to implement as geting loc,id fromm comms && using
            // rc.senseNearbyIslands(id)
            // our islandLoc is approx
            // hence when we are close to it check adjLoc for island
            // this USES 65-500 bytecodes
            if (curLoc.distanceSquaredTo(islandLocation) < 5) {
                int bc = Clock.getBytecodesLeft();
                outer: for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        MapLocation newLoc = new MapLocation(curLoc.x + dx, curLoc.y + dy);
                        if (newLoc.x >= 0 && newLoc.x < rc.getMapWidth() && newLoc.y >= 0
                                && newLoc.y < rc.getMapHeight()) {
                            if (rc.senseIsland(newLoc) != -1) {
                                islandLocation = newLoc;
                                break outer;
                            }
                        }
                    }
                }
                System.out.printf("DELTA: bc used:%d \n", bc - Clock.getBytecodesLeft());
            }

            if (rc.canPlaceAnchor()) {
                rc.setIndicatorString("Huzzah, placed anchor!");
                rc.placeAnchor();
                if (rc.getRoundNum() % 2 == 0)
                    Communication.updateIslandType(rc);
                islandId = -1;
                islandLocation = null;
            } else {
                rc.setIndicatorString("Moving my anchor towards " + islandLocation);
                Pathing.walkTowards(rc, islandLocation);
                return;
            }

        }

        // dump resources to headquarters
        if (totalResources > MAX_RESOURCE || dumpResource) {
            dumpResource = true;

            int resourceAvailable = 1;
            if (mana > adamantium)
                resourceAvailable = 2;
            MapLocation targetHQLoc = Communication.getClosestOwnHQ(rc, resourceAvailable, 0);

            if (targetHQLoc == null)
                targetHQLoc = Communication.getClosestOwnHQ(rc, -1, 0);

            assert (targetHQLoc != null);

            boolean t_ad = false, t_mn = false;
            if (adamantium != 0)
                t_ad = rc.canTransferResource(targetHQLoc, ResourceType.ADAMANTIUM, adamantium);
            if (mana != 0)
                t_mn = rc.canTransferResource(targetHQLoc, ResourceType.MANA, mana);

            if (t_ad || t_mn) {
                if (t_ad && adamantium > 0)
                    rc.transferResource(targetHQLoc, ResourceType.ADAMANTIUM, adamantium);
                if (mana != 0) {
                    if (rc.canTransferResource(targetHQLoc, ResourceType.MANA, mana))
                        rc.transferResource(targetHQLoc, ResourceType.MANA, mana);
                }
            } else {
                if (targetHQLoc != null) {
                    rc.setIndicatorString(String.format("target HQ loc: (%d,%d)", targetHQLoc.x, targetHQLoc.y));
                    Pathing.walkTowards(rc, targetHQLoc);
                }
            }

            // successfully dumped all resources;
            if (totalResources < 1)
                dumpResource = false;
            return;
        }

        // Try to gather from squares around us.
        MapLocation me = rc.getLocation();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation wellLocation = new MapLocation(me.x + dx, me.y + dy);
                if (rc.canCollectResource(wellLocation, -1)) {
                    rc.collectResource(wellLocation, -1);
                    rc.setIndicatorString("Collecting, now have, AD:" +
                            rc.getResourceAmount(ResourceType.ADAMANTIUM) +
                            " MN: " + rc.getResourceAmount(ResourceType.MANA) +
                            " EX: " + rc.getResourceAmount(ResourceType.ELIXIR));
                }
            }
        }

        // nearby wells are already sensed at RobotPlayer.run()
        MapLocation targetLocation = Communication.getClosestWell(rc);
        // We have a target location! Let's move towards it.
        if (targetLocation != null) {
            rc.setIndicatorString(String.format("target well Loc: (%d,%d)", targetLocation.x, targetLocation.y));
            Pathing.walkTowards(rc, targetLocation);
        }

        // Also try to move randomly.
        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir))
            rc.move(dir);

    }
}
