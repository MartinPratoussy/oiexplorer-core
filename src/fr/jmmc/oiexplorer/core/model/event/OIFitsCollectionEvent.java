/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.oiexplorer.core.model.event;

import fr.jmmc.oiexplorer.core.model.OIFitsCollection;

/**
 * Base class for OIFits collection events consumed by OIFitsCollectionListener
 */
public final class OIFitsCollectionEvent extends GenericEvent<OIFitsCollectionEventType> {

    /** OIFits collection related to this event */
    private final OIFitsCollection oiFitsCollection;

    /**
     * Public constructor dealing with an OIFits collection 
     * @param source event source
     * @param type event type
     * @param oiFitsCollection OIFits collection related to this event
     */
    public OIFitsCollectionEvent(final Object source, final OIFitsCollectionEventType type, final OIFitsCollection oiFitsCollection) {
        super(source, type);
        this.oiFitsCollection = oiFitsCollection;
    }

    /**
     * Return the OIFits collection related to this event
     * @return  OIFits collection related to this event or null if undefined
     */
    public OIFitsCollection getOIFitsCollection() {
        return oiFitsCollection;
    }
}
