/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.oiexplorer.core.model.event;

import fr.jmmc.oiexplorer.core.model.oi.SubsetDefinition;

/**
 * Base class for Subset definition events consumed by OIFitsCollectionListener
 */
public final class SubsetDefinitionEvent extends GenericEvent<OIFitsCollectionEventType> {

    /** subset definition related to this event */
    private final SubsetDefinition subsetDefinition;

    /**
     * Public constructor dealing with a subset definition
     * @param source event source
     * @param type event type
     * @param subsetDefinition subset definition related to this event
     */
    public SubsetDefinitionEvent(final Object source, final OIFitsCollectionEventType type, final SubsetDefinition subsetDefinition) {
        super(source, type, (subsetDefinition != null) ? subsetDefinition.getName() : null);
        this.subsetDefinition = subsetDefinition;
    }

    /**
     * Return the subset definition related to this event
     * @return subset definition related to this event or null if undefined
     */
    public SubsetDefinition getSubsetDefinition() {
        return subsetDefinition;
    }
}
