import java.util.*;
import java.awt.*;

class MoteGridPainter {
	ContourTracking.Snapshot fSnapshot;
	MoteGridPainter(ContourTracking.Snapshot snapshot) {
		fSnapshot = snapshot;
	}


	void paintComponent(Graphics g, int width, int height) {
		// draw canvas
		Graphics2D g2d = (Graphics2D)g;
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, width, height);

		// compute colors and draw motes
		int DIM = fSnapshot.getGridDimension();
		int centerX = width / 2;
		int centerY = height / 2;
		int marginX = width / 4;
		int marginY = height / 100 * 30;
		int gridX = marginX;
		int gridY = marginY;
		int gridWidth = width - 2 * marginX;
		int gridHeight = height - 2 * marginY;
		int offsetX = gridWidth / (DIM - 1);
		int offsetY = gridHeight / (DIM - 1);
		int radius = 15;

		// draw grid lines
		float []f={ 10f, 10f };
		Stroke stroke = g2d.getStroke();
		g2d.setStroke(new BasicStroke(0, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0F, f, 0f)); 
		g2d.setColor(Color.WHITE);
		g2d.drawLine(gridX, gridY, gridX+gridWidth, gridY);
		g2d.drawLine(gridX, gridY, gridX, gridY+gridHeight);
		g2d.drawLine(gridX+gridWidth, gridY+gridHeight, gridX, gridY+gridHeight);
		g2d.drawLine(gridX+gridWidth, gridY+gridHeight, gridX+gridWidth, gridY);
		for(int i  = 1; i < DIM - 1; i++) {
			g2d.drawLine(gridX + (i * gridWidth / (DIM-1)), gridY, gridX + (i * gridWidth / (DIM-1)), gridY + gridHeight);
			g2d.drawLine(gridX, gridY + (i * gridHeight / (DIM-1)), gridX + gridWidth, gridY + (i * gridHeight / (DIM-1)));
		}
		g2d.setStroke(stroke);

		for(Map.Entry<Integer, ContourTracking.Mote> entry: fSnapshot.entrySet()) {
			boolean same = true;
			int id = entry.getKey().intValue();
			ContourTracking.Mote mote = entry.getValue();

			ContourTracking.Mote nbr = fSnapshot.getMote(fSnapshot.getMoteNeighborId(id, ContourTracking.Position.L));
			if(nbr != null) 
				same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

			if(same && (nbr = fSnapshot.getMote(fSnapshot.getMoteNeighborId(id, ContourTracking.Position.R))) != null) 
				same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

			if(same && (nbr = fSnapshot.getMote(fSnapshot.getMoteNeighborId(id, ContourTracking.Position.B))) != null) 
				same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

			if(same && (nbr = fSnapshot.getMote(fSnapshot.getMoteNeighborId(id, ContourTracking.Position.U))) != null) 
				same = (mote.isAboveThreshold() == nbr.isAboveThreshold());

			mote.setColor(same ? (mote.isAboveThreshold() ? Color.WHITE : Color.BLACK) : Color.GRAY);
			int idx = fSnapshot.getMoteIndex(id);
			int row = idx / DIM;
			int col = idx % DIM;
			int moteX = gridX + col * offsetX;
			int moteY = gridY + gridHeight - row * offsetY;
			//System.out.printf("mote[%d] idx: %d, sample: %d, color: %s\n", id, idx, mote.getSample(), mote.getColor());

			// draw mote on the grid
			if(mote.getColor() == Color.BLACK) { // black
				g2d.setColor(mote.getColor());
				g2d.fillRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
				g2d.setColor(Color.WHITE);
				g2d.drawRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
				g2d.setColor(Color.WHITE);
				g2d.drawString(String.valueOf(id), moteX-3, moteY+4);
			} else { // white or gray
				g2d.setColor(mote.getColor());
				g2d.fillRect(moteX-radius, moteY-radius, 2*radius, 2*radius);
				g2d.setColor(mote.getColor() == Color.WHITE ? Color.BLACK : Color.RED);
				g2d.drawString(String.valueOf(id), moteX-3, moteY+4);
			}
		}

