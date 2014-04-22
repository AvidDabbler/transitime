/* 
 * This file is part of Transitime.org
 * 
 * Transitime.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Transitime.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Transitime.org .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.transitime.core;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.transitime.avl.TimeoutHandler;
import org.transitime.core.dataCache.PredictionDataCache;
import org.transitime.db.structs.AvlReport;
import org.transitime.db.structs.Block;
import org.transitime.db.structs.Trip;

/**
 * Takes the AVL data and processes it. Matches vehicles to their assignments.
 * Once a match is made then MatchProcessor class is used to generate
 * predictions, arrival/departure times, headway, etc.
 * 
 * @author SkiBu Smith
 * 
 */
public class DataProcessor {

	// Singleton class
	private static DataProcessor singleton = new DataProcessor();
	
	private static final Logger logger = 
			LoggerFactory.getLogger(DataProcessor.class);

	/********************** Member Functions **************************/

	/*
	 * Singleton class so shouldn't use constructor so declared private
	 */
	private DataProcessor() {		
	}
	
	/**
	 * Returns the singleton DataProcessor
	 * @return
	 */
	public static DataProcessor getInstance() {
		return singleton;
	}
	
	/**
	 * Removes predictions and the match for the vehicle and marks
	 * it as unpredictable.
	 * 
	 * @param vehicleId
	 *            The vehicle to be made unpredictable
	 */
	public void makeVehicleUnpredictable(String vehicleId) {
		VehicleState vehicleState =
				VehicleStateManager.getInstance().getVehicleState(vehicleId);

		// Update the state of the vehicle
		vehicleState.setMatch(null);

		// Remove the predictions that were generated by the vehicle
		PredictionDataCache.getInstance().removePredictions(vehicleState);		
	}
	
	/**
	 * Removes predictions and the match for the vehicle and marks
	 * is as unpredictable. Also removes block assignment.
	 * 
	 * @param vehicleId
	 *            The vehicle to be made unpredictable
	 */
	public void makeVehicleUnpredictableAndRemoveAssignment(String vehicleId) {
		makeVehicleUnpredictable(vehicleId);
		
		// Update the state of the vehicle
		VehicleState vehicleState =
				VehicleStateManager.getInstance().getVehicleState(vehicleId);
		vehicleState.setBlock(null, 
				BlockAssignmentMethod.ASSIGNMENT_TERMINATED, 
				false /* predictable*/);
	}

	/**
	 * For vehicles that were already predictable but then got a new AvlReport.
	 * Determines where in the block assignment the vehicle now matches to.
	 * Starts at the previous match and then looks ahead from there to find
	 * good spatial matches. Then determines which spatial match is best by
	 * looking at temporal match. Updates the vehicleState with the resulting
	 * best temporal match.
	 * 
	 * @param vehicleState the previous vehicle state
	 * @return the new match, if successful. Otherwise null.
	 */
	public TemporalMatch matchNewFixForPredictableVehicle(
			VehicleState vehicleState) {
		// Make sure state is correct
		if (!vehicleState.isPredictable() || 
				vehicleState.getMatch() == null) {
			throw new RuntimeException("Called DataProcessor.matchNewFix() " +
					"for a vehicle that was not already predictable. " + 
					vehicleState);
		}
		
		logger.debug("Matching already predictable vehicle using new AVL " +
				"report. The old spatial match is {}", 
				vehicleState);
		
		// Find possible spatial matches
		List<SpatialMatch> spatialMatches = 
				SpatialMatcher.getSpatialMatches(vehicleState);
		logger.debug("For vehicleId={} found the following {} spatial matches: {}",
				vehicleState.getVehicleId(), spatialMatches.size(), spatialMatches);

		// Find best temporal match of the spatial matches
		TemporalMatch bestTemporalMatch =
				TemporalMatcher.getInstance().getBestTemporalMatch(vehicleState, 
						spatialMatches);
		
		// Log this as info since matching is a significant milestone
		logger.info("For vehicleId={} the best match={}",
				vehicleState.getVehicleId(), bestTemporalMatch);

		// Record this match
		vehicleState.setMatch(bestTemporalMatch);

		// Return results
		return bestTemporalMatch;
	}

