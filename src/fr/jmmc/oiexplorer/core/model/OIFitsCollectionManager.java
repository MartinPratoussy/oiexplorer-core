/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.oiexplorer.core.model;

import fr.jmmc.jmcs.gui.component.StatusBar;
import fr.jmmc.oiexplorer.core.model.event.EventNotifier;
import fr.jmmc.oiexplorer.core.model.event.GenericEvent;
import fr.jmmc.oiexplorer.core.model.event.OIFitsCollectionEvent;
import fr.jmmc.oiexplorer.core.model.event.OIFitsCollectionEventType;
import fr.jmmc.oiexplorer.core.model.event.PlotDefinitionEvent;
import fr.jmmc.oiexplorer.core.model.event.PlotEvent;
import fr.jmmc.oiexplorer.core.model.event.SubsetDefinitionEvent;
import fr.jmmc.oiexplorer.core.model.oi.Identifiable;
import fr.jmmc.oiexplorer.core.model.oi.OIDataFile;
import fr.jmmc.oiexplorer.core.model.oi.OiDataCollection;
import fr.jmmc.oiexplorer.core.model.oi.Plot;
import fr.jmmc.oiexplorer.core.model.oi.SubsetDefinition;
import fr.jmmc.oiexplorer.core.model.oi.TableUID;
import fr.jmmc.oiexplorer.core.model.plot.PlotDefinition;
import fr.jmmc.oiexplorer.core.util.Constants;
import fr.jmmc.oitools.model.OIFitsChecker;
import fr.jmmc.oitools.model.OIFitsFile;
import fr.jmmc.oitools.model.OIFitsLoader;
import fr.jmmc.oitools.model.OITable;
import fr.nom.tam.fits.FitsException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle the oifits files collection.
 * @author mella, bourgesl
 */
public final class OIFitsCollectionManager implements OIFitsCollectionEventListener {

    /** Logger */
    private static final Logger logger = LoggerFactory.getLogger(OIFitsCollectionManager.class);
    /** Singleton pattern */
    private final static OIFitsCollectionManager instance = new OIFitsCollectionManager();
    /** Current key for SubsetDefinition */
    public final static String CURRENT_SUBSET_DEFINITION = "CURRENT_SUBSET";
    /** Current key for PlotDefinition */
    public final static String CURRENT_PLOT_DEFINITION = "CURRENT_PLOT_DEF";
    /** Current key for View */
    public final static String CURRENT_VIEW = "CURRENT_VIEW";
    /* members */
    /** OIFits collection */
    private OIFitsCollection oiFitsCollection = null;
    /** Container of loaded data and user plot definitions */
    private OiDataCollection userCollection = null;
    /* event dispatchers */
    /** OIFitsCollectionEvent notifier */
    private final EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType> oiFitsCollectionEventNotifier;
    /** SubsetDefinitionEvent notifier */
    private final EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType> subsetDefinitionEventNotifier;
    /** PlotDefinitionEvent notifier */
    private final EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType> plotDefinitionEventNotifier;
    /** PlotEvent notifier */
    private final EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType> plotEventNotifier;

    /**
     * Return the Manager singleton
     * @return singleton instance
     */
    public static OIFitsCollectionManager getInstance() {
        return instance;
    }

    /** 
     * Prevent instanciation of singleton.
     * Manager instance should be obtained using getInstance().
     */
    private OIFitsCollectionManager() {
        super();

        // allow self notification:
        this.oiFitsCollectionEventNotifier = new EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType>(0, false);
        this.subsetDefinitionEventNotifier = new EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType>(10);
        this.plotDefinitionEventNotifier = new EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType>(20);
        this.plotEventNotifier = new EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType>(30);

        // listen for OIFitsCollectionEvent to analyze collection:
        this.oiFitsCollectionEventNotifier.register(this);

        // reset without firing events:
        reset(false);
    }

    /* --- OIFits file collection handling ------------------------------------- */
    /**
     * Load the given OI Fits Files with the given checker component
     * and add it to the OIFits collection
     * @param files files to load
     * @param checker checker component
     * @throws IOException if a fits file can not be loaded
     */
    public void loadOIFitsFiles(final File[] files, final OIFitsChecker checker) throws IOException {

        // fire OIFitsCollectionChanged:
        for (File file : files) {
            loadOIFitsFile(file.getAbsolutePath(), checker);
        }
    }

