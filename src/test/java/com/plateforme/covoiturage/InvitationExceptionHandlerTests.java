package com.plateforme.covoiturage;

import com.plateforme.covoiturage.exception.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class InvitationExceptionHandlerTests {
    @RestController
    static class Probe {
        @GetMapping("/test-only/{kind}")
        String error(@PathVariable("kind") String kind) {
            if (kind.equals("invalid")) throw new InvitationInvalideException();
            throw new InvitationConflictException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "conflict"})
    void centralizedErrorsAreGenericAndContainNoTechnicalDetails(String kind) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(get("/test-only/" + kind)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(kind.equals("invalid") ? 400 : 409);
        assertThat(response.getContentAsString()).contains(kind.equals("invalid")
                ? "INVALID_INVITATION" : "INVITATION_CONFLICT")
                .doesNotContain("stackTrace", "password", "hash", "Bearer", "Exception");
    }
}
