package slideshow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
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
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

public class SlideShow extends JFrame {
	private static final long serialVersionUID = 1L;

	private BlockingQueue<Slide> imgQueue = new ArrayBlockingQueue<Slide>( 3 ); 
	private Random rand = new Random();
	private JLabel imageArea;
	private Slide currentSlide;
	private Cursor savedCursor;
	private long exposure = 7000;
	private boolean paused;

	/*
	 * This class is introduced to couple image with its path.
	 * When the show is paused the path is displayed.
	 */
	class Slide {
		Image  image;
		String path;
	};

	public SlideShow() {
		super( "Random slide show" );
		setUndecorated( true );
		setExtendedState( JFrame.MAXIMIZED_BOTH );
		addKeyListener( new KeyListener() {
			@Override
			public void keyTyped( KeyEvent e ) {
			}

			@Override
			public void keyPressed( KeyEvent e ) {
			}

			@Override
			public void keyReleased( KeyEvent e ) {
				if( e.getKeyCode() == KeyEvent.VK_ESCAPE ) {
					stop();
				}
				else if( e.getKeyCode() == KeyEvent.VK_SPACE ) {
					paused = ! paused;
					
					if( paused ) {
						imageArea.setBorder( BorderFactory.createTitledBorder(
								BorderFactory.createLineBorder( Color.white, 3 ),
								"  " + currentSlide.path + "  ", TitledBorder.CENTER, TitledBorder.BOTTOM,
								new Font( "Monospaced", Font.BOLD, 32 ), Color.white ));
					}
					else
						imageArea.setBorder( null );

				}
			}
		});

		imageArea = new JLabel();
		imageArea.setLayout( new GridBagLayout());
		imageArea.setHorizontalAlignment( JLabel.CENTER );
		imageArea.setVerticalAlignment( JLabel.CENTER );
		imageArea.setOpaque( true );
		imageArea.setBackground( Color.black );
		imageArea.addComponentListener( new ComponentListener() {
			@Override
			public void componentResized(ComponentEvent e) {
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

		/*
		 * Hide the cursor
		 */
		savedCursor = getContentPane().getCursor();
		getContentPane().setCursor( getContentPane().getToolkit().createCustomCursor(
                new BufferedImage( 1, 1, BufferedImage.TYPE_INT_ARGB ),
                new Point(), null ));
		setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE );

		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e ) {
				stop();
			}
		});
	}

	/**
	 * The method starts the show. It launches two workers.
	 * One fetches random images walking down the path and
	 * puts them to a small queue. The other worker takes
	 * the images from the queue and displays them on the
	 * screen.
	 *
	 * @param path images location in the file system.
	 */
	public void start( File path ) {
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				for( ;; ) {
					try {
						if( ! paused ) {
							Slide s = fetchRandomFile( path );

							if( s != null )
								imgQueue.put( s );
						}

						Thread.sleep( exposure );
					}
					catch( InterruptedException ignore ) {
					}
				}
			}
		}.execute();

		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				for( ;; ) {
					try {
						if( ! paused ) {
							updateView( imgQueue.take());
						}

						Thread.sleep( exposure );
					}
					catch( InterruptedException ignore ) {
					}
				}
			}
		}.execute();
	}

	/**
	 * The method stops the show. It restores the cursor saved
	 * on startup and exits the application.
	 */
	public void stop() {
		getContentPane().setCursor( savedCursor );
		System.exit( 0 );
	}

	/**
	 * The method puts a new image to the screen.
	 */
	private void updateView( Slide s ) {
		if( null == s )
			return;

		Image img = s.image;

		/*
		 * Scale the image to fit the screen.
		 */
		double wScale = (double) img.getWidth( null ) / imageArea.getWidth();
		double hScale = (double) img.getHeight( null ) / imageArea.getHeight();
		double scale = Math.max( wScale, hScale );
		int w = (int) ( img.getWidth( null ) / scale );
		int h = (int) ( img.getHeight( null ) / scale );
		img = img.getScaledInstance( w, h, Image.SCALE_SMOOTH );

		imageArea.setIcon( new ImageIcon( img ));

		/*
		 * Update the current slide variable so image path
		 * can be printed when the view is paused.
		 */
		currentSlide = s;
	}

	/**
	 * The method does a random walking down the file system from the point
	 * provided as path argument. 
	 * 
	 * @param path starting point for the search
	 * 
	 * @return a new slide with an image or null if it hits a dead end without finding an image file.
	 */
	private Slide fetchRandomFile( File path ) {
		ArrayList<File> fl = new ArrayList<File>( Arrays.asList( path.listFiles()));
		
		while( 0 < fl.size()) {
			int i = rand.nextInt( fl.size());
			File f = fl.get( i );

			try {
				if( f.isDirectory())
					return fetchRandomFile( f );

				/*
				 * If the path is a link find the original path
				 */
				 if( f.isFile() && f.getName().toLowerCase().endsWith( ".lnk" )) {
					fl.remove( i );
					LnkParser lp = new LnkParser( f );

					if( lp.isDirectory()) {
						File nf = new File( lp.getRealFilename());
						fl.add( nf );
						return fetchRandomFile( nf );
					}
				}

				/*
				 * Load image from the file, make a new slide and return it.
				 */
				BufferedImage bi = ImageIO.read( f );

				if( null == bi )
					throw new IOException( f.getCanonicalPath() + " is not an image" );

				System.out.println( f.getCanonicalPath());
				Slide s = new Slide();
				s.image = bi;
				s.path = f.getCanonicalPath();

				return s;
			}
			catch( IOException e ) {
				fl.remove( i );
			}
		}

		return null;
	}

	public static void main( String[] args ) {
		SlideShow ss = new SlideShow();
		ss.start( new File( "F:\\Screen Saver" ));
		ss.setVisible( true );
	}
}
