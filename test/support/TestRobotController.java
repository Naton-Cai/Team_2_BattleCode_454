package support;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test version of {@link RobotController}.
 *
 * <p>A test constructs one instance and sets the answers for the scenario. Those answers can
 * change between turns on the same instance, and recorded calls stay with it. Each method returns
 * the value set on this instance. Action methods record the call, then throw the {@link
 * GameActionException} set for that action when one was set.
 */
public final class TestRobotController implements RobotController {

  private static final MapLocation[] NO_LOCATIONS = new MapLocation[0];
  private static final RobotInfo[] NO_ROBOTS = new RobotInfo[0];
  private static final MapInfo[] NO_MAP_INFOS = new MapInfo[0];
  private static final Message[] NO_MESSAGES = new Message[0];

  private int roundNum;
  private int mapWidth;
  private int mapHeight;
  private boolean cooperation;
  private Team backstabbingTeam;
  private int numberOfCatTraps;
  private int numberOfRatTraps;
  private int id;
  private Team team = Team.A;
  private MapLocation location = new MapLocation(0, 0);
  private MapLocation[] allPartLocations = NO_LOCATIONS;
  private Direction direction = Direction.NORTH;
  private int health;
  private int rawCheese;
  private int globalCheese;
  private int allCheese;
  private int dirt;
  private UnitType unitType = UnitType.BABY_RAT;
  private RobotInfo carrying;
  private boolean beingThrown;
  private boolean beingCarried;
  private boolean onTheMap;
  private boolean canSenseLocation;
  private boolean locationOccupied;
  private GameActionException locationOccupiedFailure;
  private boolean canSenseRobotAtLocation;
  private RobotInfo robotAtLocation;
  private GameActionException robotAtLocationFailure;
  private boolean canSenseRobot;
  private RobotInfo sensedRobot;
  private GameActionException sensedRobotFailure;
  private RobotInfo[] nearbyRobots = NO_ROBOTS;
  private RobotInfo[] nearbyRobotsInRadius = NO_ROBOTS;
  private GameActionException nearbyRobotsInRadiusFailure;
  private RobotInfo[] nearbyRobotsOnTeam = NO_ROBOTS;
  private GameActionException nearbyRobotsOnTeamFailure;
  private RobotInfo[] nearbyRobotsAround = NO_ROBOTS;
  private GameActionException nearbyRobotsAroundFailure;
  private boolean passable;
  private GameActionException passableFailure;
  private MapInfo mapInfo;
  private GameActionException mapInfoFailure;
  private MapInfo[] nearbyMapInfos = NO_MAP_INFOS;
  private MapInfo[] nearbyMapInfosInRadius = NO_MAP_INFOS;
  private GameActionException nearbyMapInfosInRadiusFailure;
  private MapInfo[] nearbyMapInfosAtCenter = NO_MAP_INFOS;
  private GameActionException nearbyMapInfosAtCenterFailure;
  private MapInfo[] nearbyMapInfosAround = NO_MAP_INFOS;
  private GameActionException nearbyMapInfosAroundFailure;
  private MapLocation adjacentLocation;
  private MapLocation[] locationsInRadius = NO_LOCATIONS;
  private GameActionException locationsInRadiusFailure;
  private boolean actionReady;
  private int actionCooldownTurns;
  private boolean movementReady;
  private boolean turningReady;
  private int movementCooldownTurns;
  private int turningCooldownTurns;
  private boolean forwardAllowed;
  private RuntimeException forwardFailure;
  private boolean canMove;
  private final EnumMap<Direction, Boolean> directionalMoves = new EnumMap<>(Direction.class);
  private boolean simulateMovement;
  private final int[] sharedArray = new int[64];
  private int failedWriteNumber = -1;
  private int sharedWriteCount;
  private int moveCount;
  private GameActionException moveFailure;
  private GameActionException moveToFailure;
  private boolean canTurn;
  private boolean turnAllowed = true;
  private int turnCount;
  private GameActionException turnFailure;
  private final List<Direction> turns = new ArrayList<>();
  private int currentRatCost;
  private boolean canBuildRat;
  private GameActionException buildRatFailure;
  private boolean canBecomeRatKing;
  private GameActionException becomeRatKingFailure;
  private boolean canPlaceDirt;
  private GameActionException placeDirtFailure;
  private boolean canRemoveDirt;
  private GameActionException removeDirtFailure;
  private boolean canPlaceRatTrap;
  private GameActionException placeRatTrapFailure;
  private boolean canRemoveRatTrap;
  private GameActionException removeRatTrapFailure;
  private boolean canPlaceCatTrap;
  private GameActionException placeCatTrapFailure;
  private boolean canRemoveCatTrap;
  private GameActionException removeCatTrapFailure;
  private boolean canPickUpCheese;
  private Set<MapLocation> pickUpCheeseLocations;
  private int cheesePickupYield;
  private GameActionException pickUpCheeseFailure;
  private GameActionException pickUpCheeseAmountFailure;
  private boolean canAttack;
  private boolean canAttackWithCheese;
  private GameActionException attackFailure;
  private GameActionException attackWithCheeseFailure;
  private boolean squeakAccepted;
  private Message[] squeaks = NO_MESSAGES;
  private GameActionException writeSharedArrayFailure;
  private int sharedArrayReads;
  private GameActionException readSharedArrayFailure;
  private boolean canTransferCheese;
  private GameActionException transferCheeseFailure;
  private GameActionException throwRatFailure;
  private boolean canThrowRat;
  private GameActionException dropRatFailure;
  private boolean canDropRat;
  private boolean canCarryRat;
  private GameActionException carryRatFailure;
  private GameActionException indicatorDotFailure;
  private GameActionException indicatorLineFailure;
  private int typeReads;
  private final List<String> calls = new ArrayList<>();

