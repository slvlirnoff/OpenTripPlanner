package org.opentripplanner.standalone.config.routerequest;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.NA;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_0;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_1;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_2;
import static org.opentripplanner.standalone.config.routerequest.ItineraryFiltersConfig.mapItineraryFilterParams;
import static org.opentripplanner.standalone.config.routerequest.TransferConfig.mapTransferPreferences;
import static org.opentripplanner.standalone.config.routerequest.WheelchairConfig.mapWheelchairPreferences;

import java.time.Duration;
import org.opentripplanner.api.parameter.QualifiedModeSet;
import org.opentripplanner.routing.api.request.RequestModes;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.preference.BikePreferences;
import org.opentripplanner.routing.api.request.preference.CarPreferences;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.preference.StreetPreferences;
import org.opentripplanner.routing.api.request.preference.SystemPreferences;
import org.opentripplanner.routing.api.request.preference.TransitPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleParkingPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleRentalPreferences;
import org.opentripplanner.routing.api.request.preference.WalkPreferences;
import org.opentripplanner.routing.api.request.request.VehicleParkingRequest;
import org.opentripplanner.routing.api.request.request.VehicleRentalRequest;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.standalone.config.sandbox.DataOverlayParametersMapper;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.util.OTPFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteRequestConfig {

  private static final Logger LOG = LoggerFactory.getLogger(RouteRequestConfig.class);
  private static final String WHEELCHAIR_ACCESSIBILITY = "wheelchairAccessibility";

  public static RouteRequest mapDefaultRouteRequest(NodeAdapter root, String parameterName) {
    var c = root
      .of(parameterName)
      .since(V2_0)
      .summary("The default parameters for the routing query.")
      .description("Most of these are overridable through the various API endpoints.")
      .asObject();
    return mapRouteRequest(c);
  }

  public static RouteRequest mapRouteRequest(NodeAdapter c) {
    RouteRequest dft = new RouteRequest();

    if (c.isEmpty()) {
      return dft;
    }

    LOG.debug("Loading default routing parameters from JSON.");
    RouteRequest request = new RouteRequest();
    VehicleRentalRequest vehicleRental = request.journey().rental();
    VehicleParkingRequest vehicleParking = request.journey().parking();

    // Keep this alphabetically sorted so it is easy to check if a parameter is missing from the
    // mapping or duplicate exist.

    vehicleRental.setAllowedNetworks(
      c
        .of("allowedVehicleRentalNetworks")
        .since(NA)
        .summary(
          "The vehicle rental networks which may be used. If empty all networks may be used."
        )
        .asStringSet(vehicleRental.allowedNetworks())
    );
    request.setArriveBy(
      c
        .of("arriveBy")
        .since(NA)
        .summary("Whether the trip should depart or arrive at the specified date and time.")
        .asBoolean(dft.arriveBy())
    );
    vehicleParking.setBannedTags(
      c
        .of("bannedVehicleParkingTags")
        .since(NA)
        .summary("Tags with which a vehicle parking will not be used. If empty, no tags are banned")
        .asStringSet(vehicleParking.bannedTags())
    );
    vehicleRental.setBannedNetworks(
      c
        .of("bannedVehicleRentalNetworks")
        .since(NA)
        .summary(
          "he vehicle rental networks which may not be used. If empty, no networks are banned."
        )
        .asStringSet(vehicleRental.bannedNetworks())
    );

    request
      .journey()
      .rental()
      .setAllowArrivingInRentedVehicleAtDestination(
        c
          .of("allowKeepingRentedBicycleAtDestination")
          .since(NA)
          .summary(
            "If a vehicle should be allowed to be kept at the end of a station-based rental."
          )
          .asBoolean(request.journey().rental().allowArrivingInRentedVehicleAtDestination())
      );

    request.setLocale(c.of("locale").since(NA).summary("TODO").asLocale(dft.locale()));

    request
      .journey()
      .setModes(
        c
          .of("modes")
          .since(NA)
          .summary("The set of access/egress/direct/transit modes to be used for the route search.")
          .asCustomStringType(
            RequestModes.defaultRequestModes(),
            "TRANSIT,WALK",
            s -> new QualifiedModeSet(s).getRequestModes()
          )
      );

    request.setNumItineraries(
      c
        .of("numItineraries")
        .since(NA)
        .summary("The maximum number of itineraries to return.")
        .asInt(dft.numItineraries())
    );
    request.setSearchWindow(
      c
        .of("searchWindow")
        .since(NA)
        .summary("The duration of the search-window.")
        .description(
          """
  This is the time/duration in seconds from the earliest-departure-time(EDT) to the
  latest-departure-time(LDT). In case of a reverse search it will be the time from earliest to
  latest arrival time (LAT - EAT).

  All optimal travels that depart within the search window is guarantied to be found.

  This is sometimes referred to as the Range Raptor Search Window - but could be used in a none
  Transit search as well; Hence this is named search-window and not raptor-search-window. 


  This is normally dynamically calculated by the server. Use {@code null} to unset, and 
  {@link Duration#ZERO} to do one Raptor iteration. The value is dynamically  assigned a suitable
  value, if not set. In a small to medium size operation you may use a fixed value, like 60 minutes.
  If you have a mixture of high frequency cities routes and  infrequent long distant journeys, the
  best option is normally to use the dynamic auto assignment. If not provided the value is resolved
  depending on the other input parameters, available transit options and realtime changes.

  There is no need to set this when going to the next/previous page. The OTP Server will 
  increase/decrease the search-window when paging to match the requested number of itineraries. 
  """
        )
        .asDuration(dft.searchWindow())
    );
    vehicleParking.setRequiredTags(
      c
        .of("requiredVehicleParkingTags")
        .since(NA)
        .summary(
          "Tags which are required to use a vehicle parking. If empty, no tags are required."
        )
        .asStringSet(vehicleParking.requiredTags())
    );

    request.setWheelchair(WheelchairConfig.wheelchairEnabled(c, WHEELCHAIR_ACCESSIBILITY));

    NodeAdapter unpreferred = c
      .of("unpreferred")
      .since(NA)
      .summary(
        "Parameters listing authorities or lines that preferably should not be used in trip patters."
      )
      .description(
        """
A cost is applied to boarding nonpreferred authorities or lines.
                    
The routing engine will add extra penalty - on the *unpreferred* routes and/or agencies using a 
cost function. The cost function (`unpreferredCost`) is defined as a linear function of the form 
`A + B x`, where `A` is a fixed cost (in seconds) and `B` is reluctance multiplier for transit leg
travel time `x` (in seconds).
"""
      )
      .asObject();
    request
      .journey()
      .transit()
      .setUnpreferredRoutes(
        unpreferred
          .of("routes")
          .since(NA)
          .summary("TODO")
          .asFeedScopedIds(request.journey().transit().unpreferredRoutes())
      );

    request
      .journey()
      .transit()
      .setUnpreferredAgencies(
        unpreferred
          .of("routes")
          .since(NA)
          .summary("TODO")
          .asFeedScopedIds(request.journey().transit().unpreferredRoutes())
      );

    // Map preferences
    request.withPreferences(preferences -> mapPreferences(c, preferences));

    return request;
  }

  private static void mapPreferences(NodeAdapter c, RoutingPreferences.Builder preferences) {
    preferences.withTransit(it -> mapTransitPreferences(c, it));
    preferences.withBike(it -> mapBikePreferences(c, it));
    preferences.withRental(it -> mapRentalPreferences(c, it));
    preferences.withStreet(it -> mapStreetPreferences(c, it));
    preferences.withCar(it -> mapCarPreferences(c, it));
    preferences.withSystem(it -> mapSystemPreferences(c, it));
    preferences.withTransfer(it -> mapTransferPreferences(c, it));
    preferences.withParking(mapParkingPreferences(c, preferences));
    preferences.withWalk(it -> mapWalkPreferences(c, it));
    preferences.withWheelchair(mapWheelchairPreferences(c, WHEELCHAIR_ACCESSIBILITY));
    preferences.withItineraryFilter(it -> {
      mapItineraryFilterParams("itineraryFilters", c, it);
    });
  }

  private static void mapTransitPreferences(NodeAdapter c, TransitPreferences.Builder builder) {
    var dft = builder.original();
    builder
      .withAlightSlack(it ->
        it
          .withDefault(
            c
              .of("alightSlack")
              .since(NA)
              .summary("The minimum extra time after exiting a public transport vehicle.")
              .description(
                "The slack is added to the time when going from the transit vehicle to the stop."
              )
              .asDuration(dft.alightSlack().defaultValue())
          )
          .withValues(
            c
              .of("alightSlackForMode")
              .since(V2_0)
              .summary("How much time alighting a vehicle takes for each given mode.")
              .description(
                "Sometimes there is a need to configure a longer alighting times for specific " +
                "modes, such as airplanes or ferries."
              )
              .asEnumMap(TransitMode.class, Duration.class)
          )
      )
      .withBoardSlack(it ->
        it
          .withDefault(
            c
              .of("boardSlack")
              .since(NA)
              .summary(
                "The boardSlack is the minimum extra time to board a public transport vehicle."
              )
              .description(
                """
The board time is added to the time when going from the stop (offboard) to onboard a transit 
vehicle.

This is the same as the `minimumTransferTime`, except that this also apply to to the first 
transit leg in the trip. This is the default value used, if not overridden by the `boardSlackList`.
"""
              )
              .asDuration(dft.boardSlack().defaultValue())
          )
          .withValues(
            c
              .of("boardSlackForMode")
              .since(V2_0)
              .summary("How much time ride a vehicle takes for each given mode.")
              .description(
                """
Sometimes there is a need to configure a board times for specific modes, such as airplanes or 
ferries, where the check-in process needs to be done in good time before ride. 
"""
              )
              .asEnumMap(TransitMode.class, Duration.class)
          )
      )
      .setIgnoreRealtimeUpdates(
        c
          .of("ignoreRealtimeUpdates")
          .since(NA)
          .summary("When true, realtime updates are ignored during this search.")
          .asBoolean(dft.ignoreRealtimeUpdates())
      )
      .setOtherThanPreferredRoutesPenalty(
        c
          .of("otherThanPreferredRoutesPenalty")
          .since(NA)
          .summary(
            "Penalty added for using every route that is not preferred if user set any route as preferred."
          )
          .description(
            "We return number of seconds that we are willing to wait for preferred route."
          )
          .asInt(dft.otherThanPreferredRoutesPenalty())
      )
      .setReluctanceForMode(
        c
          .of("transitReluctanceForMode")
          .since(NA)
          .summary("Transit reluctance for a given transport mode")
          .asEnumMap(TransitMode.class, Double.class)
      )
      .setUnpreferredCost(
        c
          .of("unpreferredCost")
          .since(V2_2)
          .summary("A cost function used to calculate penalty for an unpreferred route.")
          .description(
            """
            Function should return number of seconds that we are willing to wait for preferred route
            or for an unpreferred agency's departure. For example, 600 + 2.0 x
            """
          )
          .asLinearFunction(dft.unpreferredCost())
      );
  }

  private static void mapBikePreferences(NodeAdapter c, BikePreferences.Builder builder) {
    var dft = builder.original();
    builder
      .withSpeed(
        c
          .of("bikeSpeed")
          .since(NA)
          .summary("Max bike speed along streets, in meters per second")
          .asDouble(dft.speed())
      )
      .withReluctance(
        c
          .of("bikeReluctance")
          .since(NA)
          .summary(
            "A multiplier for how bad biking is, compared to being in transit for equal lengths of time."
          )
          .asDouble(dft.reluctance())
      )
      .withBoardCost(
        c
          .of("bikeBoardCost")
          .since(NA)
          .summary("Prevents unnecessary transfers by adding a cost for boarding a vehicle.")
          .description(
            "This is the cost that is used when boarding while cycling." +
            "This is usually higher that walkBoardCost."
          )
          .asInt(dft.boardCost())
      )
      .withParkTime(
        c.of("bikeParkTime").since(NA).summary("Time to park a bike.").asInt(dft.parkTime())
      )
      .withParkCost(
        c.of("bikeParkCost").since(NA).summary("Cost to park a bike.").asInt(dft.parkCost())
      )
      .withWalkingSpeed(
        c
          .of("bikeWalkingSpeed")
          .since(NA)
          .summary(
            "The user's bike walking speed in meters/second. Defaults to approximately 3 MPH."
          )
          .asDouble(dft.walkingSpeed())
      )
      .withWalkingReluctance(
        c
          .of("bikeWalkingReluctance")
          .since(NA)
          .summary(
            "A multiplier for how bad walking with a bike is, compared to being in transit for equal lengths of time."
          )
          .asDouble(dft.walkingReluctance())
      )
      .withSwitchTime(
        c
          .of("bikeSwitchTime")
          .since(NA)
          .summary("The time it takes the user to fetch their bike and park it again in seconds.")
          .asInt(dft.switchTime())
      )
      .withSwitchCost(
        c
          .of("bikeSwitchCost")
          .since(NA)
          .summary("The cost of the user fetching their bike and parking it again.")
          .asInt(dft.switchCost())
      )
      .withOptimizeType(
        c
          .of("optimize")
          .since(NA)
          .summary("The set of characteristics that the user wants to optimize for.")
          .asEnum(dft.optimizeType())
      )
      .withOptimizeTriangle(it ->
        it
          .withTime(
            c
              .of("bikeTriangleTimeFactor")
              .since(NA)
              .summary("For bike triangle routing, how much time matters (range 0-1).")
              .asDouble(it.time())
          )
          .withSlope(
            c
              .of("bikeTriangleSlopeFactor")
              .since(NA)
              .summary("For bike triangle routing, how much slope matters (range 0-1).")
              .asDouble(it.slope())
          )
          .withSafety(
            c
              .of("bikeTriangleSafetyFactor")
              .since(NA)
              .summary("For bike triangle routing, how much safety matters (range 0-1).")
              .asDouble(it.safety())
          )
      );
  }

  private static void mapRentalPreferences(
    NodeAdapter c,
    VehicleRentalPreferences.Builder builder
  ) {
    var dft = builder.original();
    builder
      .withDropoffCost(
        c
          .of("bikeRentalDropoffCost")
          .since(NA)
          .summary("Cost to drop-off a rented bike.")
          .asInt(dft.dropoffCost())
      )
      .withDropoffTime(
        c
          .of("bikeRentalDropoffTime")
          .since(NA)
          .summary("Time to drop-off a rented bike.")
          .asInt(dft.dropoffTime())
      )
      .withPickupCost(
        c
          .of("bikeRentalPickupCost")
          .since(NA)
          .summary("Cost to rent a bike.")
          .asInt(dft.pickupCost())
      )
      .withPickupTime(
        c
          .of("bikeRentalPickupTime")
          .since(NA)
          .summary("Time to rent a bike.")
          .asInt(dft.pickupTime())
      )
      .withUseAvailabilityInformation(
        c
          .of("useBikeRentalAvailabilityInformation")
          .since(NA)
          .summary(
            "Whether or not bike rental availability information will be used to plan bike rental trips."
          )
          .asBoolean(dft.useAvailabilityInformation())
      )
      .withArrivingInRentalVehicleAtDestinationCost(
        c
          .of("keepingRentedBicycleAtDestinationCost")
          .since(NA)
          .summary(
            "The cost of arriving at the destination with the rented bicycle, to discourage doing so."
          )
          .asDouble(dft.arrivingInRentalVehicleAtDestinationCost())
      );
  }

  private static void mapStreetPreferences(NodeAdapter c, StreetPreferences.Builder builder) {
    var dft = builder.original();
    builder
      .withTurnReluctance(
        c
          .of("turnReluctance")
          .since(NA)
          .summary("Multiplicative factor on expected turning time.")
          .asDouble(dft.turnReluctance())
      )
      .withDrivingDirection(
        c
          .of("drivingDirection")
          .since(NA)
          .summary("The driving direction to use in the intersection traversal calculation")
          .asEnum(dft.drivingDirection())
      )
      .withElevator(elevator -> {
        var dftElevator = dft.elevator();
        elevator
          .withBoardCost(
            c
              .of("elevatorBoardCost")
              .since(NA)
              .summary("What is the cost of boarding a elevator?")
              .asInt(dftElevator.boardCost())
          )
          .withBoardTime(
            c
              .of("elevatorBoardTime")
              .since(NA)
              .summary("How long does it take to get on an elevator, on average.")
              .asInt(dftElevator.boardTime())
          )
          .withHopCost(
            c
              .of("elevatorHopCost")
              .since(NA)
              .summary("What is the cost of travelling one floor on an elevator?")
              .asInt(dftElevator.hopCost())
          )
          .withHopTime(
            c
              .of("elevatorHopTime")
              .since(NA)
              .summary("How long does it take to advance one floor on an elevator?")
              .asInt(dftElevator.hopTime())
          );
      })
      .withMaxAccessEgressDuration(
        c
          .of("maxAccessEgressDuration")
          .since(V2_2)
          .summary("This is the maximum duration for access/egress for street searches.")
          .description(
            """
This is a performance limit and should therefore be set high. Results close to the limit are not
guaranteed to be optimal. Use itinerary-filters to limit what is presented to the client. The 
duration can be set per mode(`maxAccessEgressDurationForMode`), because some street modes searches
are much more resource intensive than others. A default value is applied if the mode specific value
do not exist.
"""
          )
          .asDuration(dft.maxAccessEgressDuration().defaultValue()),
        c
          .of("maxAccessEgressDurationForMode")
          .since(NA)
          .summary("Limit access/egress per street mode.")
          .description(
            """
            Override the settings in `maxAccessEgressDuration` for specific street modes. This is 
            done because some street modes searches are much more resource intensive than others.
            """
          )
          .asEnumMap(StreetMode.class, Duration.class)
      )
      .withMaxDirectDuration(
        c
          .of("maxDirectStreetDuration")
          .since(NA)
          .summary("This is the maximum duration for a direct street search for each mode.")
          .description(
            """
This is a performance limit and should therefore be set high. Results close to the limit are not
guaranteed to be optimal. Use itinerary-filters to limit what is presented to the client. The
duration can be set per mode(`maxDirectStreetDurationForMode`), because some street modes searches
are much more resource intensive than others. A default value is applied if the mode specific value
do not exist."
"""
          )
          .asDuration(dft.maxDirectDuration().defaultValue()),
        c
          .of("maxDirectStreetDurationForMode")
          .since(V2_2)
          .summary("Limit direct route duration per street mode.")
          .description(
            """
            Override the settings in `maxDirectStreetDuration` for specific street modes. This is 
            done because some street modes searches are much more resource intensive than others.
            """
          )
          .asEnumMap(StreetMode.class, Duration.class)
      )
      .withIntersectionTraversalModel(
        c
          .of("intersectionTraversalModel")
          .since(NA)
          .summary("The model that computes the costs of turns.")
          .asEnum(dft.intersectionTraversalModel())
      );
  }

  private static void mapCarPreferences(NodeAdapter c, CarPreferences.Builder builder) {
    var dft = builder.original();
    builder
      .withSpeed(
        c
          .of("carSpeed")
          .since(NA)
          .summary("Max car speed along streets, in meters per second")
          .asDouble(dft.speed())
      )
      .withReluctance(
        c
          .of("carReluctance")
          .since(NA)
          .summary(
            "A multiplier for how bad driving is, compared to being in transit for equal lengths of time."
          )
          .asDouble(dft.reluctance())
      )
      .withDropoffTime(
        c
          .of("carDropoffTime")
          .since(NA)
          .summary(
            "Time to park a car in a park and ride, w/o taking into account driving and walking cost."
          )
          .asInt(dft.dropoffTime())
      )
      .withParkCost(
        c.of("carParkCost").since(NA).summary("Cost of parking a car.").asInt(dft.parkCost())
      )
      .withParkTime(
        c.of("carParkTime").since(NA).summary("Time to park a car").asInt(dft.parkTime())
      )
      .withPickupCost(
        c
          .of("carPickupCost")
          .since(V2_1)
          .summary("Add a cost for car pickup changes when a pickup or drop off takes place")
          .asInt(dft.pickupCost())
      )
      .withPickupTime(
        c
          .of("carPickupTime")
          .since(V2_1)
          .summary("Add a time for car pickup changes when a pickup or drop off takes place")
          .asInt(dft.pickupTime())
      )
      .withAccelerationSpeed(
        c
          .of("carAccelerationSpeed")
          .since(NA)
          .summary("The acceleration speed of an automobile, in meters per second per second.")
          .asDouble(dft.accelerationSpeed())
      )
      .withDecelerationSpeed(
        c
          .of("carDecelerationSpeed")
          .since(NA)
          .summary("The deceleration speed of an automobile, in meters per second per second.")
          .asDouble(dft.decelerationSpeed())
      );
  }

  private static void mapSystemPreferences(NodeAdapter c, SystemPreferences.Builder builder) {
    var dft = builder.original();
    builder
      .withGeoidElevation(
        c
          .of("geoidElevation")
          .since(NA)
          .summary(
            "If true, the Graph's ellipsoidToGeoidDifference is applied to all elevations returned by this query."
          )
          .asBoolean(dft.geoidElevation())
      )
      .withMaxJourneyDuration(
        c
          .of("maxJourneyDuration")
          .since(NA)
          .summary(
            "The expected maximum time a journey can last across all possible journeys for the current deployment."
          )
          .description(
            """
Normally you would just do an estimate and add enough slack, so you are sure that there is no
journeys that falls outside this window. The parameter is used find all possible dates for the
journey and then search only the services which run on those dates. The duration must include
access, egress, wait-time and transit time for the whole journey. It should also take low frequency
days/periods like holidays into account. In other words, pick the two points within your area that
has the worst connection and then try to travel on the worst possible day, and find the maximum
journey duration. Using a value that is too high has the effect of including more patterns in the
search, hence, making it a bit slower. Recommended values would be from 12 hours(small town/city),
1 day (region) to 2 days (country like Norway)."
"""
          )
          .asDuration(dft.maxJourneyDuration())
      );
    if (OTPFeature.DataOverlay.isOn()) {
      builder.withDataOverlay(
        DataOverlayParametersMapper.map(
          c
            .of("dataOverlay")
            .since(NA)
            .summary("The filled request parameters for penalties and thresholds values")
            .description(/*TODO DOC*/"TODO")
            .asObject()
        )
      );
    }
  }

  private static VehicleParkingPreferences mapParkingPreferences(
    NodeAdapter c,
    RoutingPreferences.Builder preferences
  ) {
    return VehicleParkingPreferences.of(
      c
        .of("useVehicleParkingAvailabilityInformation")
        .asBoolean(preferences.parking().useAvailabilityInformation())
    );
  }

  private static void mapWalkPreferences(NodeAdapter c, WalkPreferences.Builder walk) {
    var dft = walk.original();
    walk
      .withSpeed(
        c
          .of("walkSpeed")
          .since(NA)
          .summary("The user's walking speed in meters/second.")
          .asDouble(dft.speed())
      )
      .withReluctance(
        c
          .of("walkReluctance")
          .since(NA)
          .summary(
            "A multiplier for how bad walking is, compared to being in transit for equal lengths of time."
          )
          .description(
            """
Empirically, values between 2 and 4 seem to correspond well to the concept of not wanting to walk
too much without asking for totally ridiculous itineraries, but this observation should in no way
be taken as scientific or definitive. Your mileage may vary.
See https://github.com/opentripplanner/OpenTripPlanner/issues/4090 for impact on performance with
high values.
"""
          )
          .asDouble(dft.reluctance())
      )
      .withBoardCost(
        c
          .of("walkBoardCost")
          .since(NA)
          .summary(
            """
            Prevents unnecessary transfers by adding a cost for boarding a vehicle. This is the 
            cost that is used when boarding while walking.
            """
          )
          .asInt(dft.boardCost())
      )
      .withStairsReluctance(
        c
          .of("stairsReluctance")
          .since(NA)
          .summary("Used instead of walkReluctance for stairs.")
          .asDouble(dft.stairsReluctance())
      )
      .withStairsTimeFactor(
        c
          .of("stairsTimeFactor")
          .since(NA)
          .summary(
            "How much more time does it take to walk a flight of stairs compared to walking a similar horizontal length."
          )
          .description(
            """
            Default value is based on: Fujiyama, T., & Tyler, N. (2010). Predicting the walking
            speed of pedestrians on stairs. Transportation Planning and Technology, 33(2), 177–202.
            """
          )
          .asDouble(dft.stairsTimeFactor())
      )
      .withSafetyFactor(
        c
          .of("walkSafetyFactor")
          .since(NA)
          .summary("Factor for how much the walk safety is considered in routing.")
          .description(
            "Value should be between 0 and 1." + " If the value is set to be 0, safety is ignored."
          )
          .asDouble(dft.safetyFactor())
      );
  }
}
