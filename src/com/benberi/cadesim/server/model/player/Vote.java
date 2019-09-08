package com.benberi.cadesim.server.model.player;

import java.util.ArrayList;
import java.util.List;

import com.benberi.cadesim.server.ServerContext;

/**
 * Provide a means for players to vote on resolutions
 * A simple majority threshold carries the vote.
 *
 */
public class Vote {
	private int votesFor     = 0;              // count votes for
	private int votesAgainst = 0;              // count votes against
	public static final int MAJORITY_THRESHOLD = 50; // vote carried at >50%
	public static final int VOTE_TIMEOUT_MILLIS = 30000; // timeout after 30s
	public static final int PRINT_SCORE_MILLIS = 10000; // ms
	private List<String> eligibleIPs = new ArrayList<String>(); // restrict to players present when vote started
	private List<String> voterIPs    = new ArrayList<String>(); // prevent multi ip voting
	private long voteStartTime; // system time in millis() since vote started
	private boolean voteInProgress = true; // is vote in progress? true initially
	private String description;            // describe what the vote relates to
	private VOTE_RESULT result = VOTE_RESULT.TBD; // the result
	private PlayerManager context; // the context
	private long lastPrintUpdate = System.currentTimeMillis();
	
	/**
	 * helper method to extract an IP from a remoteAddress
	 * they are formatted like:
	 *     /127.0.0.1:1004
	 * we return:
	 *     127.0.0.1
	 */
	private String getIPFromRemoteAddress(String remoteAddress)
	{
		return remoteAddress.replace("/", "").split(":")[0];
	}
	
	/**
	 * the kind of vote result which could be returned
	 */
	public static enum VOTE_RESULT {
	    TBD,
	    FOR,
	    AGAINST,
	    TIMEDOUT
	}
	
	private int getEligibleVoters() {
		return eligibleIPs.size();
	}
	
	private int getSecondsRemaining() {
		return (int)((float)(VOTE_TIMEOUT_MILLIS - (System.currentTimeMillis() - voteStartTime)) / 1000.0);
	}
	
	private String printProgress() {
		return 
			"Vote on " + getDescription() + "-\n    " +
			getEligibleVoters() + " players eligible\n    " +
			getVotesCast() + " votes cast (" + printScore() + ")\n    " +
			getVotesToWin() + " votes is majority\n    "+
			getSecondsRemaining() + " seconds left to vote";
	}
	
	private String printScore() {
		return Integer.toString(votesFor) + "-" + Integer.toString(votesAgainst);
	}
	
	/**
	 * get the result of the vote
	 * performs timeout check, so can keep polling this method
	 */
	public VOTE_RESULT getResult() {
		if (voteInProgress)
		{
			if (!hasTimedOut())
			{
				// print update every 5 seconds
				long now = System.currentTimeMillis();
				if ((now - lastPrintUpdate) >= PRINT_SCORE_MILLIS)
				{
		            lastPrintUpdate = now;
		            context.beaconMessageFromServer(printProgress());
				}
			}
			else
			{
				this.setResult(VOTE_RESULT.TIMEDOUT);
			}
		}
		return result;
	}
	
	/**
	 * set the result of the vote
	 * 
	 * if anything other than TBD, sets voteInProgress to false
	 */
	private void setResult(VOTE_RESULT value) {
		if (voteInProgress)
		{
			result = value;
			
			if (result != VOTE_RESULT.TBD)
			{
				voteInProgress = false;
			}
			
			// printout
			switch(result)
			{
			case TBD:
				break;
			case FOR:
				context.beaconMessageFromServer(
					"Vote " + getDescription() + " passed " + printScore()
				);
				break;
			case AGAINST:
				context.beaconMessageFromServer(
						"Vote " + getDescription() + " didn't pass " + printScore()
					);
				break;
			case TIMEDOUT:
				context.beaconMessageFromServer(
					"Vote " + getDescription() + " didn't pass (timed out)" + printScore()
				);
				break;
			default:
				break; // dont care
			}
		}
	}
	
