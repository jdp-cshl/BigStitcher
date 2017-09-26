/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.stitcher.gui.popup;

import java.awt.Checkbox;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import org.joda.time.DateTime;

import fiji.util.gui.GenericDialogPlus;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOpt;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOptIterative;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOptTwoRound;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.SimpleIterativeConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.linkremoval.MaxErrorLinkRemoval;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.strong.ImageCorrelationPointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.weak.MetaDataWeakLinkFactory;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.Subset;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.algorithm.globalopt.GlobalOptimizationParameters;
import net.preibisch.stitcher.algorithm.globalopt.GlobalOptimizationParameters.GlobalOptType;
import net.preibisch.stitcher.gui.StitchingExplorerPanel;
import net.preibisch.stitcher.gui.StitchingResultsSettable;

public class OptimizeGloballyPopupExpertBatch extends JMenuItem
		implements ExplorerWindowSetable, StitchingResultsSettable
{

	// minimal link quality (e.g. cross correlation)
	public static final double minLinkQuality = 0.0;

	private StitchingResults stitchingResults;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	private boolean expertMode;

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.stitchingResults = res;
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}

	public static boolean processGlobalOptimization(
			final SpimData2 data,
			final SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping,
			final GlobalOptimizationParameters params,
			final boolean fixFirstTileByDefault)
	{
		// why can type not be BasicViewDescription?
		PairwiseSetup< ViewId > setup = new PairwiseSetup< ViewId >(
				filteringAndGrouping.getFilteredViews().stream().map( x -> (ViewId) x )
						.collect( Collectors.toList() ),
				filteringAndGrouping.getGroupedViews( false )
						.stream().map( x -> new Group< ViewId >( x.getViews().stream()
								.map( y -> (ViewId) y ).collect( Collectors.toList() ) ) )
						.collect( Collectors.toSet() ) )
		{

			@Override
			protected List< Pair< ViewId, ViewId > > definePairsAbstract()
			{
				List< Pair< ViewId, ViewId > > res = new ArrayList<>();
				for ( int i = 0; i < views.size(); i++ )
					for ( int j = i + 1; j < views.size(); j++ )
					{
						boolean differInApplicationAxis = false;
						for ( Class< ? extends Entity > cl : filteringAndGrouping.getAxesOfApplication() )
						{
							// ugly, but just undoes the casting to
							// ViewId in constructor
							BasicViewDescription< ? extends BasicViewSetup > vdA = (BasicViewDescription< ? extends BasicViewSetup >) views
									.get( i );
							BasicViewDescription< ? extends BasicViewSetup > vdB = (BasicViewDescription< ? extends BasicViewSetup >) views
									.get( j );

							if ( cl == TimePoint.class )
								differInApplicationAxis |= !vdA.getTimePoint().equals( vdB.getTimePoint() );
							else
								differInApplicationAxis |= !vdA.getViewSetup().getAttribute( cl )
										.equals( vdB.getViewSetup().getAttribute( cl ) );
						}
						if ( !differInApplicationAxis )
							res.add( new ValuePair< ViewId, ViewId >( views.get( i ), views.get( j ) ) );
					}

				return res;
			}

			@Override
			public List< ViewId > getDefaultFixedViews()
			{
				return new ArrayList<>();
			}
		};

		setup.definePairs();
		setup.detectSubsets();
		ArrayList< Subset< ViewId > > subsets = setup.getSubsets();


		final Collection< ? extends Collection< ViewId > > fixedViews;
		if (fixFirstTileByDefault)
		{
			// get first group of each subset by default
			fixedViews = subsets.stream().map( subset -> subset.getGroups().iterator().next().getViews() ).collect( Collectors.toList() );
		}
		else
		{
			fixedViews = askForFixedViews( subsets );
			if (fixedViews == null)
				return false;
		}

		final Iterator< ? extends Collection< ViewId > > fixedIterator = fixedViews.iterator();

		int subsetIdx = -1;
		for ( Subset< ViewId > subset : subsets )
		{
			System.out.println( "subset " + (++subsetIdx) );
			System.out.println( subset );

			final Collection< ViewId > fixed = fixedIterator.next();

			Collection< PairwiseStitchingResult< ViewId > > results = data.getStitchingResults().getPairwiseResults()
					.values();
			// filter to only process links between selected views
			results = results.stream()
					.filter( psr -> subset.getGroups().contains( psr.pair().getA() )
							&& subset.getGroups().contains( psr.pair().getB() ) )
					.collect( Collectors.toList() );
			// filter bad hashes here
			final int numLinksBefore = results.size();
			results = results.stream().filter( psr -> 
			{
				final ViewId firstVidA = psr.pair().getA().getViews().iterator().next();
				final ViewId firstVidB = psr.pair().getB().getViews().iterator().next();
				final ViewRegistration vrA = data.getViewRegistrations().getViewRegistration( firstVidA );
				final ViewRegistration vrB = data.getViewRegistrations().getViewRegistration( firstVidB );
				final double hash = PairwiseStitchingResult.calculateHash( vrA, vrB );
				return psr.getHash() == hash;
			}).collect( Collectors.toList() );
			final int numLinksAfter = results.size();

			if (numLinksAfter != numLinksBefore)
			{
				IOFunctions.println("Removed " + ( numLinksBefore - numLinksAfter ) + " of " + numLinksBefore + 
						" pairwise results because the underlying view registrations have changed.");
				IOFunctions.println("Did you try to re-run the global optimization after aligning the dataset?");
				IOFunctions.println("In that case, you can remove the latest transformation and try again.");
			}

			if (numLinksAfter < 1)
			{
				IOFunctions.println( new Date(System.currentTimeMillis()) + ": no links remaining in subset " + subsetIdx + ", skipping.");
				continue;
			}

			if ( params.method == GlobalOptType.TWO_ROUND )
			{
				HashMap< ViewId, AffineTransform3D > globalOptResults = GlobalOptTwoRound.compute(
						new TranslationModel3D(),
						new ImageCorrelationPointMatchCreator( results, minLinkQuality ),
						new SimpleIterativeConvergenceStrategy( params.absoluteThreshold,
								params.relativeThreshold, params.absoluteThreshold ),
						new MaxErrorLinkRemoval(),
						new MetaDataWeakLinkFactory(
								data.getViewRegistrations().getViewRegistrations(),
								new SimpleBoundingBoxOverlap<>( data ) ),
						new ConvergenceStrategy( Double.MAX_VALUE ), fixed,
						subset.getGroups() );

				globalOptResults.forEach( (k, v) -> System.out.println( k + ": " + v ) );
				globalOptResults.forEach( (k, v) -> {

					final ViewRegistration vr = data.getViewRegistrations()
							.getViewRegistration( k );

					AffineTransform3D viewTransform = new AffineTransform3D();
					viewTransform.set( v );

					final ViewTransform vt = new ViewTransformAffine( "Stitching Transform",
							viewTransform );
					vr.preconcatenateTransform( vt );
					vr.updateModel();

				} );
			}
			else if ( params.method == GlobalOptType.ITERATIVE)
			{
				HashMap< ViewId, mpicbg.models.Tile< TranslationModel3D > > globalOptResults = GlobalOptIterative.compute(
						new TranslationModel3D(),
						new ImageCorrelationPointMatchCreator( results, minLinkQuality ),
						new SimpleIterativeConvergenceStrategy( params.absoluteThreshold,
								params.relativeThreshold, params.absoluteThreshold ),
						new MaxErrorLinkRemoval(), fixed, subset.getGroups() );

				globalOptResults.forEach( (k, v) -> System.out.println( k + ": " + v ) );
				globalOptResults.forEach( (k, v) -> {

					final ViewRegistration vr = data.getViewRegistrations().getViewRegistration( k );
					AffineTransform3D viewTransform = new AffineTransform3D();
					viewTransform.set( v.getModel().getMatrix( null ) );

					final ViewTransform vt = new ViewTransformAffine( "Stitching Transform", viewTransform );
					vr.preconcatenateTransform( vt );
					vr.updateModel();

				} );
			}
			else // Simple global opt
			{
				final HashMap< ViewId, mpicbg.models.Tile< TranslationModel3D > > globalOptResults = GlobalOpt.compute( 
						new TranslationModel3D(),
						new ImageCorrelationPointMatchCreator( results, minLinkQuality ),
						new SimpleIterativeConvergenceStrategy( params.absoluteThreshold,
								params.relativeThreshold, params.absoluteThreshold ),
						fixed,
						subset.getGroups() );

				globalOptResults.forEach( (k, v) -> System.out.println( k + ": " + v ) );
				globalOptResults.forEach( (k, v) -> {

					final ViewRegistration vr = data.getViewRegistrations().getViewRegistration( k );
					AffineTransform3D viewTransform = new AffineTransform3D();
					viewTransform.set( v.getModel().getMatrix( null ) );

					final ViewTransform vt = new ViewTransformAffine( "Stitching Transform", viewTransform );
					vr.preconcatenateTransform( vt );
					vr.updateModel();

				} );
			}

		}

		return true;
	}

	public OptimizeGloballyPopupExpertBatch(boolean expertMode)
	{
		super( expertMode ? "Expert Mode" : "Simple Mode" );
		this.expertMode = expertMode;
		this.addActionListener( new MyActionListener() );
	}

	public static <V extends ViewId> Collection<? extends Collection<V> > askForFixedViews(ArrayList<? extends Subset< V > > subsets)
	{
		final ArrayList< Collection<V> > res = new ArrayList<>();
		final GenericDialogPlus gdp = new GenericDialogPlus( "Select Views to fix" );
		final boolean multipleSubsets = !(subsets.size() < 2);

		int i = 0;
		for (final Subset<V> subset : subsets)
		{

			if (multipleSubsets)
				gdp.addMessage( "Views to fix in subset " + (++i), GUIHelper.largefont, GUIHelper.neutral );

			final List<Checkbox> cboxes = new ArrayList<>();
			final List< Group< V > > groups = new ArrayList<>(subset.getGroups());
			Collections.sort( groups, new Comparator< Group<V> >()
			{
				@Override
				public int compare(Group< V > o1, Group< V > o2)
				{
					final ArrayList< ViewId > o1List = new ArrayList<>( o1.getViews() );
					final ArrayList< ViewId > o2List = new ArrayList<>( o2.getViews() );
					Collections.sort( o1List );
					Collections.sort( o2List );
					Iterator< ViewId > it1 = o1List.iterator();
					Iterator< ViewId > it2 = o2List.iterator();
					while ( it1.hasNext() && it2.hasNext() )
					{
						int comp = it1.next().compareTo( it2.next() );
						if ( comp != 0 )
							return comp;
					}
					// list 1 is longer
					if ( it1.hasNext() )
						return -1;
					// list 2 is longer
					if ( it2.hasNext() )
						return 1;
					// lists equal
					else
						return 0;
				}
			} );

			final Iterator< Group< V > > it = groups.iterator();
			if (!it.hasNext())
				continue;

			gdp.addCheckbox( "Fix_Group_" + Group.gvids( it.next() ), true );
			final Checkbox cbI = (Checkbox) gdp.getCheckboxes().get( gdp.getCheckboxes().size() -1 );
			cboxes.add( cbI );

			it.forEachRemaining( g -> {
				gdp.addCheckbox( "Fix_Group_" + Group.gvids( g ), false );
				final Checkbox cbI2 = (Checkbox) gdp.getCheckboxes().get( gdp.getCheckboxes().size() -1 );
				cboxes.add( cbI2 );
			});

			gdp.addMessage( "", GUIHelper.largestatusfont, GUIHelper.warning );
			final Label warning = (Label) gdp.getMessage();

			for (final Checkbox cb : cboxes)
			{
				cb.addItemListener( e -> {
					boolean allFalse = true;
					for (final Checkbox cbI3 : cboxes)
						allFalse &= (!cbI3.getState());
					warning.setText( allFalse ? "WARNING: you are not fixing any view" + (multipleSubsets ? " in this subset." : ".") : "" );
				});
			}
		}

		GUIHelper.addScrollBars( gdp );

		gdp.showDialog();
		if (gdp.wasCanceled())
			return null;

		for (final Subset<V> subset : subsets)
		{
			final List< Group< V > > groups = new ArrayList<>(subset.getGroups());
			Collections.sort( groups, new Comparator< Group<V> >()
			{
				@Override
				public int compare(Group< V > o1, Group< V > o2)
				{
					final ArrayList< ViewId > o1List = new ArrayList<>( o1.getViews() );
					final ArrayList< ViewId > o2List = new ArrayList<>( o2.getViews() );
					Collections.sort( o1List );
					Collections.sort( o2List );
					Iterator< ViewId > it1 = o1List.iterator();
					Iterator< ViewId > it2 = o2List.iterator();
					while ( it1.hasNext() && it2.hasNext() )
					{
						int comp = it1.next().compareTo( it2.next() );
						if ( comp != 0 )
							return comp;
					}
					// list 1 is longer
					if ( it1.hasNext() )
						return -1;
					// list 2 is longer
					if ( it2.hasNext() )
						return 1;
					// lists equal
					else
						return 0;
				}
			} );

			final HashSet< V > resI = new HashSet<>();
			for (final Group<V> group: groups)
			{
				if (gdp.getNextBoolean())
					resI.addAll( group.getViews() );
			}
			res.add( resI );
		}
		return res;
	}

	private class MyActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{
			new Thread( new Runnable()
			{

				@Override
				public void run()
				{
					try
					{
						if (!SpimData2.class.isInstance( panel.getSpimData() ) )
						{
							IOFunctions.println(new Date( System.currentTimeMillis() ) + "ERROR: expected SpimData2, but got " + panel.getSpimData().getClass().getSimpleName());
							return;
						}

						final GlobalOptimizationParameters params = expertMode ? GlobalOptimizationParameters.askUserForParameters() : new GlobalOptimizationParameters();
						if ( params == null )
							return;

						final SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping;
						final boolean isSavedFaG = ( ( (StitchingExplorerPanel< ?, ? >) panel ).getSavedFilteringAndGrouping() != null );
						if ( !isSavedFaG )
						{
							FilteredAndGroupedExplorerPanel< SpimData2, ? > panelFG = (FilteredAndGroupedExplorerPanel< SpimData2, ? >) panel;
							filteringAndGrouping = new SpimDataFilteringAndGrouping< SpimData2 >(
									(SpimData2) panel.getSpimData() );

							if (expertMode)
							{
								filteringAndGrouping.askUserForFiltering( panelFG );
								if ( filteringAndGrouping.getDialogWasCancelled() )
									return;

								filteringAndGrouping.askUserForGrouping( panelFG );
								if ( filteringAndGrouping.getDialogWasCancelled() )
									return;
							}
							else
							{
								// use whatever is selected in panel as filters
								filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );

								// get the grouping from panel and compare Tiles
								panelFG.getTableModel().getGroupingFactors().forEach( g -> filteringAndGrouping.addGroupingFactor( g ));
								filteringAndGrouping.addComparisonAxis( Tile.class );

								// compare by Channel if channels were ungrouped in UI
								if (!panelFG.getTableModel().getGroupingFactors().contains( Channel.class ))
									filteringAndGrouping.addComparisonAxis( Channel.class );

								// compare by Illumination if illums were ungrouped in UI
								if (!panelFG.getTableModel().getGroupingFactors().contains( Illumination.class ))
									filteringAndGrouping.addComparisonAxis( Illumination.class );

							}
						}
						else
						{
							// FIXME: there is some generics work to be done,
							// obviously
							filteringAndGrouping = (SpimDataFilteringAndGrouping< SpimData2 >) ( (StitchingExplorerPanel< ?, ? >) panel ).getSavedFilteringAndGrouping();
						}

						processGlobalOptimization( (SpimData2) panel.getSpimData(), filteringAndGrouping, params, !expertMode);
					}
					finally
					{
						// remove saved filtering and grouping once we are done here
						// regardless of whether optimization was successful or not
						( (StitchingExplorerPanel< ?, ? >) panel ).setSavedFilteringAndGrouping( null );
					}

					panel.bdvPopup().updateBDV();

				}
			} ).start();

		}

	}

}