	/**
	 * To be called when vehicle doesn't already have a block assignment or the
	 * vehicle is being reassigned. Uses block assignment from the AvlReport to
	 * try to match the vehicle to the assignment. If successful then the
	 * vehicle can be made predictable. The AvlReport is obtained from the
	 * vehicleState parameter.
	 * 
	 * @param avlReport
	 * @param vehicleState
	 *            provides current AvlReport plus is updated by this method with
	 *            the new state.
	 * @return true if successfully assigned vehicle
	 */
	public boolean matchVehicleToAssignment(VehicleState vehicleState) {
		logger.debug("Matching unassigned vehicle to assignment. {}", vehicleState);
		
		// Initialize some variables
		AvlReport avlReport = vehicleState.getAvlReport();
		TemporalMatch bestMatch = null;
		BlockAssignmentMethod blockAssignmentMethod = null;
		boolean predictable = false;
		
		// If the vehicle has a block assignment from the AVLFeed
		// then use it.
		Block block = BlockAssigner.getInstance().getBlock(avlReport);
		if (block == null) {
			// There was no valid block assignment from AVL feed so can't
			// do anything. But set the block assignment for the vehicle
			// so it is up to date. This call also sets the vehicle state
			// to be unpredictable.
			vehicleState.setBlock(block, blockAssignmentMethod, predictable);
			return false;
		} else {
			// There is a block assignment from AVL feed so use it
			
			// Determine best spatial matches for trips that are currently
			// active. Currently active means that the AVL time is within
			// reasonable range of the start and end time of the trip.
			List<Trip> potentialTrips = 
					TemporalMatcher.getInstance().getTripsCurrentlyActive(avlReport, block);
			List<SpatialMatch> spatialMatches = 
					SpatialMatcher.getSpatialMatches(avlReport, potentialTrips, 
							block);
			logger.debug("For vehicleId={} and blockId={} spatial matches={}",
					avlReport.getVehicleId(), block.getId(), spatialMatches);

			bestMatch = TemporalMatcher.getInstance()
					.getBestTemporalMatchComparedToSchedule(avlReport,
							spatialMatches);
			logger.debug("Best temporal match for vehicleId={} is {}",
					avlReport.getVehicleId(), bestMatch);
			
			// If couldn't find an adequate spatial/temporal match then resort
			// to matching to a wait stop at a terminal. 
			if (bestMatch == null) {
				logger.debug("For vehicleId={} could not find reasonable " +
						"match so will try to match to wait stop.",
						avlReport.getVehicleId());
				
				Trip trip = TemporalMatcher.getInstance().
						matchToWaitStopEvenIfOffRoute(avlReport, potentialTrips);
				if (trip != null) {
					SpatialMatch beginningOfTrip = 
							new SpatialMatch(vehicleState.getVehicleId(),
							block, 
							block.getTripIndex(trip.getId()),
							0,    //  stopPathIndex 
							0,    // segmentIndex 
							0.0,  // distanceToSegment
							0.0); // distanceAlongSegment
	
					bestMatch = new TemporalMatch(beginningOfTrip, 
							new TemporalDifference(0));
					logger.debug("For vehicleId={} could not find reasonable " +
							"match for blockId={} so had to match to layover. " +
							"The match is {}",
							avlReport.getVehicleId(), block.getId(), bestMatch);
				} else {
					logger.debug("For vehicleId={} couldn't find match for " +
							"blockId={}", 
							avlReport.getVehicleId(), block.getId());
				}
			}
			
			// If got a valid match then keep track of state
			if (bestMatch != null) {
				blockAssignmentMethod = BlockAssignmentMethod.AVL_FEED;
				predictable = true;
				logger.info("For vehicleId={} matched to blockId={}. " +
						"Vehicle is now predictable. Match={}",
						avlReport.getVehicleId(), block.getId(), bestMatch);
			} else {
				logger.info("For vehicleId={} could not assign to blockId={}. " +
						"Therefore vehicle is not predictable.",
						avlReport.getVehicleId(), block.getId());
			}

			// Update the vehicle state with the determined block assignment
			// and match. Of course might not have been successful in 
			// matching vehicle, but still should update VehicleState.
			vehicleState.setMatch(bestMatch);
			vehicleState.setBlock(block, blockAssignmentMethod, predictable);

			// Return whether successfully matched the vehicle
			return predictable;
		}
	}
	
	/**
	 * Looks at the last match in vehicleState to determine if at end of
	 * block assignment. Note that this will not always work since might
	 * not actually get an AVL report that matches to the last stop.
	 *  
	 * @param vehicleState
	 */
	private void handlePossibleEndOfBlock(VehicleState vehicleState) {
		// Determine if at end of block assignment
		TemporalMatch temporalMatch = vehicleState.getMatch();
		if (temporalMatch != null) {
			VehicleAtStopInfo atStopInfo = temporalMatch.getAtStop();
			if (atStopInfo != null) {
				if (atStopInfo.atEndOfBlock()) {
					// At end of block assignment so remove it
					makeVehicleUnpredictableAndRemoveAssignment(
							vehicleState.getVehicleId());
				}
			}
		}
	}
	
