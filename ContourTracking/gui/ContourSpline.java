import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;

/** this class represents a cubic polynomial */
class Cubic {
	float a,b,c,d;         /* a + b*u + c*u^2 +d*u^3 */
	public Cubic(float a, float b, float c, float d) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
	}

	/** evaluate cubic */
	public float eval(float u) {
		return (((d*u) + c)*u + b)*u + a;
	}
}

/** This class represents a curve defined by a sequence of control points */
class ControlCurve {
	protected Polygon pts;
	protected int selection = -1;
	public ControlCurve() {
		pts = new Polygon();
	}

	static Font f = new Font("Courier",Font.PLAIN,12);

	/** paint this curve into g.*/
	public void paint(Graphics g){
		FontMetrics fm = g.getFontMetrics(f);
		/*
		g.setFont(f);
		int h = fm.getAscent()/2;
		for(int i = 0; i < pts.npoints; i++)  {
			String s = Integer.toString(i);
			int w = fm.stringWidth(s)/2;
			g.drawString(Integer.toString(i),pts.xpoints[i]-w,pts.ypoints[i]+h);
		}
		*/		
	}

	static final int EPSILON = 36;  /* square of distance for picking */

	/** return index of control point near to (x,y) or -1 if nothing near */
	public int selectPoint(int x, int y) {
		int mind = Integer.MAX_VALUE;
		selection = -1;
		for (int i = 0; i < pts.npoints; i++) {
			int d = sqr(pts.xpoints[i]-x) + sqr(pts.ypoints[i]-y);
			if (d < mind && d < EPSILON) {
				mind = d;
				selection = i;
			}
		}
		return selection;
	}
	static int sqr(int x) {
		return x*x;
	}

	/** add a control point, return index of new control point */
	public int addPoint(int x, int y) {
		pts.addPoint(x,y);
		return selection = pts.npoints - 1;
	}

	/** set selected control point */
	public void setPoint(int x, int y) {
		if (selection >= 0) {
			pts.xpoints[selection] = x;
			pts.ypoints[selection] = y;
		}
	}

	/** remove selected control point */
	public void removePoint() {
		if (selection >= 0) {
			pts.npoints--;
			for (int i = selection; i < pts.npoints; i++) {
				pts.xpoints[i] = pts.xpoints[i+1];
				pts.ypoints[i] = pts.ypoints[i+1];
			}
		}
	}

	public String toString() {
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < pts.npoints; i++) {
			result.append(" " + pts.xpoints[i] + " " + pts.ypoints[i]);
		}
		return result.toString();
	}
}

class NatCubic extends ControlCurve{
	/* calculates the natural cubic spline that interpolates
	 * y[0], y[1], ... y[n]
	 * The first segment is returned as
	 * C[0].a + C[0].b*u + C[0].c*u^2 + C[0].d*u^3 0<=u <1
	 * the other segments are in C[1], C[2], ...  C[n-1] */
	Cubic[] calcNaturalCubic(int n, int[] x) {
		float[] gamma = new float[n+1];
		float[] delta = new float[n+1];
		float[] D = new float[n+1];
		int i;
		/* We solve the equation
		 *        [2 1       ] [D[0]]   [3(x[1] - x[0])  ]
		 *               |1 4 1     | |D[1]|   |3(x[2] - x[0])  |
		 *                      |  1 4 1   | | .  | = |      .         |
		 *                             |    ..... | | .  |   |      .         |
		 *                                    |     1 4 1| | .  |   |3(x[n] - x[n-2])|
		 *                                           [       1 2] [D[n]]   [3(x[n] - x[n-1])]
		 *                                                  
		 *                                                         by using row operations to convert the matrix to upper triangular
		 *                                                                and then back sustitution.  The D[i] are the derivatives at the knots.
		 *                                                                       */
		gamma[0] = 1.0f/2.0f;
		for ( i = 1; i < n; i++) {
			gamma[i] = 1/(4-gamma[i-1]);
		}
		gamma[n] = 1/(2-gamma[n-1]);

		delta[0] = 3*(x[1]-x[0])*gamma[0];
		for ( i = 1; i < n; i++) {
			delta[i] = (3*(x[i+1]-x[i-1])-delta[i-1])*gamma[i];
		}
		delta[n] = (3*(x[n]-x[n-1])-delta[n-1])*gamma[n];

		D[n] = delta[n];
		for ( i = n-1; i >= 0; i--) {
			D[i] = delta[i] - gamma[i]*D[i+1];
		}

		/* now compute the coefficients of the cubics */
		Cubic[] C = new Cubic[n];
		for ( i = 0; i < n; i++) {
			C[i] = new Cubic((float)x[i], D[i], 3*(x[i+1] - x[i]) - 2*D[i] - D[i+1],
					2*(x[i] - x[i+1]) + D[i] + D[i+1]);
		}
		return C;
	}

	final int STEPS = 12;