  /** Arranges {@link #canMoveForward()} and clears any forward-check failure. */
  public void setForwardAllowed(boolean allowed) {
    forwardAllowed = allowed;
    forwardFailure = null;
  }

  /** Makes {@link #canMoveForward()} throw before either action branch runs. */
  public void setForwardFailure(RuntimeException failure) {
    forwardFailure = failure;
  }

  /** Makes {@link #moveForward()} throw after the call is recorded. */
  public void setMoveFailure(GameActionException failure) {
    moveFailure = failure;
  }

  /** Arranges {@link #canTurn(Direction)}. */
  public void setTurnAllowed(boolean allowed) {
    turnAllowed = allowed;
  }

  /** Makes {@link #turn(Direction)} throw after the call is recorded. */
  public void setTurnFailure(GameActionException failure) {
    turnFailure = failure;
  }

  /** Opt-in state changes keep legacy tests' canned controller answers unchanged. */
  public void setSimulateMovement(boolean simulateMovement) {
    this.simulateMovement = simulateMovement;
  }

  /** Arranges a footprint-legality answer for one direction, without modeling engine geometry. */
  public void setDirectionAllowed(Direction direction, boolean allowed) {
    directionalMoves.put(direction, allowed);
  }

  /** Fails a particular write, counting from the next write as 1. */
  public void failSharedWrite(int number, GameActionException failure) {
    failedWriteNumber = sharedWriteCount + number;
    writeSharedArrayFailure = failure;
  }

  public int moveCount() {
    return moveCount;
  }

  public int turnCount() {
    return turnCount;
  }

  public int typeReads() {
    return typeReads;
  }

  public List<Direction> turns() {
    return turns;
  }

  /** Action calls recorded on this instance, in order. */
  public List<String> calls() {
    return calls;
  }

  public void setRoundNum(int roundNum) {
    this.roundNum = roundNum;
  }

  public void setMapWidth(int mapWidth) {
    this.mapWidth = mapWidth;
  }

  public void setMapHeight(int mapHeight) {
    this.mapHeight = mapHeight;
  }

  public void setCooperation(boolean cooperation) {
    this.cooperation = cooperation;
  }

  public void setBackstabbingTeam(Team backstabbingTeam) {
    this.backstabbingTeam = backstabbingTeam;
  }

  public void setNumberOfCatTraps(int numberOfCatTraps) {
    this.numberOfCatTraps = numberOfCatTraps;
  }

  public void setNumberOfRatTraps(int numberOfRatTraps) {
    this.numberOfRatTraps = numberOfRatTraps;
  }

  public void setId(int id) {
    this.id = id;
  }

  public void setTeam(Team team) {
    this.team = team;
  }