	/**
	 * Determines the real-time schedule adherence for the vehicle. To be called
	 * after the vehicle is matched.
	 * <p>
	 * If schedule adherence is not within bounds then will try to match the
	 * vehicle to the assignment again. This can be important if system is run
	 * for a while and then paused and then started up again. Vehicle might
	 * continue to match to the pre-paused match, but by then the vehicle might
	 * be on a whole different trip, causing schedule adherence to be really far
	 * off. To prevent this the vehicle is re-matched to the assignment.
	 * 
	 * @param vehicleState
	 * @return
	 */
	private TemporalDifference checkScheduleAdherence(VehicleState vehicleState) {
		logger.debug("Processing real-time schedule adherence for vehicleId={}",
				vehicleState.getVehicleId());
	
		// Determine the schedule adherence for the vehicle
		TemporalDifference scheduleAdherence = 
				RealTimeSchedAdhProcessor.generate(vehicleState);
		
		// Make sure the schedule adherence is reasonable
		if (scheduleAdherence != null && !scheduleAdherence.isWithinBounds()) { 
			// Schedule adherence not reasonable so match vehicle to assignment
			// again.
			matchVehicleToAssignment(vehicleState);
			
			// Now that have matched vehicle to assignment again determine
			// schedule adherence once more.
			scheduleAdherence = 
					RealTimeSchedAdhProcessor.generate(vehicleState);
		}
		
		// Store the schedule adherence with the vehicle
		vehicleState.setRealTimeSchedAdh(scheduleAdherence);
		
		// Return results
		return scheduleAdherence;
	}
	
	/**
	 * Processes the AVL report by matching to the assignment and
	 * generating predictions and such. Sets VehicleState for the
	 * vehicle based on the results.
	 * 
	 * @param avlReport
	 */
	public void processAvlReport(AvlReport avlReport) {
		// The beginning of processing AVL data is an important milestone 
		// in processing data so log it as info.
		logger.info("====================================================" +
				"DataProcessor processing {}", avlReport);		
		
		// If any vehicles have timed out then handle them. This is done
		// here instead of using a regular timer so that it will work
		// even when in playback mode or when reading batch data.
		TimeoutHandler.get().handlePossibleTimeout(avlReport);
		
		// Logging to syserr just for debugging. This should eventually be removed
		System.err.println("Processing avlReport for vehicleId=" + 
				avlReport.getVehicleId() + 
				//" AVL time=" + Time.timeStrMsec(avlReport.getTime()) +
				" " + avlReport +
				" ...");
		
		// Determine previous state of vehicle
		String vehicleId =avlReport.getVehicleId();
		VehicleState vehicleState =
				VehicleStateManager.getInstance().getVehicleState(vehicleId);
		
		// Since modifying the VehicleState should synchronize in case another
		// thread simultaneously processes data for the same vehicle. This  
		// would be extremely rare but need to be safe.
		synchronized (vehicleState) {
			// Keep track of last AvlReport even if vehicle not predictable. 
			vehicleState.setAvlReport(avlReport);			

			// Do the matching depending on the old and the new assignment
			// for the vehicle.
			boolean matchAlreadyPredictableVehicle = vehicleState.isPredictable() && 
					!vehicleState.hasNewBlockAssignment(avlReport);
			boolean matchToNewAssignment = avlReport.hasValidAssignment() && 
					(!vehicleState.isPredictable() || 
							vehicleState.hasNewBlockAssignment(avlReport));
			
			if (matchAlreadyPredictableVehicle) {
				// Vehicle was already assigned and assignment hasn't
				// changed so update the match of where the vehicle is 
				// within the assignment.
				matchNewFixForPredictableVehicle(vehicleState);								
			} else if (matchToNewAssignment) {
				// Remove old assignment if there was one
				if (vehicleState.isPredictable() && 
						vehicleState.hasNewBlockAssignment(avlReport)) {
					makeVehicleUnpredictableAndRemoveAssignment(vehicleId);					
				}

				// New assignment so match the vehicle to it
				matchVehicleToAssignment(vehicleState);
			} else {
				// Can't do anything so set the match to null, which also
				// specifies that the vehicle is not predictable. In the 
				// future might want to change code to try to auto assign 
				// vehicle.
				vehicleState.setMatch(null);
			}
			
			// Determine and store the schedule adherence. If schedule 
			// adherence is bad then try matching vehicle to assignment
			// again.
			checkScheduleAdherence(vehicleState);
			
			// Generates the corresponding data for the vehicle such as 
			// predictions and arrival times
			if (vehicleState.isPredictable())
				MatchProcessor.getInstance().generateResultsOfMatch(vehicleState);
			
			// If finished block assignment then should remove assignment
			handlePossibleEndOfBlock(vehicleState);
		}  // End of synchronizing on vehicleState
	}
	
}
