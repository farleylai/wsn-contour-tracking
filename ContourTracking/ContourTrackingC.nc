/*
 * Copyright (c) 2006 Intel Corporation
 * All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE     
 * file. If you do not find these files, copies can be found by writing to
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300, Berkeley, CA, 
 * 94704.  Attention:  Intel License Inquiry.
 */

/**
 * ContourTracking demo application. See README.txt file in this directory.
 *
 * @author David Gay
 */
#include "Timer.h"
#include "ContourTracking.h"

module ContourTrackingC
{
  uses {
    interface Boot;
    interface SplitControl as RadioControl;
    interface AMSend;
    interface Receive;
    interface Timer<TMilli>;
    interface Read<uint16_t>;
    interface Leds;

		interface GlobalTime<TMilli>;
		interface TimeSyncInfo;
		interface Packet;
		interface PacketTimeStamp<TMilli, uint32_t>;

		//interface LocalTime<TMilli> as LocalTime;
		//interface TimeSyncAMSend<TMilli, uint32_t> as AMSend;
  }
}
implementation
{
  message_t sendBuf;
  bool sendBusy;

  /* Current local state - interval, version and accumulated readings */
  contourtracking_t local;

  uint8_t reading; /* 0 to NREADINGS */

  /* When we head an ContourTracking message, we check it's sample count. If
     it's ahead of ours, we "jump" forwards (set our count to the received
     count). However, we must then suppress our next count increment. This
     is a very simple form of "time" synchronization (for an abstract
     notion of time). */
  bool suppressCountChange;

  // Use LEDs to report various status issues.
  void report_problem() { call Leds.led0Toggle(); }
  void report_sent() { call Leds.led1Toggle(); }
  void report_received() { call Leds.led2Toggle(); }

  event void Boot.booted() {
		local.version = 0;
    local.interval = DEFAULT_INTERVAL;
    local.threshold = DEFAULT_THRESHOLD;
    local.id = TOS_NODE_ID;
    local.clock = 0;
    if (call RadioControl.start() != SUCCESS) 
      ;//report_problem();
  }

	void startTimer() {
		uint32_t timestamp = call GlobalTime.getLocalTime();
		bool synced = (call GlobalTime.local2Global(&timestamp) == SUCCESS);
		uint32_t gTimestamp = timestamp;
		if(synced)
			call Timer.startOneShot(local.interval - gTimestamp % local.interval);
		else
			call Timer.startOneShot(local.interval);
  }

  event void Timer.fired() {
		if (local.clock > 0) {
			local.clock += local.interval;
			if (call Read.read() != SUCCESS)
				report_problem();
		}
		startTimer();
  }

/*
  void startTimer() {
    call Timer.startPeriodic(local.interval);
    reading = 0;
  }

	event void Timer.fired() {
		if (local.clock > 0) {
			local.clock += local.interval;
			if (call Read.read() != SUCCESS)
				report_problem();
		}
	}
*/
  event void RadioControl.startDone(error_t error) {
    startTimer();
  }

  event void RadioControl.stopDone(error_t error) {
  }

  event message_t* Receive.receive(message_t* msg, void* payload, uint8_t len) {
		contourtracking_t *omsg = payload;

		report_received();

		/* If we receive a newer version, update our interval. 
			 If we hear from a future count, jump ahead but suppress our own change
		 */
		if (omsg->version > local.version)
		{
			local.version = omsg->version;
			local.interval = omsg->interval;
			local.threshold = omsg->threshold;
			startTimer();
		}
		if (omsg->count > local.count)
		{
			local.count = omsg->count;
			suppressCountChange = TRUE;
		}

		local.clock = omsg->clock;
		return msg;
  }

  event void AMSend.sendDone(message_t* msg, error_t error) {
    if (error == SUCCESS)
      report_sent();
    else
      report_problem();

    sendBusy = FALSE;
  }

  event void Read.readDone(error_t result, uint16_t data) {
		uint32_t timestamp = call GlobalTime.getLocalTime(); //call LocalTime.get();
    if (result != SUCCESS)
      {
	data = 0xffff;
	report_problem();
      }
    local.readings[reading++] = data;

	// send readings immediately when the buffer is full in case of delay.
	if (reading == NREADINGS)
	{
	  if (!sendBusy && sizeof local <= call AMSend.maxPayloadLength())
	  {
		// Don't need to check for null because we've already checked length
		// above
		local.ftsp_local_timestamp = timestamp;
		local.ftsp_synced = (call GlobalTime.local2Global(&timestamp) == SUCCESS);
		local.ftsp_global_timestamp = timestamp;
		local.ftsp_root_id = call TimeSyncInfo.getRootID();
		local.ftsp_seq = call TimeSyncInfo.getSeqNum();
		local.ftsp_table_entries = call TimeSyncInfo.getNumEntries();
		local.ftsp_skew = call TimeSyncInfo.getSkew();
		memcpy(call AMSend.getPayload(&sendBuf, sizeof(local)), &local, sizeof local);
		if (call AMSend.send(AM_BROADCAST_ADDR, &sendBuf, sizeof local) == SUCCESS)
		  sendBusy = TRUE;
	  }

	  if (!sendBusy)
		report_problem();

	  reading = 0;
	  /* Part 2 of cheap "time sync": increment our count if we didn't
		 jump ahead. */
	  if (!suppressCountChange)
		local.count++;
	  suppressCountChange = FALSE;
	}
  }
}
