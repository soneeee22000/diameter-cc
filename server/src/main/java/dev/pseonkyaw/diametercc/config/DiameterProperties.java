package dev.pseonkyaw.diametercc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the {@code diameter} prefix in {@code application.yml}.
 * Drives the jdiameter Stack XML lookup and basic peer identity.
 */
@ConfigurationProperties(prefix = "diameter")
public record DiameterProperties(
        String stackXmlClasspath,
        String originHost,
        String originRealm,
        int listenPort
) {
    public DiameterProperties {
        if (stackXmlClasspath == null || stackXmlClasspath.isBlank()) {
            stackXmlClasspath = "/diameter-server.xml";
        }
        if (originHost == null || originHost.isBlank()) {
            originHost = "diameter-cc.local";
        }
        if (originRealm == null || originRealm.isBlank()) {
            originRealm = "pseonkyaw.dev";
        }
        if (listenPort <= 0) {
            listenPort = 3868;
        }
    }
}
