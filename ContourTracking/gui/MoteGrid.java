/*
 * Copyright (c) 2006 Intel Corporation
 * All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE     
 * file. If you do not find these files, copies can be found by writing to
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300, Berkeley, CA, 
 * 94704.  Attention:  Intel License Inquiry.
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.util.*;

/* Panel for drawing mote-data graphs */
class MoteGrid extends JPanel {
	final static int BORDER_LEFT = 20;
	final static int BORDER_RIGHT =20;
	final static int BORDER_TOP = 10;
	final static int BORDER_BOTTOM = 10;

	final static int TICK_SPACING = 40;
	final static int MAX_TICKS = 16;
	final static int TICK_WIDTH = 10;

	final static int MIN_WIDTH = 50;

	//Maximum sensor reading - Vivek
	final static double MAXREAD = 1000.0 ;

	int gx0, gx1, gy0, gy1; // graph bounds
	int scale = 2; // gx1 - gx0 == MIN_WIDTH << scale
	Window parent;

	/* Graph to screen coordinate conversion support */
	int height, width;
	double xscale, yscale;

	void updateConversion() {
		height = getHeight() - BORDER_TOP - BORDER_BOTTOM;
		width = getWidth() - BORDER_LEFT - BORDER_RIGHT;
		if (height < 1) {
			height = 1;
		}
		if (width < 1) {
			width = 1;
		}
		xscale = (double)width / (gx1 - gx0 + 1);
		yscale = (double)height / (gy1 - gy0 + 1);
	}

	Graphics makeClip(Graphics g) {
		return g.create(BORDER_LEFT, BORDER_TOP, width, height);
	}

	MoteGrid(Window parent) {
		setPreferredSize(new Dimension(0, 800));
		this.parent = parent;
		gx0 = 0; gx1 = MIN_WIDTH << scale;
	}

	protected void paintComponent(Graphics g) {
		ContourTracking.Snapshot snapshot = parent.parent.getLatestSnapshot();
		if(snapshot == null)
			return;

		new MoteGridPainter(snapshot).paintComponent(g, getWidth(), getHeight());
	}
}
