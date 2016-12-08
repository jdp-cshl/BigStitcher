package gui.popup;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import bdv.BigDataViewer;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.state.SourceState;
import gui.PreviewRegularGridPanel;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorer;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class TestPopup extends JMenuItem implements ExplorerWindowSetable {
	
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	PreviewRegularGridPanel< ? > regularGridPanel;

	public TestPopup() 
	{
		super( "Test..." );
		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}
	 

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			/*
			BigDataViewer bdv = panel.bdvPopup().getBDV();
			
			if (bdv == null)
			{
				IOFunctions.println( "BigDataViewer is not open. Please start it to access this funtionality." );
				return;
			}
			*/		
			
			regularGridPanel = new PreviewRegularGridPanel<>( panel );
									
			JFrame frame = new JFrame( "Link Explorer" );
			frame.add( regularGridPanel, BorderLayout.CENTER );
			frame.setSize( regularGridPanel.getPreferredSize() );
			
			frame.addWindowListener( new WindowAdapter()
			{
				@Override
				public void windowClosing( WindowEvent evt )
				{
					regularGridPanel.quit();
				}
			} );
			
			frame.pack();
			frame.setVisible( true );
			frame.requestFocus();
			
			/*
			for (int i = 0; i < bdv.getViewer().getVisibilityAndGrouping().numSources(); ++i)
			{
				Integer tpId = bdv.getViewer().getState().getCurrentTimepoint();
				SourceState<?> s = bdv.getViewer().getVisibilityAndGrouping().getSources().get( i );
				
				// get manual transform
				AffineTransform3D tAffine = new AffineTransform3D();
				((TransformedSource< ? >)s.getSpimSource()).getFixedTransform( tAffine );
				
				// get old transform
				ViewRegistration vr = panel.getSpimData().getViewRegistrations().getViewRegistration( new ViewId(tpId, i ));
				AffineGet old = vr.getTransformList().get( 1 ).asAffine3D();
				
				// update transform in ViewRegistrations
				AffineTransform3D newTransform = new AffineTransform3D();
				newTransform.set( old.get( 0, 3 ) + tAffine.get( 0, 3 ), 0, 3 );
				newTransform.set( old.get( 1, 3 ) + tAffine.get( 1, 3 ), 1, 3 );
				newTransform.set( old.get( 2, 3 ) + tAffine.get( 2, 3 ), 2, 3 );
				
				ViewTransform newVt = new ViewTransformAffine( "Translation", newTransform );				
				vr.getTransformList().set( 1, newVt );
				vr.updateModel();
				
				// reset manual transform
				((TransformedSource< ? >)s.getSpimSource()).setFixedTransform( new AffineTransform3D() );
				bdv.getViewer().requestRepaint();
			}
			
			panel.bdvPopup().updateBDV();			
			
			*/
		}
	}

}
