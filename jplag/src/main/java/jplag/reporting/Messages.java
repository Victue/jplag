package jplag.reporting;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
/**
 * html中的文字
 */
public class Messages {

    private final ResourceBundle resourceBundle;

    /**
     * @param countryTag may be "de", "en", "fr", "es", "pt" or "ptbr"
     */
    public Messages(String countryTag) {
        String bundleName = "jplag.messages";
        resourceBundle = ResourceBundle.getBundle(bundleName, new Locale(countryTag));
    }

    /*
     * @param key ：Report.Language
     * 
     * @return Language
     */
    public String getString(String key) {
        try {
            return resourceBundle.getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }
}
