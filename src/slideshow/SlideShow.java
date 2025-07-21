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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

//import com.sun.jna.platform.win32.User32;

public class SlideShow extends JFrame {
	private static final long serialVersionUID = 1L;
	

	/* configuration parameters */
	private static String CFG_SHOW_FOLDER = "showFolder";
	private static String CFG_EXPOSURE_TIME = "exposureTime";
	private static String CFG_ENABLE_LOG = "enableLog";

	/* randomizing approach */
	private static enum RND { LIST, WALK };

	private BlockingQueue<Slide> imgQueue = new ArrayBlockingQueue<Slide>( 3 ); 
	private Random rand = new Random();
	private Logger logger = Logger.getLogger( SlideShow.class.getName());
	private String showFolder = System.getProperty( "user.home" ) + "\\Pictures";
	private String cfgFile = System.getProperty( "user.home" ) + "\\.slideshow\\cfg.txt";
	private String logFile = System.getProperty( "user.home" ) + "\\.slideshow\\log.txt";
	private ArrayList<String>filesList = new ArrayList<String>();
	private JLabel imageArea;
	private Slide currentSlide;
	private Cursor savedCursor;
	private long exposure = 7;
	private boolean enableLog = true;
	private boolean paused;
	private Properties prop = new Properties();
	private RND randomiser = RND.LIST;

	/*
	 * This class is introduced to couple image with its path.
	 * When the show is paused the path is displayed.
	 */
	class Slide {
		Image  image;
		String path;
	};

	public SlideShow( String[] args ) {
		super( "Random slide show" );

		/* load configuration */
		try( FileInputStream fis = new FileInputStream( cfgFile )) {
		    prop.load( fis );
			showFolder = prop.getProperty( CFG_SHOW_FOLDER );
			exposure = Long.decode( prop.getProperty( CFG_EXPOSURE_TIME ));
			enableLog = Boolean.parseBoolean( prop.getProperty( CFG_ENABLE_LOG ));
		}
		catch( FileNotFoundException ex ) {
			System.out.println( "Configuration file has not been found" );
		}
		catch( IOException ex ) {
			System.out.println( "Error reading configuration file" );
		}

		if( enableLog ) {
			/* make a file logger */
			try {
				FileHandler fh = new FileHandler( logFile, 50000, 1, false );
				fh.setFormatter( new SimpleFormatter());
				logger.addHandler( fh );
			}
			catch( SecurityException | IOException e ) {
				e.printStackTrace();
			}
		}

		/* check what arguments are passed by the system */
//		if( null != args )
//			for( int i = 0; i < args.length; i++ )
//				logger.log( Level.INFO, args[ i ] );

		/* take whole screen */
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

		/* hide the cursor */
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
	 * The method starts the show.
	 *
	 * @param path images location in the file system.
	 */
	public void start() {
		/* walk the file system and make a list of all files down */
		if( randomiser == RND.LIST ) {
			new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground() throws Exception {
					fillFileList( new File( showFolder ), filesList );
					return null;
				}
			}.execute();

			/*
			 * let the worker to add some files to the list
			 */
			delay( 5000 );
		}

		/*
		 * This worker gets random images and puts them
		 * to the queue for displaying.
		 */
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				Slide s;

				for( ;; ) {
					if( randomiser == RND.LIST )
						s = fetchRandomFile( filesList );
					else
						s = fetchRandomFile( new File( showFolder ));

					if( s != null )
						imgQueue.put( s );
				}
			}
		}.execute();
		 
		/*
		 * This worker takes the images from the queue
		 * and displays them on the screen.
		 */
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				for( ;; ) {
					if( ! paused )
						updateView( imgQueue.take());

					delay( exposure * 1000 );
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

		if( enableLog )
			logger.log( Level.INFO, s.path );
	}

	/**
	 * The method does a random walking down the file system from the point
	 * provided as path argument. It only works well if there is a large
	 * number of folders on the top level. Otherwise there is a good chance
	 * of repeating the same path.
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

				Slide s = new Slide();
				s.image = bi;
				s.path = f.getCanonicalPath();

				if( enableLog )
					logger.log( Level.INFO, s.path );

				return s;
			}
			catch( IOException e ) {
				fl.remove( i );
			}
		}

		return null;
	}

	/**
	 * The method gets a random file from the list of files.
	 * If a file cannot be loaded as image it is deleted from the list.
	 * 
	 * @param list the list of all available images
	 * 
	 * @return a new slide with an image or null if it hits a dead end without finding an image file.
	 */
	private Slide fetchRandomFile( ArrayList<String>list ) {
		for( ;; ) {
			int i = rand.nextInt( list.size());
			File f = new File( list.get( i ));

			try {
				/*
				 * Load image from the file, make a new slide and return it.
				 */
				BufferedImage bi = ImageIO.read( f );

				if( null != bi ) {
					Slide s = new Slide();
					s.image = bi;
					s.path = f.getCanonicalPath();

					if( enableLog )
						logger.log( Level.INFO, s.path );

					return s;
				}
				else {
					list.remove( i );
				}
			}
			catch( IOException e ) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * The method goes down the provided path and makes
	 * a list of all found files.
	 *
	 * @param path search starting point
	 * @param list the files list
	 */
	private void fillFileList( File path, ArrayList<String>list ) {
		try {
			/*
			 * If the path is a link find the original path
			 */
			if( path.isFile() && path.getName().toLowerCase().endsWith( ".lnk" )) {
				LnkParser lp = new LnkParser( path );

				if( lp.isDirectory()) {
					path = new File( lp.getRealFilename());
					fillFileList( path, list );
				}
			}

			if( path.isDirectory()) {
				File[] children = path.listFiles();

				for( File f : children ) {
					if( f.isDirectory() || f.getName().toLowerCase().endsWith( ".lnk" ))
						fillFileList( f, list );
					else
						list.add( f.getAbsolutePath());
				}
			}
		}
		catch( IOException e ) {
			System.out.println( e.getMessage());
		}
	}

	/**
	 * The method delays execution for certain time
	 *
	 * @param ms number of milliseconds to delay
	 */
	private void delay( long ms ) {
		try {
			Thread.sleep( ms );
		}
		catch( InterruptedException ignore ) {}
	}

	/**
	 * Application starting point
	 *
	 * @param args
	 * 
	 * /s - start in display mode
	 * /p <window handle> - Screen Saver selector panel is opened
	 * /c - open configuration panel
	 */
	public static void main( String[] args ) {
		if( "/s".equalsIgnoreCase( args[ 0 ] )) {
			SlideShow ss = new SlideShow( args );
			ss.start();
			ss.setVisible( true );
		}
	}
}
