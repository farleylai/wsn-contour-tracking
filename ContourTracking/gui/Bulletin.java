import javax.swing.*;
import java.awt.*;
import java.util.*;

/* As the name suggests, it serves as an information signage */
class Bulletin extends JPanel {
	private JLabel field;

	Bulletin() {
		super();
		field = new JLabel();
		setup();
	}

	private void setup() {
		field.setForeground(Color.WHITE);
		field.setFont(new Font("Georgia", Font.BOLD, 36));
		field.setAlignmentX(CENTER_ALIGNMENT);
		setBackground(Color.BLACK);
		add(field);
	}

	public void setText(String txt) {
		field.setText(txt);
	}
}
