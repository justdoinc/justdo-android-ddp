package justdo.today.ddp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Created by Ryan Wong (ryan@rwmobi.com)
 */

public class JsonUtil {
    // Instance of Jackson library's ObjectMapper that converts between JSON and Java objects (POJOs)
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Borrowed from Android-DDP Library
     * Serializes the given Java object (POJO) with the Jackson library
     *
     * @param obj the object to serialize
     * @return the serialized object in JSON format
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static <T> T fromJson(final String json, final Class<T> targetType) {
        try {
            if (json != null) {
                final JsonNode jsonNode = OBJECT_MAPPER.readTree(json);
                return OBJECT_MAPPER.convertValue(jsonNode, targetType);
            } else {
                return null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
