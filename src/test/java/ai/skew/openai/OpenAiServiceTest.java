package ai.skew.openai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAiServiceTest {

    @Test
    public void testClientCreation() {
        OpenAiService service = new OpenAiService("test-key");
        assertNotNull(service);
    }

    @Test
    public void testWorksWithoutSkewKey() {
        OpenAiService service = new OpenAiService("test-key");
        assertNotNull(service);
    }
}
