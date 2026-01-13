package com.spring.teambondbackend.rabbitmq.consumer;

import com.spring.teambondbackend.hackathon.service.MailService;
import com.spring.teambondbackend.recommendation.dtos.GithubScoreRequest;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Listener for processing messages in the Dead Letter Queue (DLQ).
 * Logs failed messages and sends HTML email notifications to supervisors with
 * error details.
 */
@Component
@RequiredArgsConstructor
public class DeadLetterQueueConsumer {
    private static final Logger logger = LoggerFactory.getLogger(DeadLetterQueueConsumer.class);
    private static final String NO_STACK_TRACE = "No stack trace available";

    private final MailService mailService;

    @org.springframework.beans.factory.annotation.Value("${Supervisor_Emails}")
    private String supervisorEmails;

    /**
     * Processes messages from the DLQ, logging errors and sending HTML email
     * notifications to supervisors.
     *
     * @param request The GitHub score request that failed processing.
     * @param headers Message headers, including the exception stack trace.
     */
    @RabbitListener(queues = "${rabbitmq.dlq.queue}")
    public void handleDeadLetterMessage(@Payload GithubScoreRequest request, @Headers Map<String, Object> headers) {
        String stackTrace = extractStackTrace(headers);
        logger.error("Received DLQ message for user: {}, error: {}", request.getUsername(), stackTrace);

        String emailSubject = String.format("Error Processing GitHub Score Request for User: %s",
                request.getUsername());
        String emailBody = buildHtmlEmailBody(request, stackTrace);

        sendSupervisorEmails(emailSubject, emailBody, request);
    }

    /**
     * Extracts the stack trace from message headers, providing a default if
     * unavailable.
     *
     * @param headers Message headers containing the stack trace.
     * @return The stack trace or a default message if not present.
     */
    private String extractStackTrace(Map<String, Object> headers) {
        Object stackTraceObj = headers.get("x-exception-stacktrace");
        return stackTraceObj != null ? stackTraceObj.toString().replace("\n", "<br>") : NO_STACK_TRACE;
    }

    /**
     * Builds an HTML email body with request and error details.
     *
     * @param request    The failed GitHub score request.
     * @param stackTrace The error stack trace, HTML-escaped.
     * @return The formatted HTML email body.
     */
    private String buildHtmlEmailBody(GithubScoreRequest request, String stackTrace) {
        String userEmail = request.getEmail() != null ? request.getEmail() : "N/A";
        String requestDetails = request.toString().replace("\n", "<br>");

        return String.format(
                """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>Error Notification</title>
                            <style>
                                body {
                                    font-family: Arial, sans-serif;
                                    line-height: 1.6;
                                    color: #333;
                                    margin: 0;
                                    padding: 0;
                                    background-color: #f4f4f4;
                                }
                                .container {
                                    max-width: 600px;
                                    margin: 20px auto;
                                    background-color: #fff;
                                    padding: 20px;
                                    border-radius: 8px;
                                    box-shadow: 0 0 10px rgba(0,0,0,0.1);
                                }
                                h2 {
                                    color: #d9534f;
                                    margin-top: 0;
                                }
                                .section {
                                    margin-bottom: 20px;
                                }
                                .label {
                                    font-weight: bold;
                                    color: #555;
                                }
                                .error-details {
                                    background-color: #f8f8f8;
                                    padding: 10px;
                                    border-left: 4px solid #d9534f;
                                    font-family: 'Courier New', Courier, monospace;
                                    white-space: pre-wrap;
                                    word-wrap: break-word;
                                }
                                .footer {
                                    margin-top: 20px;
                                    font-size: 12px;
                                    color: #777;
                                    text-align: center;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <h2>GitHub Score Processing Error</h2>
                                <p>Dear Supervisor,</p>
                                <p>An error occurred while processing a request after 5 retries.</p>

                                <div class="section">
                                    <span class="label">User:</span> %s
                                </div>
                                <div class="section">
                                    <span class="label">Email:</span> %s
                                </div>
                                <div class="section">
                                    <span class="label">Error Details:</span>
                                    <div class="error-details">%s</div>
                                </div>
                                <div class="section">
                                    <span class="label">Original Request:</span>
                                    <div class="error-details">%s</div>
                                </div>

                                <div class="footer">
                                    <p>This is an automated notification from TeamBond Backend.</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                request.getUsername(),
                userEmail,
                stackTrace,
                requestDetails);
    }

    /**
     * Sends HTML email notifications to supervisors listed in SUPERVISOR_EMAILS.
     *
     * @param subject The email subject.
     * @param body    The HTML email body.
     * @param request The failed request for logging purposes.
     */
    private void sendSupervisorEmails(String subject, String body, GithubScoreRequest request) {
        if (supervisorEmails == null || supervisorEmails.trim().isEmpty()) {
            logger.error("No supervisor emails configured in SUPERVISOR_EMAILS");
            return;
        }

        try {
            String[] emails = Arrays.stream(supervisorEmails.split(","))
                    .map(String::trim)
                    .filter(email -> !email.isEmpty())
                    .toArray(String[]::new);

            if (emails.length == 0) {
                logger.error("No valid supervisor emails found in SUPERVISOR_EMAILS");
                return;
            }

            logger.debug("Sending emails to supervisors: {}", String.join(", ", emails));
            for (String email : emails) {
                logger.info("Sending email: {}", email);
                mailService.sendEmail(email, subject, body);
            }
            logger.info("Emails sent to supervisors for failed message: {}", request);
        } catch (Exception e) {
            logger.error("Failed to send emails to supervisors for user {}: {}", request.getUsername(), e.getMessage());
        }
    }
}