	/* draw a cubic spline */
	public void paint(Graphics g){
		super.paint(g);
		if (pts.npoints >= 2) {
			Cubic[] X = calcNaturalCubic(pts.npoints-1, pts.xpoints);
			Cubic[] Y = calcNaturalCubic(pts.npoints-1, pts.ypoints);

			/* very crude technique - just break each segment up into steps lines */
			Polygon p = new Polygon();
			p.addPoint((int) Math.round(X[0].eval(0)),
					(int) Math.round(Y[0].eval(0)));
			for (int i = 0; i < X.length; i++) {
				for (int j = 1; j <= STEPS; j++) {
					float u = j / (float) STEPS;
					p.addPoint(Math.round(X[i].eval(u)),
							Math.round(Y[i].eval(u)));
				}
			}
			g.drawPolyline(p.xpoints, p.ypoints, p.npoints);
		}
	}
}

class NatCubicClosed extends NatCubic{
	/* calculates the closed natural cubic spline that interpolates
	 *      x[0], x[1], ... x[n]
	 *           The first segment is returned as
	 *                C[0].a + C[0].b*u + C[0].c*u^2 + C[0].d*u^3 0<=u <1
	 *                     the other segments are in C[1], C[2], ...  C[n] */
	Cubic[] calcNaturalCubic(int n, int[] x) {
		float[] w = new float[n+1];
		float[] v = new float[n+1];
		float[] y = new float[n+1];
		float[] D = new float[n+1];
		float z, F, G, H;
		int k;
		/* We solve the equation
		 *        [4 1      1] [D[0]]   [3(x[1] - x[n])  ]
		 *               |1 4 1     | |D[1]|   |3(x[2] - x[0])  |
		 *                      |  1 4 1   | | .  | = |      .         |
		 *                             |    ..... | | .  |   |      .         |
		 *                                    |     1 4 1| | .  |   |3(x[n] - x[n-2])|
		 *                                           [1      1 4] [D[n]]   [3(x[0] - x[n-1])]
		 *                                                  
		 *                                                         by decomposing the matrix into upper triangular and lower matrices
		 *                                                                and then back sustitution.  See Spath "Spline Algorithms for Curves
		 *                                                                       and Surfaces" pp 19--21. The D[i] are the derivatives at the knots.
		 *                                                                              */
		w[1] = v[1] = z = 1.0f/4.0f;
		y[0] = z * 3 * (x[1] - x[n]);
		H = 4;
		F = 3 * (x[0] - x[n-1]);
		G = 1;
		for ( k = 1; k < n; k++) {
			v[k+1] = z = 1/(4 - v[k]);
			w[k+1] = -z * w[k];
			y[k] = z * (3*(x[k+1]-x[k-1]) - y[k-1]);
			H = H - G * w[k];
			F = F - G * y[k-1];
			G = -v[k] * G;
		}
		H = H - (G+1)*(v[n]+w[n]);
		y[n] = F - (G+1)*y[n-1];

		D[n] = y[n]/H;
		D[n-1] = y[n-1] - (v[n]+w[n])*D[n]; /* This equation is WRONG! in my copy of Spath */
		for ( k = n-2; k >= 0; k--) {
			D[k] = y[k] - v[k+1]*D[k+1] - w[k+1]*D[n];
		}


		/* now compute the coefficients of the cubics */
		Cubic[] C = new Cubic[n+1];
		for ( k = 0; k < n; k++) {
			C[k] = new Cubic((float)x[k], D[k], 3*(x[k+1] - x[k]) - 2*D[k] - D[k+1],
					2*(x[k] - x[k+1]) + D[k] + D[k+1]);
		}
		C[n] = new Cubic((float)x[n], D[n], 3*(x[0] - x[n]) - 2*D[n] - D[0],
				2*(x[n] - x[0]) + D[n] + D[0]);
		return C;
	}

}

public class ContourSpline {
	NatCubicClosed fSpline;
	Vector<Point> fPoints;
	Area fArea;

	public ContourSpline() {
		fSpline = new NatCubicClosed();
		fPoints = new Vector<Point>();
		fArea = new Area();
	}

	public void addPoint(int x, int y) {
		fPoints.add(new Point(x, y));
		//return fSpline.addPoint(x, y);
	}

	public void addShape(Shape shape) {
		fArea.add(new Area(shape));
	}

