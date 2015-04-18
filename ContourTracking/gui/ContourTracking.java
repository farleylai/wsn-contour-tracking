/*
 * Copyright (c) 2006 Intel Corporation
 * All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE     
 * file. If you do not find these files, copies can be found by writing to
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300, Berkeley, CA, 
 * 94704.  Attention:  Intel License Inquiry.
 */

import net.tinyos.message.*;
import net.tinyos.util.*;
import java.io.*;
import java.util.*;
import java.lang.Math;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

/* The "ContourTracking" demo app. Displays graphs showing data received from
   the ContourTracking mote application, and allows the user to:
   - zoom in or out on the X axis
   - set the scale on the Y axis
   - change the sampling period
   - change the color of each mote's graph
   - clear all data

   This application is in three parts:
   - the Node and Data objects store data received from the motes and support
     simple queries
   - the Window and Graph and miscellaneous support objects implement the
     GUI and graph drawing
   - the ContourTracking object talks to the motes and coordinates the other
     objects

   Synchronization is handled through the ContourTracking object. Any operation
   that reads or writes the mote data must be synchronized on ContourTracking.
   Note that the messageReceived method below is synchronized, so no further
   synchronization is needed when updating state based on received messages.
*/

public class ContourTracking extends TimerTask implements MessageListener, Serializable
{
	static final Integer MOTE_IDs[];
	static {
		MOTE_IDs = new Integer[16];
		for(int i = 0; i < MOTE_IDs.length ; i++)
			MOTE_IDs[i] = new Integer(i + 1);
	}

	enum Position {
		BL, B, BR, L, R, UL, U, UR
	}

	class Mote implements Serializable {
		Color fColor;
		int fThreshold;
		int fSample;
		long fSampleTimestamp;
		Mote(int threshold, int sample, long timestamp) {
			fThreshold = threshold;
			fSample = sample;
			fSampleTimestamp = timestamp;
		}

		Color getColor() {
			return fColor;
		}

		void setColor(Color color) {
			fColor = color;
		}

		int getSample() {
			return fSample;
		}

		long getSampleTimestamp() {
			return fSampleTimestamp;
		}
		
		boolean isAboveThreshold() {
			return fSample >= fThreshold;
		}
	}

	class Blob implements Serializable {
		Snapshot fSnapshot;
		Set<Integer> fMotes;
		Blob(Snapshot snapshot) {
			fSnapshot = snapshot;
			fMotes = new TreeSet<Integer>();
		}

		int size() {
			return fMotes.size();
		}

		void addMote(int id) {
			Mote mote = fSnapshot.getMote(id);
			if(mote == null || !mote.isAboveThreshold())
				return;

			fMotes.add(MOTE_IDs[id-1]);

			// search for neighbors BR, R, UL, U, UR
			Set<Position> positions = new TreeSet<Position>();
			positions.add(Position.L);
			positions.add(Position.B);
			positions.add(Position.R);
			positions.add(Position.U);
			positions.add(Position.BL);
			positions.add(Position.BR);
			positions.add(Position.UL);
			positions.add(Position.UR);
			for(Position pos: positions) {
				int neighborId = fSnapshot.getMoteNeighborId(id, pos);
				mote = fSnapshot.getMote(neighborId);
				//System.out.println("mote #" + id + ". has neighbor Id: " + neighborId + " and mote: " + mote);
				if(mote != null && mote.isAboveThreshold() && !contains(neighborId))
					addMote(neighborId);
			}
		}

		boolean contains(int id) {
			if(id < 1 || id > MOTE_IDs.length - 1)
				return false;

			return fMotes.contains(MOTE_IDs[id-1]);
		}

		Set<Integer> getMotes() {
			return fMotes;
		}

		boolean isNeighboring(Blob blob) {
			if(blob == this || isIntersected(blob))
				return false;

			for(Integer thatId: blob.getMotes()) {
				for(Integer thisId: fMotes)
					if(fSnapshot.isNeighboringMotes(thisId.intValue(), thatId.intValue()))
						return true;
			}
			return false;
		}

