package dev.pseonkyaw.diametercc.config;

import java.io.InputStream;

import org.jdiameter.api.Stack;
import org.jdiameter.server.impl.StackImpl;
import org.jdiameter.server.impl.helpers.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the jdiameter {@link Stack} as a Spring-managed bean.
 *
 * The Stack is initialised here but NOT started — start happens in
 * {@code DiameterPeerLifecycle} after the rest of the bean graph
 * (network listeners) has been wired.
 */
@Configuration
@EnableConfigurationProperties(DiameterProperties.class)
public class DiameterStackConfig {

    private static final Logger log = LoggerFactory.getLogger(DiameterStackConfig.class);

    @Bean(destroyMethod = "destroy")
    public Stack diameterStack(DiameterProperties props) throws Exception {
        log.info("Loading jdiameter Stack XML config from classpath:{}", props.stackXmlClasspath());

        try (InputStream xml = getClass().getResourceAsStream(props.stackXmlClasspath())) {
            if (xml == null) {
                throw new IllegalStateException(
                    "Diameter Stack XML config not found on classpath: " + props.stackXmlClasspath());
            }

            org.jdiameter.api.Configuration cfg = new XMLConfiguration(xml);
            Stack stack = new StackImpl();
            stack.init(cfg);

            log.info("jdiameter Stack initialised — origin-host={} origin-realm={} listen-port={}",
                props.originHost(), props.originRealm(), props.listenPort());

            return stack;
        }
    }
}
