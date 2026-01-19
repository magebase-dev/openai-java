package ai.langmesh.openai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAiServiceTest {

    @Test
    public void testClientCreation() {
        OpenAiService service = new OpenAiService("test-key");
        assertNotNull(service);
    }

    @Test
    public void testWorksWithoutlangmeshKey() {
        OpenAiService service = new OpenAiService("test-key");
        assertNotNull(service);
    }
}