		boolean isIntersected(Blob blob) {
			for(Integer id: blob.getMotes())
				if(fMotes.contains(id))
					return true;

			return false;
		}

		public Point2D getCenter() {
			double x = 0, y = 0;
			final int DIM = fSnapshot.getGridDimension();
			for(Integer id: fMotes) {
				int idx = fSnapshot.getMoteIndex(id.intValue());
				x += idx % DIM;
				y += idx / DIM;
			}

			return new Point2D.Double(x / fMotes.size(), y / fMotes.size());
		}
	}

	static class Event implements Serializable {
		enum Direction {
			EAST, WEST, SOUTH, NORTH, NE, NW, SE, SW
		}

		boolean FORM, VANISH, MERGE, SPLIT, EXPAND, SHRINK, MOVE;
		Direction fDir;

		void setFORM() { FORM = true; }
		void setVANISH() { VANISH = true; }
		void setMERGE() { MERGE = true; }
		void setSPLIT() { SPLIT = true; }
		void setEXPAND() { EXPAND = true; }
		void setSHRINK() { SHRINK = true; }
		void setMOVE(Direction dir) { MOVE = true; fDir = dir; }

		boolean isMERGE() { return MERGE; }
		boolean isSPLIT() { return SPLIT; }

		public String toString() {
			String line = "";
			if(MOVE) {
				switch(fDir) {
					case EAST:
						line = "MOVED EAST";
						break;
					case WEST:
						line = "MOVED WEST";
						break;
					case SOUTH:
						line = "MOVED SOUTH";
						break;
					case NORTH:
						line = "MOVED NORTH";
						break;
					case NE:
						line = "MOVED NE";
						break;
					case NW:
						line = "MOVED NW";
						break;
					case SE:
						line = "MOVED SE";
						break;
					case SW:
						line = "MOVED SW";
						break;
				}
			}

			if(MERGE)
				line += line.length() > 0 ? ", MERGE" : "MERGE";
			if(SPLIT)
				line += line.length() > 0 ? ", SPLIT" : "SPLIT";
			if(EXPAND)
				line += line.length() > 0 ? ", EXPAND" : "EXPAND";
			if(SHRINK)
				line += line.length() > 0 ? ", SHRINK" : "SHRINK";
			if(FORM)
				line += line.length() > 0 ? ", FORM" : "BLOB FORM";
			if(VANISH)
				line += line.length() > 0 ? ", VANISH" : "BLOB VANISH";
			return line.length() == 0 ? "No Event" : line;
		}
	}

	class Snapshot implements Serializable {
		Map<Integer, Mote> fMoteGrid;
		Set<Blob> fBlobs;
		Event fEvent;
		Snapshot(ContourTracking contourTracker) {
			fMoteGrid = new TreeMap<Integer, Mote>();
			fBlobs = new HashSet<Blob>();
			for(int i = 0; i < contourTracker.getMotesCount(); i++) {
				int id = window.moteListModel.get(i);
				Mote mote = new Mote(contourTracker.getThreshold(), data.getData(id, data.maxX(id)), data.getLastSampleTimestamp(id));
				putMote(id, mote);
			}

			// blob clustering
			for(Map.Entry<Integer, Mote> entry: fMoteGrid.entrySet()) {
				if(!entry.getValue().isAboveThreshold())
					continue;

				boolean contained = false;
				for(Blob blob: fBlobs) {
					if(blob.contains(entry.getKey().intValue())) {
						contained = true;
						break;
					}
				}
				if(contained)
					continue;

				Blob blob = new Blob(this);
				blob.addMote(entry.getKey().intValue());
				fBlobs.add(blob);
			}
		}

		Event getEvent() {
			return fEvent;
		}

		void setEvent(Event event) {
			fEvent = event;
		}
			
