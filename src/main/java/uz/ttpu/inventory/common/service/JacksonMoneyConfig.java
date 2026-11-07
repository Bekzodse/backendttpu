package uz.ttpu.inventory.common.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global Jackson configuration for monetary values. All {@link BigDecimal} values are serialized as
 * Strings with exactly two decimal places (e.g. "150000.00") to avoid IEEE 754 floating point
 * precision errors on frontend clients.
 */
@Configuration
public class JacksonMoneyConfig {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jacksonMoneyCustomizer() {
    return builder -> {
      builder.serializerByType(
          BigDecimal.class,
          new JsonSerializer<BigDecimal>() {
            @Override
            public void serialize(
                BigDecimal value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
              if (value == null) {
                gen.writeNull();
              } else {
                gen.writeString(value.setScale(2, RoundingMode.HALF_UP).toPlainString());
              }
            }
          });

      builder.deserializerByType(
          BigDecimal.class,
          new JsonDeserializer<BigDecimal>() {
            @Override
            public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
              String text = p.getText();
              if (text == null || text.isBlank()) {
                return null;
              }
              return new BigDecimal(text.trim()).setScale(2, RoundingMode.HALF_UP);
            }
          });
    };
  }
}
