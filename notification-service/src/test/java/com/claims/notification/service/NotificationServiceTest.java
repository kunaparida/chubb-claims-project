package com.claims.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.claims.notification.TestEvents;
import com.claims.notification.mail.EmailComposer;
import com.claims.notification.mail.EmailDeliveryException;
import com.claims.notification.mail.EmailMessage;
import com.claims.notification.mail.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock EmailSender sender;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(new EmailComposer(TestEvents.properties()), sender, new ProcessedEvents(100));
    }

    @Test
    void sendsOneEmailToTheClaimant() {
        service.handle(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED"));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(sent.capture());
        assertEquals("jane.doe@example.com", sent.getValue().toAddress());
        assertEquals("Your claim CLM-TEST-7 has been approved", sent.getValue().subject());
    }

    @Test
    void aDuplicateOfAHandledEventSendsNothing() {
        service.handle(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED"));
        service.handle(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED"));

        verify(sender, times(1)).send(any());
    }

    @Test
    void differentEventsForTheSameClaimEachSendAnEmail() {
        service.handle(TestEvents.event("evt-1", "SUBMITTED", "UNDER_REVIEW"));
        service.handle(TestEvents.event("evt-2", "UNDER_REVIEW", "APPROVED"));

        verify(sender, times(2)).send(any());
    }

    @Test
    void aFailedSendIsNotRememberedSoTheRetryTriesAgain() {
        doThrow(new EmailDeliveryException("smtp down", null)).doNothing().when(sender).send(any());

        assertThrows(EmailDeliveryException.class, () -> service.handle(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED")));
        service.handle(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED"));

        verify(sender, times(2)).send(any());
    }

    @Test
    void afterASuccessfulRetryTheEventIsRemembered() {
        doThrow(new EmailDeliveryException("smtp down", null)).doNothing().when(sender).send(any());
        assertThrows(EmailDeliveryException.class, () -> service.handle(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED")));
        service.handle(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED"));

        service.handle(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED"));

        verify(sender, times(2)).send(any());
    }
}