	/**
	 * create a new vote
	 * @param totalPlayers the number of players currently active
	 */
	public Vote(PlayerManager pm, String description)
	{
		this.context = pm;
		voteStartTime = System.currentTimeMillis();
		
		for (Player p:pm.listRegisteredPlayers())
		{
			String ip = getIPFromRemoteAddress(p.getChannel().remoteAddress().toString());
			if (!eligibleIPs.contains(ip))
			{
				// only one player from each IP is eligible
				eligibleIPs.add(ip);
			}
		}
		
		this.description = description;
		
		context.beaconMessageFromServer("Started vote on " + description);
	}
	
	/**
	 * @return whether vote is in progress (true) or not (false)
	 */
	public boolean isVoteInProgress() {
		return voteInProgress;
	}
	
	/**
	 * get the description.
	 * @return string representation of the description configured with
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * get the miniumum number of votes to win
	 */
	public int getVotesToWin() {
		return (int)Math.ceil(((float)getEligibleVoters() / 100.0) * MAJORITY_THRESHOLD);
	}
	
	/**
	 * get the number of votes still to be cast
	 */
	public int getVotesRemaining() {
		return getEligibleVoters() - (getVotesCast());
	}
	
	public int getVotesCast() {
		return votesFor + votesAgainst;
	}
	
	/**
	 * get whether the 'for' voters have a majority.
	 * note this is only valid if min threshold have voted.
	 */
	private boolean hasForWon() {
		return (((float)votesFor / (float)getEligibleVoters()) * 100.0) > MAJORITY_THRESHOLD;
	}
	
	/**
	 * get whether the 'for' voters have a majority.
	 * note this is only valid if min threshold have voted.
	 */
	private boolean hasAgainstWon() {
		return (((float)votesAgainst / (float)getEligibleVoters()) * 100.0) > MAJORITY_THRESHOLD;
	}
	
	/**
	 * get whether For could win
	 */
	private boolean couldForWin() {
		return (((float)(votesFor + (getEligibleVoters() - votesAgainst - votesFor)) / (float)getEligibleVoters()) * 100.0) > MAJORITY_THRESHOLD;
	}
	
	/**
	 * helper method to calculate timeout for vote
	 * @return true if has timed out; false otherwise
	 */
	private boolean hasTimedOut() {
		// timeout after n seconds
		if ((System.currentTimeMillis() - voteStartTime) > VOTE_TIMEOUT_MILLIS)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * Check the result.
	 * @return VOTE_RESULT
	 */
	private VOTE_RESULT checkResult()
	{
		// is it possible for a vote to be won? if not, exit early
		if (!couldForWin())
		{
			setResult(VOTE_RESULT.AGAINST);
			return result;
		}
		
		// simple majority of > CARRY_VOTE_BEYOND% carries the vote
		if (hasForWon())
		{
			setResult(VOTE_RESULT.FOR);
			return result;
		}
		else if (hasAgainstWon())
		{
			setResult(VOTE_RESULT.AGAINST);
			return result;
		}
		else
		{
			// if not timed out, and not resolved, must be TBD
			return VOTE_RESULT.TBD;
		}
	}
	
	/**
	 * a player casts a vote.
	 * @param pl      the player
	 * @param voteFor true->for, false->against
	 * @return        the vote result after the vote is cast
	 */
	public VOTE_RESULT castVote(Player pl, boolean voteFor)
	{
		// timeout after n seconds
		if (hasTimedOut())
		{
			setResult(VOTE_RESULT.TIMEDOUT);
			return result;
		}

		// restrict based on IP - can only vote if were around when vote was cast
		if (!eligibleIPs.contains(getIPFromRemoteAddress(pl.getChannel().remoteAddress().toString())))
		{
			context.serverMessage(
				pl,
				"Couldn't process this vote - you joined the game after the vote started."
			);
			return result;
		}
		
		// prevent duplicate IPs
		if (voterIPs.contains(
				getIPFromRemoteAddress(pl.getChannel().remoteAddress().toString()))
		)
		{
			context.serverMessage(
				pl,
				"Couldn't process this vote - you can't vote twice!"
			);
			return result;
		}

		// add votes
		if (voteFor) { votesFor++; } else { votesAgainst++; }
		
		context.serverMessage(
				pl,
				"You voted " + (voteFor?"FOR":"AGAINST") + " " + getDescription()
		);
		voterIPs.add(getIPFromRemoteAddress(pl.getChannel().remoteAddress().toString()));

		// notify
		context.beaconMessageFromServer(printProgress());

		// check the result
		VOTE_RESULT r = checkResult();
		return r;
	}
}