  public void setLocation(MapLocation location) {
    this.location = location;
  }

  public void setAllPartLocations(MapLocation[] allPartLocations) {
    this.allPartLocations = allPartLocations;
  }

  public void setDirection(Direction direction) {
    this.direction = direction;
  }

  public void setHealth(int health) {
    this.health = health;
  }

  public void setRawCheese(int rawCheese) {
    this.rawCheese = rawCheese;
  }

  public void setGlobalCheese(int globalCheese) {
    this.globalCheese = globalCheese;
  }

  public void setAllCheese(int allCheese) {
    this.allCheese = allCheese;
  }

  public void setDirt(int dirt) {
    this.dirt = dirt;
  }

  public void setUnitType(UnitType unitType) {
    this.unitType = unitType;
  }

  public void setCarrying(RobotInfo carrying) {
    this.carrying = carrying;
  }

  public void setBeingThrown(boolean beingThrown) {
    this.beingThrown = beingThrown;
  }

  public void setBeingCarried(boolean beingCarried) {
    this.beingCarried = beingCarried;
  }

  public void setOnTheMap(boolean onTheMap) {
    this.onTheMap = onTheMap;
  }

  public void setCanSenseLocation(boolean canSenseLocation) {
    this.canSenseLocation = canSenseLocation;
  }

  public void setLocationOccupied(boolean locationOccupied) {
    this.locationOccupied = locationOccupied;
  }

  public void setLocationOccupiedFailure(GameActionException failure) {
    locationOccupiedFailure = failure;
  }

  public void setCanSenseRobotAtLocation(boolean canSenseRobotAtLocation) {
    this.canSenseRobotAtLocation = canSenseRobotAtLocation;
  }

  public void setRobotAtLocation(RobotInfo robotAtLocation) {
    this.robotAtLocation = robotAtLocation;
  }

  public void setRobotAtLocationFailure(GameActionException failure) {
    robotAtLocationFailure = failure;
  }

  public void setCanSenseRobot(boolean canSenseRobot) {
    this.canSenseRobot = canSenseRobot;
  }

  public void setSensedRobot(RobotInfo sensedRobot) {
    this.sensedRobot = sensedRobot;
  }

  public void setSensedRobotFailure(GameActionException failure) {
    sensedRobotFailure = failure;
  }

  public void setNearbyRobots(RobotInfo[] nearbyRobots) {
    this.nearbyRobots = nearbyRobots;
  }

  public void setNearbyRobotsInRadius(RobotInfo[] nearbyRobotsInRadius) {
    this.nearbyRobotsInRadius = nearbyRobotsInRadius;
  }

  public void setNearbyRobotsInRadiusFailure(GameActionException failure) {
    nearbyRobotsInRadiusFailure = failure;
  }

  public void setNearbyRobotsOnTeam(RobotInfo[] nearbyRobotsOnTeam) {
    this.nearbyRobotsOnTeam = nearbyRobotsOnTeam;
  }

  public void setNearbyRobotsOnTeamFailure(GameActionException failure) {
    nearbyRobotsOnTeamFailure = failure;
  }

  public void setNearbyRobotsAround(RobotInfo[] nearbyRobotsAround) {
    this.nearbyRobotsAround = nearbyRobotsAround;
  }

  public void setNearbyRobotsAroundFailure(GameActionException failure) {
    nearbyRobotsAroundFailure = failure;
  }

  public void setPassable(boolean passable) {
    this.passable = passable;
  }

  public void setPassableFailure(GameActionException failure) {
    passableFailure = failure;
  }

  public void setMapInfo(MapInfo mapInfo) {
    this.mapInfo = mapInfo;
  }

  public void setMapInfoFailure(GameActionException failure) {
    mapInfoFailure = failure;
  }

  public void setNearbyMapInfos(MapInfo[] nearbyMapInfos) {
    this.nearbyMapInfos = nearbyMapInfos;
  }

  public void setNearbyMapInfosInRadius(MapInfo[] nearbyMapInfosInRadius) {
    this.nearbyMapInfosInRadius = nearbyMapInfosInRadius;
  }

  public void setNearbyMapInfosInRadiusFailure(GameActionException failure) {
    nearbyMapInfosInRadiusFailure = failure;
  }

