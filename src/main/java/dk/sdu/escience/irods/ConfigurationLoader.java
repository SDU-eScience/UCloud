package dk.sdu.escience.irods;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

// NOTE(dan): Is a singleton ideal for this? Maybe not. But the convenience factor is
// probably enough to justify this. The factory layer still accepts full configuration. Assuming
// that this never becomes too complicated it should be fine, otherwise we should start passing a
// configuration object to the factory layer instead.
enum ConfigurationLoader {
    INSTANCE;
    private Configuration properties = null;

    private static final String[] PROPERTIES_SEARCH_LOCATIONS = {
            "irodsaccesswrapper.properties",
            "/var/lib/irodsaccesswrapper/conf/irodsaccesswrapper.properties",
            "C:\\irodsaccesswrapper.properties"
    };

    @NotNull
    public Configuration retrieveOrLoadProperties() {
        Configuration properties = retrieveOrLoadProperties(true);
        assert properties != null;
        return properties;
    }

    @Nullable
    public Configuration retrieveOrLoadProperties(boolean required) {
        if (properties != null) {
            return properties;
        }

        for (String location : PROPERTIES_SEARCH_LOCATIONS) {
            File f = new File(location);
            if (!f.exists()) continue;
            Properties props = new Properties();
            try {
                props.load(new FileReader(f));
            } catch (IOException e) {
                if (required) {
                    throw new IllegalStateException("Unable to read properties file at " + f.getAbsolutePath() +
                            ". Please make sure the current user has permissions to read this file and " +
                            "that the properties file is well-formed.", e);
                } else {
                    return null;
                }
            }
            try {
                properties = new Configuration(props);
            } catch (ConfigurationException e) {
                if (required) throw e;
                return null;
            }
            return properties;
        }

        if (required) {
            throw new IllegalStateException("Unable to locate properties file. Search locations are: " +
                    Arrays.toString(PROPERTIES_SEARCH_LOCATIONS));
        } else {
            return null;
        }
    }
}
