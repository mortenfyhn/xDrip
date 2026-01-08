package com.eveningoutpost.dexdrip.utilitymodels;

/**
 * Pump state flags
 * Should not be used directly, but thru an EnumSet<PumpState>
 */
public enum PumpState {
	// MiniMed 780G states
	SMARTGUARD_ON,
	DELIVERY_SUSPENDED,
	TEMPORARY_TARGET
}
