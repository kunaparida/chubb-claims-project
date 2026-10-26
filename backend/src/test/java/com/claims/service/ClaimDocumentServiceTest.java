package com.claims.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.claims.dto.ClaimDocumentResponse;
import com.claims.entity.Claim;
import com.claims.entity.ClaimDocument;
import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimType;
import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.exception.BusinessException;
import com.claims.exception.ResourceNotFoundException;
import com.claims.repository.ClaimDocumentRepository;
import com.claims.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

/** The rules for attaching, listing and downloading documents. Limits are small here (1 KB, 3 files) so they can be hit cheaply. */
class ClaimDocumentServiceTest {
    private static final byte[] PDF = bytes("%PDF-1.4\n1 0 obj\n<<>>\nendobj\n");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    private ClaimService claimService;
    private ClaimDocumentRepository documents;
    private UserRepository users;
    private ClaimDocumentService service;

    private User claimant;
    private User officer;
    private Claim claim;

    @BeforeEach
    void setUp() {
        claimService = mock(ClaimService.class);
        documents = mock(ClaimDocumentRepository.class);
        users = mock(UserRepository.class);
        service = new ClaimDocumentService(claimService, documents, users, 1024, 3);

        claimant = new User("claimant@example.com", "encoded", "Jane Claimant", Role.CLAIMANT);
        officer = new User("officer@claims.local", "encoded", "Claims Officer", Role.OFFICER);
        claim = new Claim("CLM-TEST", "POL-123", "Jane Claimant", "claimant@example.com", LocalDate.now(),
                "Rear bumper damage from a parking collision.", new BigDecimal("100.00"), ClaimType.MOTOR, "SG", claimant);

        when(users.findByEmailIgnoreCase("claimant@example.com")).thenReturn(Optional.of(claimant));
        when(users.findByEmailIgnoreCase("officer@claims.local")).thenReturn(Optional.of(officer));
        when(claimService.viewableClaim(any(), any())).thenReturn(claim);
        when(documents.countByClaimId(any())).thenReturn(0L);
        when(documents.save(any(ClaimDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({"estimate.pdf,application/pdf", "photo.png,image/png", "photo.jpg,image/jpeg", "PHOTO.JPEG,image/jpeg"})
    void storesAnAcceptedFileUnderTheTypeItsContentsProve(String name, String expectedType) {
        byte[] content = name.toLowerCase().endsWith(".pdf") ? PDF : name.toLowerCase().endsWith(".png") ? PNG : JPEG;

        ClaimDocumentResponse response = service.add(1L, file(name, "text/plain", content), "claimant@example.com");

        assertEquals(expectedType, response.contentType()); // not the "text/plain" the client claimed
        assertEquals(name, response.fileName());
        assertEquals(content.length, response.sizeBytes());
        assertEquals("Jane Claimant", response.uploadedByName());
        ArgumentCaptor<ClaimDocument> saved = ArgumentCaptor.forClass(ClaimDocument.class);
        verify(documents).save(saved.capture());
        assertSame(claim, saved.getValue().getClaim());
        assertSame(claimant, saved.getValue().getUploadedBy());
        assertArrayEquals(content, saved.getValue().getContent());
    }

    @Test
    void keepsOnlyTheFileNameWhenTheClientSendsAPath() {
        ClaimDocumentResponse response = service.add(1L, file("..\\..\\secrets/evil.pdf", "application/pdf", PDF), "claimant@example.com");

        assertEquals("evil.pdf", response.fileName());
        assertEquals("evil.pdf", ClaimDocumentService.cleanName("C:\\Users\\jane\\evil.pdf"));
        assertEquals("a.pdf", ClaimDocumentService.cleanName(" a\u0007.pdf "));
        assertEquals("", ClaimDocumentService.cleanName(null));
    }

    @Test
    void refusesAFileThatIsNotReallyAPdfPngOrJpegWhateverItIsCalled() {
        BusinessException error = refused(file("estimate.pdf", "application/pdf", bytes("just some text, not a pdf")));

        assertEquals(415, error.getStatus().value());
        assertTrue(error.getMessage().contains("estimate.pdf"));
        assertTrue(error.getMessage().contains("PDF, JPG or PNG"));
    }

    @Test
    void refusesAFileWhoseNameContradictsItsContents() {
        BusinessException error = refused(file("photo.pdf", "application/pdf", PNG));

        assertEquals(415, error.getStatus().value());
        assertTrue(error.getMessage().contains("PNG"));
    }

    @Test
    void refusesAFileWithNoExtension() {
        assertEquals(415, refused(file("estimate", "application/pdf", PDF)).getStatus().value());
    }

    @Test
    void refusesEmptyOversizedAndNamelessFiles() {
        BusinessException empty = refused(file("empty.pdf", "application/pdf", new byte[0]));
        assertEquals(400, empty.getStatus().value());
        assertTrue(empty.getMessage().contains("empty.pdf"));

        byte[] big = new byte[1025];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        BusinessException tooLarge = refused(file("big.pdf", "application/pdf", big));
        assertEquals(413, tooLarge.getStatus().value());
        assertTrue(tooLarge.getMessage().contains("larger than"));

        assertEquals(400, refused(file("", "application/pdf", PDF)).getStatus().value());
        assertEquals(400, refused(file("x".repeat(256) + ".pdf", "application/pdf", PDF)).getStatus().value());
    }

    @Test
    void acceptsAFileExactlyAtTheSizeLimit() {
        byte[] exact = new byte[1024];
        System.arraycopy(PDF, 0, exact, 0, PDF.length);

        assertEquals(1024, service.add(1L, file("exact.pdf", "application/pdf", exact), "claimant@example.com").sizeBytes());
    }

    @Test
    void onlyTheClaimantMayAttachDocuments() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.add(1L, file("estimate.pdf", "application/pdf", PDF), "officer@claims.local"));

        assertEquals(403, error.getStatus().value());
        verify(documents, never()).save(any());
    }

    @Test
    void aClaimThatIsNotVisibleToTheCallerIsNotFoundAndNothingIsStored() {
        when(claimService.viewableClaim(any(), any())).thenThrow(new ResourceNotFoundException("Claim not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.add(9L, file("estimate.pdf", "application/pdf", PDF), "claimant@example.com"));
        verify(documents, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"REJECTED", "SETTLED"})
    void closedClaimsTakeNoMoreDocuments(ClaimStatus status) {
        claim.transitionTo(status);

        BusinessException error = refused(file("estimate.pdf", "application/pdf", PDF));

        assertEquals(409, error.getStatus().value());
        assertTrue(error.getMessage().toLowerCase().contains(status.name().toLowerCase()));
    }

    @Test
    void openClaimsInEveryOtherStatusStillTakeDocuments() {
        for (ClaimStatus status : List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.MORE_INFO_REQUIRED, ClaimStatus.APPROVED)) {
            claim.transitionTo(status);
            assertNotNull(service.add(1L, file("estimate.pdf", "application/pdf", PDF), "claimant@example.com"), status.name());
        }
    }

    @Test
    void aClaimHoldsAtMostTheConfiguredNumberOfDocuments() {
        when(documents.countByClaimId(any())).thenReturn(3L);

        BusinessException error = refused(file("one-too-many.pdf", "application/pdf", PDF));

        assertEquals(409, error.getStatus().value());
        assertTrue(error.getMessage().contains("at most 3"));
    }

    @Test
    void listsTheDetailsOfTheClaimsDocumentsAfterCheckingTheCallerMayViewTheClaim() {
        List<ClaimDocumentResponse> details = List.of(new ClaimDocumentResponse(1L, "a.pdf", "application/pdf", 10, null, "Jane Claimant"));
        when(documents.findDetailsByClaimId(any())).thenReturn(details);

        assertSame(details, service.list(1L, "officer@claims.local"));
        verify(claimService).viewableClaim(1L, "officer@claims.local");
    }

    @Test
    void downloadsADocumentOfTheClaimOrReportsItMissing() {
        ClaimDocument stored = new ClaimDocument(claim, "estimate.pdf", "application/pdf", PDF, claimant);
        when(documents.findByIdAndClaimId(any(), any())).thenReturn(Optional.of(stored));

        ClaimDocumentService.StoredDocument document = service.download(1L, 5L, "officer@claims.local");
        assertEquals("estimate.pdf", document.fileName());
        assertEquals("application/pdf", document.contentType());
        assertArrayEquals(PDF, document.content());

        when(documents.findByIdAndClaimId(any(), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.download(1L, 6L, "officer@claims.local"));
    }

    private BusinessException refused(MockMultipartFile file) {
        BusinessException error = assertThrows(BusinessException.class, () -> service.add(1L, file, "claimant@example.com"));
        verify(documents, never()).save(any());
        return error;
    }

    private static MockMultipartFile file(String name, String clientType, byte[] content) {
        return new MockMultipartFile("file", name, clientType, content);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