    /**
     * Load OIDataCollection files (TODO: plot def to be handled)
     * @param oiDataCollection OiDataCollection to look for
     * @param checker to report validation information
     * @throws IOException if a fits file can not be loaded
     */
    public void loadOIDataCollection(final OiDataCollection oiDataCollection, final OIFitsChecker checker) throws IOException {

        // first reset:
        reset();

        // fire OIFitsCollectionChanged:
        for (OIDataFile oidataFile : oiDataCollection.getFiles()) {
            loadOIFitsFile(oidataFile.getFile(), checker);
        }

        // TODO: check missing files !

        // TODO what about user plot definitions ...
        // add them but should be check for consistency related to loaded files (errors can occur while loading):

        // then add SubsetDefinition:
        for (SubsetDefinition subsetDefinition : oiDataCollection.getSubsetDefinitions()) {
            // fix OIDataFile reference:
            for (TableUID tableUID : subsetDefinition.getTables()) {
                tableUID.setFile(getOIDataFile(tableUID.getFile().getName()));
                // if missing, remove ?
            }
            addSubsetDefinitionRef(subsetDefinition);
        }

        // then add PlotDefinition:
        for (PlotDefinition plotDefinition : oiDataCollection.getPlotDefinitions()) {
            addPlotDefinitionRef(plotDefinition);
        }

        // TODO: check subset and plot definition references in Plot ?

        // then add Plot:
        for (Plot plot : oiDataCollection.getPlots()) {
            this.addPlotRef(plot);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("subsetDefinitions {}", this.userCollection.getSubsetDefinitions());
        }
    }

    /**
     * Load the given OI Fits File with the given checker component
     * and add it to the OIFits collection
     * @param fileLocation absolute File Path or remote URL
     * @param checker checker component
     * @throws IOException if a fits file can not be loaded
     */
    private void loadOIFitsFile(final String fileLocation, final OIFitsChecker checker) throws IOException {
        //@todo test if file has already been loaded before going further ??        

        StatusBar.show("loading file: " + fileLocation);

        try {
            // The file must be one oidata file (next line automatically unzip gz files)
            final OIFitsFile oifitsFile = OIFitsLoader.loadOIFits(checker, fileLocation);

            addOIFitsFile(oifitsFile);

            logger.info("file loaded : '{}'", oifitsFile.getAbsoluteFilePath());

        } catch (MalformedURLException mue) {
            throw new IOException("Could not load the file : " + fileLocation, mue);
        } catch (IOException ioe) {
            throw new IOException("Could not load the file : " + fileLocation, ioe);
        } catch (FitsException fe) {
            throw new IOException("Could not load the file : " + fileLocation, fe);
        }
    }

    // TODO: save / merge ... (elsewhere)
    /**
     * Reset the OIFits file collection
     */
    public void reset() {
        reset(true);
    }

    /**
     * Reset the OIFits file collection
     * @param notify true to fireOIFitsCollectionChanged()
     */
    private void reset(final boolean notify) {
        oiFitsCollection = new OIFitsCollection();
        userCollection = new OiDataCollection();

        if (notify) {
            fireOIFitsCollectionChanged();
        }
    }

    public OIFitsCollection getOIFitsCollection() {
        return oiFitsCollection;
    }

    public void addOIFitsFile(final OIFitsFile oiFitsFile) {
        if (oiFitsFile != null) {
            // check if already present in collection:
            if (oiFitsCollection.addOIFitsFile(oiFitsFile) == null) {

                // Add new OIDataFile in collection 
                final OIDataFile dataFile = new OIDataFile();

                final String id = oiFitsFile.getName().replaceAll(Constants.REGEXP_INVALID_TEXT_CHARS, "_");

                // TODO: make it unique !!
                dataFile.setName(id);

                dataFile.setFile(oiFitsFile.getAbsoluteFilePath());
                // checksum !

                // store oiFitsFile reference:
                dataFile.setOIFitsFile(oiFitsFile);

                addOIDataFileRef(dataFile);
            }

            fireOIFitsCollectionChanged();
        }
    }

