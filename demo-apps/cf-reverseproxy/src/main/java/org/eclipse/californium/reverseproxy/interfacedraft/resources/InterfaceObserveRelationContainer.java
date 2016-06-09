package org.eclipse.californium.reverseproxy.interfacedraft.resources;


import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObserveRelationContainer;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * This is a container for {@link ObserveRelation}s that resources use to hold
 * their observe relations. When a resource changes it will notify all relations
 * in the container. Each observe relation must only exist once. However, an
 * endpoint could establish more than one observe relation to the same resource.
 */
public class InterfaceObserveRelationContainer implements ObserveRelationContainer{

	/** The logger. */
    protected final static Logger LOGGER = Logger.getLogger(InterfaceObserveRelationContainer.class.getCanonicalName());
    
    /** The set of observe relations */
    private ConcurrentHashMap<String, InterfaceObserveRelation> observeRelations;

    /**
     * Constructs a container for observe relations.
     */
    public InterfaceObserveRelationContainer() {
        this.observeRelations = new ConcurrentHashMap<>();
    }

    /**
     * Adds the specified observe relation.
     *
     * @param relation the observe relation
     * @return true, if a old relation was replaced by the provided one,
     *         false, if the provided relation was added.
     */
    @Override
    public boolean add(ObserveRelation relation) {
        if (relation == null)
            throw new NullPointerException();
        ObserveRelation previous = observeRelations.put(relation.getKey(), (InterfaceObserveRelation) relation);
        if (null != previous) {
            previous.cancel();
            return true;
        }
        return false;
    }

    /**
     * Removes the specified observe relation.
     *
     * @param relation the observe relation
     * @return true, if successful
     */
    @Override
    public boolean remove(ObserveRelation relation) {
        if (relation == null)
            throw new NullPointerException();
        return observeRelations.remove(relation.getKey(), relation);
    }

    /**
     * Gets the number of observe relations in this container.
     *
     * @return the number of observe relations
     */
    @Override
    public int getSize() {
        return observeRelations.size();
    }

    /* (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public Iterator<ObserveRelation> iterator() {
    	Collection<ObserveRelation> ret = new ArrayList<>();
    	for(ObserveRelation rel : observeRelations.values())
    	{
    		ret.add(rel);
    	}
        return ret.iterator();
    }
}

