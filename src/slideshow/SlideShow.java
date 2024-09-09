package slideshow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

public class SlideShow extends JFrame {
	private static final long serialVersionUID = 1L;

	private BlockingQueue<Image> imgQueue = new ArrayBlockingQueue<Image>( 3 ); 
	private Random rand = new Random();
	private JLabel imageArea;
	private Image currentImage;
	private boolean paused;

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
					System.exit( 0 );
			    }
				else if( e.getKeyCode() == KeyEvent.VK_SPACE ) {
					paused = ! paused;
					
					if( paused )
						imageArea.setBorder( BorderFactory.createLineBorder( Color.red, 4 ));
					else
						imageArea.setBorder( null );

				}
			}
		});

		( imageArea = new JLabel()).setLayout( new GridBagLayout());
		imageArea.setHorizontalAlignment( JLabel.CENTER );
		imageArea.setVerticalAlignment( JLabel.CENTER );
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
		setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE );

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
						if( ! paused ) {
							BufferedImage img = fetchRandomFile( path );

							if( img != null )
								imgQueue.put( img );
						}

						Thread.sleep( 5000 );
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
						if( ! paused ) {
							currentImage = imgQueue.take();
							updateView();
						}

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

		double wScale = (double) img.getWidth( null ) / imageArea.getWidth();
		double hScale = (double) img.getHeight( null ) / imageArea.getHeight();
		double scale = Math.max( wScale, hScale );
		int w = (int) ( img.getWidth( null ) / scale );
		int h = (int) ( img.getHeight( null ) / scale );
		img = img.getScaledInstance( w, h, Image.SCALE_SMOOTH );

		imageArea.setIcon( new ImageIcon( img ));
	}

	private BufferedImage fetchRandomFile( File path ) {
		ArrayList<File> fl = new ArrayList<File>( Arrays.asList( path.listFiles()));
		
		while( 0 < fl.size()) {
			int i = rand.nextInt( fl.size());
			File f = fl.get( i );

			try {
				if( f.isDirectory())
					return fetchRandomFile( f );

				if( f.isFile() && f.getName().toLowerCase().endsWith( ".lnk" )) {
					fl.remove( i );
					LnkParser lp = new LnkParser( f );

					if( lp.isDirectory()) {
						File nf = new File( lp.getRealFilename());
						fl.add( nf );
						return fetchRandomFile( nf );
					}
				}

				System.out.println( f.getCanonicalPath());
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
		ss.start( new File( "F:\\Screen Saver" ));
		ss.setVisible( true );
	}
}
