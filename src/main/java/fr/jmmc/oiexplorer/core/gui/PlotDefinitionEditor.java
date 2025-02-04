/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.oiexplorer.core.gui;

import fr.jmmc.jmcs.gui.component.GenericListModel;
import fr.jmmc.jmcs.gui.util.SwingUtils;
import fr.jmmc.jmcs.util.ObjectUtils;
import fr.jmmc.oiexplorer.core.model.OIFitsCollectionManager;
import fr.jmmc.oiexplorer.core.model.OIFitsCollectionManagerEvent;
import fr.jmmc.oiexplorer.core.model.OIFitsCollectionManagerEventListener;
import fr.jmmc.oiexplorer.core.model.OIFitsCollectionManagerEventType;
import fr.jmmc.oiexplorer.core.model.PlotDefinitionFactory;
import fr.jmmc.oiexplorer.core.model.plot.Axis;
import fr.jmmc.oiexplorer.core.model.plot.AxisRangeMode;
import fr.jmmc.oiexplorer.core.model.plot.ColorMapping;
import fr.jmmc.oiexplorer.core.model.plot.PlotDefinition;
import fr.jmmc.oiexplorer.core.model.util.ColorMappingListCellRenderer;
import fr.jmmc.oitools.model.OIFitsFile;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Panel allow to select columns of data to be plotted.
 * After being created and inserted in a GUI, it becomes plotDefinition editor of a dedicated plotDefinition using setPlotDefId().
 * It can also be editor for the plotDefinition of a particular Plot using setPlotId(). In the Plot case,
 * the subset is also watched to find available columns to plot.
 *
 * @author mella
 */
public final class PlotDefinitionEditor extends javax.swing.JPanel implements OIFitsCollectionManagerEventListener {

    /** default serial UID for Serializable interface */
    private static final long serialVersionUID = 1;
    /** Logger */
    private final static Logger logger = LoggerFactory.getLogger(PlotDefinitionEditor.class);
    /** flag to enable / disable the expression editor */
    private static boolean ENABLE_EXPRESSION_EDITOR = true;
    /** Define the max number of plots */
    private final static int MAX_Y_AXES = 10;

    /**
     * Define the flag to enable / disable the expression editor
     * @param flag true to enable / false to disable the expression editor
     */
    public static void setEnableExpressionEditor(final boolean flag) {
        ENABLE_EXPRESSION_EDITOR = flag;
    }

    /* members */
    /** OIFitsCollectionManager singleton reference */
    private final static OIFitsCollectionManager ocm = OIFitsCollectionManager.getInstance();
    /** OPTIONAL plot identifier */
    private String plotId = null;
    /** plot definition identifier */
    private String plotDefId = null;
    /* Swing components */
    /** Store all axis choices available given the plot's subset if any */
    private final List<String> axisChoices = new LinkedList<String>();
    /** xAxisEditor */
    private AxisEditor xAxisEditor;
    /** List of y axes with their editors (identity hashcode) */
    private final HashMap<Axis, AxisEditor> yAxisEditors = new LinkedHashMap<Axis, AxisEditor>();
    /** Flag to declare that component has to notify an event from user gesture */
    private boolean notify;
    /* expression editor */
    private ExpressionEditor expressionEditor = null;

    /** Creates new form PlotDefinitionEditor */
    public PlotDefinitionEditor() {
        // TODO maybe move it in setPlotId, setPlotId to register to event notifiers instead of both:
        ocm.getPlotDefinitionChangedEventNotifier().register(this);
        ocm.getPlotChangedEventNotifier().register(this);
        ocm.getPlotViewportChangedEventNotifier().register(this);

        initComponents();
        postInit();
    }

    /**
     * Free any resource or reference to this instance :
     * remove this instance from OIFitsCollectionManager event notifiers
     */
    @Override
    public void dispose() {
        if (logger.isDebugEnabled()) {
            logger.debug("dispose: {}", ObjectUtils.getObjectInfo(this));
        }

        ocm.unbind(this);

        resetForm();
    }

    private void disposeYAxisEditors() {
        for (AxisEditor editor : yAxisEditors.values()) {
            editor.dispose();
        }
        yAxisEditors.clear();
        yAxesPanel.removeAll();
    }

