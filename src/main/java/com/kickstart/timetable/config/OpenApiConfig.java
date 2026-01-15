package com.kickstart.timetable.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Kickstart Timetable API",
                version = "v1",
                description = "Backend APIs for timetable OCR (PaddleOCR) + LLM structuring (AIStudio) and scheduling.",
                contact = @Contact(name = "Kickstart")
        )
)
public class OpenApiConfig {
}