  public void setNearbyMapInfosAtCenter(MapInfo[] nearbyMapInfosAtCenter) {
    this.nearbyMapInfosAtCenter = nearbyMapInfosAtCenter;
  }

  public void setNearbyMapInfosAtCenterFailure(GameActionException failure) {
    nearbyMapInfosAtCenterFailure = failure;
  }

  public void setNearbyMapInfosAround(MapInfo[] nearbyMapInfosAround) {
    this.nearbyMapInfosAround = nearbyMapInfosAround;
  }

  public void setNearbyMapInfosAroundFailure(GameActionException failure) {
    nearbyMapInfosAroundFailure = failure;
  }

  public void setAdjacentLocation(MapLocation adjacentLocation) {
    this.adjacentLocation = adjacentLocation;
  }

  public void setLocationsInRadius(MapLocation[] locationsInRadius) {
    this.locationsInRadius = locationsInRadius;
  }

  public void setLocationsInRadiusFailure(GameActionException failure) {
    locationsInRadiusFailure = failure;
  }

  public void setActionReady(boolean actionReady) {
    this.actionReady = actionReady;
  }

  public void setActionCooldownTurns(int actionCooldownTurns) {
    this.actionCooldownTurns = actionCooldownTurns;
  }

  public void setMovementReady(boolean movementReady) {
    this.movementReady = movementReady;
  }

  public void setTurningReady(boolean turningReady) {
    this.turningReady = turningReady;
  }

  public void setMovementCooldownTurns(int movementCooldownTurns) {
    this.movementCooldownTurns = movementCooldownTurns;
  }

  public void setTurningCooldownTurns(int turningCooldownTurns) {
    this.turningCooldownTurns = turningCooldownTurns;
  }

  /** Arranges {@link #canMove(Direction)}. This is separate from {@link #canMoveForward()}. */
  public void setCanMove(boolean canMove) {
    this.canMove = canMove;
  }

  /** Makes {@link #move(Direction)} throw after the call is recorded. */
  public void setMoveToFailure(GameActionException failure) {
    moveToFailure = failure;
  }

  /** Arranges {@link #canTurn()} with no direction. {@link #setTurnAllowed} arranges the other. */
  public void setCanTurn(boolean canTurn) {
    this.canTurn = canTurn;
  }

  public void setCurrentRatCost(int currentRatCost) {
    this.currentRatCost = currentRatCost;
  }

  public void setCanBuildRat(boolean canBuildRat) {
    this.canBuildRat = canBuildRat;
  }

  public void setBuildRatFailure(GameActionException failure) {
    buildRatFailure = failure;
  }

  public void setCanBecomeRatKing(boolean canBecomeRatKing) {
    this.canBecomeRatKing = canBecomeRatKing;
  }

  public void setBecomeRatKingFailure(GameActionException failure) {
    becomeRatKingFailure = failure;
  }

  public void setCanPlaceDirt(boolean canPlaceDirt) {
    this.canPlaceDirt = canPlaceDirt;
  }

  public void setPlaceDirtFailure(GameActionException failure) {
    placeDirtFailure = failure;
  }

  public void setCanRemoveDirt(boolean canRemoveDirt) {
    this.canRemoveDirt = canRemoveDirt;
  }

  public void setRemoveDirtFailure(GameActionException failure) {
    removeDirtFailure = failure;
  }

  public void setCanPlaceRatTrap(boolean canPlaceRatTrap) {
    this.canPlaceRatTrap = canPlaceRatTrap;
  }

  public void setPlaceRatTrapFailure(GameActionException failure) {
    placeRatTrapFailure = failure;
  }

  public void setCanRemoveRatTrap(boolean canRemoveRatTrap) {
    this.canRemoveRatTrap = canRemoveRatTrap;
  }

  public void setRemoveRatTrapFailure(GameActionException failure) {
    removeRatTrapFailure = failure;
  }

  public void setCanPlaceCatTrap(boolean canPlaceCatTrap) {
    this.canPlaceCatTrap = canPlaceCatTrap;
  }

  public void setPlaceCatTrapFailure(GameActionException failure) {
    placeCatTrapFailure = failure;
  }

  public void setCanRemoveCatTrap(boolean canRemoveCatTrap) {
    this.canRemoveCatTrap = canRemoveCatTrap;
  }