    public OIFitsFile removeOIFitsFile(final OIFitsFile oiFitsFile) {
        final OIFitsFile previous = oiFitsCollection.removeOIFitsFile(oiFitsFile);

        if (previous != null) {
            // Remove OiDataFile from user collection
            final String filePath = oiFitsFile.getAbsoluteFilePath();

            for (final Iterator<OIDataFile> it = userCollection.getFiles().iterator(); it.hasNext();) {
                final OIDataFile dataFile = it.next();
                if (filePath.equals(dataFile.getFile())) {
                    it.remove();
                }
            }

            fireOIFitsCollectionChanged();
        }

        return previous;
    }

    /** This method can be used to export current file list */
    public OiDataCollection getUserCollection() {
        return userCollection;
    }

    /* --- identifiable finder ------------------------------------- */
    /**
     * Return an Identifiable object
     * @param name name
     * @param list list of identifiable object
     * @param <K> identifiable class type
     * @return Identifiable object or null if not found
     */
    private static <K extends Identifiable> K getIdentifiable(final String name, final List<K> list) {
        for (K object : list) {
            if (name.equals(object.getName())) {
                return object;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <K extends Identifiable> K clone(final K source) {
        if (source == null) {
            return null;
        }
        return (K) source.clone();
    }

    private static <K extends Identifiable> void copy(final K source, final K dest) {
        if (source != null) {
            throw new IllegalStateException("undefined source object");
        }
        dest.copy(source);
    }

    private static <K extends Identifiable> boolean addIdentifiable(final K object, final List<K> list) {
        if (object != null && object.getName() != null && getIdentifiable(object.getName(), list) == null) {
            // replace previous ??
            list.add(object);
            return true;
        }
        return false;
    }

    private static <K extends Identifiable> K removeIdentifiable(final K object, final List<K> list) {
        if (object != null) {
            return removeIdentifiable(object.getName(), list);
        }
        return null;
    }

    private static <K extends Identifiable> K removeIdentifiable(final String name, final List<K> list) {
        if (name != null) {
            final K previous = getIdentifiable(name, list);

            if (previous != null) {
                list.remove(previous);
            }

            return previous;
        }
        return null;
    }

    /* --- file handling ------------------------------------- */
    /**
     * Return an OIDataFile given its name
     * @param name file name
     * @return OIDataFile or null if not found
     */
    public OIDataFile getOIDataFile(final String name) {
        return getIdentifiable(name, this.userCollection.getFiles());
    }

    /**
     * Return an OIDataFile given its related OIFitsFile
     * @param oiFitsFile OIFitsFile to find
     * @return OIDataFile or null if not found
     */
    public OIDataFile getOIDataFile(final OIFitsFile oiFitsFile) {
        for (OIDataFile dataFile : this.userCollection.getFiles()) {
            if (oiFitsFile == dataFile.getOIFitsFile()) {
                return dataFile;
            }
        }
        return null;
    }

    /**
     * Add the given OIDataFile
     * @param dataFile OIDataFile to add
     * @return true if the given OIDataFile was added
     */
    private boolean addOIDataFileRef(final OIDataFile dataFile) {
        if (logger.isDebugEnabled()) {
            logger.debug("addOIDataFileRef: {}", dataFile);
        }
        return addIdentifiable(dataFile, this.userCollection.getFiles());
    }

    /**
     * Remove the OIDataFile given its identifier
     * @param name OIDataFile identifier
     */
    private void removeOIDataFile(final String name) {
        removeIdentifiable(name, this.userCollection.getFiles());
    }

    /* --- subset definition handling ------------------------------------- */
    /**
     * Return the current subset definition (copy)
     * @return subset definition (copy)
     */
    public SubsetDefinition getCurrentSubsetDefinition() {
        final SubsetDefinition subsetDefinition = clone(getCurrentSubsetDefinitionRef());

        if (logger.isDebugEnabled()) {
            logger.debug("getCurrentSubsetDefinition {}", subsetDefinition);
        }
        return subsetDefinition;
    }

    /**
     * Return the current subset definition (reference)
     * @return subset definition (reference)
     */
    public SubsetDefinition getCurrentSubsetDefinitionRef() {
        SubsetDefinition subsetDefinition = getSubsetDefinitionRef(CURRENT_SUBSET_DEFINITION);
        if (subsetDefinition == null) {
            subsetDefinition = new SubsetDefinition();
            subsetDefinition.setName(CURRENT_SUBSET_DEFINITION);

            addSubsetDefinitionRef(subsetDefinition);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("getCurrentSubsetDefinitionRef {}", subsetDefinition);
        }
        return subsetDefinition;
    }

    /**
     * Add the given SubsetDefinition
     * @param subsetDefinition SubsetDefinition to add
     * @return true if the given SubsetDefinition was added
     */
    public boolean addSubsetDefinition(final SubsetDefinition subsetDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("addSubsetDefinition: {}", subsetDefinition);
        }

        if (addSubsetDefinitionRef(subsetDefinition)) {
            // update subset reference and fire events (SubsetDefinitionChanged, PlotChanged):
            updateSubsetDefinitionRef(this, subsetDefinition);
            return true;
        }
        return false;
    }

    /**
     * Add the given SubsetDefinition
     * @param subsetDefinition SubsetDefinition to add
     * @return true if the given SubsetDefinition was added
     */
    private boolean addSubsetDefinitionRef(final SubsetDefinition subsetDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("addSubsetDefinitionRef: {}", subsetDefinition);
        }
        return addIdentifiable(subsetDefinition, this.userCollection.getSubsetDefinitions());
    }

    /**
     * Remove the SubsetDefinition given its identifier
     * @param name SubsetDefinition identifier
     */
    private void removeSubsetDefinition(final String name) {
        removeIdentifiable(name, this.userCollection.getSubsetDefinitions());
    }

    /**
     * Return a subset definition (copy) by its name
     * @param name subset definition name
     * @return subset definition (copy) or null if not found
     */
    public SubsetDefinition getSubsetDefinition(final String name) {
        final SubsetDefinition subsetDefinition = clone(getSubsetDefinitionRef(name));

        if (logger.isDebugEnabled()) {
            logger.debug("getSubsetDefinition {}", subsetDefinition);
        }
        return subsetDefinition;
    }

    /**
     * Return a subset definition (reference) by its name
     * @param name plot definition name
     * @return subset definition (reference) or null if not found
     */
    public SubsetDefinition getSubsetDefinitionRef(final String name) {
        return getIdentifiable(name, this.userCollection.getSubsetDefinitions());
    }

    /**
     * Return true if this subset definition exists in this data collection given its name
     * @param name subset definition name
     * @return true if this subset definition exists in this data collection given its name
     */
    public boolean hasSubsetDefinition(final String name) {
        return getSubsetDefinitionRef(name) != null;
    }

    /**
     * Update the subset definition corresponding to the same name
     * @param source event source
     * @param subsetDefinition subset definition with updated values
     */
    public void updateSubsetDefinition(final Object source, final SubsetDefinition subsetDefinition) {
        final SubsetDefinition subset = getSubsetDefinitionRef(subsetDefinition.getName());

        if (subset == null) {
            throw new IllegalStateException("subset not found : " + subsetDefinition);
        }

        boolean changed = false;

        if (subset != subsetDefinition) {
            changed = !OIBase.areEquals(subset, subsetDefinition);
        } else {
            throw new IllegalStateException("equal subset references : " + subset);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("updateSubsetDefinition: {}", subsetDefinition);
            logger.debug("updateSubsetDefinition: changed: {}", changed);
        }

        if (changed) {
            // copy data:
            subset.copy(subsetDefinition);

            // update subset reference and fire events (SubsetDefinitionChanged, PlotChanged):
            updateSubsetDefinitionRef(source, subset);
        }
    }

    /**
     * Update the given subset definition (reference) and fire events
     * @param source event source
     * @param subsetDefinition subset definition (reference)
     */
    private void updateSubsetDefinitionRef(final Object source, final SubsetDefinition subsetDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("updateSubsetDefinitionRef: subsetDefinition: {}", subsetDefinition);
        }

        // Get OIFitsFile structure for this target:
        final OIFitsFile oiFitsSubset;

        if (this.oiFitsCollection.isEmpty()) {
            oiFitsSubset = null;
        } else {
            final OIFitsFile dataForTarget = this.oiFitsCollection.getOiFits(subsetDefinition.getTarget());

            if (dataForTarget == null) {
                oiFitsSubset = null;
            } else {
                // apply table selection:
                if (subsetDefinition.getTables().isEmpty()) {
                    oiFitsSubset = dataForTarget;
                } else {
                    oiFitsSubset = new OIFitsFile();

                    for (TableUID table : subsetDefinition.getTables()) {
                        final OIDataFile oiDataFile = table.getFile();
                        final OIFitsFile oiFitsFile = oiDataFile.getOIFitsFile();

                        if (oiFitsFile != null) {
                            final Integer extNb = table.getExtNb();

                            // add all tables:
                            for (OITable oiData : dataForTarget.getOiTables()) {
                                // file path comparison:
                                if (oiData.getOIFitsFile().equals(oiFitsFile)) {

                                    if (extNb == null || oiData.getExtNb() == extNb.intValue()) {
                                        oiFitsSubset.addOiTable(oiData);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("updateSubsetDefinitionRef: oiFitsSubset: {}", oiFitsSubset);
        }

        subsetDefinition.setOIFitsSubset(oiFitsSubset);

        fireSubsetDefinitionChanged(source, subsetDefinition);

        // find dependencies:
        for (Plot plot : this.userCollection.getPlots()) {
            if (plot.getSubsetDefinition() != null && plot.getSubsetDefinition().getName().equals(subsetDefinition.getName())) {
                // match
                plot.setSubsetDefinition(subsetDefinition);
                // fire PlotChanged event:
                firePlotChanged(plot);
            }
        }
    }

    /* --- plot definition handling --------- ---------------------------- */
    /**
     * Return the current plot definition (copy)
     * @return plot definition (copy)
     */
    public PlotDefinition getCurrentPlotDefinition() {
        final PlotDefinition plotDefinition = clone(getCurrentPlotDefinitionRef());

        if (logger.isDebugEnabled()) {
            logger.debug("getCurrentPlotDefinition {}", plotDefinition);
        }
        return plotDefinition;
    }

    /**
     * Return the current plot definition (reference)
     * @return plot definition (reference)
     */
    public PlotDefinition getCurrentPlotDefinitionRef() {
        PlotDefinition plotDefinition = getPlotDefinitionRef(CURRENT_PLOT_DEFINITION);
        if (plotDefinition == null) {
            plotDefinition = new PlotDefinition();
            plotDefinition.setName(CURRENT_PLOT_DEFINITION);

            // HACK:
            plotDefinition.copy(PlotDefinitionFactory.getInstance().getDefault(PlotDefinitionFactory.PLOT_DEFAULT));

            addPlotDefinitionRef(plotDefinition);
        }
        return plotDefinition;
    }

    /**
     * Add the given PlotDefinition
     * @param plotDefinition PlotDefinition to add
     * @return true if the given PlotDefinition was added
     */
    public boolean addPlotDefinition(final PlotDefinition plotDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("addPlotDefinition: {}", plotDefinition);
        }

        if (addPlotDefinitionRef(plotDefinition)) {
            // update plot definition reference and fire events (PlotDefinitionChanged, PlotChanged):
            updatePlotDefinitionRef(this, plotDefinition);
            return true;
        }
        return false;
    }

    /**
     * Add the given PlotDefinition
     * @param plotDefinition PlotDefinition to add
     * @return true if the given PlotDefinition was added
     */
    private boolean addPlotDefinitionRef(final PlotDefinition plotDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("addPlotDefinitionRef: {}", plotDefinition);
        }
        return addIdentifiable(plotDefinition, this.userCollection.getPlotDefinitions());
    }

    /**
     * Remove the PlotDefinition given its identifier
     * @param name PlotDefinition identifier
     */
    private void removePlotDefinition(final String name) {
        removeIdentifiable(name, this.userCollection.getPlotDefinitions());
    }

    /**
     * Return a plot definition (copy) by its name
     * @param name plot definition name
     * @return plot definition (copy) or null if not found
     */
    public PlotDefinition getPlotDefinition(final String name) {
        final PlotDefinition plotDefinition = clone(getPlotDefinitionRef(name));

        if (logger.isDebugEnabled()) {
            logger.debug("getPlotDefinition {}", plotDefinition);
        }
        return plotDefinition;
    }

    /**
     * Return a plot definition (reference) by its name
     * @param name plot definition name
     * @return plot definition (reference) or null if not found
     */
    public PlotDefinition getPlotDefinitionRef(final String name) {
        return getIdentifiable(name, this.userCollection.getPlotDefinitions());
    }

    /**
     * Return true if this plot definition exists in this data collection given its name
     * @param name plot definition name
     * @return true if this plot definition exists in this data collection given its name
     */
    public boolean hasPlotDefinition(final String name) {
        return getPlotDefinitionRef(name) != null;
    }

    /**
     * Update the plot definition corresponding to the same name
     * @param source event source
     * @param plotDefinition plot definition with updated values
     */
    public void updatePlotDefinition(final Object source, final PlotDefinition plotDefinition) {
        final PlotDefinition plotDef = getPlotDefinitionRef(plotDefinition.getName());

        if (plotDef == null) {
            throw new IllegalStateException("plot definition not found : " + plotDefinition);
        }

        boolean changed = false;

        if (plotDef != plotDefinition) {
            changed = !OIBase.areEquals(plotDef, plotDefinition);
        } else {
            throw new IllegalStateException("equal plot definition references : " + plotDef);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("updatePlotDefinition: {}", plotDefinition);
            logger.debug("updatePlotDefinition: changed: {}", changed);
        }

        if (changed) {
            // copy data:
            plotDef.copy(plotDefinition);

            // update plot definition reference and fire events (PlotDefinitionChanged, PlotChanged):
            updatePlotDefinitionRef(source, plotDefinition);
        }
    }

    /**
     * Update the given plot definition (reference) and fire events
     * @param source event source
     * @param plotDefinition plot definition (reference)
     */
    private void updatePlotDefinitionRef(final Object source, final PlotDefinition plotDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("updatePlotDefinitionRef: plotDefinition: {}", plotDefinition);
        }

        firePlotDefinitionChanged(source, plotDefinition);

        // find dependencies:
        for (Plot plot : this.userCollection.getPlots()) {
            if (plot.getPlotDefinition() != null && plot.getPlotDefinition().getName().equals(plotDefinition.getName())) {
                // match
                plot.setPlotDefinition(plotDefinition);
                // fire PlotChanged event:
                firePlotChanged(plot);
            }
        }
    }

    /* --- plot handling --------- ---------------------------- */
    /**
     * Return the current plot (copy)
     * @return plot (copy)
     */
    public Plot getCurrentPlot() {
        final Plot plot = clone(getCurrentPlotRef());

        if (logger.isDebugEnabled()) {
            logger.debug("getCurrentPlot {}", plot);
        }
        return plot;
    }

    /**
     * Return the current plot (reference)
     * @return plot (reference)
     */
    public Plot getCurrentPlotRef() {
        Plot plot = getPlotRef(CURRENT_VIEW);
        if (plot == null) {
            plot = new Plot();
            plot.setName(CURRENT_VIEW);

            // HACK to define current pointers:
            plot.setSubsetDefinition(getCurrentSubsetDefinitionRef());
            plot.setPlotDefinition(getCurrentPlotDefinitionRef());

            addPlotRef(plot);
        }
        return plot;
    }

    /**
     * Add the given Plot
     * @param plot Plot to add
     * @return true if the given Plot was added
     */
    public boolean addPlot(final Plot plot) {
        if (logger.isDebugEnabled()) {
            logger.debug("addPlot: {}", plot);
        }

        if (addPlotRef(plot)) {
            // fire PlotChanged event:
            firePlotChanged(plot);
            return true;
        }
        return false;
    }

    /**
     * Add the given Plot
     * @param plot Plot to add
     * @return true if the given Plot was added
     */
    private boolean addPlotRef(final Plot plot) {
        if (logger.isDebugEnabled()) {
            logger.debug("addPlotRef: {}", plot);
        }
        return addIdentifiable(plot, this.userCollection.getPlots());
    }

    /**
     * Remove the Plot given its identifier
     * @param name Plot identifier
     */
    private void removePlot(final String name) {
        removeIdentifiable(name, this.userCollection.getPlots());
    }

    /**
     * Return a plot (copy) by its name
     * @param name plot name
     * @return plot (copy) or null if not found
     */
    public Plot getPlot(final String name) {
        final Plot plot = clone(getPlotRef(name));

        if (logger.isDebugEnabled()) {
            logger.debug("getPlot {}", plot);
        }
        return plot;
    }

    /**
     * Return a plot (reference) by its name
     * @param name plot name
     * @return plot (reference) or null if not found
     */
    public Plot getPlotRef(final String name) {
        return getIdentifiable(name, this.userCollection.getPlots());
    }

    /**
     * Return true if this plot exists in this data collection given its name
     * @param name plot name
     * @return true if this plot exists in this data collection given its name
     */
    public boolean hasPlot(final String name) {
        return getPlotRef(name) != null;
    }

    /**
     * Update the plot corresponding to the same name
     * @param plot plot with updated values
     */
    public void updatePlot(final Plot plot) {
        final Plot plotRef = getPlotRef(plot.getName());

        if (plotRef == null) {
            throw new IllegalStateException("plot not found : " + plot);
        }

        boolean changed = false;

        if (plotRef != plot) {
            changed = !OIBase.areEquals(plotRef, plot);
        } else {
            throw new IllegalStateException("equal plot references : " + plotRef);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("updatePlot: {}", plot);
            logger.debug("updatePlot: changed: {}", changed);
        }

        if (changed) {
            // copy data:
            plotRef.copy(plot);

            // fire PlotChanged event:
            firePlotChanged(plot);
        }
    }

    // --- EVENTS ----------------------------------------------------------------
    /**
     * Return the OIFitsCollectionEvent notifier
     * @return OIFitsCollectionEvent notifier
     */
    public EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType> getOiFitsCollectionEventNotifier() {
        return oiFitsCollectionEventNotifier;
    }

    /**
     * This fires an OIFitsCollectionChanged event to given registered listener ASYNCHRONOUSLY !
     * 
     * Note: this is ONLY useful to initialize new registered listeners properly !
     * 
     * @param destination destination listener
     */
    public void fireOIFitsCollectionChanged(final OIFitsCollectionEventListener destination) {
        if (logger.isDebugEnabled()) {
            logger.debug("fireOIFitsCollectionChanged: {} TO {}", this.oiFitsCollection, destination);
        }

        this.oiFitsCollectionEventNotifier.fireEvent(new OIFitsCollectionEvent(this, OIFitsCollectionEventType.CHANGED, destination, this.oiFitsCollection));
    }

    /**
     * This fires an OIFitsCollectionChanged event to given registered listeners ASYNCHRONOUSLY !
     */
    private void fireOIFitsCollectionChanged() {
        if (logger.isDebugEnabled()) {
            logger.debug("fireOIFitsCollectionChanged: {}", this.oiFitsCollection);
        }
        this.oiFitsCollectionEventNotifier.fireEvent(new OIFitsCollectionEvent(this, OIFitsCollectionEventType.CHANGED, this.oiFitsCollection));
    }

    /**
     * Return the SubsetDefinitionEvent notifier
     * @return SubsetDefinitionEvent notifier
     */
    public EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType> getSubsetDefinitionEventNotifier() {
        return subsetDefinitionEventNotifier;
    }

    /**
     * This fires a SubsetDefinitionChanged event to all registered listeners ASYNCHRONOUSLY !
     * @param source event source
     * @param subsetDefinition subset definition to use
     */
    public void fireSubsetDefinitionChanged(final Object source, final String subsetDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("fireOIFitsCollectionChanged: {}", this.oiFitsCollection);
        }
        this.oiFitsCollectionEventNotifier.fireEvent(new OIFitsCollectionEvent(this, OIFitsCollectionEventType.CHANGED, this.oiFitsCollection));
    }

    /**
     * This fires a SubsetDefinitionChanged event to all registered listeners ASYNCHRONOUSLY !
     * @param source event source
     * @param subsetDefinition subset definition to use
     */
    private void fireSubsetDefinitionChanged(final Object source, final SubsetDefinition subsetDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("fireSubsetDefinitionChanged: {}", subsetDefinition);
        }
        this.subsetDefinitionEventNotifier.fireEvent(new SubsetDefinitionEvent(source, OIFitsCollectionEventType.SUBSET_CHANGED, subsetDefinition));
    }

    /**
     * Return the PlotDefinitionEvent notifier
     * @return PlotDefinitionEvent notifier
     */
    public EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType> getPlotDefinitionEventNotifier() {
        return plotDefinitionEventNotifier;
    }

    /**
     * This fires a PlotDefinitionChanged event to all registered listeners ASYNCHRONOUSLY !
     * @param source event source
     * @param plotDefinition plot definition to use
     */
    private void firePlotDefinitionChanged(final Object source, final PlotDefinition plotDefinition) {
        if (logger.isDebugEnabled()) {
            logger.debug("firePlotDefinitionChanged: {}", plotDefinition);
        }
        this.plotDefinitionEventNotifier.fireEvent(new PlotDefinitionEvent(source, OIFitsCollectionEventType.PLOT_DEFINITION_CHANGED, plotDefinition));
    }

    /**
     * Return the PlotEvent notifier
     * @return PlotEvent notifier
     */
    public EventNotifier<GenericEvent<OIFitsCollectionEventType>, OIFitsCollectionEventType> getPlotEventNotifier() {
        return plotEventNotifier;
    }

    /**
     * This fires a PlotChanged event to given registered listener ASYNCHRONOUSLY !
     * 
     * Note: this is ONLY useful to initialize new registered listeners properly !
     * 
     * @param plotId plot identifier
     * @param destination destination listener
     */
    public void firePlotChanged(final String plotId, final OIFitsCollectionEventListener destination) {

        // resolve object now:
        // TODO: resolve object just before firing events !!
        final Plot plot = getPlotRef(plotId);

        // resolve issue:
        if (plot != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("firePlotChanged: {} TO {}", plotId, destination);
            }

            // set source to this in order to merge events:
            this.plotEventNotifier.fireEvent(new PlotEvent(this, OIFitsCollectionEventType.PLOT_CHANGED, destination, plot));
        }
    }

    /**
     * This fires a PlotChanged event to all registered listeners ASYNCHRONOUSLY !
     * @param plot plot to use
     */
    private void firePlotChanged(final Plot plot) {
        if (logger.isDebugEnabled()) {
            logger.debug("firePlotChanged: {}", plot);
        }

        // set source to this in order to merge events:
        this.plotEventNotifier.fireEvent(new PlotEvent(this, OIFitsCollectionEventType.PLOT_CHANGED, plot));
    }

    /* --- OIFitsCollectionEventListener implementation --- */
    /**
     * Return the optional subject id i.e. related object id that this listener accepts
     * @see GenericEvent#subjectId
     * @param type event type
     * @return subject id i.e. related object id (null allowed)
     */
    public String getSubjectId(final OIFitsCollectionEventType type) {
        // useless
        return null;
    }

    /**
     * Handle the given OIFits collection event
     * @param event OIFits collection event
     */
    @Override
    public void onProcess(final GenericEvent<OIFitsCollectionEventType> event) {
        logger.debug("onProcess {}", event);

        switch (event.getType()) {
            case CHANGED:
                // update collection analysis:
                oiFitsCollection.analyzeCollection();

                // TODO: see if the "GUI" manager decide to create objects itself ?
                // TODO: remove ASAP:
                // initialize current objects: subsetDefinition, plotDefinition, plot if NOT PRESENT:
                getCurrentPlotRef();

                // CASCADE EVENTS:

                // SubsetDefinition:
                for (SubsetDefinition subsetDefinition : this.userCollection.getSubsetDefinitions()) {
                    // force fireSubsetChanged, update plot reference and firePlotChanged:
                    updateSubsetDefinitionRef(this, subsetDefinition);
                }

                // PlotDefinition:
                for (PlotDefinition plotDefinition : this.userCollection.getPlotDefinitions()) {
                    // force PlotDefinitionChanged, update plot reference and firePlotChanged:
                    updatePlotDefinitionRef(this, plotDefinition);
                }

                // Note: no explicit firePlotChanged event fired as done in updateSubsetDefinitionRef and updatePlotDefinitionRef
                break;
            default:
        }
        logger.debug("onProcess {} - done", event);
    }
}