    /**
     * This method is useful to set the models and specific features of initialized swing components :
     */
    private void postInit() {
        // start with compact form
        detailledToggleButtonActionPerformed(null);

        colorMappingComboBox.setRenderer(ColorMappingListCellRenderer.getListCellRenderer());

        // Fill colorMapping combobox
        for (ColorMapping cm : ColorMapping.values()) {
            if (cm != ColorMapping.OBSERVATION_DATE) { // not implemented
                colorMappingComboBox.addItem(cm);
            }
        }

        xAxisEditor = new AxisEditor(this);
        xAxisPanel.add(xAxisEditor);

        if (ENABLE_EXPRESSION_EDITOR) {
            expressionEditor = new ExpressionEditor(this);
            expressionEditor.setVisible(false);

            this.jPanelOtherEditors.add(expressionEditor, BorderLayout.CENTER);
        } else {
            jToggleButtonExprEditor.setVisible(false);
        }

        // Adjust fonts:
        final Font fixedFont = new Font(Font.MONOSPACED, Font.PLAIN, SwingUtils.adjustUISize(12));
        this.jToggleButtonAuto.setFont(fixedFont);
        this.jToggleButtonDefault.setFont(fixedFont);
        this.jToggleButtonFixed.setFont(fixedFont);
    }

    private void resetForm() {
        logger.debug("resetForm : plotDefId = {}", plotDefId);

        // TODO: is it necessary to use notify flag here ?
        try {
            // Leave programatic changes on widgets ignored to prevent model changes
            notify = false;

            // Clear all content
            axisChoices.clear();

            // clear x/y AxisEditors
            xAxisEditor.dispose();
            disposeYAxisEditors();

        } finally {
            notify = true;
        }
    }

    /**
     * Fill axes combo boxes with all distinct columns present in the available
     * tables.
     * @param plotDef plot definition to use
     * @param oiFitsSubset OIFits structure coming from plot's subset definition
     */
    private void refreshForm(final PlotDefinition plotDef, final OIFitsFile oiFitsSubset) {
        logger.debug("refreshForm : plotDefId = {} - plotDef {}", plotDefId, plotDef);

        if (plotDef == null) {
            resetForm();
        } else {
            try {
                // Leave programatic changes on widgets ignored to prevent model changes
                notify = false;

                // Add column present in associated subset if any
                // TODO generate one synthetic OiFitsSubset to give all available choices
                if (oiFitsSubset != null) {
                    // Get whole available columns
                    final Set<String> columns = getDistinctColumns(oiFitsSubset);

                    // Clear all content
                    axisChoices.clear();
                    axisChoices.addAll(columns);
                }

                // Add choices present in the associated plotDef
                final String currentX = plotDef.getXAxis().getName();
                if (!axisChoices.contains(currentX)) {
                    axisChoices.add(currentX);
                }

                for (Axis y : plotDef.getYAxes()) {
                    final String currentY = y.getName();
                    if (!axisChoices.contains(currentY)) {
                        axisChoices.add(currentY);
                    }
                }

                logger.debug("refreshForm : axisChoices {}", axisChoices);

                xAxisEditor.setAxis((Axis) plotDef.getXAxis().clone(), axisChoices);

                // fill with associated plotdefinition
                if (logger.isDebugEnabled()) {
                    logger.debug("refreshForm : yaxes to add : {}", plotDef.getYAxes());
                }

                // clear y AxisEditors
                disposeYAxisEditors();

                for (Axis yAxis : plotDef.getYAxes()) {
                    addYEditor((Axis) yAxis.clone());
                }

                // Init colorMapping
                colorMappingComboBox.setSelectedItem((plotDef.getColorMapping() != null) ? plotDef.getColorMapping() : ColorMapping.WAVELENGTH_RANGE);

                // Init flaggedDataCheckBox
                flaggedDataCheckBox.setSelected(plotDef.isSkipFlaggedData());

                // Init drawLinesCheckBox
                drawLinesCheckBox.setSelected(plotDef.isDrawLine());

                checkYAxisActionButtons();

                refreshPlotDefinitionNames(plotDef);

                refreshRangeModeButtons();
            } finally {
                notify = true;
            }
        }
    }