  public void setRemoveCatTrapFailure(GameActionException failure) {
    removeCatTrapFailure = failure;
  }

  public void setCanPickUpCheese(boolean canPickUpCheese) {
    this.canPickUpCheese = canPickUpCheese;
    pickUpCheeseLocations = null;
  }

  /**
   * Restricts {@link #canPickUpCheese(MapLocation)} to these tiles. Adjacent pickup loops every
   * neighbor, so a global true would claim empty tiles too.
   */
  public void setCanPickUpCheeseAt(MapLocation... locations) {
    pickUpCheeseLocations = new HashSet<>(Arrays.asList(locations));
  }

  /**
   * Raw cheese added by {@link #pickUpCheese(MapLocation)} after a successful call. The default 0
   * leaves the stash unchanged.
   */
  public void setCheesePickupYield(int cheesePickupYield) {
    this.cheesePickupYield = cheesePickupYield;
  }

  public void setPickUpCheeseFailure(GameActionException failure) {
    pickUpCheeseFailure = failure;
  }

  public void setPickUpCheeseAmountFailure(GameActionException failure) {
    pickUpCheeseAmountFailure = failure;
  }

  /** Arranges {@link #canAttack(MapLocation)}. */
  public void setCanAttack(boolean canAttack) {
    this.canAttack = canAttack;
  }

  /** Arranges {@link #canAttack(MapLocation, int)}. */
  public void setCanAttackWithCheese(boolean canAttackWithCheese) {
    this.canAttackWithCheese = canAttackWithCheese;
  }

  public void setAttackFailure(GameActionException failure) {
    attackFailure = failure;
  }

  public void setAttackWithCheeseFailure(GameActionException failure) {
    attackWithCheeseFailure = failure;
  }

  public void setSqueakAccepted(boolean squeakAccepted) {
    this.squeakAccepted = squeakAccepted;
  }

  public void setSqueaks(Message[] squeaks) {
    this.squeaks = squeaks;
  }

  public void setWriteSharedArrayFailure(GameActionException failure) {
    writeSharedArrayFailure = failure;
  }

  public void setSharedArrayValue(int sharedArrayValue) {
    Arrays.fill(sharedArray, sharedArrayValue);
  }

  /** Sets one shared-array slot. The index must be at least 0 and less than the array length. */
  public void setSharedArrayValue(int index, int value) {
    if (index < 0 || index >= sharedArray.length) {
      throw new IllegalArgumentException("shared array index " + index);
    }
    sharedArray[index] = value;
  }

  /** How many times {@link #readSharedArray(int)} was called on this instance. */
  public int sharedArrayReadCount() {
    return sharedArrayReads;
  }

  public void setReadSharedArrayFailure(GameActionException failure) {
    readSharedArrayFailure = failure;
  }

  public void setCanTransferCheese(boolean canTransferCheese) {
    this.canTransferCheese = canTransferCheese;
  }

  public void setTransferCheeseFailure(GameActionException failure) {
    transferCheeseFailure = failure;
  }

  public void setThrowRatFailure(GameActionException failure) {
    throwRatFailure = failure;
  }

  public void setCanThrowRat(boolean canThrowRat) {
    this.canThrowRat = canThrowRat;
  }

  public void setDropRatFailure(GameActionException failure) {
    dropRatFailure = failure;
  }

  public void setCanDropRat(boolean canDropRat) {
    this.canDropRat = canDropRat;
  }

  public void setCanCarryRat(boolean canCarryRat) {
    this.canCarryRat = canCarryRat;
  }

  public void setCarryRatFailure(GameActionException failure) {
    carryRatFailure = failure;
  }

  public void setIndicatorDotFailure(GameActionException failure) {
    indicatorDotFailure = failure;
  }

  public void setIndicatorLineFailure(GameActionException failure) {
    indicatorLineFailure = failure;
  }

  @Override
  public int getRoundNum() {
    return roundNum;
  }

  @Override
  public int getMapWidth() {
    return mapWidth;
  }

  @Override
  public int getMapHeight() {
    return mapHeight;
  }

  @Override
  public boolean isCooperation() {
    return cooperation;
  }

  @Override
  public Team getBackstabbingTeam() {
    return backstabbingTeam;
  }

