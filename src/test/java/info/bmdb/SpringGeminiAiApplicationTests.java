package info.bmdb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class SpringGeminiAiApplicationTests {

	@Value("${spring.ai.google.genai.api-key:MISSING}")
	private String apiKey;

	@Test
	void contextLoads() {
		assertNotNull(apiKey, "L'API key ne devrait pas être null");
		assertFalse("MISSING".equals(apiKey), "L'API key n'est pas résolue");
		assertFalse("${GOOGLE_GENAI_API_KEY}".equals(apiKey), "L'API key est restée sous forme de placeholder");
		System.out.println("[DEBUG_LOG] API key résolue: " + apiKey.substring(0, Math.min(apiKey.length(), 5)) + "...");
	}

}