	public void paint(Graphics g) {
		Vector<Vector<Point>> pointLists = new Vector<Vector<Point>>();
		Point prevVector = null;
		for(PathIterator itr = fArea.getPathIterator(null); !itr.isDone(); itr.next()) {
			double[] coords = new double[6];
			switch(itr.currentSegment(coords)) {
				case PathIterator.SEG_MOVETO:
					prevVector = null;
					pointLists.add(new Vector<Point>());
					pointLists.lastElement().add(new Point((int)coords[0], (int)coords[1]));
					//System.out.printf("new spline and add control point(%d, %d)\n", (int)coords[0], (int)coords[1]);
					break;
				case PathIterator.SEG_LINETO: {
					Point	prev = pointLists.lastElement().lastElement();
					if(prevVector == null) {
						prevVector = new Point((int)(coords[0] - prev.getX()), (int)(coords[1] - prev.getY()));
						pointLists.lastElement().add(new Point((int)coords[0], (int)coords[1]));
					} else {
						Point vector = new Point((int)(coords[0] - prev.getX()), (int)(coords[1] - prev.getY()));
						double multiple = (prevVector.getX() != 0) ? vector.getX() / prevVector.getX() : vector.getY() / prevVector.getY();
						//System.out.printf("prev vector(%d, %d), vector(%d, %d), multiptle: %f\n", 
						//									(int)prevVector.getX(), (int)prevVector.getY(), (int)vector.getX(), (int)vector.getY(), multiple);
						if(prevVector.getX() * multiple == vector.getX() && prevVector.getY() * multiple == vector.getY()) {
							//System.out.printf("remove control point(%d, %d)\n", (int)prev.getX(), (int)prev.getY());
							pointLists.lastElement().remove(prev);
							prev = pointLists.lastElement().lastElement();
						}
						pointLists.lastElement().add(new Point((int)coords[0], (int)coords[1]));
						prevVector = new Point((int)(coords[0] - prev.getX()), (int)(coords[1] - prev.getY()));
					}
					//System.out.printf("add control point(%d, %d)\n", (int)coords[0], (int)coords[1]);
					break;
				}
				case PathIterator.SEG_CLOSE:
				default:
			}
		}

		Graphics2D g2d = (Graphics2D)g;
		for(Vector<Point> points: pointLists) {
			NatCubicClosed spline = new NatCubicClosed();
			System.out.println("add cpoints to spline --------------------");
			for(Point p: points) {
				spline.addPoint((int)p.getX(), (int)p.getY());
				System.out.printf("add cpoint(%d, %d) to spline\n", (int)p.getX(), (int)p.getY());
			}
			Stroke stroke = g2d.getStroke();
			g2d.setStroke(new BasicStroke(3));
			spline.paint(g);
			g2d.setStroke(stroke);
		}
		for(Vector<Point> points: pointLists) {
			for(Point p: points) {
				Color c = g2d.getColor();
				g2d.setColor(Color.GREEN);
				g.fillOval((int)p.getX(), (int)p.getY(), 5, 5);
				g2d.setColor(c);
			}
		}
	}

	public static void main(String[] args) {
		Area area1 = new Area(new Rectangle(50, 50, 10, 10));
		Area area2 = new Area(new Rectangle(40, 40, 10, 10));
		Polygon poly = new Polygon();
		poly.addPoint(40, 50);
		poly.addPoint(50, 60);
		poly.addPoint(60, 50);
		poly.addPoint(50, 40);
		Area area = new Area(poly);
		area.add(area1);
		area.add(area2);
		double [] coords = new double[6];
		for (PathIterator pi = area.getPathIterator(null); !pi.isDone(); pi.next()) {
			int type = pi.currentSegment(coords);
			switch(type) {
				case PathIterator.SEG_MOVETO:
					System.out.printf("SEG_MOVETO (%d, %d)\n", (int)coords[0], (int)coords[1]);
					break;
				case PathIterator.SEG_LINETO:
					System.out.printf("SEG_LINETO (%d, %d)\n", (int)coords[0], (int)coords[1]);
					break;
				case PathIterator.SEG_QUADTO:
					System.out.printf("SEG_QUADTO (%d, %d)\n", (int)coords[0], (int)coords[1]);
					break;
				case PathIterator.SEG_CUBICTO:
					System.out.printf("SEG_CUBICTO (%d, %d)\n", (int)coords[0], (int)coords[1]);
					break;
				case PathIterator.SEG_CLOSE:
					System.out.printf("SEG_CLOSE (%d, %d)\n", (int)coords[0], (int)coords[1]);
					break;
			}
		}
		/*
			 JFrame frame = new JFrame("Contour Spline Test");
		//frame.setBackground(bgColor);
		//content.setBackground(bgColor);
		frame.setSize(200, 200);
		//frame.setContentPane(content);
		//frame.addWindowListener(new ExitListener());
		frame.setVisible(true);
		frame.addWindowListener(new WindowAdapter() {
		public void windowClosing(WindowEvent e) { System.exit(0); }
		});

		frame.getContentPane().add(new JComponent() {
		public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		g2.setStroke(new BasicStroke(5));

				ContourSpline spline = new ContourSpline();
				int x = 100, y = 100;
				spline.addPoint(x, y);
				spline.addPoint(x+20, y+20);
				spline.addPoint(x+10, y+50);
				spline.addPoint(x-15, y+20);
				spline.addPoint(x-5, y+15);
				spline.paint(g);
			}
		});
		*/
	}
}
