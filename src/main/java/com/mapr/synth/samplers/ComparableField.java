package com.mapr.synth.samplers;

public interface ComparableField {
	
	String getMaxAsString();
	String getMinAsString();
	int compareTo(String c); 
	void setMaxAsString(String c, boolean plusOne);
	void setMinAsString(String c, boolean plusOne);
	String getLastSampledAsString();

}