  @Override
  public int getNumberCatTraps() {
    return numberOfCatTraps;
  }

  @Override
  public int getNumberRatTraps() {
    return numberOfRatTraps;
  }

  @Override
  public int getID() {
    return id;
  }

  @Override
  public Team getTeam() {
    return team;
  }

  @Override
  public MapLocation getLocation() {
    return location;
  }

  @Override
  public MapLocation[] getAllPartLocations() {
    return allPartLocations;
  }

  @Override
  public Direction getDirection() {
    return direction;
  }

  @Override
  public int getHealth() {
    return health;
  }

  @Override
  public int getRawCheese() {
    return rawCheese;
  }

  @Override
  public int getGlobalCheese() {
    return globalCheese;
  }

  @Override
  public int getAllCheese() {
    return allCheese;
  }

  @Override
  public int getDirt() {
    return dirt;
  }

  @Override
  public UnitType getType() {
    typeReads += 1;
    return unitType;
  }

  @Override
  public RobotInfo getCarrying() {
    return carrying;
  }

  @Override
  public boolean isBeingThrown() {
    return beingThrown;
  }

  @Override
  public boolean isBeingCarried() {
    return beingCarried;
  }

  @Override
  public boolean onTheMap(MapLocation loc) {
    return onTheMap;
  }

  @Override
  public boolean canSenseLocation(MapLocation loc) {
    return canSenseLocation;
  }

  @Override
  public boolean isLocationOccupied(MapLocation loc) throws GameActionException {
    throwIfSet(locationOccupiedFailure);
    return locationOccupied;
  }

  @Override
  public boolean canSenseRobotAtLocation(MapLocation loc) {
    return canSenseRobotAtLocation;
  }

  @Override
  public RobotInfo senseRobotAtLocation(MapLocation loc) throws GameActionException {
    throwIfSet(robotAtLocationFailure);
    return robotAtLocation;
  }

  @Override
  public boolean canSenseRobot(int id) {
    return canSenseRobot;
  }

  @Override
  public RobotInfo senseRobot(int robotId) throws GameActionException {
    throwIfSet(sensedRobotFailure);
    return sensedRobot;
  }

  @Override
  public RobotInfo[] senseNearbyRobots() {
    return nearbyRobots;
  }

  @Override
  public RobotInfo[] senseNearbyRobots(int radiusSquared) throws GameActionException {
    throwIfSet(nearbyRobotsInRadiusFailure);
    return nearbyRobotsInRadius;
  }

  @Override
  public RobotInfo[] senseNearbyRobots(int radiusSquared, Team sensedTeam)
      throws GameActionException {
    throwIfSet(nearbyRobotsOnTeamFailure);
    return nearbyRobotsOnTeam;
  }

  @Override
  public RobotInfo[] senseNearbyRobots(MapLocation center, int radiusSquared, Team sensedTeam)
      throws GameActionException {
    throwIfSet(nearbyRobotsAroundFailure);
    return nearbyRobotsAround;
  }

  @Override
  public boolean sensePassability(MapLocation loc) throws GameActionException {
    throwIfSet(passableFailure);
    return passable;
  }

  @Override
  public MapInfo senseMapInfo(MapLocation loc) throws GameActionException {
    throwIfSet(mapInfoFailure);
    return mapInfo;
  }

  @Override
  public MapInfo[] senseNearbyMapInfos() {
    return nearbyMapInfos;
  }

  @Override
  public MapInfo[] senseNearbyMapInfos(int radiusSquared) throws GameActionException {
    throwIfSet(nearbyMapInfosInRadiusFailure);
    return nearbyMapInfosInRadius;
  }

  @Override
  public MapInfo[] senseNearbyMapInfos(MapLocation center) throws GameActionException {
    throwIfSet(nearbyMapInfosAtCenterFailure);
    return nearbyMapInfosAtCenter;
  }

  @Override
  public MapInfo[] senseNearbyMapInfos(MapLocation center, int radiusSquared)
      throws GameActionException {
    throwIfSet(nearbyMapInfosAroundFailure);
    return nearbyMapInfosAround;
  }

  @Override
  public MapLocation adjacentLocation(Direction dir) {
    return adjacentLocation;
  }

