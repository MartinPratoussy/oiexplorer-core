/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.oiexplorer.core.util;

import fr.jmmc.jmcs.util.StringUtils;
import fr.jmmc.oiexplorer.core.function.ConverterFactory;
import fr.jmmc.oitools.model.OIData;
import fr.jmmc.oitools.model.StaNamesDir;
import fr.jmmc.oitools.util.StationNamesComparator;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jfree.data.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author bourgesl
 */
public final class OIDataListHelper {

    /** Class logger */
    private static final Logger logger = LoggerFactory.getLogger(OIDataListHelper.class.getName());
    /** double formatter for wave lengths */
    private final static NumberFormat df4 = new DecimalFormat("0.000#");

    /* GetOIDataString operators */
    public final static GetOIDataString GET_ARR_NAME = new GetOIDataString() {
        @Override
        public String getString(final OIData oiData) {
            return oiData.getArrName();
        }
    };

    public final static GetOIDataString GET_INS_NAME = new GetOIDataString() {
        @Override
        public String getString(final OIData oiData) {
            return oiData.getInsName();
        }
    };

    public final static GetOIDataString GET_DATE_OBS = new GetOIDataString() {
        @Override
        public String getString(final OIData oiData) {
            return oiData.getDateObs();
        }
    };

    private OIDataListHelper() {
        super();
    }

    /**
     * Return the unique String values from given operator applied on given OIData tables
     * @param oiDataList OIData tables
     * @param set set instance to use
     * @param operator operator to get String values
     * @return unique String values
     */
    public static Set<String> getDistinct(final List<OIData> oiDataList, final Set<String> set, final GetOIDataString operator) {
        String value;
        for (OIData oiData : oiDataList) {
            value = operator.getString(oiData);
            if (value != null) {
                logger.debug("getDistinct: {}", value);

                int pos = value.indexOf('_');

                if (pos != -1) {
                    value = value.substring(0, pos);
                }
                set.add(value);
            }
        }
        return set;
    }

    /**
     * Return the unique String values from given operator applied on given OIData tables
     * @param oiDataList OIData tables
     * @param set set instance to use
     * @param operator operator to get String values
     * @return unique String values
     */
    public static Set<String> getDistinctNoSuffix(final List<OIData> oiDataList, final Set<String> set, final GetOIDataString operator) {
        String value;
        for (OIData oiData : oiDataList) {
            value = operator.getString(oiData);
            if (value != null) {
                logger.debug("getDistinctNoSuffix: {}", value);

                int pos = value.lastIndexOf('_');

                if (pos != -1) {
                    final String suffix = value.substring(pos + 1, value.length());
                    try {
                        Integer.parseInt(suffix);
                        // strip suffix:
                        value = value.substring(0, pos);
                    } catch (NumberFormatException nfe) {
                        logger.debug("getDistinctNoSuffix: {}", suffix, nfe);
                        // use complete value
                    }
                }
                set.add(value);
            }
        }
        return set;
    }

    /**
     * Return the unique staNames values (sorted by name) from given OIData tables
     * @param oiDataList OIData tables
     * @param usedStaNamesMap Map of used staNames to StaNamesDir (reference StaNames / orientation)
     * @return given set instance
     */
    public static List<String> getDistinctStaNames(final List<OIData> oiDataList,
                                                   final Map<String, StaNamesDir> usedStaNamesMap) {

        final Set<String> set = new HashSet<String>(32);

        for (final OIData oiData : oiDataList) {
            for (final short[] staIndexes : oiData.getDistinctStaIndex()) {
                final String staNames = oiData.getRealStaNames(usedStaNamesMap, staIndexes);
                set.add(staNames);
            }
        }
        // Sort by name (consistent naming & colors):
        final List<String> sortedList = new ArrayList<String>(set);
        Collections.sort(sortedList, StationNamesComparator.INSTANCE);

        return sortedList;
    }

    /**
     * Return the unique staConfs values from given OIData tables
     * @param oiDataList OIData tables
     * @return given set instance
     */
    public static List<String> getDistinctStaConfs(final List<OIData> oiDataList) {
        final Set<String> set = new HashSet<String>(32);

        for (OIData oiData : oiDataList) {
            for (short[] staConf : oiData.getDistinctStaConf()) {
                final String staNames = oiData.getStaNames(staConf);
                set.add(staNames);
            }
        }
        // Sort by name (consistent naming & colors):
        final List<String> sortedList = new ArrayList<String>(set);
        Collections.sort(sortedList, StationNamesComparator.INSTANCE);

        logger.debug("getDistinctStaConfs : {}", sortedList);
        return sortedList;
    }

    /**
     * Return the unique wave length ranges from given OIData tables
     * @param oiDataList OIData tables
     * @param set set instance to use
     */
    public static void getDistinctWaveLengthRange(final List<OIData> oiDataList, final Set<String> set) {
        final StringBuilder sb = new StringBuilder(20);

        for (OIData oiData : oiDataList) {
            final fr.jmmc.oitools.model.range.Range effWaveRange = oiData.getEffWaveRange();

            if (effWaveRange.isFinite()) {
                sb.append('[').append(df4.format(ConverterFactory.CONVERTER_MICRO_METER.evaluate(effWaveRange.getMin())))
                        .append(' ').append(ConverterFactory.CONVERTER_MICRO_METER.getUnit());
                sb.append(" - ").append(df4.format(ConverterFactory.CONVERTER_MICRO_METER.evaluate(effWaveRange.getMax())))
                        .append(' ').append(ConverterFactory.CONVERTER_MICRO_METER.getUnit()).append(']');

                final String wlenRange = sb.toString();
                sb.setLength(0);
                logger.debug("wlen range : {}", wlenRange);

                set.add(wlenRange);
            }
        }
    }

    /**
     * Return the largest wave length range from given OIData tables
     * @param oiDataList OIData tables
     * @return largest wave length range
     */
    public static Range getWaveLengthRange(final List<OIData> oiDataList) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (OIData oiData : oiDataList) {
            final fr.jmmc.oitools.model.range.Range effWaveRange = oiData.getEffWaveRange();

            if (effWaveRange.getMin() < min) {
                min = effWaveRange.getMin();
            }
            if (effWaveRange.getMax() > max) {
                max = effWaveRange.getMax();
            }
        }
        return new Range(min, max);
    }

    public static void toString(final Set<String> set, final StringBuilder sb, final String internalSeparator, final String separator) {
        toString(set, sb, internalSeparator, separator, Integer.MAX_VALUE);
    }

    public static void toString(final Set<String> set, final StringBuilder sb, final String internalSeparator, final String separator, final int threshold, final String alternateText) {
        // hard coded limit:
        if (set.size() > threshold) {
            sb.append(alternateText);
        } else {
            toString(set, sb, internalSeparator, separator, Integer.MAX_VALUE);
        }
    }

    private static void toString(final Set<String> set, final StringBuilder sb, final String internalSeparator, final String separator, final int maxLength) {
        int n = 0;
        for (String v : set) {
            sb.append(StringUtils.replaceWhiteSpaces(v, internalSeparator)).append(separator);
            n++;
            if (n > maxLength) {
                return;
            }
        }
        if (n != 0) {
            // remove separator at the end:
            sb.setLength(sb.length() - separator.length());

        }
    }

    /**
     * Get String operator applied on any OIData table
     */
    private interface GetOIDataString {

        /**
         * Return a String value (keyword for example) for the given OIData table
         * @param oiData OIData table
         * @return String value
         */
        public String getString(final OIData oiData);
    }

}