    private void refreshRangeModeButtons() {
        AxisRangeMode mode = null;
        final int nAllAxes = 1 + yAxisEditors.size();

        if (countAxisEditors(AxisRangeMode.AUTO) == nAllAxes) {
            mode = AxisRangeMode.AUTO;
        } else if (countAxisEditors(AxisRangeMode.DEFAULT) == nAllAxes) {
            mode = AxisRangeMode.DEFAULT;
        } else if (countAxisEditors(AxisRangeMode.RANGE) == nAllAxes) {
            mode = AxisRangeMode.RANGE;
        }
        updateRangeModeButtons(mode);
    }

    private void updateRangeModeButtons(final AxisRangeMode mode) {
        this.jToggleButtonAuto.setSelected(mode == AxisRangeMode.AUTO);
        this.jToggleButtonDefault.setSelected(mode == AxisRangeMode.DEFAULT);
        this.jToggleButtonFixed.setSelected(mode == AxisRangeMode.RANGE);
    }

    /**
     * Update axis editors using the given data.
     * @param plotInfosData PlotInfosData
     */
    private void refreshForm(final PlotInfosData plotInfosData) {
        logger.debug("refreshForm : plotInfosData = {}", plotInfosData);

        if (plotInfosData != null) {
            final PlotInfo[] plotInfos = plotInfosData.getPlotInfos();

            if (plotInfos.length != 0) {
                boolean changed = false;
                try {
                    // Leave programatic changes on widgets ignored to prevent model changes
                    notify = false;

                    AxisInfo axisInfo = plotInfos[0].xAxisInfo;

                    // check axis name to be sure
                    if (axisInfo.getColumnMeta().getName().equals(xAxisEditor.getAxis().getName())) {
                        changed |= xAxisEditor.setAxisRange(axisInfo.plotRange.getLowerBound(), axisInfo.plotRange.getUpperBound());
                    }

                    // Merge plotInfos with yAxis (in-order):
                    final AxisEditor[] yAxisEditorArray = yAxisEditors.values().toArray(new AxisEditor[yAxisEditors.size()]);

                    // plotInfos may be smaller than yAxisEditors (no data)
                    for (int idxEditor = 0, idxPlot = 0; idxEditor < yAxisEditorArray.length; idxEditor++) {
                        final AxisEditor editor = yAxisEditorArray[idxEditor];
                        axisInfo = plotInfos[idxPlot].yAxisInfo;

                        if (axisInfo.getColumnMeta().getName().equals(editor.getAxis().getName())) {
                            changed |= editor.setAxisRange(axisInfo.plotRange.getLowerBound(), axisInfo.plotRange.getUpperBound());

                            if (++idxPlot == plotInfos.length) {
                                break;
                            }
                        }
                    }
                } finally {
                    notify = true;
                    if (changed) {
                        // notify once:
                        updateModel();
                    }
                }
            }
        }
    }

    private void checkYAxisActionButtons() {
        // disable buttons to limit number of yAxes
        addYAxisButton.setEnabled(yAxisEditors.size() < MAX_Y_AXES);
        delYAxisButton.setEnabled(yAxisEditors.size() > 1);
    }

    private void refreshPlotDefinitionNames(final PlotDefinition plotDef) {
        logger.debug("refreshPlotDefinitionNames: {}", plotId);

        // use identifiers to keep unique values:
        final Set<String> plotDefNames = new LinkedHashSet<String>();

        final StringBuilder sb = new StringBuilder(64);

        if (plotDef != null) {
            // Y axes:
            for (Axis axis : plotDef.getYAxes()) {
                // skip invalid axis names:
                if (axis.getName() != null) {
                    if (sb.length() != 0) {
                        sb.append(", ");
                    }
                    sb.append(axis.getName());
                }
            }
            // X axis:
            sb.append(" vs ").append(plotDef.getXAxis().getName());

            // add first entry corresponding to the edited plot definition:
            plotDefNames.add(sb.toString());
        }

        for (PlotDefinition plotDefPreset : PlotDefinitionFactory.getInstance().getDefaults()) {
            plotDefNames.add(plotDefPreset.getName());
        }

        plotDefinitionComboBox.setModel(new GenericListModel<String>(new ArrayList<String>(plotDefNames), true));
        plotDefinitionComboBox.setSelectedIndex(0);
    }

