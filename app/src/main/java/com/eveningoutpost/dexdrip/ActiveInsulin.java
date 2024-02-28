package com.eveningoutpost.dexdrip;

// A dirty hack to make it possible to get active insulin onto my Mi Band watch.
// This gives some nice global state, that I can
//   * write to from the CareLinkDataProcessor, and
//   * read from in the WatchFaceGenerator.

public class ActiveInsulin {
    public static double amount = -1.0;
}