		int size() {
			return fMoteGrid.size();
		}

		int sizeOfBlobs() {
			int size = 0;
			for(Blob blob: fBlobs)
				size += blob.size();

			return size;
		}

		int blobCount() {
			return fBlobs.size();
		}

		void putMote(int id, Mote mote) {
			if(mote == null || id < 1 || id > MOTE_IDs.length)
				return;

			fMoteGrid.put(MOTE_IDs[id-1], mote);
		}

		Set<Blob> getBlobs() {
			return fBlobs;
		}

		Set<Map.Entry<Integer, Mote>> entrySet() {
			return fMoteGrid.entrySet();
		}

		Mote getMote(int id) {
			if(id < 1 || id > MOTE_IDs.length)
				return null;

			return fMoteGrid.get(MOTE_IDs[id-1]);
		}

		int getMoteIndex(int id) {
			if(id < 1 || id > MOTE_IDs.length)
				return -1;
			
			int idx = 0;
			Integer key = MOTE_IDs[id-1];
			for(Integer k: fMoteGrid.keySet()) {
				if(k.intValue() == key.intValue())
					return idx;

				idx++;
			}
			return -1;
		}

		int getMoteId(int idx) {
			int i = 0;
			for(Integer key: fMoteGrid.keySet()) {
				if(i == idx)
					return key.intValue();

				i++;
			}
			return -1;
		}

		int getGridDimension() {
			return fMoteGrid.size() <= 9 ? 3 : 4;
		}

		int getMoteNeighborId(int id, Position pos) {
			int idx = getMoteIndex(id);
			if(idx == -1)
				return -1;

			int nbrIdx = -1;
			int row = idx / getGridDimension();
			int col = idx % getGridDimension();
			switch(pos) {
				case BL:
					return row - 1 >= 0 && col - 1 >=0 ? getMoteId(idx - getGridDimension() - 1) : -1;
				case B:
					return row - 1 >= 0 ? getMoteId(idx - getGridDimension()) : -1;
				case BR:
					return row - 1 >= 0 && col + 1 < getGridDimension() ? getMoteId(idx - getGridDimension() + 1) : -1;
				case L:
					return col - 1 >= 0 ? getMoteId(idx - 1) : -1;
				case R:
					return col + 1 < getGridDimension() ? getMoteId(idx + 1) : -1;
				case UL:
					return row + 1 < getGridDimension() && col - 1 >=0 ? getMoteId(idx + getGridDimension() - 1) : -1;
				case U:
					return row + 1 < getGridDimension() ? getMoteId(idx + getGridDimension()) : -1;
				case UR:
				default:
					return row + 1 < getGridDimension() && col + 1 < getGridDimension() ? getMoteId(idx + getGridDimension() + 1) : -1;
			}
		}

		boolean isNeighboringMotes(int thisId, int thatId) {
			if(getMoteIndex(thisId) == -1 || getMoteIndex(thatId) == -1)
				return false;
				
			if(getMoteNeighborId(thisId, Position.B) == thatId)
				return true;
			else if(getMoteNeighborId(thisId, Position.L) == thatId)
				return true;
			else if(getMoteNeighborId(thisId, Position.R) == thatId)
				return true;
			else if(getMoteNeighborId(thisId, Position.U) == thatId)
				return true;
			else if(getMoteNeighborId(thisId, Position.BL) == thatId)
				return true;
			else if(getMoteNeighborId(thisId, Position.BR) == thatId)
				return true;
			else if(getMoteNeighborId(thisId, Position.UL) == thatId)
				return true;
			else if(getMoteNeighborId(thisId, Position.UR) == thatId)
				return true;

			return false;
		}

		long getLatestSampleTimestamp() {
			long timestamp = -1;
			for(Map.Entry<Integer, Mote> entry: fMoteGrid.entrySet()) {
				if(timestamp < entry.getValue().getSampleTimestamp())
					timestamp = entry.getValue().getSampleTimestamp();
			}

			return timestamp;
		}

