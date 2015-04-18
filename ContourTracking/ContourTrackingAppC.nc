/*
 * Copyright (c) 2006 Intel Corporation
* All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE     
 * file. If you do not find these files, copies can be found by writing to
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300, Berkeley, CA, 
 * 94704.  Attention:  Intel License Inquiry.
 */

#include "Timer.h"
#include "ContourTracking.h"

/**
 * ContourTracking demo application. Uses the demo sensor - change the
 * new DemoSensorC() instantiation if you want something else.
 *
 * See README.txt file in this directory for usage instructions.
 *
 * @author David Gay
 */
configuration ContourTrackingAppC { }
implementation
{
	components MainC, ActiveMessageC, LedsC, TimeSyncC;
  components new TimerMilliC(), new DemoSensorC() as Sensor; 
  components new AMSenderC(AM_CONTOURTRACKING), new AMReceiverC(AM_CONTOURTRACKING);

	MainC.SoftwareInit -> TimeSyncC;
	TimeSyncC.Boot -> MainC;

  components ContourTrackingC as App;
  App.Boot -> MainC;
  App.Leds -> LedsC;
  App.Read -> Sensor;
  App.RadioControl -> ActiveMessageC;
  App.AMSend -> AMSenderC;
  App.Receive -> AMReceiverC;
	App.Packet -> ActiveMessageC;
	App.PacketTimeStamp -> ActiveMessageC;
	App.GlobalTime -> TimeSyncC;
	App.TimeSyncInfo -> TimeSyncC;
  App.Timer -> TimerMilliC;

	//components CounterMilli32C;
	//components new CounterToLocalTimeC(TMilli) as LocalTimeMilli32C;
	//LocalTimeMilli32C.Counter -> CounterMilli32C;
}