		// draw contour splines for each blob
		//System.out.printf("offset(%d, %d)\n", offsetX, offsetY);
		for(ContourTracking.Blob blob: fSnapshot.getBlobs()) {
			ContourSpline spline = new ContourSpline();
			Set<Integer> contourMotes = new HashSet<Integer>();
			for(Integer moteId: blob.getMotes()) {
				int idx = fSnapshot.getMoteIndex(moteId.intValue());
				int row = idx / DIM; // row
				int col = idx % DIM; // col
				int moteX = gridX + col * offsetX;
				int moteY = gridY + gridHeight - row * offsetY;
				int shiftX = col == 0 ? offsetX / 4 : col == DIM -1 ? -offsetX / 4 : 0;
				int shiftY = row == 0 ? -offsetY / 4 : row == DIM -1 ? offsetY / 4 : 0;
				spline.addShape(new Rectangle(moteX + shiftX - offsetX/2, moteY + shiftY - offsetY /2, offsetX, offsetY));
				//if(true) continue;
				Set<ContourTracking.Position> positions = new TreeSet<ContourTracking.Position>();
				positions.add(ContourTracking.Position.L);
				positions.add(ContourTracking.Position.R);
				positions.add(ContourTracking.Position.U);
				positions.add(ContourTracking.Position.B);
				for(ContourTracking.Position pos: positions) {
					int nbrId = fSnapshot.getMoteNeighborId(moteId.intValue(), pos);
					if(nbrId < 0 || !blob.contains(nbrId)) {
						//System.out.printf("add contour mote at (%d, %d), (%d, %d)\n", col, row, moteX, moteY);
						contourMotes.add(moteId);
						Set<ContourTracking.Position> diagonals = new TreeSet<ContourTracking.Position>();
						diagonals.add(ContourTracking.Position.UL);
						diagonals.add(ContourTracking.Position.UR);
						diagonals.add(ContourTracking.Position.BL);
						diagonals.add(ContourTracking.Position.BR);
						for(ContourTracking.Position diagonal: diagonals) {
							nbrId = fSnapshot.getMoteNeighborId(moteId.intValue(), diagonal);
							if(nbrId > 0 && contourMotes.contains(new Integer(nbrId))) {
								int nbrIdx = fSnapshot.getMoteIndex(nbrId);
								int nbrRow = nbrIdx / DIM;
								int nbrCol = nbrIdx % DIM;
								int nbrX = gridX + nbrCol * offsetX;
								int nbrY = gridY + gridHeight - nbrRow * offsetY;
								//System.out.printf("found diagonal contour neighbor at (%d, %d), (%d, %d)\n", nbrCol, nbrRow, nbrX, nbrY);
								Polygon polygon = new Polygon();
								switch(diagonal) {
									case UL:
									case BR:
										polygon.addPoint(nbrX - offsetX/5, nbrY + offsetY/5);
										polygon.addPoint(nbrX + offsetX/5, nbrY - offsetY/5);
										polygon.addPoint(moteX + offsetX/5, moteY - offsetY/5);
										polygon.addPoint(moteX - offsetX/5, moteY + offsetY/5);
										spline.addShape(polygon);
										break;
									case UR:
									case BL:
										polygon.addPoint(nbrX - offsetX/5, nbrY - offsetY/5);
										polygon.addPoint(nbrX + offsetX/5, nbrY + offsetY/5);
										polygon.addPoint(moteX + offsetX/5, moteY + offsetY/5);
										polygon.addPoint(moteX - offsetX/5, moteY - offsetY/5);
										spline.addShape(polygon);
										break;
									default:
								}
							}
						}
						break;
					}
				}
			}
			g2d.setColor(Color.RED);
			spline.paint(g);
		}
	}
}
