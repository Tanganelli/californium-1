package org.eclipse.californium.core.observe;

import java.util.Iterator;

public interface ObserveRelationContainer extends Iterable<ObserveRelation> {

	/**
	 * Adds the specified observe relation.
	 *
	 * @param relation the observe relation
	 * @return true, if a old relation was replaced by the provided one, 
	 *         false, if the provided relation was added.
	 */
	public boolean add(ObserveRelation relation);
	
	/**
	 * Removes the specified observe relation.
	 *
	 * @param relation the observe relation
	 * @return true, if successful
	 */
	public boolean remove(ObserveRelation relation);
	
	/**
	 * Gets the number of observe relations in this container.
	 *
	 * @return the number of observe relations
	 */
	public int getSize();
	
}
