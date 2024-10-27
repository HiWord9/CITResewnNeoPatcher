package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import com.mojang.logging.LogUtils;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;

public class CITResewnNeoPatcherBootstrap {
    public static final String MODID = "citresewn_neopatcher";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String CITR_VERSION = "[1.2.0, 1.2.2]";
    public static final VersionRange CITR_VERSION_RANGE;

    static {
        try {
            CITR_VERSION_RANGE = VersionRange.createFromVersionSpec(CITR_VERSION);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
            // this should never happen
        }
    }
}
