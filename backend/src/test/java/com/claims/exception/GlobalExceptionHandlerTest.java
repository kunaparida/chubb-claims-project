package com.claims.exception;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessExceptionToItsConfiguredStatusAndProblemDetails() {
        var response = handler.handleBusiness(
                new BusinessException(HttpStatus.CONFLICT, "Duplicate account"), request("/api/auth/register"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Duplicate account", response.getBody().getDetail());
        assertEquals("/api/auth/register", response.getBody().getInstance().toString());
    }

    @Test
    void mapsMissingResourcesToNotFound() {
        var response = handler.handleResourceNotFound(
                new ResourceNotFoundException("Claim not found"), request("/api/claims/42"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Claim not found", response.getBody().getDetail());
    }

    @Test
    void includesValidationFieldErrorsInProblemDetails() {
        var response = handler.handleValidationException(
                new ValidationException("Invalid claim", Map.of("amount", "must be positive")),
                request("/api/claims"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("amount", "must be positive"), response.getBody().getProperties().get("errors"));
    }

    @Test
    void reportsUnsupportedRequestMediaTypeAs415() {
        var exception = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of());
        var response = handler.handleMediaTypeNotSupported(exception, request("/api/auth/register"));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("Content-Type must be application/json", response.getBody().getDetail());
    }

    @Test
    void namesMultipartWhenThatIsWhatTheEndpointAccepts() {
        var exception = new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_JSON, java.util.List.of(MediaType.MULTIPART_FORM_DATA));
        var response = handler.handleMediaTypeNotSupported(exception, request("/api/claims/1/documents"));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("Content-Type must be multipart/form-data", response.getBody().getDetail());
    }

    @Test
    void reportsAnUploadOverTheServerLimitAs413() {
        var response = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(5_242_880), request("/api/claims/1/documents"));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("The uploaded file is larger than the size limit", response.getBody().getDetail());
    }

    @Test
    void asksForTheFileWhenThePartIsMissing() {
        var response = handler.handleMissingPart(new MissingServletRequestPartException("file"), request("/api/claims/1/documents"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getDetail().contains("\"file\" field"));
    }

    @Test
    void asksForMultipartWhenTheRequestIsNotMultipart() {
        var response = handler.handleNotMultipart(new MultipartException("Current request is not a multipart request"),
                request("/api/claims/1/documents"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("The request must be multipart/form-data containing a file", response.getBody().getDetail());
    }

    @Test
    void hidesUnexpectedExceptionDetailsFromProblemResponse() {
        var response = handler.handleUnexpected(
                new IllegalStateException("sensitive internal detail"), request("/api/claims"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().getDetail());
    }

    private HttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
