package algorithm.globalopt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class PairwiseStrategyTools
{
	
	// shorthand for when we do not care about fixed views or groups, but just want all (overlapping) pairs
	public static < V > List< Pair< V, V > > overlappingTiles(
			final HashMap< V, Dimensions > vd,
			final HashMap< V, TranslationGet > vl,
			final List< ? extends V > views )
	{
		return overlappingTiles( vd, vl, views, new ArrayList< V >(), new ArrayList< ArrayList< V > >() );		
	}
	
	
	public static < V > List< Pair< V, V > > overlappingTiles(
			final HashMap< V, Dimensions > vd,
			final HashMap< V, TranslationGet > vl,
			final List< ? extends V > views,
			final Collection< V > fixed,
			final Collection< ? extends Collection< V > > groups )
	{
		// all pairs that need to be compared
		final ArrayList< Pair< V, V > > viewPairs = new ArrayList< Pair< V, V >>();

		for ( int a = 0; a < views.size() - 1; ++a )
			for ( int b = a + 1; b < views.size(); ++b )
			{
				final V viewIdA = views.get( a );
				final V viewIdB = views.get( b );

				// only compare those to views if not both are fixed, are not
				// part of the same group, and are overlapping currently
				if ( validPair( viewIdA, viewIdB, fixed, groups ) &&
						overlaps( vd.get( viewIdA ), vd.get( viewIdB ), vl.get( viewIdA ), vl.get( viewIdB ) ) )
				{
					viewPairs.add( new ValuePair< V, V >( viewIdA, viewIdB ) );
				}
			}

		return viewPairs;
	}

	public static boolean overlaps(
			final Dimensions viewDimensionA,
			final Dimensions viewDimensionB,
			final TranslationGet viewLocationA,
			final TranslationGet viewLocationB )
	{
		for ( int d = 0; d < viewLocationA.numDimensions(); ++d )
		{
			final double startA = viewLocationA.getTranslation( d );
			final double startB = viewLocationB.getTranslation( d );
			
			final double endA = startA + viewDimensionA.dimension( d );
			final double endB = startB + viewDimensionB.dimension( d );

			if ( startB > startA && startB > endA )
				return false;

			if ( startA > startB && startA > endB )
				return false;
		}

		return true;
	}

	public static < V > boolean validPair( final V viewIdA, final V viewIdB,
			final Collection< V > fixed,
			final Collection< ? extends Collection< V >> groups )
	{
		if ( fixed.contains( viewIdA ) && fixed.contains( viewIdB ) )
			return false;

		if ( oneSetContainsBoth( viewIdA, viewIdB, groups ) )
			return false;

		return true;
	}

	public static < V > boolean oneSetContainsBoth( final V viewIdA,
			final V viewIdB, final Collection< ? extends Collection< V >> sets )
	{
		for (final Collection< V > set : sets)
			if ( set.contains( viewIdA ) && set.contains( viewIdB ) )
				return true;

		return false;
	}

}
