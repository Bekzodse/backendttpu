package uz.ttpu.inventory.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.ttpu.inventory.common.service.JacksonMoneyConfig;

class JacksonMoneyFormattingTest {

  private ObjectMapper objectMapper;

  record MoneySample(String name, BigDecimal amount) {}

  @BeforeEach
  void setUp() {
    org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder =
        new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
    new JacksonMoneyConfig().jacksonMoneyCustomizer().customize(builder);
    objectMapper = builder.build();
  }

  @Test
  @DisplayName(
      "Prompt 13 / Money: BigDecimal is serialized as a string with exactly two decimal places")
  void testBigDecimalSerializationTwoDecimals() throws Exception {
    MoneySample sample1 = new MoneySample("Laptop", new BigDecimal("150000"));
    String json1 = objectMapper.writeValueAsString(sample1);
    assertThat(json1).contains("\"amount\":\"150000.00\"");

    MoneySample sample2 = new MoneySample("Mouse", new BigDecimal("123.4"));
    String json2 = objectMapper.writeValueAsString(sample2);
    assertThat(json2).contains("\"amount\":\"123.40\"");

    MoneySample sample3 = new MoneySample("Rounding", new BigDecimal("99.999"));
    String json3 = objectMapper.writeValueAsString(sample3);
    assertThat(json3).contains("\"amount\":\"100.00\"");

    MoneySample sampleNull = new MoneySample("Empty", null);
    String jsonNull = objectMapper.writeValueAsString(sampleNull);
    assertThat(jsonNull).contains("\"amount\":null");
  }

  @Test
  @DisplayName(
      "Prompt 13 / Money: Deserializer accepts both string and numeric representations into"
          + " BigDecimal with scale 2")
  void testBigDecimalDeserialization() throws Exception {
    String jsonFromString = "{\"name\":\"Monitor\",\"amount\":\"2500000.50\"}";
    MoneySample fromString = objectMapper.readValue(jsonFromString, MoneySample.class);
    assertThat(fromString.amount()).isEqualTo(new BigDecimal("2500000.50"));
    assertThat(fromString.amount().scale()).isEqualTo(2);

    String jsonFromNumber = "{\"name\":\"Keyboard\",\"amount\":450000}";
    MoneySample fromNumber = objectMapper.readValue(jsonFromNumber, MoneySample.class);
    assertThat(fromNumber.amount()).isEqualTo(new BigDecimal("450000.00"));
    assertThat(fromNumber.amount().scale()).isEqualTo(2);
  }
}