    /**
     * Return the set of distinct columns available in the table of given OIFitsFile.
     * @param oiFitsFile oifitsFile to search data into
     * @return a Set of Strings with every distinct column names
     */
    private Set<String> getDistinctColumns(final OIFitsFile oiFitsFile) {
        final Set<String> columns = new LinkedHashSet<String>(32);

        // Add every column of every tables for given target into ordered sets
        if (oiFitsFile.hasOiVis2()) {
            oiFitsFile.getOiVis2()[0].getNumericalColumnsNames(columns);
        }
        if (oiFitsFile.hasOiVis()) {
            oiFitsFile.getOiVis()[0].getNumericalColumnsNames(columns);
        }
        if (oiFitsFile.hasOiT3()) {
            oiFitsFile.getOiT3()[0].getNumericalColumnsNames(columns);
        }
        if (oiFitsFile.hasOiFlux()) {
            oiFitsFile.getOiFlux()[0].getNumericalColumnsNames(columns);
        }

        return columns;
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        refreshButton = new javax.swing.JButton();
        jToggleButtonAuto = new javax.swing.JToggleButton();
        jToggleButtonDefault = new javax.swing.JToggleButton();
        jToggleButtonFixed = new javax.swing.JToggleButton();
        plotDefLabel = new javax.swing.JLabel();
        plotDefinitionComboBox = new javax.swing.JComboBox();
        colorMappingLabel = new javax.swing.JLabel();
        colorMappingComboBox = new javax.swing.JComboBox();
        plotDefinitionName = new javax.swing.JLabel();
        flaggedDataCheckBox = new javax.swing.JCheckBox();
        detailledToggleButton = new javax.swing.JToggleButton();
        drawLinesCheckBox = new javax.swing.JCheckBox();
        extendedPanel = new javax.swing.JPanel();
        yLabel = new javax.swing.JLabel();
        xLabel = new javax.swing.JLabel();
        addYAxisButton = new javax.swing.JButton();
        delYAxisButton = new javax.swing.JButton();
        xAxisPanel = new javax.swing.JPanel();
        yAxesPanel = new javax.swing.JPanel();
        jPanelOtherEditors = new javax.swing.JPanel();
        jToggleButtonExprEditor = new javax.swing.JToggleButton();

        setLayout(new java.awt.GridBagLayout());

        refreshButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/fr/jmmc/jmcs/resource/image/refresh.png"))); // NOI18N
        refreshButton.setToolTipText("refresh zoom / remove plot selection");
        refreshButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 4);
        add(refreshButton, gridBagConstraints);

        jToggleButtonAuto.setText("A");
        jToggleButtonAuto.setToolTipText("Auto: automatically adjust the viewport to see all data");
        jToggleButtonAuto.setMargin(new java.awt.Insets(0, 0, 0, 0));
        jToggleButtonAuto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButtonAutoActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        add(jToggleButtonAuto, gridBagConstraints);

        jToggleButtonDefault.setText("D");
        jToggleButtonDefault.setToolTipText("Default: adjust the viewport to use default data ranges");
        jToggleButtonDefault.setMargin(new java.awt.Insets(0, 0, 0, 0));
        jToggleButtonDefault.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButtonDefaultActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        add(jToggleButtonDefault, gridBagConstraints);

        jToggleButtonFixed.setText("F");
        jToggleButtonFixed.setToolTipText("Fixed: set the viewport according to fixed ranges");
        jToggleButtonFixed.setMargin(new java.awt.Insets(0, 0, 0, 0));
        jToggleButtonFixed.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButtonFixedActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        add(jToggleButtonFixed, gridBagConstraints);

        plotDefLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        plotDefLabel.setText("Show");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 2);
        add(plotDefLabel, gridBagConstraints);

        plotDefinitionComboBox.setName("plotDefinitionComboBox"); // NOI18N
        plotDefinitionComboBox.setPrototypeDisplayValue("01234567890123456789");
        plotDefinitionComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotDefinitionComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 0.2;
        add(plotDefinitionComboBox, gridBagConstraints);

        colorMappingLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        colorMappingLabel.setText("Color by");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 2);
        add(colorMappingLabel, gridBagConstraints);

        colorMappingComboBox.setPrototypeDisplayValue("0123456789");
        colorMappingComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                colorMappingComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 7;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        add(colorMappingComboBox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        add(plotDefinitionName, gridBagConstraints);

        flaggedDataCheckBox.setText("Skip Flagged");
        flaggedDataCheckBox.setToolTipText("skip flagged data (FLAG=T means the data is invalid)");
        flaggedDataCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flaggedDataCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 8;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        add(flaggedDataCheckBox, gridBagConstraints);

        detailledToggleButton.setText("...");
        detailledToggleButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        detailledToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                detailledToggleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 10;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        add(detailledToggleButton, gridBagConstraints);

        drawLinesCheckBox.setText("Draw lines");
        drawLinesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                drawLinesCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 9;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        add(drawLinesCheckBox, gridBagConstraints);

        extendedPanel.setLayout(new java.awt.GridBagLayout());

        yLabel.setText("y Axes");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        extendedPanel.add(yLabel, gridBagConstraints);

        xLabel.setText("x Axis");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        extendedPanel.add(xLabel, gridBagConstraints);

        addYAxisButton.setText("+");
        addYAxisButton.setMargin(new java.awt.Insets(0, 1, 0, 1));
        addYAxisButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addYAxisButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        extendedPanel.add(addYAxisButton, gridBagConstraints);

        delYAxisButton.setText("-");
        delYAxisButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
        delYAxisButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                delYAxisButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_END;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        extendedPanel.add(delYAxisButton, gridBagConstraints);

        xAxisPanel.setLayout(new java.awt.BorderLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        extendedPanel.add(xAxisPanel, gridBagConstraints);

        yAxesPanel.setLayout(new javax.swing.BoxLayout(yAxesPanel, javax.swing.BoxLayout.Y_AXIS));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        extendedPanel.add(yAxesPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 12;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 0);
        add(extendedPanel, gridBagConstraints);

        jPanelOtherEditors.setLayout(new java.awt.BorderLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 12;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        add(jPanelOtherEditors, gridBagConstraints);

        jToggleButtonExprEditor.setText("Expr editor");
        jToggleButtonExprEditor.setMargin(new java.awt.Insets(0, 0, 0, 0));
        jToggleButtonExprEditor.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButtonExprEditorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 11;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        add(jToggleButtonExprEditor, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void addYAxisButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addYAxisButtonActionPerformed
        final Axis axis = new Axis();

        if (this.jToggleButtonAuto.isSelected()) {
            axis.setRangeMode(AxisRangeMode.AUTO);
        } else if (this.jToggleButtonDefault.isSelected()) {
            axis.setRangeMode(AxisRangeMode.DEFAULT);
        } else if (this.jToggleButtonFixed.isSelected()) {
            axis.setRangeMode(AxisRangeMode.RANGE);
        }

        // Add to PlotDefinition
        addYEditor(axis);
        updateModel(true);

        checkYAxisActionButtons();
    }//GEN-LAST:event_addYAxisButtonActionPerformed

    private void delYAxisButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_delYAxisButtonActionPerformed
        final int size = yAxisEditors.size();
        if (size > 1) {
            // TODO replace by removal of the last yCombobox which one has lost the focus
            Axis[] yAxisArray = yAxisEditors.keySet().toArray(new Axis[size]);
            Axis yAxis = yAxisArray[size - 1];
            delYEditor(yAxis);

            // Delete from PlotDefinition
            updateModel(true);
        }
        checkYAxisActionButtons();
    }//GEN-LAST:event_delYAxisButtonActionPerformed

    private void colorMappingComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_colorMappingComboBoxActionPerformed
        updateModel();
    }//GEN-LAST:event_colorMappingComboBoxActionPerformed

    private void detailledToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_detailledToggleButtonActionPerformed
        extendedPanel.setVisible(detailledToggleButton.isSelected());
        revalidate();
    }//GEN-LAST:event_detailledToggleButtonActionPerformed

    private void flaggedDataCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flaggedDataCheckBoxActionPerformed
        updateModel();
    }//GEN-LAST:event_flaggedDataCheckBoxActionPerformed

    private void drawLinesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_drawLinesCheckBoxActionPerformed
        updateModel();
    }//GEN-LAST:event_drawLinesCheckBoxActionPerformed

    private void plotDefinitionComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotDefinitionComboBoxActionPerformed
        // this method should apply preset on current plotDef

        // TODO find better solution to allow the selection of a preset with keyboard shortcut
        // until now the choice is limited to the first item
        final int idx = plotDefinitionComboBox.getSelectedIndex();
        if (idx == 0) {
            return;
        }

        String presetPlotDefId = null;

        Collection<PlotDefinition> presets = PlotDefinitionFactory.getInstance().getDefaults();

        int i = 1; // first element is not a preset
        for (PlotDefinition plotDefinition : presets) {
            if (i == plotDefinitionComboBox.getSelectedIndex()) {
                presetPlotDefId = plotDefinition.getId();
                break;
            }
            i++;
        }

        if (presetPlotDefId == null) {
            logger.debug("[{}] plotDefinitionComboBoxActionPerformed() event ignored : no current selection", plotId);
            return;
        }

        final PlotDefinition plotDefCopy = getPlotDefinition();

        final ColorMapping colorMapping = plotDefCopy.getColorMapping();

        // TODO: decide: should only copy axis infos or all ?
        // copy values from preset:
        plotDefCopy.copyValues(PlotDefinitionFactory.getInstance().getDefault(presetPlotDefId));

        // TODO: clear name and description fields ?
        // keep color mapping:
        plotDefCopy.setColorMapping(colorMapping);

        refreshForm(plotDefCopy, null);
        updateModel();
    }//GEN-LAST:event_plotDefinitionComboBoxActionPerformed

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        final PlotDefinition plotDefCopy = getPlotDefinition();

        plotDefCopy.incVersion();

        ocm.updatePlotDefinition(this, plotDefCopy);
    }//GEN-LAST:event_refreshButtonActionPerformed

    private void jToggleButtonExprEditorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButtonExprEditorActionPerformed
        if (ENABLE_EXPRESSION_EDITOR) {
            expressionEditor.setVisible(jToggleButtonExprEditor.isSelected());
        }
    }//GEN-LAST:event_jToggleButtonExprEditorActionPerformed

    private void jToggleButtonAutoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButtonAutoActionPerformed
        updateAxesRangeMode(AxisRangeMode.AUTO);
    }//GEN-LAST:event_jToggleButtonAutoActionPerformed

    private void jToggleButtonDefaultActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButtonDefaultActionPerformed
        updateAxesRangeMode(AxisRangeMode.DEFAULT);
    }//GEN-LAST:event_jToggleButtonDefaultActionPerformed

    private void jToggleButtonFixedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButtonFixedActionPerformed
        updateAxesRangeMode(AxisRangeMode.RANGE);
    }//GEN-LAST:event_jToggleButtonFixedActionPerformed

    private void updateAxesRangeMode(final AxisRangeMode mode) {
        updateRangeModeButtons(mode);

        xAxisEditor.updateRangeMode(mode);

        for (AxisEditor editor : yAxisEditors.values()) {
            editor.updateRangeMode(mode);
        }
        updateModel();
    }

    private int countAxisEditors(final AxisRangeMode mode) {
        int count = 0;
        if (xAxisEditor.getAxis().getRangeMode() == mode) {
            count++;
        }
        for (AxisEditor editor : yAxisEditors.values()) {
            if (editor.getAxis().getRangeMode() == mode) {
                count++;
            }
        }
        return count;
    }

    /**
     * Return colorMapping Value stored by associated combobox.
     * @return the colorMapping Value stored by associated combobox.
     */
    private ColorMapping getColorMapping() {
        return (ColorMapping) colorMappingComboBox.getSelectedItem();
    }

    /**
     * Create a new widget to edit given Axis.
     * @param yAxis axis to be edited by new yAxisEditor
     */
    private void addYEditor(final Axis yAxis) {
        // Link new Editor and Axis
        final AxisEditor yAxisEditor = new AxisEditor(this);
        yAxisEditor.setAxis(yAxis, axisChoices);

        // Add in editor list
        yAxesPanel.add(yAxisEditor);

        // Add in Map
        yAxisEditors.put(yAxis, yAxisEditor);

        revalidate();
    }

    /** Synchronize management for the addition of a given combo and update GUI.
     * @param yAxis yAxis of editor to remove
     */
    private void delYEditor(final Axis yAxis) {
        // Link new Editor and Axis
        AxisEditor yAxisEditor = yAxisEditors.get(yAxis);

        // Remove from editor list
        yAxesPanel.remove(yAxisEditor);
        yAxisEditor.dispose();

        // Delete from Map
        yAxisEditors.remove(yAxis);

        revalidate();
    }

    /**
     * Update current plotDefinition
     * and request a plotDefinitionUpdate to the OIFitsCollectionManager.
     */
    void updateModel() {
        updateModel(false);
    }

    /**
     * Update current plotDefinition
     * and request a plotDefinitionUpdate to the OIFitsCollectionManager.
     *
     * @param forceRefreshPlotDefNames true to refresh plotDefinition names
     */
    void updateModel(final boolean forceRefreshPlotDefNames) {
        if (notify) {
            logger.debug("updateModel");
            // get copy:
            final PlotDefinition plotDefCopy = getPlotDefinition();

            if (plotDefCopy != null) {
                // handle xAxis
                plotDefCopy.setXAxis(xAxisEditor.getAxis());
                // handle yAxes
                final List<Axis> yAxesCopy = plotDefCopy.getYAxes();
                yAxesCopy.clear();
                // We may also compute the yAxes Collection calling getAxis on the editor list
                // This may reduce references nightmare
                yAxesCopy.addAll(yAxisEditors.keySet());

                plotDefCopy.setColorMapping(getColorMapping());

                plotDefCopy.setDrawLine(drawLinesCheckBox.isSelected());
                plotDefCopy.setSkipFlaggedData(flaggedDataCheckBox.isSelected());

                ocm.updatePlotDefinition(this, plotDefCopy);

                if (forceRefreshPlotDefNames) {
                    refreshPlotDefinitionNames(plotDefCopy);
                }
                refreshRangeModeButtons();
            }

        } else {
            logger.debug("updateModel: disabled");
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addYAxisButton;
    private javax.swing.JComboBox colorMappingComboBox;
    private javax.swing.JLabel colorMappingLabel;
    private javax.swing.JButton delYAxisButton;
    private javax.swing.JToggleButton detailledToggleButton;
    private javax.swing.JCheckBox drawLinesCheckBox;
    private javax.swing.JPanel extendedPanel;
    private javax.swing.JCheckBox flaggedDataCheckBox;
    private javax.swing.JPanel jPanelOtherEditors;
    private javax.swing.JToggleButton jToggleButtonAuto;
    private javax.swing.JToggleButton jToggleButtonDefault;
    private javax.swing.JToggleButton jToggleButtonExprEditor;
    private javax.swing.JToggleButton jToggleButtonFixed;
    private javax.swing.JLabel plotDefLabel;
    private javax.swing.JComboBox plotDefinitionComboBox;
    private javax.swing.JLabel plotDefinitionName;
    private javax.swing.JButton refreshButton;
    private javax.swing.JPanel xAxisPanel;
    private javax.swing.JLabel xLabel;
    private javax.swing.JPanel yAxesPanel;
    private javax.swing.JLabel yLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Define the plot identifier and reset plot
     * @param plotId plot identifier or null to reset state
     */
    public void setPlotId(final String plotId) {
        logger.debug("setPlotId {}", plotId);

        final String prevPlotId = this.plotId;

        _setPlotId(plotId);

        if (plotId != null && !ObjectUtils.areEquals(prevPlotId, plotId)) {
            logger.debug("firePlotChanged {}", plotId);

            // bind(plotId) ?
            // fire PlotChanged event to initialize correctly the widget:
            ocm.firePlotChanged(null, plotId, this); // null forces different source
        }
    }

    public String getPlotId() {
        return plotId;
    }

    /**
     * Define the plot identifier and reset plot
     * @param plotId plot identifier or null to reset state
     */
    private void _setPlotId(final String plotId) {
        logger.debug("_setPlotId {}", plotId);

        this.plotId = plotId;

        // reset case:
        if (plotId == null) {
            // reset plotDefId:
            if (this.plotDefId != null) {
                _setPlotDefId(null);
            }

            // TODO: how to fire reset event ie DELETE(id)
            resetForm();
        }
    }

    /**
     * Return a new copy of the PlotDefinition given its identifier (to update it)
     * @return copy of the PlotDefinition or null if not found
     */
    private PlotDefinition getPlotDefinition() {
        if (plotDefId != null) {
            return ocm.getPlotDefinition(plotDefId);
        }
        return null;
    }

    /**
     * Define the plot definition identifier and reset plot definition
     * @param plotDefId plot definition identifier
     */
    public void setPlotDefId(final String plotDefId) {
        logger.debug("setPlotDefId {}", plotDefId);

        final String prevPlotDefId = this.plotDefId;

        _setPlotDefId(plotDefId);

        // reset plotId:
        if (this.plotId != null) {
            _setPlotId(null);
        }

        // reset case:
        if (plotDefId == null) {
            // reset plotId:
            if (this.plotId != null) {
                _setPlotId(null);
            }

            // TODO: how to fire reset event ie DELETE(id)
            resetForm();

        } else if (!ObjectUtils.areEquals(prevPlotDefId, plotDefId)) {
            logger.debug("firePlotDefinitionChanged {}", plotDefId);

            // bind(plotDefId) ?
            // fire PlotDefinitionChanged event to initialize correctly the widget:
            ocm.firePlotDefinitionChanged(null, plotDefId, this); // null forces different source
        }
    }

    /**
     * Define the plot definition identifier and reset plot definition
     * @param plotDefId plot definition identifier
     */
    private void _setPlotDefId(final String plotDefId) {
        logger.debug("_setPlotDefId {}", plotDefId);

        this.plotDefId = plotDefId;

        // do not change plotId
    }

    /*
     * OIFitsCollectionManagerEventListener implementation
     */
    /**
     * Return the optional subject id i.e. related object id that this listener accepts
     * @param type event type
     * @return subject id (null means accept any event) or DISCARDED_SUBJECT_ID to discard event
     */
    @Override
    public String getSubjectId(final OIFitsCollectionManagerEventType type) {
        switch (type) {
            case PLOT_DEFINITION_CHANGED:
                if (this.plotDefId != null) {
                    return this.plotDefId;
                }
                break;
            case PLOT_CHANGED:
                if (this.plotId != null) {
                    return this.plotId;
                }
                break;
            default:
        }
        return DISCARDED_SUBJECT_ID;
    }

    /**
     * Handle the given OIFits collection event
     * @param event OIFits collection event
     */
    @Override
    public void onProcess(final OIFitsCollectionManagerEvent event) {
        logger.debug("onProcess {}", event);

        switch (event.getType()) {
            case PLOT_DEFINITION_CHANGED:
                // define id of associated plotDefinition
                _setPlotDefId(event.getPlotDefinition().getId());

                refreshForm(event.getPlotDefinition(), null);
                break;
            case PLOT_CHANGED:
                final PlotDefinition plotDef = event.getPlot().getPlotDefinition();

                // define id of associated plotDefinition
                _setPlotDefId(plotDef.getId());

                refreshForm(plotDef, event.getPlot().getSubsetDefinition().getOIFitsSubset());
                break;
            case PLOT_VIEWPORT_CHANGED:
                final PlotInfosData plotInfosData = event.getPlotInfosData();
                if (this.plotId != null && plotInfosData != null && this.plotId.equals(plotInfosData.getPlotId())) {
                    refreshForm(plotInfosData);
                }
            default:
                logger.debug("onProcess {} - done", event);
        }
    }
}
