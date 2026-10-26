package com.claims.service;

import com.claims.dto.ClaimDocumentResponse;
import com.claims.entity.Claim;
import com.claims.entity.ClaimDocument;
import com.claims.entity.ClaimStatus;
import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.exception.BusinessException;
import com.claims.exception.ResourceNotFoundException;
import com.claims.repository.ClaimDocumentRepository;
import com.claims.repository.UserRepository;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documents a claimant attaches to a claim. Who may see them is exactly who may see the claim; only the claimant who owns the claim
 * can add them, and only while the claim is still open. Every rule about the file itself is here, so the web layer stays thin.
 *
 * <p>The type of a file is decided by its first bytes, never by its name or by the type the browser reports: a file that is not
 * really a PDF, PNG or JPEG is refused even if it is called {@code estimate.pdf}.
 */
@Service
public class ClaimDocumentService {
    /** The only formats accepted, each recognised by its signature (its first bytes) and its allowed file extensions. */
    private enum Format {
        PDF("PDF", "application/pdf", List.of("pdf"), new int[] {0x25, 0x50, 0x44, 0x46, 0x2D}),
        PNG("PNG", "image/png", List.of("png"), new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
        JPEG("JPG", "image/jpeg", List.of("jpg", "jpeg"), new int[] {0xFF, 0xD8, 0xFF});

        private final String label;
        private final String contentType;
        private final List<String> extensions;
        private final int[] signature;

        Format(String label, String contentType, List<String> extensions, int[] signature) {
            this.label = label;
            this.contentType = contentType;
            this.extensions = extensions;
            this.signature = signature;
        }

        boolean matches(byte[] content) {
            if (content.length < signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                if ((content[i] & 0xFF) != signature[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A stored file, ready to be sent back. */
    public record StoredDocument(String fileName, String contentType, byte[] content) {}

    private static final int MAX_NAME_LENGTH = 255;
    private static final String ACCEPTED = "PDF, JPG or PNG";

    private final ClaimService claimService;
    private final ClaimDocumentRepository documents;
    private final UserRepository users;
    private final long maxBytes;
    private final int maxFiles;

    public ClaimDocumentService(ClaimService claimService, ClaimDocumentRepository documents, UserRepository users,
                                @Value("${app.documents.max-bytes:5242880}") long maxBytes,
                                @Value("${app.documents.max-files:5}") int maxFiles) {
        this.claimService = claimService;
        this.documents = documents;
        this.users = users;
        this.maxBytes = maxBytes;
        this.maxFiles = maxFiles;
    }

    @Transactional
    public ClaimDocumentResponse add(Long claimId, MultipartFile file, String actorEmail) {
        User actor = actor(actorEmail);
        Claim claim = claimService.viewableClaim(claimId, actorEmail);
        if (actor.getRole() != Role.CLAIMANT) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only the claimant can attach documents to a claim");
        }
        if (claim.getStatus() == ClaimStatus.REJECTED || claim.getStatus() == ClaimStatus.SETTLED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Documents can no longer be added: this claim is " + claim.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (documents.countByClaimId(claim.getId()) >= maxFiles) {
            throw new BusinessException(HttpStatus.CONFLICT, "A claim can have at most " + maxFiles + " documents");
        }

        String name = cleanName(file.getOriginalFilename());
        if (name.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The file has no name");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The file name is longer than " + MAX_NAME_LENGTH + " characters");
        }
        if (file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "\"" + name + "\" is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "\"" + name + "\" is larger than " + megabytes(maxBytes));
        }

        byte[] content = read(file, name);
        Format format = detect(content);
        if (format == null) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "\"" + name + "\" is not a " + ACCEPTED + " file");
        }
        if (!format.extensions.contains(extension(name))) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "\"" + name + "\" is a " + format.label + " file but its name says otherwise; rename it with the right ending");
        }

        ClaimDocument saved = documents.save(new ClaimDocument(claim, name, format.contentType, content, actor));
        return new ClaimDocumentResponse(saved.getId(), saved.getFileName(), saved.getContentType(), saved.getSizeBytes(),
                saved.getUploadedAt(), actor.getFullName());
    }

    @Transactional(readOnly = true)
    public List<ClaimDocumentResponse> list(Long claimId, String actorEmail) {
        Claim claim = claimService.viewableClaim(claimId, actorEmail);
        return documents.findDetailsByClaimId(claim.getId());
    }

    @Transactional(readOnly = true)
    public StoredDocument download(Long claimId, Long documentId, String actorEmail) {
        Claim claim = claimService.viewableClaim(claimId, actorEmail);
        ClaimDocument document = documents.findByIdAndClaimId(documentId, claim.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return new StoredDocument(document.getFileName(), document.getContentType(), document.getContent());
    }

    private User actor(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    private static byte[] read(MultipartFile file, String name) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "\"" + name + "\" could not be read");
        }
    }

    private static Format detect(byte[] content) {
        for (Format format : Format.values()) {
            if (format.matches(content)) {
                return format;
            }
        }
        return null;
    }

    /** The name without any folder (browsers and scripts can send "..\\..\\x.pdf") and without control characters. */
    static String cleanName(String original) {
        if (original == null) {
            return "";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.replaceAll("\\p{Cntrl}", "").trim();
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String megabytes(long bytes) {
        double mb = bytes / 1_048_576.0;
        return (mb == Math.rint(mb) ? String.valueOf((long) mb) : String.format(Locale.ROOT, "%.1f", mb)) + " MB";
    }
}