		long getEarliestSampleTimestamp() {
			long timestamp = Long.MAX_VALUE;
			for(Map.Entry<Integer, Mote> entry: fMoteGrid.entrySet()) {
				if(timestamp > entry.getValue().getSampleTimestamp())
					timestamp = entry.getValue().getSampleTimestamp();
			}

			return timestamp;
		}

		public boolean differs(Snapshot snapshot) {
			if(snapshot == null)
				return true;
			
			if(fMoteGrid.size() != snapshot.size())
				return true;

			for(Map.Entry<Integer, Mote> entry: fMoteGrid.entrySet()) {
				int id = entry.getKey().intValue();
				Mote thisMote = entry.getValue();
				Mote thatMote = snapshot.getMote(id);
				if(thatMote == null || thisMote.isAboveThreshold() != thatMote.isAboveThreshold())
					return true;
			}
			return false;
		}

		void debug() {
			System.out.println("------------------------------------------------------------------");
			System.out.print("snapshot {");
			Set<Map.Entry<Integer, Mote>> entrySet = fMoteGrid.entrySet();
			for(Iterator<Map.Entry<Integer, Mote>> itr = entrySet.iterator(); itr.hasNext();) {
				Map.Entry<Integer, Mote> entry = itr.next();
				System.out.print(entry.getKey().intValue() + (itr.hasNext() ? ", " : ""));
			}
			System.out.println("}");

			for(Blob blob: fBlobs) {
				System.out.print("blob {");
				Set<Integer> ids = blob.getMotes();
				for(Iterator<Integer> itr = ids.iterator(); itr.hasNext();) {
					Integer id = itr.next();
					System.out.print(id.intValue() + (itr.hasNext() ? ", " : ""));
				}
				System.out.println("}");
			}

			System.out.println("Earliest Sample Timestamp: " + getEarliestSampleTimestamp());
			System.out.println("Latest Sample Timestamp: " + getLatestSampleTimestamp());
			System.out.println("Timestamp Difference: " + (getLatestSampleTimestamp() - getEarliestSampleTimestamp()));
			System.out.println("Event(s): " + fEvent);
		}
	}

	transient MoteIF mote;
	transient Data data;
	transient Window window;

	/* The current sampling period. If we receive a message from a mote
	   with a newer version, we update our interval. If we receive a message
	   with an older version, we broadcast a message with the current interval
	   and version. If the user changes the interval, we increment the
	   version and broadcast the new interval and version. */
	int interval = Constants.DEFAULT_INTERVAL;
	int threshold = Constants.DEFAULT_THRESHOLD;
	int version = 0;

	boolean fRecording;
	transient ObjectOutputStream fOut;
	public boolean startRecording() {
		if(fRecording)
			return false;

		Calendar calendar = new GregorianCalendar();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
		String filename = dateFormat.format(calendar.getTime()) + ".ect";
		try {
			FileOutputStream fos = new FileOutputStream(filename);
			fOut = new ObjectOutputStream(fos);
			if(!snapshots.isEmpty());
				fOut.writeObject(snapshots.lastElement());
		} catch(IOException e) {
			e.printStackTrace();
			return false;
		}
		
		fRecording = true; 
		return true;
	}

	public boolean stopRecording() { 
		if(!fRecording)
			return false;

		try {
			fOut.close();
		} catch(IOException e) {
			e.printStackTrace();
		}

		fRecording = false;
		return true;
	}

	/* event tracking info */
	private Vector<Snapshot> snapshots = new Vector<Snapshot>();
	Snapshot getLatestSnapshot() {
		if(!snapshots.isEmpty()) {
			return snapshots.lastElement();
		}
		return null;
	}

	/* TimerTask: update motes clock periodically */
	public void run() {
		sendBeacon();
		track();
	}

