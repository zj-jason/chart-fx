package de.gsi.microservice.aggregate.lsa;

/**
 * Extension of the standard FAIR Selector to include the (unique) LSA IDs.
 * This of course cannot be used for purposes of the timing system, but allows
 * to have unique identifiers for different machine settings.
 * (R: pRocess, E: sEquence, H: cHain)
 * 
 * Currently it does not use the Database ids but the hash of the chain name, because
 * the IDs are not available on the REST interface and we do not want to spam the LSA server.
 *
 * @author Alexander Krimm
 */
public class FairLsaSelector extends FairSelector {
    protected static final char SELECTOR_LSA_PROCESS = 'R'; // pRocress
    protected static final char SELECTOR_LSA_SEQUENCE = 'E'; // sEquence
    protected static final char SELECTOR_LSA_CHAIN = 'H'; // cHain

    protected long lsaProcessId = -1;
    protected long lsaSequenceId = -1;
    protected long lsaChainId = -1;

    /**
     * Constructs a catch-all selector
     */
    public FairLsaSelector() {
        super();
    }

    public FairLsaSelector(final String selectorString) {
        if (!selectorString.startsWith(SELECTOR_PREFIX))
            throw new IllegalArgumentException("Unsupported Selector: " + selectorString);
        String[] components = selectorString.substring(SELECTOR_PREFIX.length()).split(SELECTOR_SEPARATOR);
        for (String component : components) {
            switch (component.charAt(0)) {
            case SELECTOR_CHAIN:
                chainId = Integer.valueOf(component.substring(2));
                if (chainId < -1) {
                    throw new IllegalArgumentException("chain ID cannot be negative (except -1)");
                }
                break;
            case SELECTOR_PROCESS:
                processId = Integer.valueOf(component.substring(2));
                if (processId < -1) {
                    throw new IllegalArgumentException("process ID cannot be negative (except -1)");
                }
                break;
            case SELECTOR_SEQUENCE:
                sequenceId = Integer.valueOf(component.substring(2));
                if (sequenceId < -1) {
                    throw new IllegalArgumentException("sequence ID cannot be negative (except -1)");
                }
                break;
            case SELECTOR_TIMING:
                timingGroup = Integer.valueOf(component.substring(2));
                if (timingGroup < -1) {
                    throw new IllegalArgumentException("timing group cannot be negative (except -1)");
                }
                break;
            case SELECTOR_LSA_CHAIN:
                lsaChainId = Long.valueOf(component.substring(2));
                if (lsaChainId < -1) {
                    throw new IllegalArgumentException("lsa chain ID cannot be negative (except -1)");
                }
                break;
            case SELECTOR_LSA_PROCESS:
                lsaProcessId = Long.valueOf(component.substring(2));
                if (lsaProcessId < -1) {
                    throw new IllegalArgumentException("lsa process ID cannot be negative (except -1)");
                }
                break;
            case SELECTOR_LSA_SEQUENCE:
                lsaSequenceId = Long.valueOf(component.substring(2));
                if (lsaSequenceId < -1) {
                    throw new IllegalArgumentException("lsa sequence ID cannot be negative (except -1)");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported Selector field: " + component);
            }
        }
        selector = convertToString();
    }

    public FairLsaSelector(int chainId, int sequenceId, int processId, int timingGroup, long lsaProcessId, long lsaSequenceId, long lsaChainId) {
        super(chainId, sequenceId, processId, timingGroup);
        this.lsaProcessId = lsaProcessId;
        this.lsaSequenceId = lsaSequenceId;
        this.lsaChainId = lsaChainId;
    }

    @Override
    protected int convertToString(StringBuilder sb) {
        int elements = super.convertToString(sb);
        if (lsaSequenceId != -1) {
            if (elements > 0)
                sb.append(SELECTOR_SEPARATOR);
            sb.append(SELECTOR_LSA_PROCESS).append('=').append(lsaSequenceId);
            ++elements;
        }
        if (lsaChainId != -1) {
            if (elements > 0)
                sb.append(SELECTOR_SEPARATOR);
            sb.append(SELECTOR_LSA_PROCESS).append('=').append(lsaChainId);
            ++elements;
        }
        if (lsaProcessId != -1) {
            if (elements > 0)
                sb.append(SELECTOR_SEPARATOR);
            sb.append(SELECTOR_LSA_PROCESS).append('=').append(lsaProcessId);
            ++elements;
        }
        return elements;
    }
}
