package com.eric_eldard.util;

public final class EnvUtils
{
    /// Gets a system property, with fallbacks to an env-var, then a hard-coded value
    public static String getProperty(String propName, String fallbackEnvVarName, String fallbackValue)
    {
        String property = System.getProperty(propName, System.getenv(fallbackEnvVarName));
        return property == null || property.isBlank() ? fallbackValue : property;
    }

    private EnvUtils()
    {
        // util ctor
    }
}