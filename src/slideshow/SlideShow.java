package slideshow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

public class SlideShow extends JFrame {
	private static final long serialVersionUID = 1L;

	private BlockingQueue<Image> imgQueue = new ArrayBlockingQueue<Image>( 3 ); 
	private Random rand = new Random();
	private JLabel imageArea;
	private Image currentImage;

	public SlideShow() {
		super( "Random slide show" );
		( imageArea = new JLabel()).setLayout( new GridBagLayout());
		imageArea.setHorizontalAlignment( JLabel.CENTER );
		imageArea.setVerticalAlignment( JLabel.CENTER );
		//imageArea.setBorder(BorderFactory.createLineBorder( Color.red, 4 ));
		imageArea.setOpaque( true );
		imageArea.setBackground( Color.black );
		imageArea.addComponentListener( new ComponentListener() {

			@Override
			public void componentResized(ComponentEvent e) {
				updateView();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
			}

			@Override
			public void componentShown(ComponentEvent e) {
			}

			@Override
			public void componentHidden(ComponentEvent e) {
			}
		});
		getContentPane().add( imageArea, BorderLayout.CENTER );
		setSize( 500, 400 );

		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e ) {
				System.exit( 0 );
			}
		});
	}

	public void start( File path ) {
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				for( ;; ) {
					try {
						BufferedImage img = fetchRandomFile( path );

						if( img == null )
							continue;

						imgQueue.put( img );
					}
					catch( Exception e ) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}.execute();

		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				for( ;; ) {
					try {
						currentImage = imgQueue.take();
						System.out.println( currentImage.toString());
						updateView();
						Thread.sleep( 5000 );
					}
					catch( Exception e ) {
						e.printStackTrace();
					}
				}
			}
		}.execute();
	}

	private void updateView() {
		if( null == currentImage )
			return;

		Image img = currentImage;

		if( imageArea.getWidth() < img.getWidth( null ) ||
			imageArea.getHeight() < img.getHeight( null ) ) {
			double wScale = (double) img.getWidth( null ) / imageArea.getWidth();
			double hScale = (double) img.getHeight( null ) / imageArea.getHeight();
			double scale = Math.max( wScale, hScale );
			int w = (int) ( img.getWidth( null ) / scale );
			int h = (int) ( img.getHeight( null ) / scale );
			img = img.getScaledInstance( w, h, Image.SCALE_SMOOTH );
		}

		imageArea.setIcon( new ImageIcon( img ));
		//img = null;
		//System.gc();
		//imageArea.setToolTipText( img.toString());
	}

	private BufferedImage fetchRandomFile( File path ) {
		ArrayList<File> fl = new ArrayList<File>( Arrays.asList( path.listFiles()));

		while( 0 < fl.size()) {
			int i = rand.nextInt( fl.size());
			File f = fl.get( i );

			if( f.isDirectory())
				return fetchRandomFile( f );
			
			try {
				return ImageIO.read( f );
			}
			catch( IOException e ) {
				fl.remove( i );
			}
		}

		return null;
	}

	public static void main( String[] args ) {
		SlideShow ss = new SlideShow();
		ss.start( new File( "F:\\Posters" ));
		ss.setVisible( true );
	}
}
