package de.gsi.microservice.cmwlight;

import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation for the Selector, implements matching the selector, -1 matches everything
 * TODO: check difference between "FAIR.SELECTOR.ALL", "FAIR.SELECTOR.C=-1:S=-1:P=-1" and "".
 * TODO: use factory class to cache objects?
 * 
 * @author Alexander Krimm
 */
public class FairSelector implements Serializable{
    private static final long serialVersionUID = 1L;
    protected static final String SELECTOR_PREFIX = "FAIR.SELECTOR.";
    protected static final char SELECTOR_CHAIN = 'C';
    protected static final char SELECTOR_PROCESS = 'P';
    protected static final char SELECTOR_SEQUENCE = 'S';
    protected static final char SELECTOR_TIMING = 'T';
    protected static final String SELECTOR_SEPARATOR = ":";

    protected int chainId = -1;
    protected int sequenceId = -1;
    protected int processId = -1;
    protected int timingGroup = -1;
    protected String selector;

    /**
     * Default ALL Selector
     */
    public FairSelector() {
        selector = convertToString();
    }

    public FairSelector(final int chainId, final int sequenceId, final int processId, final int timingGroup) {
        super();
        this.chainId = chainId;
        this.sequenceId = sequenceId;
        this.processId = processId;
        this.timingGroup = timingGroup;
        selector = convertToString();
    }

    public FairSelector(final String selectorString) {
        if (!selectorString.startsWith(SELECTOR_PREFIX))
            throw new IllegalArgumentException("Unsupported Selector: " + selectorString);
        String[] components = selectorString.substring(SELECTOR_PREFIX.length()).split(SELECTOR_SEPARATOR);
        for (String component : components) {
            switch (component.charAt(0)) {
            case SELECTOR_CHAIN:
                chainId = Integer.valueOf(component.substring(2));
                if (chainId < -1) {
                    throw new IllegalArgumentException("chain ID cannot be negative");
                }
                break;
            case SELECTOR_PROCESS:
                processId = Integer.valueOf(component.substring(2));
                if (processId < -1) {
                    throw new IllegalArgumentException("process ID cannot be negative");
                }
                break;
            case SELECTOR_SEQUENCE:
                sequenceId = Integer.valueOf(component.substring(2));
                if (sequenceId < -1) {
                    throw new IllegalArgumentException("sequence ID cannot be negative");
                }
                break;
            case SELECTOR_TIMING:
                timingGroup = Integer.valueOf(component.substring(2));
                if (timingGroup < -1) {
                    throw new IllegalArgumentException("timing group cannot be negative");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported Selector field: " + component);
            }
        }
        selector = convertToString();
    }

    /**
     * Check if a selector matches on another selector/context, where -1 matches everything
     * 
     * @param context A context to match against. All fields should be >= 0.
     * @return true if all fields that are not -1 match the given context
     */
    public boolean matches(final FairSelector context) {
        if (!context.isContext()) {
            throw new UnsupportedOperationException("Can only match against a context");
        }
        return (this.chainId == -1 || this.chainId == context.chainId) //
                && (this.sequenceId == -1 || this.sequenceId == context.sequenceId) //
                && (this.processId == -1 || this.processId == context.processId) //
                && (this.timingGroup == -1 || this.timingGroup == context.timingGroup);
    }

    /**
     * @param other Another selector
     * @return whether the contexts matched by the other selector contain all selectors for this selector
     */
    public boolean isIncludedIn(FairSelector other) {
        return (other.chainId == -1 || this.chainId == other.chainId) //
                && (other.sequenceId == -1 || this.sequenceId == other.sequenceId) //
                && (other.processId == -1 || this.processId == other.processId) //
                && (other.timingGroup == -1 || this.timingGroup == other.timingGroup); //
    }

    /**
     * @return true if all fields of the selector are not -1
     */
    public boolean isContext() {
        return this.chainId != -1 && this.sequenceId != -1 && this.processId != -1 && this.timingGroup != -1;
    }

    /**
     * @return The string representation of the context or selector
     */
    protected String convertToString() {
        StringBuilder sb = new StringBuilder(SELECTOR_PREFIX);
        int elements = convertToString(sb);
        if (elements == 0) {
            // default all chains selector
            // sb.append("ALL");
            sb.append(SELECTOR_CHAIN).append('=').append(-1);
        }
        return sb.toString();
    }

    /**
     * Internal helper function, allows to easily extend the toString function.
     * Adds all fields implemented in this class to the provided string builder.
     * 
     * @param sb A string builder to add the fields to.
     * @return The number of fields added
     */
    protected int convertToString(StringBuilder sb) {
        int elements = 0;
        if (sequenceId != -1) {
            sb.append(SELECTOR_SEQUENCE).append('=').append(sequenceId);
            elements++;
        }
        if (chainId != -1) {
            if (elements > 0)
                sb.append(SELECTOR_SEPARATOR);
            sb.append(SELECTOR_CHAIN).append('=').append(chainId);
            elements++;
        }
        if (processId != -1) {
            if (elements > 0)
                sb.append(SELECTOR_SEPARATOR);
            sb.append(SELECTOR_PROCESS).append('=').append(processId);
            elements++;
        }
        if (timingGroup != -1) {
            if (elements > 0)
                sb.append(SELECTOR_SEPARATOR);
            sb.append(SELECTOR_TIMING).append('=').append(timingGroup);
            elements++;
        }
        return elements;
    }

    @Override
    public String toString() {
        return selector;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this)
            return true;
        if (!(other instanceof FairSelector))
            return false;
        FairSelector otherSelector = (FairSelector) other;
        return this.chainId == otherSelector.chainId //
                && this.sequenceId == otherSelector.sequenceId //
                && this.processId == otherSelector.processId //
                && this.timingGroup == otherSelector.timingGroup;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chainId, sequenceId, processId, timingGroup);
    }

    /**
     * @param c The identifier for the wanted selector component
     * @return The value for the specified selector component
     */
    public long get(char c) {
        switch (c) {
        case SELECTOR_CHAIN:
            return chainId;
        case SELECTOR_SEQUENCE:
            return sequenceId;
        case SELECTOR_PROCESS:
            return processId;
        case SELECTOR_TIMING:
            return timingGroup;
        default:
            return -1;
        }
    }
}
