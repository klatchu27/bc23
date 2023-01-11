package bot1;

import battlecode.common.*;

public strictfp class Communication {
    private static final int NUM_TYPES = 6;
    private static final int MIN_ENEMY_IDX = 49;
    private static final int MIN_WELL_IDX = 37;
    private static final int MAX_WELL_IDX = 49;
    private static final int MIN_HQ_IDX = 13;
    private static final int MAX_HQ_IDX = 17;
    public static final int MIN_ISLAND_IDX = 19;
    public static final int MAX_ISLAND_IDX = 33;
    public static final int MIN_EXPLORE_IDX = 33;
    public static final int MAX_EXPLORE_IDX = 37;

    static void reportAlive(RobotController rc) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;

        final int typeIdx = typeToIndex(rc.getType());

        try {
            // Zero out in-progress counts if necessary
            if (rc.readSharedArray(0) != rc.getRoundNum()) {
                final int thisRound = rc.getRoundNum() % 2;
                for (int i = 0; i < NUM_TYPES; i++) {
                    if (rc.readSharedArray(thisRound * NUM_TYPES + i + 1) != 0) {
                        rc.writeSharedArray(thisRound * NUM_TYPES + i + 1, 0);
                    }
                }
                rc.writeSharedArray(0, rc.getRoundNum());
            }

            // Increment alive counter
            final int arrayIdx = (rc.getRoundNum() % 2) * NUM_TYPES + typeIdx + 1;
            rc.writeSharedArray(arrayIdx, rc.readSharedArray(arrayIdx) + 1);
        } catch (GameActionException e) {
            e.printStackTrace();
        }
    }

    static int getAlive(RobotController rc, RobotType type) {
        final int typeIdx = typeToIndex(type);

        // Read from previous write cycle
        final int arrayIdx = ((rc.getRoundNum() + 1) % 2) * NUM_TYPES + typeIdx + 1;
        try {
            return rc.readSharedArray(arrayIdx);
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
                value = rc.readSharedArray(i);
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
                rc.writeSharedArray(slot, locationToInt(rc, loc));
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
                value = rc.readSharedArray(i);
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
    static void clearExploreLoc(RobotController rc, int radius) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;
        MapLocation curLoc = rc.getLocation();
        for (int i = MIN_EXPLORE_IDX; i < MAX_EXPLORE_IDX; i++) {
            int value;
            try {
                value = rc.readSharedArray(i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value);
            if (m == null || curLoc.distanceSquaredTo(m) > radius) { // We might want a stronger check than this
                continue;
            }
            try {
                rc.writeSharedArray(i, 0);
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
                value = rc.readSharedArray(i) / 8;
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
                rc.writeSharedArray(slot, value);
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
                value = rc.readSharedArray(i);
            } catch (GameActionException e) {
                continue;
            }
            requirement = (value % 2) + 2 * ((value / 2) % 2);
            int anchorAvailable = (value / 4) % 2;
            final MapLocation m = intToLocation(rc, value / 8);
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
                value = rc.readSharedArray(i);
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
                System.out.println(String.format("reporting well location at (%d,%d)", wellLoc.x, wellLoc.y));
                rc.writeSharedArray(slot, locationToInt(rc, wellLoc));
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
                value = rc.readSharedArray(i);
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
        Team opponent = rc.getTeam().opponent();
        int islandType = 0;// 0 free, 1 own occupied, 2 enemy occupied

        for (int islandIdx : islands) {

            MapLocation[] thisIslandLocs = null;
            try {
                if (rc.senseAnchor(islandIdx) != null) {
                    islandType = 1;
                    if (rc.senseTeamOccupyingIsland(islandIdx) == opponent)
                        islandType = 2;
                }
                thisIslandLocs = rc.senseNearbyIslandLocations(islandIdx);

            } catch (GameActionException e) {
                System.out.println("ISLAND INDEX problem");
                continue;
            }

            for (MapLocation islandLoc : thisIslandLocs) {

                try {
                    if (rc.senseIsland(islandLoc) == -1)
                        System.out.println(String.format("%d,%d is not an island", islandLoc.x, islandLoc.y));
                } catch (GameActionException e) {
                    ;
                }

                int slot = -1;
                for (int i = MIN_ISLAND_IDX; i < MAX_ISLAND_IDX; i++) {
                    int value;
                    try {
                        value = rc.readSharedArray(i);
                    } catch (GameActionException e) {
                        continue;
                    }
                    final MapLocation m = intToLocation(rc, value / 4);
                    if (m == null && slot == -1) {
                        slot = i;
                    } else if (m != null && islandLoc.distanceSquaredTo(m) <= 16) {
                        // another island loc is with 4**2 units , then skip this location
                        slot = -1;
                        break;
                    }
                }
                if (slot != -1) {
                    try {
                        rc.setIndicatorDot(islandLoc, 200, 0, 0);
                        System.out.println(
                                String.format("Writing island at %d,%d at index:%d", islandLoc.x, islandLoc.y, slot));
                        rc.writeSharedArray(slot, locationToInt(rc, islandLoc) * 4 + islandType);
                        break;// write one location for one island only
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                }

            }

        }

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
                value = rc.readSharedArray(i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value / 4);
            int type = (value % 2) + 2 * ((value / 2) % 2);
            if (m != null && (type == reqType)
                    && (answer == null || curLoc.distanceSquaredTo(m) < curLoc.distanceSquaredTo(answer))) {
                answer = m;
                if (answer.x == 2 && answer.y == 6)
                    System.out.println(String.format("index of 2,6 is %d type:%d", i, type));
            }
        }

        return answer;
    }

    static void updateIslandType(RobotController rc) {
        for (int i = MIN_ISLAND_IDX; i < MAX_ISLAND_IDX; i++) {
            final int value;
            try {
                value = rc.readSharedArray(i);
            } catch (GameActionException e) {
                continue;
            }
            final MapLocation m = intToLocation(rc, value / 4);
            int oldType = (value % 2) + 2 * ((value / 2) % 2);
            if (m == null || !rc.canSenseLocation(m)) { // We might want a stronger check than this
                continue;
            }
            try {
                int islandIdx = rc.senseIsland(m);
                if (islandIdx == -1)
                    rc.writeSharedArray(i, 0);
                else {

                    int newType = 0;
                    if (rc.senseAnchor(islandIdx) != null) {
                        newType = 2;
                        if (rc.senseTeamOccupyingIsland(islandIdx) == rc.getTeam())
                            newType = 1;
                    }

                    if (oldType != newType) {
                        System.out.println(String.format("UPDATING ISLAND TYPE at %d,%d at index:%d", m.x, m.y, i));
                        rc.writeSharedArray(i, ((value / 4) * 4) + newType);
                    }

                }
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    static void reportEnemy(RobotController rc, MapLocation enemy) {
        // check if we can write to the shared array b4 reporting
        if (!rc.canWriteSharedArray(0, 0))
            return;

        int slot = -1;
        for (int i = MIN_ENEMY_IDX; i < GameConstants.SHARED_ARRAY_LENGTH; i++) {
            final int value;
            try {
                value = rc.readSharedArray(i);
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
                rc.writeSharedArray(slot, locationToInt(rc, enemy));
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
    }

    static MapLocation getClosestEnemy(RobotController rc) {
        MapLocation answer = null;
        for (int i = MIN_ENEMY_IDX; i < GameConstants.SHARED_ARRAY_LENGTH; i++) {
            final int value;
            try {
                value = rc.readSharedArray(i);
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
        for (int i = MIN_ENEMY_IDX; i < GameConstants.SHARED_ARRAY_LENGTH; i++) {
            final int value;
            try {
                value = rc.readSharedArray(i);
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
                    rc.writeSharedArray(i, locationToInt(rc, null));
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

    private static int locationToInt(RobotController rc, MapLocation m) {
        if (m == null) {
            return 0;
        }
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