  @Override
  public MapLocation[] getAllLocationsWithinRadiusSquared(MapLocation center, int radiusSquared)
      throws GameActionException {
    throwIfSet(locationsInRadiusFailure);
    return locationsInRadius;
  }

  @Override
  public boolean isActionReady() {
    return actionReady;
  }

  @Override
  public int getActionCooldownTurns() {
    return actionCooldownTurns;
  }

  @Override
  public boolean isMovementReady() {
    return movementReady;
  }

  @Override
  public boolean isTurningReady() {
    return turningReady;
  }

  @Override
  public int getMovementCooldownTurns() {
    return movementCooldownTurns;
  }

  @Override
  public int getTurningCooldownTurns() {
    return turningCooldownTurns;
  }

  @Override
  public boolean canMoveForward() {
    if (forwardFailure != null) {
      throw forwardFailure;
    }
    return directionalMoves.getOrDefault(direction, forwardAllowed);
  }

  @Override
  public boolean canMove(Direction moveDirection) {
    return directionalMoves.getOrDefault(moveDirection, canMove);
  }

  @Override
  public void moveForward() throws GameActionException {
    moveCount += 1;
    record("moveForward");
    throwIfSet(moveFailure);
    if (simulateMovement) {
      location = location.add(direction);
    }
  }

  @Override
  public void move(Direction moveDirection) throws GameActionException {
    record("move:" + moveDirection);
    throwIfSet(moveToFailure);
  }

  @Override
  public boolean canTurn() {
    return canTurn;
  }

  @Override
  public boolean canTurn(Direction turnDirection) {
    return turnAllowed;
  }

  @Override
  public void turn(Direction turnDirection) throws GameActionException {
    turnCount += 1;
    turns.add(turnDirection);
    record("turn:" + turnDirection);
    throwIfSet(turnFailure);
    if (simulateMovement) {
      direction = turnDirection;
    }
  }

  @Override
  public int getCurrentRatCost() {
    return currentRatCost;
  }

  @Override
  public boolean canBuildRat(MapLocation loc) {
    return canBuildRat;
  }

  @Override
  public void buildRat(MapLocation loc) throws GameActionException {
    record("buildRat:" + loc);
    throwIfSet(buildRatFailure);
  }

  @Override
  public boolean canBecomeRatKing() {
    return canBecomeRatKing;
  }

  @Override
  public void becomeRatKing() throws GameActionException {
    record("becomeRatKing");
    throwIfSet(becomeRatKingFailure);
  }

  @Override
  public boolean canPlaceDirt(MapLocation loc) {
    return canPlaceDirt;
  }

  @Override
  public void placeDirt(MapLocation loc) throws GameActionException {
    record("placeDirt:" + loc);
    throwIfSet(placeDirtFailure);
  }

  @Override
  public boolean canRemoveDirt(MapLocation loc) {
    return canRemoveDirt;
  }

  @Override
  public void removeDirt(MapLocation loc) throws GameActionException {
    record("removeDirt:" + loc);
    throwIfSet(removeDirtFailure);
  }

  @Override
  public boolean canPlaceRatTrap(MapLocation loc) {
    return canPlaceRatTrap;
  }

  @Override
  public void placeRatTrap(MapLocation loc) throws GameActionException {
    record("placeRatTrap:" + loc);
    throwIfSet(placeRatTrapFailure);
  }

  @Override
  public boolean canRemoveRatTrap(MapLocation loc) {
    return canRemoveRatTrap;
  }

  @Override
  public void removeRatTrap(MapLocation loc) throws GameActionException {
    record("removeRatTrap:" + loc);
    throwIfSet(removeRatTrapFailure);
  }

  @Override
  public boolean canPlaceCatTrap(MapLocation loc) {
    return canPlaceCatTrap;
  }

  @Override
  public void placeCatTrap(MapLocation loc) throws GameActionException {
    record("placeCatTrap:" + loc);
    throwIfSet(placeCatTrapFailure);
  }

  @Override
  public boolean canRemoveCatTrap(MapLocation loc) {
    return canRemoveCatTrap;
  }

  @Override
  public void removeCatTrap(MapLocation loc) throws GameActionException {
    record("removeCatTrap:" + loc);
    throwIfSet(removeCatTrapFailure);
  }