	/* Main entry point */
	void exec() {
		data = new Data(this);
		window = new Window(this);
		window.setup();
		mote = new MoteIF(PrintStreamMessenger.err);
		mote.registerListener(new ContourTrackingMsg(), this);
		new Timer().schedule(this, 0, 500);
	}

	private synchronized boolean track() {
		Snapshot snapshot = new Snapshot(this);
		if(snapshots.isEmpty()) {
			snapshots.add(snapshot);
			return true;
		}

		Snapshot prevSnapshot = snapshots.lastElement();
		if(!snapshot.differs(prevSnapshot)) {
			//System.out.println("no significant change with the previous snapshot");
			snapshots.remove(prevSnapshot);
			snapshots.add(snapshot);
			return true;
		}

		detect(prevSnapshot, snapshot);
		snapshot.debug();
		if(fRecording) {
			try {
				fOut.writeObject(snapshot);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		snapshots.add(snapshot);
		if(snapshots.size() > 10)
			snapshots.remove(snapshots.firstElement());

		return true;
	}

	public int getMotesCount() {
		return window.moteListModel.size();
	}

	public int getGridDimension() {
		return window.moteListModel.size() <= 9 ? 3 : 4;
	}

	// function two print 2 decimal float
	double roundTwoDecimals(double d) {
		DecimalFormat twoDForm = new DecimalFormat("#.##");
		return Double.valueOf(twoDForm.format(d));
	}
	
	private void detect(Snapshot fromSnapshot, Snapshot toSnapshot) {
		if(fromSnapshot == null || toSnapshot == null)
			return;

		Event event = new Event();
		// MOVE: neighboring or intersect with only one blob of the same size
		for(Blob toBlob: toSnapshot.getBlobs()) {
			for(Blob fromBlob: fromSnapshot.getBlobs()) {
				if(toBlob.size() == fromBlob.size() && (toBlob.isNeighboring(fromBlob) || toBlob.isIntersected(fromBlob))) {
					Point2D from = fromBlob.getCenter();
					Point2D to = toBlob.getCenter();
					//System.out.println("from(" + from.getX() + ", " + from.getY() + "), to(" + to.getX() + ", " + to.getY() + ")");
					double dirX = roundTwoDecimals(to.getX() - from.getX());
					double dirY = roundTwoDecimals (to.getY() - from.getY());
					if(dirX>0) {
						if(dirY>0)
							event.setMOVE(Event.Direction.NE);
						else if(dirY<0)
							event.setMOVE(Event.Direction.SE);
						else
							event.setMOVE(Event.Direction.EAST);
					}	else if(dirX<0) {
						if(dirY>0)
							event.setMOVE(Event.Direction.NW);
						else if(dirY<0)
							event.setMOVE(Event.Direction.SW);
						else
							event.setMOVE(Event.Direction.WEST);
					}	else {
						if(dirY>0)
							event.setMOVE(Event.Direction.NORTH);
						else if(dirY<0)
							event.setMOVE(Event.Direction.SOUTH);
					}
				}
			}
		}

		// MERGE: intersect with two or more, last --> prev
		for(Blob toBlob: toSnapshot.getBlobs()) {
			int intersections = 0;
			for(Blob fromBlob: fromSnapshot.getBlobs()) {
				if(toBlob.isIntersected(fromBlob))
					intersections++;
			}

			if(intersections > 1) {
				event.setMERGE();
				break;
			}
		}

		// SPLIT: intersect with two or more, prev --> last
		for(Blob fromBlob: fromSnapshot.getBlobs()) {
			int intersections = 0;
			for(Blob toBlob: toSnapshot.getBlobs()) {
				if(fromBlob.isIntersected(toBlob))
					intersections++;
			}

			if(intersections > 1) {
				event.setSPLIT();
				break;
			}
		}


		// EXPAND: total number of bright motes increases
		// SHRINK: total number of bright motes decreases
		int fromSizeOfBlobs = fromSnapshot.sizeOfBlobs();
		int toSizeOfBlobs = toSnapshot.sizeOfBlobs();
		if(fromSizeOfBlobs < toSizeOfBlobs) {
			event.setEXPAND();
		} else if(fromSizeOfBlobs > toSizeOfBlobs) {
			event.setSHRINK();
		}

		// FORM
		// VANISH
		if(!event.isSPLIT() && fromSnapshot.blobCount() < toSnapshot.blobCount())
			event.setFORM();
		else if(!event.isMERGE() && fromSnapshot.blobCount() > toSnapshot.blobCount())
			event.setVANISH();

		toSnapshot.setEvent(event);
		window.showText(event + ", " + toSnapshot.getLatestSampleTimestamp());
		//System.out.println(event + ", " + toSnapshot.getLatestSampleTimestamp());
	}

	/* The data object has informed us that nodeId is a previously unknown
	   mote. Update the GUI. */
	void newNode(int nodeId) {
		window.newNode(nodeId);
	}

	public synchronized void messageReceived(int dest_addr,	Message msg) {
		if (msg instanceof ContourTrackingMsg) {
			ContourTrackingMsg omsg = (ContourTrackingMsg)msg;

			/* Update interval and mote data */
			periodUpdate(omsg.get_version(), omsg.get_interval());
			//System.out.println("mote[" + omsg.get_id() + "] msg seq: " + omsg.get_count() + " with clock: " + omsg.get_clock() + ", local ts: " + omsg.get_ftsp_local_timestamp() + ", global ts: " + omsg.get_ftsp_global_timestamp() + ", root id: " + omsg.get_ftsp_root_id() + ", skew: " + omsg.get_ftsp_skew() + ", synced: " + omsg.get_ftsp_synced() + " at " + System.currentTimeMillis());
			if(omsg.get_ftsp_synced() > 0) {
				data.update(omsg.get_id(), omsg.get_count(), omsg.get_readings(), omsg.get_ftsp_global_timestamp(), omsg.get_ftsp_synced() > 0);
				/* Inform the GUI that new data showed up */
				window.newData();
			}
		}
	}

	/* A potentially new version and interval has been received from the mote */
	void periodUpdate(int moteVersion, int moteInterval) {
		if (moteVersion > version) {
			/* It's new. Update our vision of the interval. */
			version = moteVersion;
			interval = moteInterval;
			window.updateSamplePeriod();
		}
		else if (moteVersion < version) {
			/* It's old. Update the mote's vision of the interval. */
			sendBeacon();
		}
	}

	/* The user wants to set the interval to newPeriod. Refuse bogus values
	   and return false, or accept the change, broadcast it, and return
	   true */
	synchronized boolean setInterval(int newPeriod) {
		if (newPeriod < 1 || newPeriod > 65535) {
			return false;
		}
		interval = newPeriod;
		version++;
		sendBeacon();
		return true;
	}

	/* The user wants to set the threshold to newThreshold. Refuse bogus values
	   and return false, or accept the change, broadcast it, and return
	   true */
	synchronized boolean setThreshold(int newThreshold) {
		if (newThreshold < 1 || newThreshold > 1000) {
			return false;
		}
		threshold = newThreshold;
		version++;
		sendBeacon();
		return true;
	}

	int getThreshold() {
		return threshold;
	}

	/* Broadcast a version+interval message. */
	void sendBeacon() {
		ContourTrackingMsg omsg = new ContourTrackingMsg();
		omsg.set_version(version);
		omsg.set_interval(interval);
		omsg.set_threshold(threshold);
		omsg.set_clock(System.currentTimeMillis());
		try {
			mote.send(MoteIF.TOS_BCAST_ADDR, omsg);
		}
		catch (IOException e) {
			window.error("Cannot send message to mote");
		}
	}

	/* User wants to clear all data. */
	void clear() {
		data = new Data(this);
	}

	public static void main(String[] args) {
		ContourTracking me = new ContourTracking();
		me.exec();
	}
}
