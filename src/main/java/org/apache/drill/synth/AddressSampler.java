package org.apache.drill.synth;

/**
 * Sample kind of plausible addresses
 */
public class AddressSampler extends FieldSampler {

    private final StreetNameSampler street;
    private final ForeignKeySampler number;

    public AddressSampler() {
        street = new StreetNameSampler();
        number = new ForeignKeySampler(100000, 0.5);
    }

    @Override
    public String sample() {
        return number.sample() + " " + street.sample();
    }
}
