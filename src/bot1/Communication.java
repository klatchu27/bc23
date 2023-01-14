package bot1;

import battlecode.common.*;
import java.util.ArrayList;
// import java.util.Arrays;

public strictfp class Communication {
    private static final int NUM_TYPES = 6;
    // Array 0 bounds
    private static final int MIN_HQ_IDX = 13;
    private static final int MAX_HQ_IDX = 17;
    private static final int MIN_ENEMY_IDX = 17;
    private static final int MAX_ENEMY_IDX = 35;
    public static final int MIN_EXPLORE_IDX = 35;
    public static final int MAX_EXPLORE_IDX = 39;
    private static final int MIN_WELL_IDX = 39;
    private static final int MAX_WELL_IDX = 52;
    // Array 1 bounds
    public static final int MIN_ISLAND_IDX = 64 + 17;
    public static final int MAX_ISLAND_IDX = 64 + 35;

    public static int thisRound = 0;
    public static int[][] sharedArrayCopy = new int[2][GameConstants.SHARED_ARRAY_LENGTH];

    // variables for appprox location for island
    public static int scale = 2, scaledWidth = 0, scaledHeight = 0;
    public static int width = 0, height = 0;

    // stashed write queries
    public static ArrayList<Integer> stashedislandLocs = new ArrayList<Integer>(0);

    static void initialiseComms(RobotController rc) {
        width = rc.getMapWidth();
        height = rc.getMapHeight();
        scale = 2;
        if (width * height > (1 << 10))// change later to more accurate
            scale = 4;
        scaledWidth = (width + scale - 1) / scale;
        scaledHeight = (height + scale - 1) / scale;
        return;
    }

    static void copySharedArray(RobotController rc) throws GameActionException {
        // if (rc.getType() == RobotType.HEADQUARTERS)
        // return;
        thisRound = rc.getRoundNum() % 2;
        for (int i = 12; ++i < GameConstants.SHARED_ARRAY_LENGTH;)
            sharedArrayCopy[thisRound][i] = rc.readSharedArray(i);
        // System.out.println("after copyShared" + Arrays.toString(sharedArrayCopy[0]));
        // System.out.println("after copyShared" + Arrays.toString(sharedArrayCopy[1]));
    }

    static void reportAlive(RobotController rc) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;

        final int typeIdx = typeToIndex(rc.getType());

        try {
            // Zero out in-progress counts if necessary
            thisRound = rc.getRoundNum() % 2;
            if (readSharedArray(rc, 0) != thisRound) {

                for (int i = 12; ++i < GameConstants.SHARED_ARRAY_LENGTH;) {
                    int val = rc.readSharedArray(i);
                    sharedArrayCopy[1 - thisRound][i] = val;
                    if (sharedArrayCopy[thisRound][i] != val)
                        rc.writeSharedArray(i, sharedArrayCopy[thisRound][i]);
                }
                writeSharedArray(rc, 0, rc.getRoundNum() % 2);

                for (int i = -1; ++i < NUM_TYPES;) {
                    if (readSharedArray(rc, thisRound * NUM_TYPES + i + 1) != 0) {
                        writeSharedArray(rc, thisRound * NUM_TYPES + i + 1, 0);
                    }
                }

            }

            // Increment alive counter
            final int arrayIdx = (rc.getRoundNum() % 2) * NUM_TYPES + typeIdx + 1;
            writeSharedArray(rc, arrayIdx, readSharedArray(rc, arrayIdx) + 1);

        } catch (GameActionException e) {
            e.printStackTrace();
        }
    }

    static int getAlive(RobotController rc, RobotType type) {
        final int typeIdx = typeToIndex(type);

        // Read from previous write cycle
        final int arrayIdx = ((rc.getRoundNum() + 1) % 2) * NUM_TYPES + typeIdx + 1;
        try {
            return readSharedArray(rc, arrayIdx);
        } catch (GameActionException e) {
            e.printStackTrace();
            return 0;
        }
    }

    static int reportExploreLoc(RobotController rc, MapLocation loc, boolean getIndex) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return -1;

        int slot = -1;
        for (int i = MIN_EXPLORE_IDX; i < MAX_EXPLORE_IDX; i++) {
            int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);
            if (m == null && slot == -1) {
                slot = i;
            } else if (m != null && loc.distanceSquaredTo(m) <= 20) {
                return -2;
            }
        }
        if (slot != -1) {
            try {
                writeSharedArray(rc, slot, locationToInt(rc, loc));
                return slot;
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    static MapLocation getClosestExploreLoc(RobotController rc) {
        MapLocation answer = null;
        for (int i = MIN_EXPLORE_IDX; i < MAX_EXPLORE_IDX; i++) {
            final int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);
            if (m != null && (answer == null
                    || rc.getLocation().distanceSquaredTo(m) < rc.getLocation().distanceSquaredTo(answer))) {
                answer = m;
            }
        }
        return answer;
    }

    /**
     * clears exploreLocs with 9 unit**2 of curLocation of bot
     * 
     * @param rc
     * @param radius if rc.getLocation().distanceSquaredTo(exploreLoc) < radius then
     *               it gets cleared
     */
    static void clearExploreLoc(RobotController rc, MapLocation curLoc, int radius) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;
        for (int i = MIN_EXPLORE_IDX; i < MAX_EXPLORE_IDX; i++) {
            int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);
            if (m == null || curLoc.distanceSquaredTo(m) > radius) { // We might want a stronger check than this
                continue;
            }
            try {
                writeSharedArray(rc, i, 0);
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * report the HQ loc along with the resourceTypeRequired
     * 
     * @param rc
     * @param HQLoc
     * @param resourceTypeRequired 0:nothing, 1:need Ad, 2:need Mn, 3:need El,
     *                             4:have anchor need nothing, 5: have anchor,need
     *                             nothing, 6: have anchor need Ad, 7: have anchor
     *                             need Mn, 8: have anchor need El
     */
    static void reportOwnHQ(RobotController rc, MapLocation HQLoc, int resourceTypeRequired) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;

        int slot = -1;
        for (int i = MIN_HQ_IDX; i < MAX_HQ_IDX; i++) {
            int value;
            try {
                value = readSharedArray(rc, i) / 8;
            } catch (GameActionException e) {
                continue;
            }
            // int requirement = (combined_value%2) + 2*((combined_value/2)%2);

            final MapLocation m = intToLocation(rc, value);
            if (m == null && slot == -1) {
                slot = i;
            } else if (m != null && m.equals(HQLoc)) {
                // this HQLoc is already reported. But the resource requirement need to be
                // updated
                slot = i;
                break;
            }
        }
        if (slot != -1) {
            try {
                int value = locationToInt(rc, HQLoc);
                value = value * 8;
                value |= resourceTypeRequired;
                writeSharedArray(rc, slot, value);
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * returns closest HQ location whose resource requirement matches the
     * resouceAvailable
     * with the carrier
     * 
     * @param rc
     * @param resourceAvailable 0->nothing, 1->adamantium, 2->mana,
     *                          3->elixir, >=4:anchor(have anchor and looking for an
     *                          HQ with anchor)
     * @return MapLocation
     */
    static MapLocation getClosestOwnHQ(RobotController rc, int resourceAvailable, int anchorRequirement) {
        MapLocation answer = null;
        int requirement = 0;
        MapLocation curLoc = rc.getLocation();
        for (int i = MIN_HQ_IDX; i < MAX_HQ_IDX; i++) {
            int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            requirement = (value % 2) + 2 * ((value / 2) % 2);
            int anchorAvailable = (value / 4) % 2;
            final MapLocation m = intToLocation(rc, value / 8);

            // if (m != null)
            // System.out.println(
            // String.format("loc: %d,%d, req:%d ,anchor:%d", m.x, m.y, requirement,
            // anchorAvailable));

            if (m != null
                    && (requirement == resourceAvailable
                            || (resourceAvailable == -1 && anchorAvailable >= anchorRequirement))
                    && (answer == null || curLoc.distanceSquaredTo(m) < curLoc.distanceSquaredTo(answer))) {
                answer = m;
            }
        }
        return answer;
    }

    static void reportWell(RobotController rc, MapLocation wellLoc) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;

        int slot = -1;
        for (int i = MIN_WELL_IDX; i < MAX_WELL_IDX; i++) {
            final int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);

            if (m == null && slot == -1) {
                slot = i;
            } else if (m != null && wellLoc.distanceSquaredTo(m) <= 1) {
                // the well location is already reported.
                // for this,we do not break immediately when we find free slot in line 62
                return;
            }
        }
        if (slot != -1) {
            try {

                // System.out.printf("ALL wellLocs : ");
                // for (int j = MIN_WELL_IDX; j < MAX_WELL_IDX; j++) {
                // int val = readSharedArray(rc, j);
                // if (val != 0)
                // System.out.printf(" " + intToLocation(rc, val));
                // }
                // System.out.printf("\n");

                System.out.println(String.format("reporting well location at (%d,%d)=%d", wellLoc.x, wellLoc.y,
                        locationToInt(rc, wellLoc)));
                writeSharedArray(rc, slot, locationToInt(rc, wellLoc));
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }

    }

    static MapLocation getClosestWell(RobotController rc) {
        MapLocation answer = null;
        MapLocation curLoc = rc.getLocation();
        for (int i = MIN_WELL_IDX; i < MAX_WELL_IDX; i++) {
            final int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);
            if (m != null && (answer == null || curLoc.distanceSquaredTo(m) < curLoc.distanceSquaredTo(answer))) {
                answer = m;
            }
        }
        return answer;
    }

    static void reportIsland(RobotController rc) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;

        int[] islands = rc.senseNearbyIslands();
        int islandType = 0;// 0 free, 1 own occupied, 2 enemy occupied

        for (int islandIdx : islands) {

            MapLocation[] thisIslandLocs = null;
            try {
                if (rc.senseAnchor(islandIdx) != null) {
                    islandType = 2;
                    if (rc.senseTeamOccupyingIsland(islandIdx) == rc.getTeam())
                        islandType = 1;
                }
                thisIslandLocs = rc.senseNearbyIslandLocations(islandIdx);

            } catch (GameActionException e) {
                System.out.println("ISLAND INDEX problem");
                continue;
            }

            for (MapLocation islandLoc : thisIslandLocs)
                if (reportIsland(rc, locationToApproxInt(rc, islandLoc) * 256 + islandIdx * 4 + islandType))
                    break;
        }

    }

    public static boolean reportIsland(RobotController rc, int approxInt) {
        int islandIdx = (approxInt / 4) % (1 << 6);
        int islandType = approxInt % 4;
        MapLocation islandLoc = approxIntToApproxLocation(rc, approxInt / 256);

        // try {
        // if (rc.senseIsland(islandLoc) == -1)
        // System.out.println(String.format("%d,%d is not an island", islandLoc.x,
        // islandLoc.y));
        // } catch (GameActionException e) {
        // ;
        // }

        int slot = -1;
        for (int i = MIN_ISLAND_IDX; i < MAX_ISLAND_IDX; i++) {
            int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = approxIntToApproxLocation(rc, value / 256);
            int oldIslandIdx = 0;
            value /= 4;
            for (int j = -1; ++j < 6; value /= 2)
                oldIslandIdx += (1 << j) * (value % 2);
            if (m == null && slot == -1) {
                slot = i;
            } else if (m != null && oldIslandIdx == islandIdx) {
                // this island id already exist !!
                slot = -1;
                // System.out.printf("islnad already exist: %d \n", oldIslandIdx);
                return false;
            }
        }
        if (slot != -1) {
            try {
                int approxIntMap = locationToApproxInt(rc, islandLoc);
                MapLocation approxLoc = approxIntToApproxLocation(rc, approxIntMap);
                int val = approxIntMap * 256 + islandIdx * 4 + islandType;

                // System.out.printf("ALL islandIds : \n");
                // for (int j = MIN_ISLAND_IDX; j < MAX_ISLAND_IDX; j++) {
                // int val1 = readSharedArray(rc, j);
                // if (val1 != 0)
                // System.out.printf(" id:%d \n", (val1 / 4) % (1 << 6));
                // }

                if (rc.getRoundNum() % 2 == 1) {
                    rc.setIndicatorDot(islandLoc, 200, 0, 0);
                    rc.setIndicatorDot(approxLoc, 0, 200, 0);
                    System.out.println(
                            String.format("Writing island:%d at (%d,%d) as [%d,%d] at index:%d val:%d bcleft:%d",
                                    islandIdx, islandLoc.x, islandLoc.y, approxLoc.x, approxLoc.y, slot, val,
                                    Clock.getBytecodesLeft()));
                    writeSharedArray(rc, slot, val);
                    return true;
                } else {
                    // no stashing for AMPLIFIER due to restricted bytecode
                    // if (rc.getType() == RobotType.AMPLIFIER)
                    // return false;

                    System.out.printf("stashing islandLoc:" + islandLoc
                            + String.format(" val:%d id:%d \n", approxInt, islandIdx));
                    if (stashedislandLocs.contains(new Integer(approxInt)) == false)
                        stashedislandLocs.add(new Integer(approxInt));
                    return false;
                }

            } catch (GameActionException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            System.out.printf("no slots available for island \n");
        }
        return false;
    }

    /**
     * gets closest islandLoc of type=reqType
     * 
     * @param rc
     * @param reqType 0: free, 1: own, 2: enemy
     * @return
     */
    static MapLocation getClosestIslandLoc(RobotController rc, int reqType) {
        MapLocation answer = null;
        MapLocation curLoc = rc.getLocation();
        for (int i = MIN_ISLAND_IDX; i < MAX_ISLAND_IDX; i++) {
            int value = 0;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = approxIntToApproxLocation(rc, value / 256);
            int type = (value % 2) + 2 * ((value / 2) % 2);
            if (m != null && (type == reqType)
                    && (answer == null || curLoc.distanceSquaredTo(m) < curLoc.distanceSquaredTo(answer))) {
                answer = m;
            }
        }

        return answer;
    }

    static ArrayList<MapLocation> getIslandLocs(RobotController rc, int reqType) {
        ArrayList<MapLocation> answer = new ArrayList<MapLocation>(0);
        for (int i = MIN_ISLAND_IDX; i < MAX_ISLAND_IDX; i++) {
            int value = 0;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = approxIntToApproxLocation(rc, value / 256);
            int type = (value % 2) + 2 * ((value / 2) % 2);
            if (m != null && (type == reqType)) {
                answer.add(m);
            }
        }
        return answer;
    }

    static void updateIslandType(RobotController rc) {

        // System.out.printf("UPdate island called,");
        for (int i = MIN_ISLAND_IDX; i < MAX_ISLAND_IDX; i++) {
            int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = approxIntToApproxLocation(rc, value / 256);
            int oldType = (value % 2) + 2 * ((value / 2) % 2);
            if (m == null)// We might want a stronger check than this
                continue;

            int islandIdx = 0;
            int val = value / 4;
            for (int j = -1; ++j < 6; val /= 2)
                islandIdx += (1 << j) * (val % 2);

            int newType = 0;
            Team occupied = Team.NEUTRAL;
            try {

                occupied = rc.senseTeamOccupyingIsland(islandIdx);

                if (occupied != Team.NEUTRAL) {
                    newType = 2;
                    if (occupied == rc.getTeam())
                        newType = 1;
                }

                if (oldType != newType) {
                    System.out.println(String.format("UPDATING ISLAND TYPE at %d,%d at index:%d", m.x, m.y, i));
                    writeSharedArray(rc, i, ((value / 4) * 4) + newType);
                }

            } catch (GameActionException e) {
                // System.out.printf("island id:%d notsensed bc:%d \n", islandIdx,
                // Clock.getBytecodesLeft());
                continue;
            }
        }
    }

    static void reportEnemy(RobotController rc, MapLocation enemy) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;

        int slot = -1;
        for (int i = MIN_ENEMY_IDX; i < MAX_ENEMY_IDX; i++) {
            final int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);
            if (m == null && slot == -1) {
                slot = i;
            } else if (m != null && enemy.distanceSquaredTo(m) <= 10) {
                return;
            }
        }
        if (slot != -1) {
            try {
                System.out.println(String.format("reporting ENEMY at %d,%d at index:%d", enemy.x, enemy.y, slot));
                writeSharedArray(rc, slot, locationToInt(rc, enemy));
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    static MapLocation getClosestEnemy(RobotController rc) {
        MapLocation answer = null;
        for (int i = MIN_ENEMY_IDX; i < MAX_ENEMY_IDX; i++) {
            final int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);
            if (m != null && (answer == null
                    || rc.getLocation().distanceSquaredTo(m) < rc.getLocation().distanceSquaredTo(answer))) {
                answer = m;
            }
        }
        return answer;
    }

    static void clearObsoleteEnemies(RobotController rc) {
        for (int i = MIN_ENEMY_IDX; i < MAX_ENEMY_IDX; i++) {
            final int value;
            try {
                value = readSharedArray(rc, i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);
            if (m == null || !rc.canSenseLocation(m)) { // We might want a stronger check than this
                continue;
            }
            try {
                final RobotInfo r = rc.senseRobotAtLocation(m);
                if (r == null || r.team == rc.getTeam()) {
                    System.out.println(String.format("Clearing obs ENEMY at %d,%d at index:%d", m.x, m.y, i));
                    writeSharedArray(rc, i, 0);
                }
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    private static int typeToIndex(RobotType type) {
        switch (type) {
            case HEADQUARTERS:
                return 0;
            case CARRIER:
                return 1;
            case LAUNCHER:
                return 2;
            case AMPLIFIER:
                return 3;
            case BOOSTER:
                return 4;
            case DESTABILIZER:
                return 5;
            default:
                throw new RuntimeException("Unknown type: " + type);
        }
    }

    /**
     * reads from rc.readSharedArray(index%64) if index==0 or roundNum%2 == index/64
     * else reads from sharedArrayCopy[index/64][index%64]
     */
    public static int readSharedArray(RobotController rc, int index) throws GameActionException {
        int q = index / GameConstants.SHARED_ARRAY_LENGTH;
        int r = index % GameConstants.SHARED_ARRAY_LENGTH;
        if (r < 13 || q == rc.getRoundNum() % 2)
            return rc.readSharedArray(r);
        return sharedArrayCopy[q][r];
    }

    public static void writeSharedArray(RobotController rc, int index, int value) throws GameActionException {
        int q = index / GameConstants.SHARED_ARRAY_LENGTH;
        int r = index % GameConstants.SHARED_ARRAY_LENGTH;
        int turn = (rc.getRoundNum()) % 2;
        if (r < 13 || q == turn)
            rc.writeSharedArray(r, value);
    }

    public static int locationToApproxInt(RobotController rc, MapLocation m) {
        if (m == null)
            return 0;
        return 1 + (m.x / scale) + (m.y / scale) * scaledWidth;
    }

    public static MapLocation approxIntToApproxLocation(RobotController rc, int m) {
        if (m == 0) {
            return null;
        }
        m--;
        int x = (m % scaledWidth) * scale;
        int y = (m / scaledWidth) * scale;
        if (x + 1 < width)
            x++;
        if (y + 1 < height)
            y++;
        return new MapLocation(x, y);
    }

    private static int locationToInt(RobotController rc, MapLocation m) {
        if (m == null)
            return 0;
        return 1 + m.x + m.y * rc.getMapWidth();
    }

    private static MapLocation intToLocation(RobotController rc, int m) {
        if (m == 0) {
            return null;
        }
        m--;
        return new MapLocation(m % rc.getMapWidth(), m / rc.getMapWidth());
    }

}