  @Override
  public boolean canPickUpCheese(MapLocation loc) {
    if (pickUpCheeseLocations != null) {
      return pickUpCheeseLocations.contains(loc);
    }
    return canPickUpCheese;
  }

  @Override
  public void pickUpCheese(MapLocation loc) throws GameActionException {
    record("pickUpCheese:" + loc);
    throwIfSet(pickUpCheeseFailure);
    rawCheese += cheesePickupYield;
  }

  @Override
  public void pickUpCheese(MapLocation loc, int pickUpAmount) throws GameActionException {
    record("pickUpCheese:" + loc + ":" + pickUpAmount);
    throwIfSet(pickUpCheeseAmountFailure);
  }

  @Override
  public boolean canAttack(MapLocation loc) {
    return canAttack;
  }

  @Override
  public boolean canAttack(MapLocation loc, int cheeseAmount) {
    return canAttackWithCheese;
  }

  @Override
  public void attack(MapLocation loc) throws GameActionException {
    record("attack:" + loc);
    throwIfSet(attackFailure);
  }

  @Override
  public void attack(MapLocation loc, int cheeseAmount) throws GameActionException {
    record("attack:" + loc + ":" + cheeseAmount);
    throwIfSet(attackWithCheeseFailure);
  }

  @Override
  public boolean squeak(int messageContent) {
    record("squeak:" + messageContent);
    return squeakAccepted;
  }

  @Override
  public Message[] readSqueaks(int round) {
    return squeaks;
  }

  @Override
  public void writeSharedArray(int index, int value) throws GameActionException {
    record("writeSharedArray:" + index + ":" + value);
    sharedWriteCount++;
    if (failedWriteNumber == -1 || failedWriteNumber == sharedWriteCount) {
      throwIfSet(writeSharedArrayFailure);
    }
    sharedArray[index] = value;
  }

  @Override
  public int readSharedArray(int index) throws GameActionException {
    sharedArrayReads += 1;
    throwIfSet(readSharedArrayFailure);
    return sharedArray[index];
  }

  @Override
  public boolean canTransferCheese(MapLocation loc, int amount) {
    return canTransferCheese;
  }

  @Override
  public void transferCheese(MapLocation loc, int amount) throws GameActionException {
    record("transferCheese:" + loc + ":" + amount);
    throwIfSet(transferCheeseFailure);
    rawCheese -= amount;
    if (rawCheese < 0) {
      rawCheese = 0;
    }
  }

  @Override
  public void throwRat() throws GameActionException {
    record("throwRat");
    throwIfSet(throwRatFailure);
  }

  @Override
  public boolean canThrowRat() {
    return canThrowRat;
  }

  @Override
  public void dropRat(Direction dropDirection) throws GameActionException {
    record("dropRat:" + dropDirection);
    throwIfSet(dropRatFailure);
  }

  @Override
  public boolean canDropRat(Direction dropDirection) {
    return canDropRat;
  }

  @Override
  public boolean canCarryRat(MapLocation loc) {
    return canCarryRat;
  }

  @Override
  public void carryRat(MapLocation loc) throws GameActionException {
    record("carryRat:" + loc);
    throwIfSet(carryRatFailure);
  }

  @Override
  public void disintegrate() {
    record("disintegrate");
  }

  @Override
  public void resign() {
    record("resign");
  }

  @Override
  public void setIndicatorString(String string) {
    record("setIndicatorString:" + string);
  }

  @Override
  public void setIndicatorDot(MapLocation loc, int red, int green, int blue)
      throws GameActionException {
    record("setIndicatorDot:" + loc);
    throwIfSet(indicatorDotFailure);
  }

  @Override
  public void setIndicatorLine(
      MapLocation startLoc, MapLocation endLoc, int red, int green, int blue)
      throws GameActionException {
    record("setIndicatorLine:" + startLoc + ":" + endLoc);
    throwIfSet(indicatorLineFailure);
  }

  @Override
  public void setTimelineMarker(String label, int red, int green, int blue) {
    record("setTimelineMarker:" + label);
  }

  private void record(String call) {
    calls.add(call);
  }

  private static void throwIfSet(GameActionException failure) throws GameActionException {
    if (failure != null) {
      throw failure;
    }
  }
}
